package com.ticketmind.agent.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketmind.agent.core.SystemPromptMemoryAgent;
import com.ticketmind.common.BusinessException;
import com.ticketmind.common.ResultCode;
import com.ticketmind.config.AgentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SystemPromptMemoryService {

    private static final String KEY_PREFIX = "ticket-mind:system-prompt:memory:";
    private static final String EMPTY_INJECTION = "";

    private final StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper;

    private final AgentProperties properties;

    private final SystemPromptMemoryAgent memoryAgent;

    public void updateFromUserMessage(String memoryId, String userMessage) {
        if (!properties.getSystemPromptMemory().isEnabled() || !StringUtils.hasText(memoryId)
                || !StringUtils.hasText(userMessage)) {
            return;
        }

        try {
            List<SystemPromptMemory> extracted = extract(userMessage);
            if (extracted.isEmpty()) {
                cleanup(memoryId, Instant.now());
                return;
            }
            save(memoryId, extracted);
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.UNKNOWN_SERVER_ERROR, "获取用户偏好失败");
        }
    }

    public String renderForSystemPrompt(String memoryId) {
        if (!properties.getSystemPromptMemory().isEnabled() || !StringUtils.hasText(memoryId)) {
            return EMPTY_INJECTION;
        }

        try {
            List<SystemPromptMemory> memories = activeMemories(memoryId, Instant.now());
            if (memories.isEmpty()) {
                return EMPTY_INJECTION;
            }

            StringBuilder builder = new StringBuilder();
            builder.append("""

                    当前可用记忆：
                    - 这些记忆是用户显式要求保留或可稳定影响后续响应的偏好与约束。
                    - 请在不违反更高优先级指令和 JSON 输出格式的前提下遵循。
                    """);
            for (SystemPromptMemory memory : memories) {
                builder.append("- [")
                        .append(memory.type())
                        .append("/")
                        .append(formatScope(memory.scope()))
                        .append("] ")
                        .append(memory.content())
                        .append('\n');
            }
            return builder.toString();
        } catch (Exception ex) {
            return EMPTY_INJECTION;
        }
    }

    public void markPromptInjected(String memoryId) {
        if (!properties.getSystemPromptMemory().isEnabled() || !StringUtils.hasText(memoryId)) {
            return;
        }

        try {
            Instant now = Instant.now();
            List<SystemPromptMemory> updated = new ArrayList<>();
            boolean changed = false;
            for (SystemPromptMemory memory : readAll(memoryId).values()) {
                if (isExpired(memory, now)) {
                    changed = true;
                    continue;
                }
                String nextScope = decrementScope(memory.scope());
                if (nextScope == null) {
                    changed = true;
                    continue;
                }
                if (!Objects.equals(nextScope, memory.scope())) {
                    updated.add(copy(memory, memory.content(), nextScope, memory.priority(), now, expiresAt(now)));
                    changed = true;
                } else {
                    updated.add(copy(memory, memory.content(), memory.scope(), memory.priority(), memory.updatedAt(), expiresAt(now)));
                    changed = true;
                }
            }

            if (changed) {
                replaceAll(memoryId, updated, now);
            }
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.UNKNOWN_SERVER_ERROR, "更新用户偏好失败");
        }
    }

    private List<SystemPromptMemory> extract(String userMessage) throws Exception {
        String response = memoryAgent.extract(userMessage);
        if (!StringUtils.hasText(response)) {
            return List.of();
        }

        JsonNode memoriesNode = objectMapper.readTree(response).path("memories");
        if (!memoriesNode.isArray()) {
            return List.of();
        }

        Instant now = Instant.now();
        List<SystemPromptMemory> memories = new ArrayList<>();
        int limit = Math.max(1, properties.getSystemPromptMemory().getMaxItems());
        for (JsonNode node : memoriesNode) {
            if (memories.size() >= limit) {
                break;
            }
            String content = text(node, "content");
            if (!StringUtils.hasText(content)) {
                continue;
            }
            String scope = normalizeScope(node.path("scope"));
            memories.add(new SystemPromptMemory(
                    UUID.randomUUID().toString(),
                    normalizeType(text(node, "type")),
                    trim(content, 160),
                    scope,
                    clamp(node.path("priority").asInt(5), 1, 10),
                    now,
                    now,
                    expiresAt(now)
            ));
        }
        return memories;
    }

    private void save(String memoryId, List<SystemPromptMemory> newMemories) throws Exception {
        Instant now = Instant.now();
        Map<String, SystemPromptMemory> merged = new LinkedHashMap<>();
        for (SystemPromptMemory existing : readAll(memoryId).values()) {
            if (!isExpired(existing, now)) {
                merged.put(fingerprint(existing), existing);
            }
        }
        for (SystemPromptMemory incoming : newMemories) {
            String fingerprint = fingerprint(incoming);
            SystemPromptMemory existing = merged.get(fingerprint);
            if (existing == null) {
                merged.put(fingerprint, incoming);
            } else {
                merged.put(fingerprint, merge(existing, incoming, now));
            }
        }

        List<SystemPromptMemory> compacted = compact(new ArrayList<>(merged.values()), now);
        replaceAll(memoryId, compacted, now);
    }

    private void cleanup(String memoryId, Instant now) throws Exception {
        List<SystemPromptMemory> active = activeMemories(memoryId, now);
        replaceAll(memoryId, active, now);
    }

    private List<SystemPromptMemory> activeMemories(String memoryId, Instant now) throws Exception {
        List<SystemPromptMemory> memories = readAll(memoryId).values().stream()
                .filter(memory -> !isExpired(memory, now))
                .toList();
        List<SystemPromptMemory> compacted = compact(memories, now);
        if (compacted.size() != memories.size()) {
            replaceAll(memoryId, compacted, now);
        }
        return compacted;
    }

    private Map<String, SystemPromptMemory> readAll(String memoryId) throws Exception {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key(memoryId));
        Map<String, SystemPromptMemory> memories = new LinkedHashMap<>();
        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            String field = String.valueOf(entry.getKey());
            String value = String.valueOf(entry.getValue());
            if (!StringUtils.hasText(value)) {
                continue;
            }
            memories.put(field, objectMapper.readValue(value, SystemPromptMemory.class));
        }
        return memories;
    }

    private void replaceAll(String memoryId, List<SystemPromptMemory> memories, Instant now) throws Exception {
        String key = key(memoryId);
        redisTemplate.delete(key);
        if (memories.isEmpty()) {
            return;
        }

        Map<String, String> values = new LinkedHashMap<>();
        for (SystemPromptMemory memory : memories) {
            values.put(memory.id(), objectMapper.writeValueAsString(memory));
        }
        redisTemplate.opsForHash().putAll(key, values);
        redisTemplate.expire(key, keyTtl(memories, now));
    }

    private List<SystemPromptMemory> compact(List<SystemPromptMemory> memories, Instant now) {
        int limit = Math.max(1, properties.getSystemPromptMemory().getMaxItems());
        Map<String, SystemPromptMemory> deduped = new LinkedHashMap<>();
        for (SystemPromptMemory memory : memories) {
            if (isExpired(memory, now)) {
                continue;
            }
            String fingerprint = fingerprint(memory);
            deduped.compute(fingerprint, (k, existing) -> existing == null ? memory : merge(existing, memory, now));
        }
        return deduped.values().stream()
                .sorted(memoryComparator())
                .limit(limit)
                .toList();
    }

    private SystemPromptMemory merge(SystemPromptMemory existing, SystemPromptMemory incoming, Instant now) {
        String content = incoming.content().length() >= existing.content().length()
                ? incoming.content()
                : existing.content();
        String scope = longerScope(existing.scope(), incoming.scope());
        int priority = Math.max(existing.priority(), incoming.priority());
        Instant createdAt = existing.createdAt().isBefore(incoming.createdAt()) ? existing.createdAt() : incoming.createdAt();
        return new SystemPromptMemory(
                existing.id(),
                incoming.type(),
                content,
                scope,
                priority,
                createdAt,
                now,
                expiresAt(now)
        );
    }

    private SystemPromptMemory copy(SystemPromptMemory memory,
                                    String content,
                                    String scope,
                                    int priority,
                                    Instant updatedAt,
                                    Instant expiresAt) {
        return new SystemPromptMemory(
                memory.id(),
                memory.type(),
                content,
                scope,
                priority,
                memory.createdAt(),
                updatedAt,
                expiresAt
        );
    }

    private Comparator<SystemPromptMemory> memoryComparator() {
        return Comparator
                .comparingInt(SystemPromptMemory::priority).reversed()
                .thenComparing(Comparator.comparingInt((SystemPromptMemory memory) -> scopeRank(memory.scope())).reversed())
                .thenComparing(SystemPromptMemory::updatedAt, Comparator.reverseOrder());
    }

    private Duration keyTtl(List<SystemPromptMemory> memories, Instant now) {
        Instant latestExpiresAt = memories.stream()
                .map(SystemPromptMemory::expiresAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(now.plus(sessionTtl()));
        Duration ttl = Duration.between(now, latestExpiresAt);
        if (ttl.isNegative() || ttl.isZero()) {
            return Duration.ofMinutes(1);
        }
        return ttl;
    }

    private Instant expiresAt(Instant now) {
        return now.plus(sessionTtl());
    }

    private Duration sessionTtl() {
        return Duration.ofHours(Math.max(1, properties.getChat().getShortMemoryTtlHours()));
    }

    private boolean isExpired(SystemPromptMemory memory, Instant now) {
        return memory.expiresAt() != null && !memory.expiresAt().isAfter(now);
    }

    private String decrementScope(String scope) {
        if ("permanent".equals(scope)) {
            return scope;
        }
        int remainingTurns = parseTurnScope(scope);
        if (remainingTurns <= 1) {
            return null;
        }
        return String.valueOf(remainingTurns - 1);
    }

    private String longerScope(String left, String right) {
        return scopeRank(left) >= scopeRank(right) ? left : right;
    }

    private int scopeRank(String scope) {
        if ("permanent".equals(scope)) {
            return Integer.MAX_VALUE;
        }
        return parseTurnScope(scope);
    }

    private String fingerprint(SystemPromptMemory memory) {
        return normalizeType(memory.type()) + ":" + normalizeContent(memory.content());
    }

    private String normalizeContent(String value) {
        return StringUtils.hasText(value)
                ? value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "")
                : "";
    }

    private String normalizeType(String value) {
        if (!StringUtils.hasText(value)) {
            return "other";
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "preference", "response_format", "language_style", "travel_preference",
                    "notification_preference", "constraint", "other" -> normalized;
            default -> "other";
        };
    }

    private String normalizeScope(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return "permanent";
        }
        if (value.isInt() || value.isLong()) {
            return String.valueOf(clamp(value.asInt(), 1, 10));
        }
        if (value.isTextual()) {
            String normalized = value.asText().strip().toLowerCase(Locale.ROOT);
            if ("permanent".equals(normalized)) {
                return "permanent";
            }
            try {
                return String.valueOf(clamp(Integer.parseInt(normalized), 1, 10));
            } catch (NumberFormatException ignored) {
                return "permanent";
            }
        };
        return "permanent";
    }

    private int parseTurnScope(String scope) {
        try {
            return clamp(Integer.parseInt(scope), 1, 10);
        } catch (Exception ex) {
            return 1;
        }
    }

    private String formatScope(String scope) {
        if ("permanent".equals(scope)) {
            return "本会话内一直有效";
        }
        return "下" + parseTurnScope(scope) + "条对话有效";
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText().strip() : "";
    }

    private String trim(String value, int maxLength) {
        String stripped = value.strip();
        if (stripped.length() <= maxLength) {
            return stripped;
        }
        return stripped.substring(0, maxLength);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String key(String memoryId) {
        return KEY_PREFIX + memoryId;
    }
}

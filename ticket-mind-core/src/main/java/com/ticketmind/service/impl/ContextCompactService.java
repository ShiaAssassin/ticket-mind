package com.ticketmind.service.impl;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import com.ticketmind.agent.core.SummaryAgent;
import com.ticketmind.config.AgentProperties;
import com.ticketmind.model.dto.MemoryMessage;
import com.ticketmind.model.entity.MessageRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Four-layer context compaction pipeline.
 *
 * <p>Execution order: large tool result spillover, message-count trimming,
 * earlier tool result placeholder compaction, full summary, emergency truncation.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContextCompactService {

    private static final Encoding DEFAULT_ENCODING =
            Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);

    private static final Pattern TOOL_CALL_PATTERN = Pattern.compile(
            "(?is)(tool[_ -]?call|function[_ -]?call|调用工具|工具调用|工具请求|\"tool_calls\"|\"toolCalls\"|\"function_call\")");

    private static final Pattern TOOL_RESULT_PATTERN = Pattern.compile(
            "(?is)(tool[_ -]?result|function[_ -]?result|observation|工具返回|工具结果|调用结果|执行结果)");

    private final SummaryAgent summaryAgent;

    private final AgentProperties properties;

    public List<MemoryMessage> compact(String sessionId, List<MemoryMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }

        List<MemoryMessage> compacted = new ArrayList<>(messages);
        compacted = toolResultBudget(sessionId, compacted);
        compacted = snipCompact(compacted);
        compacted = microCompact(compacted);
        compacted = summarize(compacted);
        compacted = reactiveCompact(compacted);
        return compacted;
    }

    public int countTokens(List<MemoryMessage> messages) {
        return DEFAULT_ENCODING.countTokens(formatConversation(messages));
    }

    private List<MemoryMessage> toolResultBudget(String sessionId, List<MemoryMessage> messages) {
        AgentProperties.ContextCompact config = config();
        int maxChars = Math.max(1, config.getToolResultMaxChars());
        Set<Integer> toolResultIndexes = toolResultIndexes(messages);
        List<MemoryMessage> result = new ArrayList<>(messages.size());

        for (int i = 0; i < messages.size(); i++) {
            MemoryMessage message = messages.get(i);
            String content = content(message);
            if (!toolResultIndexes.contains(i) || content.length() <= maxChars) {
                result.add(message);
                continue;
            }

            String storedPath = storeToolResult(sessionId, i, content);
            if (storedPath == null) {
                result.add(message);
                continue;
            }
            result.add(rewrite(message, String.format(config.getStoredToolResultPlaceholder(), storedPath)));
        }
        return result;
    }

    private List<MemoryMessage> snipCompact(List<MemoryMessage> messages) {
        AgentProperties.ContextCompact config = config();
        List<MemoryMessage> cleaned = deleteGarbageRounds(messages);
        int threshold = Math.max(2, config.getMessageThreshold());
        if (cleaned.size() <= threshold) {
            return cleaned;
        }

        int headCount = Math.max(0, Math.min(config.getHeadMessageCount(), cleaned.size()));
        int tailCount = Math.max(0, threshold - headCount);
        int tailStart = Math.max(headCount, cleaned.size() - tailCount);
        boolean[] keep = new boolean[cleaned.size()];

        for (int i = 0; i < headCount; i++) {
            keep[i] = true;
        }
        for (int i = tailStart; i < cleaned.size(); i++) {
            keep[i] = true;
        }
        protectToolPairs(cleaned, keep);

        List<MemoryMessage> result = new ArrayList<>();
        for (int i = 0; i < cleaned.size(); i++) {
            if (keep[i]) {
                result.add(cleaned.get(i));
            }
        }
        return result;
    }

    private List<MemoryMessage> microCompact(
            List<MemoryMessage> messages) {
        AgentProperties.ContextCompact config = config();
        int keepRecent = Math.max(0, config.getRecentToolResultCount());
        Set<Integer> toolResultIndexes = toolResultIndexes(messages);
        Set<Integer> recentToolResultIndexes = new HashSet<>();
        int seen = 0;

        for (int i = messages.size() - 1; i >= 0; i--) {
            if (!toolResultIndexes.contains(i)) {
                continue;
            }
            if (seen < keepRecent) {
                recentToolResultIndexes.add(i);
            }
            seen++;
        }

        List<MemoryMessage> result = new ArrayList<>(messages.size());
        for (int i = 0; i < messages.size(); i++) {
            MemoryMessage message = messages.get(i);
            if (toolResultIndexes.contains(i) && !recentToolResultIndexes.contains(i)) {
                result.add(rewrite(message, config.getEarlierToolResultPlaceholder()));
            } else {
                result.add(message);
            }
        }
        return result;
    }

    private List<MemoryMessage> summarize(
            List<MemoryMessage> messages) {
        int threshold = tokenThreshold();
        if (countTokens(messages) <= threshold) {
            return messages;
        }

        try {
            String summary = summaryAgent.summarize(formatConversation(messages));
            if (!StringUtils.hasText(summary)) {
                return messages;
            }
            return List.of(new MemoryMessage(
                    MessageRole.SYSTEM,
                    "上下文摘要:\n" + summary.strip()
            ));
        } catch (Exception exception) {
            log.warn("Context summary failed, emergency truncation will be used if still over threshold", exception);
            return messages;
        }
    }

    private List<MemoryMessage> reactiveCompact(
            List<MemoryMessage> messages) {
        int threshold = tokenThreshold();
        if (countTokens(messages) <= threshold) {
            return messages;
        }

        int targetTokens = Math.max(1, (int) Math.floor(threshold * emergencyTargetRatio()));
        List<MessageBlock> blocks = messageBlocks(messages);
        List<MemoryMessage> selected = new ArrayList<>();
        int selectedTokens = 0;

        for (int i = blocks.size() - 1; i >= 0; i--) {
            MessageBlock block = blocks.get(i);
            List<MemoryMessage> blockMessages = messages.subList(block.start(), block.end());
            int blockTokens = countTokens(blockMessages);
            if (selectedTokens + blockTokens <= targetTokens) {
                selected.addAll(0, blockMessages);
                selectedTokens += blockTokens;
                continue;
            }
            if (selected.isEmpty() && block.size() == 1 && !isToolCall(messages.get(block.start()))) {
                MemoryMessage truncated = truncateSingleMessage(messages.get(block.start()), targetTokens);
                if (countTokens(List.of(truncated)) <= targetTokens) {
                    selected.add(truncated);
                }
                break;
            }
        }

        return selected;
    }

    private MemoryMessage truncateSingleMessage(MemoryMessage message,
                                                                int targetTokens) {
        String content = content(message);
        String suffix = "\n[Context hard-truncated.]";
        int low = 0;
        int high = content.length();
        int best = 0;

        while (low <= high) {
            int mid = low + (high - low) / 2;
            MemoryMessage candidate = rewrite(message, content.substring(content.length() - mid) + suffix);
            if (countTokens(List.of(candidate)) <= targetTokens) {
                best = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return rewrite(message, content.substring(content.length() - best) + suffix);
    }

    private List<MemoryMessage> deleteGarbageRounds(
            List<MemoryMessage> messages) {
        return messages.stream()
                .filter(message -> !isGarbage(message))
                .toList();
    }

    private boolean isGarbage(MemoryMessage message) {
        if (message == null || !StringUtils.hasText(message.content())) {
            return true;
        }
        String normalized = message.content().strip().toLowerCase(Locale.ROOT);
        return normalized.equals("null")
                || normalized.equals("undefined")
                || normalized.equals("{}")
                || normalized.equals("[]");
    }

    private void protectToolPairs(List<MemoryMessage> messages, boolean[] keep) {
        for (int i = 0; i < messages.size() - 1; i++) {
            if (!isToolCall(messages.get(i)) || !isToolResult(messages, i + 1)) {
                continue;
            }
            if (keep[i] || keep[i + 1]) {
                keep[i] = true;
                keep[i + 1] = true;
            }
        }
    }

    private Set<Integer> toolResultIndexes(List<MemoryMessage> messages) {
        Set<Integer> indexes = new HashSet<>();
        for (int i = 0; i < messages.size(); i++) {
            if (isToolResult(messages, i)) {
                indexes.add(i);
            }
        }
        return indexes;
    }

    private List<MessageBlock> messageBlocks(List<MemoryMessage> messages) {
        List<MessageBlock> blocks = new ArrayList<>();
        int index = 0;
        while (index < messages.size()) {
            if (index < messages.size() - 1
                    && isToolCall(messages.get(index))
                    && isToolResult(messages, index + 1)) {
                blocks.add(new MessageBlock(index, index + 2));
                index += 2;
            } else {
                blocks.add(new MessageBlock(index, index + 1));
                index++;
            }
        }
        return blocks;
    }

    private boolean isToolCall(MemoryMessage message) {
        return message != null && TOOL_CALL_PATTERN.matcher(content(message)).find();
    }

    private boolean isToolResult(List<MemoryMessage> messages, int index) {
        MemoryMessage message = messages.get(index);
        if (message == null) {
            return false;
        }
        if (TOOL_RESULT_PATTERN.matcher(content(message)).find()) {
            return true;
        }
        return index > 0
                && isToolCall(messages.get(index - 1))
                && message.role() != MessageRole.USER
                && StringUtils.hasText(message.content());
    }

    private String storeToolResult(String sessionId, int index, String content) {
        try {
            Path directory = localStoreDirectory();
            Files.createDirectories(directory);
            String fileName = safeFilePart(sessionId) + "-" + Instant.now().toEpochMilli() + "-" + index + ".txt";
            Path target = directory.resolve(fileName).normalize();
            Files.writeString(target, content, StandardCharsets.UTF_8);
            return target.toAbsolutePath().toString();
        } catch (IOException exception) {
            log.warn("Failed to store large tool result for sessionId={}", sessionId, exception);
            return null;
        }
    }

    private String formatConversation(List<MemoryMessage> messages) {
        StringBuilder builder = new StringBuilder();
        for (MemoryMessage message : messages) {
            builder.append(roleName(message))
                    .append(": ")
                    .append(content(message))
                    .append('\n');
        }
        return builder.toString();
    }

    private MemoryMessage rewrite(MemoryMessage message, String content) {
        return new MemoryMessage(message.role(), content);
    }

    private String roleName(MemoryMessage message) {
        if (message == null || message.role() == null) {
            return "UNKNOWN";
        }
        return message.role().name();
    }

    private String content(MemoryMessage message) {
        return message == null || message.content() == null ? "" : message.content();
    }

    private Path localStoreDirectory() {
        String directory = config().getLocalStoreDirectory();
        if (!StringUtils.hasText(directory)) {
            directory = "context-compact/tool-results";
        }
        return Paths.get(directory).toAbsolutePath().normalize();
    }

    private String safeFilePart(String value) {
        if (!StringUtils.hasText(value)) {
            return "unknown-session";
        }
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private int tokenThreshold() {
        return Math.max(1, config().getTokenThreshold());
    }

    private double emergencyTargetRatio() {
        double ratio = config().getEmergencyTargetRatio();
        if (ratio <= 0 || ratio > 1) {
            return 0.8;
        }
        return ratio;
    }

    private AgentProperties.ContextCompact config() {
        return properties.getContextCompact();
    }

    private record MessageBlock(int start, int end) {
        int size() {
            return end - start;
        }
    }
}

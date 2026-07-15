package com.ticketmind.service.memory;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketmind.common.BusinessException;
import com.ticketmind.common.ResultCode;
import com.ticketmind.config.AgentProperties;
import com.ticketmind.model.entity.MessageRole;
import lombok.AllArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Redis 短期记忆服务。
 *
 * <p>每个会话只保留最近 N 轮上下文，完整对话仍然写入 MySQL 作为长期记忆。</p>
 */
@Service
@AllArgsConstructor
public class ShortTermMemory {

    private final String KEY_PREFIX = "ticket-mind:chat:short-memory:";

    private final StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper;

    private final AgentProperties properties;


    public void append(String sessionId, MessageRole role, String content) {
        try {
            String key = key(sessionId);
            String value = objectMapper.writeValueAsString(new MemoryMessage(role, content));
            redisTemplate.opsForList().rightPush(key, value);
            redisTemplate.opsForList().trim(key, -messageLimit(), -1);
            redisTemplate.expire(key, ttl());
        } catch (Exception exception) {
            throw new RedisException("Redis short-term memory append skipped: " + exception.getMessage());
        }
    }

    public List<MemoryMessage> recent(String sessionId) {
        try {
            List<String> values = redisTemplate.opsForList().range(key(sessionId), 0, -1);
            if(values == null || values.isEmpty()) return List.of();
            return values.stream()
                    .map(this::readMessage)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (Exception exception) {
            return List.of();
        }
    }
    public void refresh(String sessionId, List<MemoryMessage> messages) {
        if (messages.isEmpty()) return;
        String key = key(sessionId);
        redisTemplate.delete(key);
        List<String> values = messages.stream()
                .skip(Math.max(0, messages.size() - messageLimit()))
                .map(this::writeMessage)
                .toList();
        redisTemplate.opsForList().rightPushAll(key, values);
        redisTemplate.expire(key, ttl());
    }

    private MemoryMessage readMessage(String value) {
        try {
            return objectMapper.readValue(value, MemoryMessage.class);
        } catch (Exception exception) {
            throw new RedisException("Failed to deserialize memory message");
        }
    }

    private String writeMessage(MemoryMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (Exception exception) {
            throw new RedisException("Failed to serialize memory message");
        }
    }

    private int messageLimit() {
        return Math.max(2, properties.getChat().getHistoryLimit() * 2);
    }

    private Duration ttl() {
        return Duration.ofHours(Math.max(1, properties.getChat().getShortMemoryTtlHours()));
    }

    private String key(String sessionId) {
        return KEY_PREFIX + sessionId;
    }

    public record MemoryMessage(MessageRole role, String content) {
    }

    private static class RedisException extends BusinessException {
        RedisException(String message) {
            super(ResultCode.CACHE_ERROR, message);
        }
    }
}
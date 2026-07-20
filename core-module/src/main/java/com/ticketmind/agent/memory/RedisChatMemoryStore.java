package com.ticketmind.agent.memory;

import com.ticketmind.common.BusinessException;
import com.ticketmind.common.ResultCode;
import com.ticketmind.config.AgentProperties;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RedisChatMemoryStore implements ChatMemoryStore {

    private static final String KEY_PREFIX = "ticket-mind:chat:memory:";

    private final StringRedisTemplate redisTemplate;

    private final ContextCompactor contextCompactor;

    private final AgentProperties properties;

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        try {
            String value = redisTemplate.opsForValue().get(key(memoryId));
            if (value == null || value.isBlank()) {
                return List.of();
            }
            return ChatMessageDeserializer.messagesFromJson(value);
        } catch (Exception exception) {
            throw new MemoryStoreException("Failed to read chat memory: " + exception.getMessage());
        }
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        try {
            List<ChatMessage> compacted = contextCompactor.compact(memoryId, messages);
            String value = ChatMessageSerializer.messagesToJson(compacted);
            redisTemplate.opsForValue().set(key(memoryId), value, ttl());
        } catch (Exception exception) {
            throw new MemoryStoreException("Failed to update chat memory: " + exception.getMessage());
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        try {
            redisTemplate.delete(key(memoryId));
        } catch (Exception exception) {
            throw new MemoryStoreException("Failed to delete chat memory: " + exception.getMessage());
        }
    }

    private Duration ttl() {
        return Duration.ofHours(Math.max(1, properties.getChat().getShortMemoryTtlHours()));
    }

    private String key(Object memoryId) {
        return KEY_PREFIX + memoryId;
    }

    private static class MemoryStoreException extends BusinessException {
        MemoryStoreException(String message) {
            super(ResultCode.CACHE_ERROR, message);
        }
    }
}

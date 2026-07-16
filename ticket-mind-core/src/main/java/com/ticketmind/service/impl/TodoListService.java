package com.ticketmind.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketmind.common.BusinessException;
import com.ticketmind.common.ResultCode;
import com.ticketmind.config.AgentProperties;
import com.ticketmind.model.dto.TodoItem;
import com.ticketmind.model.dto.TodoListArchiveEvent;
import com.ticketmind.model.dto.TodoListSnapshot;
import com.ticketmind.model.dto.TodoStatus;
import com.ticketmind.service.todo.TodoListArchivePublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ticket-mind.rabbitmq", name = "enabled", havingValue = "true")
public class TodoListService {

    private static final String KEY_PREFIX = "ticket-mind:task:todo-list:";

    private final StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper;

    private final AgentProperties agentProperties;

    private final TodoListArchivePublisher archivePublisher;

    public TodoListSnapshot createOrReplace(String taskId, List<String> contents) {
        validateTaskId(taskId);
        OffsetDateTime now = OffsetDateTime.now();
        List<TodoItem> items = contents == null ? List.of() : contents.stream()
                .filter(StringUtils::hasText)
                .map(content -> new TodoItem(UUID.randomUUID().toString(), content.trim(), TodoStatus.PENDING, now, now))
                .toList();
        TodoListSnapshot snapshot = new TodoListSnapshot(taskId, items, now);
        save(snapshot);
        return snapshot;
    }

    public TodoListSnapshot getActive(String taskId) {
        validateTaskId(taskId);
        String value = redisTemplate.opsForValue().get(key(taskId));
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return readSnapshot(value);
    }

    public TodoListSnapshot updateItemStatus(String taskId, String itemId, TodoStatus status) {
        validateTaskId(taskId);
        if (!StringUtils.hasText(itemId) || status == null) {
            throw new BusinessException(ResultCode.MISSING_REQUIRED_PARAMETER, "itemId and status are required");
        }
        TodoListSnapshot snapshot = requireActive(taskId);
        OffsetDateTime now = OffsetDateTime.now();
        List<TodoItem> updatedItems = snapshot.items().stream()
                .map(item -> item.id().equals(itemId)
                        ? new TodoItem(item.id(), item.content(), status, item.createdAt(), now)
                        : item)
                .toList();
        boolean itemExists = updatedItems.stream().anyMatch(item -> item.id().equals(itemId));
        if (!itemExists) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "Todo item not found");
        }
        TodoListSnapshot updatedSnapshot = new TodoListSnapshot(taskId, updatedItems, now);
        save(updatedSnapshot);
        return updatedSnapshot;
    }

    public TodoListArchiveEvent complete(String taskId) {
        TodoListSnapshot snapshot = requireActive(taskId);
        TodoListArchiveEvent event = new TodoListArchiveEvent(
                taskId,
                List.copyOf(snapshot.items()),
                OffsetDateTime.now()
        );
        archivePublisher.publish(event);
        redisTemplate.delete(key(taskId));
        return event;
    }

    public TodoListSnapshot append(String taskId, String content) {
        validateTaskId(taskId);
        if (!StringUtils.hasText(content)) {
            throw new BusinessException(ResultCode.MISSING_REQUIRED_PARAMETER, "content is required");
        }
        TodoListSnapshot snapshot = requireActive(taskId);
        OffsetDateTime now = OffsetDateTime.now();
        List<TodoItem> items = new ArrayList<>(snapshot.items());
        items.add(new TodoItem(UUID.randomUUID().toString(), content.trim(), TodoStatus.PENDING, now, now));
        TodoListSnapshot updatedSnapshot = new TodoListSnapshot(taskId, List.copyOf(items), now);
        save(updatedSnapshot);
        return updatedSnapshot;
    }

    private TodoListSnapshot requireActive(String taskId) {
        TodoListSnapshot snapshot = getActive(taskId);
        if (snapshot == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "Active TodoList not found");
        }
        return snapshot;
    }

    private void save(TodoListSnapshot snapshot) {
        try {
            redisTemplate.opsForValue().set(key(snapshot.taskId()),
                    objectMapper.writeValueAsString(snapshot),
                    ttl());
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.CACHE_ERROR, "TodoList Redis 写入失败");
        }
    }

    private TodoListSnapshot readSnapshot(String value) {
        try {
            return objectMapper.readValue(value, TodoListSnapshot.class);
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.CACHE_ERROR, "TodoList Redis 读取失败");
        }
    }

    private Duration ttl() {
        return Duration.ofHours(Math.max(1, agentProperties.getTodoList().getActiveTtlHours()));
    }

    private String key(String taskId) {
        return KEY_PREFIX + taskId;
    }

    private void validateTaskId(String taskId) {
        if (!StringUtils.hasText(taskId)) {
            throw new BusinessException(ResultCode.MISSING_REQUIRED_PARAMETER, "taskId is required");
        }
    }
}

package com.ticketmind.service.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketmind.common.BusinessException;
import com.ticketmind.common.ResultCode;
import com.ticketmind.config.AgentProperties;
import com.ticketmind.model.dto.TaskExecutionStatus;
import com.ticketmind.model.dto.TaskPlanItem;
import com.ticketmind.model.dto.TaskPlanSnapshot;
import com.ticketmind.model.dto.TaskPlanStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskPlanService {

    private static final String KEY_PREFIX = "ticket-mind:task:plan:";

    private final StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper;

    private final AgentProperties agentProperties;

    public TaskPlanSnapshot create(String taskType,
                                   String userMessage,
                                   String summary,
                                   List<TaskPlanItem> items) {
        if (!StringUtils.hasText(taskType) || !StringUtils.hasText(userMessage)) {
            throw new BusinessException(ResultCode.MISSING_REQUIRED_PARAMETER, "taskType and userMessage are required");
        }
        OffsetDateTime now = OffsetDateTime.now();
        TaskPlanSnapshot snapshot = new TaskPlanSnapshot(
                UUID.randomUUID().toString(),
                taskType.trim(),
                userMessage.strip(),
                summary == null ? "" : summary.strip(),
                TaskPlanStatus.PLANNING,
                items == null ? List.of() : List.copyOf(items),
                now,
                now
        );
        save(snapshot);
        return snapshot;
    }

    public TaskPlanSnapshot getActive(String planId) {
        validatePlanId(planId);
        String value = redisTemplate.opsForValue().get(key(planId));
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return readSnapshot(value);
    }

    public TaskPlanSnapshot save(TaskPlanSnapshot snapshot) {
        if (snapshot == null || !StringUtils.hasText(snapshot.planId())) {
            throw new BusinessException(ResultCode.MISSING_REQUIRED_PARAMETER, "snapshot.planId is required");
        }
        TaskPlanSnapshot normalizedSnapshot = new TaskPlanSnapshot(
                snapshot.planId(),
                snapshot.taskType(),
                snapshot.userMessage(),
                snapshot.summary(),
                snapshot.status(),
                snapshot.items() == null ? List.of() : List.copyOf(snapshot.items()),
                snapshot.createdAt() == null ? OffsetDateTime.now() : snapshot.createdAt(),
                OffsetDateTime.now()
        );
        try {
            redisTemplate.opsForValue().set(key(normalizedSnapshot.planId()),
                    objectMapper.writeValueAsString(normalizedSnapshot),
                    ttl());
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.CACHE_ERROR, "任务计划 Redis 写入失败");
        }
        return normalizedSnapshot;
    }

    public TaskPlanSnapshot complete(TaskPlanSnapshot snapshot, String finalSummary, TaskPlanStatus finalStatus) {
        if (snapshot == null || !StringUtils.hasText(snapshot.planId())) {
            throw new BusinessException(ResultCode.MISSING_REQUIRED_PARAMETER, "snapshot is required");
        }
        return save(new TaskPlanSnapshot(
                snapshot.planId(),
                snapshot.taskType(),
                snapshot.userMessage(),
                StringUtils.hasText(finalSummary) ? finalSummary.strip() : snapshot.summary(),
                finalStatus == null ? TaskPlanStatus.COMPLETED : finalStatus,
                snapshot.items(),
                snapshot.createdAt(),
                snapshot.updatedAt()
        ));
    }

    public TaskPlanItem updateTask(TaskPlanItem original,
                                   TaskExecutionStatus status,
                                   String result) {
        OffsetDateTime now = OffsetDateTime.now();
        return new TaskPlanItem(
                original.taskCode(),
                original.title(),
                original.description(),
                original.assignee(),
                original.dependencies(),
                status,
                original.predefined(),
                result,
                original.createdAt(),
                now
        );
    }

    private TaskPlanSnapshot readSnapshot(String value) {
        try {
            return objectMapper.readValue(value, TaskPlanSnapshot.class);
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.CACHE_ERROR, "任务计划 Redis 读取失败");
        }
    }

    private Duration ttl() {
        return Duration.ofHours(Math.max(1, agentProperties.getTaskPlan().getActiveTtlHours()));
    }

    private String key(String planId) {
        return KEY_PREFIX + planId;
    }

    private void validatePlanId(String planId) {
        if (!StringUtils.hasText(planId)) {
            throw new BusinessException(ResultCode.MISSING_REQUIRED_PARAMETER, "planId is required");
        }
    }
}

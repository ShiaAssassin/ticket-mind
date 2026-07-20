package com.ticketmind.agent.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketmind.model.dto.TaskAssignee;
import com.ticketmind.model.dto.TaskExecutionStatus;
import com.ticketmind.model.dto.TaskPlanItem;
import com.ticketmind.model.entity.IntentType;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class TaskTemplateCatalog {

    private static final String ROOT = "classpath:tasks/";

    private final ObjectMapper objectMapper;

    private final ResourceLoader resourceLoader;

    private final Map<IntentType, List<TaskTemplateDefinition>> cache = new ConcurrentHashMap<>();

    public List<TaskPlanItem> load(IntentType intentType) {
        if (intentType == null || intentType == IntentType.NON_BUSINESS_CHAT) {
            return List.of();
        }
        List<TaskTemplateDefinition> templates = cache.computeIfAbsent(intentType, this::readTemplates);
        OffsetDateTime now = OffsetDateTime.now();
        return templates.stream()
                .map(template -> new TaskPlanItem(
                        template.taskCode(),
                        template.title(),
                        template.description(),
                        template.assignee(),
                        template.dependencies(),
                        template.dependencies().isEmpty() ? TaskExecutionStatus.READY : TaskExecutionStatus.PENDING,
                        true,
                        "",
                        now,
                        now
                ))
                .toList();
    }

    private List<TaskTemplateDefinition> readTemplates(IntentType intentType) {
        Resource resource = resourceLoader.getResource(ROOT + resourceName(intentType));
        if (!resource.exists()) {
            return List.of();
        }

        try (InputStream inputStream = resource.getInputStream()) {
            JsonNode tasksNode = objectMapper.readTree(inputStream).path("tasks");
            if (!tasksNode.isArray()) {
                return List.of();
            }

            List<TaskTemplateDefinition> templates = new ArrayList<>();
            for (JsonNode taskNode : tasksNode) {
                String taskCode = text(taskNode, "taskCode");
                if (!StringUtils.hasText(taskCode)) {
                    continue;
                }
                templates.add(new TaskTemplateDefinition(
                        taskCode,
                        textOrDefault(taskNode, "title", taskCode),
                        textOrDefault(taskNode, "description", ""),
                        parseAssignee(text(taskNode, "assignee")),
                        stringArray(taskNode.path("dependencies"))
                ));
            }
            return List.copyOf(templates);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to load task templates for " + intentType, ex);
        }
    }

    private String resourceName(IntentType intentType) {
        return intentType.name().toLowerCase().replace('_', '-') + ".json";
    }

    private TaskAssignee parseAssignee(String value) {
        try {
            return TaskAssignee.valueOf(value);
        } catch (Exception ex) {
            return TaskAssignee.BUSINESS_EXECUTOR;
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText().strip() : "";
    }

    private String textOrDefault(JsonNode node, String field, String defaultValue) {
        String value = text(node, field);
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private List<String> stringArray(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isTextual() && StringUtils.hasText(item.asText())) {
                values.add(item.asText().strip());
            }
        }
        return List.copyOf(values);
    }

    private record TaskTemplateDefinition(
            String taskCode,
            String title,
            String description,
            TaskAssignee assignee,
            List<String> dependencies
    ) {
    }
}

package com.ticketmind.service.todo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketmind.common.BusinessException;
import com.ticketmind.common.ResultCode;
import com.ticketmind.config.AgentProperties;
import com.ticketmind.model.dto.TodoListArchiveEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ticket-mind.rabbitmq", name = "enabled", havingValue = "true")
public class LocalFileTodoListArchiveStorage implements TodoListArchiveStorage {

    private final ObjectMapper objectMapper;

    private final AgentProperties agentProperties;

    @Override
    public void store(TodoListArchiveEvent event) {
        try {
            Path archiveDirectory = Path.of(agentProperties.getTodoList().getTempArchiveDirectory());
            Files.createDirectories(archiveDirectory);
            Path archiveFile = archiveDirectory.resolve(filename(event));
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(archiveFile.toFile(), event);
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.UNKNOWN_SERVER_ERROR, "TodoList 临时文件归档失败");
        }
    }

    private String filename(TodoListArchiveEvent event) {
        String completedAt = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                .format(event.completedAt());
        return sanitize(event.taskId()) + "-" + completedAt + ".json";
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown-task";
        }
        return value.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}

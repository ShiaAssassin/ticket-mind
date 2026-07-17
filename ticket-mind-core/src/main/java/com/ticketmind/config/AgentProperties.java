package com.ticketmind.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "ticket-mind")
public class AgentProperties {

    private final Chat chat = new Chat();

    private final Knowledge knowledge = new Knowledge();

    private final Chroma chroma = new Chroma();

    private final Rabbitmq rabbitmq = new Rabbitmq();

    private final TodoList todoList = new TodoList();

    private final ContextCompact contextCompact = new ContextCompact();

    @Data
    public static class Chat {
        private int historyLimit = 10;
        private long shortMemoryTtlHours = 24;
    }

    @Data
    public static class Knowledge {
        private int topK = 4;
        private int chunkSize = 512;
        private int chunkOverlap = 64;
    }

    @Data
    public static class Chroma {
        private boolean enabled = true;
        private String baseUrl = "http://localhost:8000";
        private String knowledgeCollection = "ticket-mind-knowledge";
        private String skillCollection = "ticket-mind-skill";
        private String skillRootPath = "ticket-mind-core/src/main/resources/skills";
        private boolean skillWatchEnabled = true;
    }

    @Data
    public static class Rabbitmq {
        private boolean enabled = false;
        private String knowledgeUploadExchange = "ticket-mind.knowledge.exchange";
        private String knowledgeUploadQueue = "ticket-mind.knowledge.uploaded.queue";
        private String knowledgeUploadRoutingKey = "knowledge.uploaded";
        private String todoListArchiveExchange = "ticket-mind.todo-list.exchange";
        private String todoListArchiveQueue = "ticket-mind.todo-list.archived.queue";
        private String todoListArchiveRoutingKey = "todo-list.archived";
    }

    @Data
    public static class TodoList {
        private long activeTtlHours = 24;
        private String tempArchiveDirectory = "todo-list-archive";
    }

    @Data
    public static class ContextCompact {
        private int messageThreshold = 50;
        private int headMessageCount = 3;
        private int recentToolResultCount = 3;
        private int toolResultMaxChars = 8000;
        private int tokenThreshold = 6000;
        private double emergencyTargetRatio = 0.8;
        private String localStoreDirectory = "context-compact/tool-results";
        private String earlierToolResultPlaceholder = "[Earlier tool result compacted. Re-run if needed.]";
        private String storedToolResultPlaceholder = "[Tool result stored locally: %s. Re-run or inspect the file if needed.]";
    }

}

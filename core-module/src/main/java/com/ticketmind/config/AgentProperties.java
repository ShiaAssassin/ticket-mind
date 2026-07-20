package com.ticketmind.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "ticket-mind")
public class AgentProperties {

    private final Chat chat = new Chat();

    private final Knowledge knowledge = new Knowledge();

    private final Chroma chroma = new Chroma();

    private final Rabbitmq rabbitmq = new Rabbitmq();

    private final TaskPlan taskPlan = new TaskPlan();

    private final ContextCompact contextCompact = new ContextCompact();

    private final SystemPromptMemory systemPromptMemory = new SystemPromptMemory();

    private final Mcp mcp = new Mcp();

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
        private String skillRootPath = "core-module/src/main/resources/skills";
        private boolean skillWatchEnabled = true;
    }

    @Data
    public static class Rabbitmq {
        private boolean enabled = false;
        private String knowledgeUploadExchange = "ticket-mind.knowledge.exchange";
        private String knowledgeUploadQueue = "ticket-mind.knowledge.uploaded.queue";
        private String knowledgeUploadRoutingKey = "knowledge.uploaded";
    }

    @Data
    public static class TaskPlan {
        private long activeTtlHours = 24;
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

    @Data
    public static class SystemPromptMemory {
        private boolean enabled = true;
        private int maxItems = 10;
    }

    @Data
    public static class Mcp {
        private boolean enabled = false;
        private String sseUrl = "";
        private String clientName = "ticket-mind-core";
        private String clientVersion = "0.0.1-SNAPSHOT";
        private String protocolVersion = "2024-11-05";
        private Duration timeout = Duration.ofSeconds(60);
        private Duration initializationTimeout = Duration.ofSeconds(30);
        private Duration toolExecutionTimeout = Duration.ofSeconds(60);
        private Duration resourcesTimeout = Duration.ofSeconds(60);
        private Duration promptsTimeout = Duration.ofSeconds(60);
        private Duration pingTimeout = Duration.ofSeconds(10);
        private Duration reconnectInterval = Duration.ofSeconds(5);
        private boolean logRequests = false;
        private boolean logResponses = false;
    }

}

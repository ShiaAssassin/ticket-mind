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
    }

}

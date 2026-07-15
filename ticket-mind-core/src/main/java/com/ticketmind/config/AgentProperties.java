package com.ticketmind.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "ticket-mind")
public class AgentProperties {

    private final Chat chat = new Chat();

    private final Knowledge knowledge = new Knowledge();

    private final Chroma chroma = new Chroma();

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
        private String skillRootPath = "ticket-mind-core/src/main/java/com/ticketmind/agent/skill";
        private boolean skillWatchEnabled = true;
    }

}

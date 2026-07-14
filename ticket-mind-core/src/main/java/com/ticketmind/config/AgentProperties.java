package com.ticketmind.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "ticket-mind")
public class AgentProperties {

    private final Chat chat = new Chat();

    private final Knowledge knowledge = new Knowledge();

    @Data
    public static class Chat {
        private int historyLimit = 10;
        private long shortMemoryTtlHours = 24;
    }

    @Data
    public static class Knowledge {
        private int topK = 4;
        private boolean useChroma;
        private String chromaBaseUrl = "http://localhost:8000";
        private String chromaCollection = "multimodalAgent_knowledge";
        private int chunkSize = 512;
        private int chunkOverlap = 64;
    }

}

package com.ticketmind.model.dto;

public record KnowledgeUploadResult(
        String filename,
        String source,
        int chunkCount
) {
}

package com.ticketmind.model.dto;

import java.time.OffsetDateTime;

public record KnowledgeDocumentUploadedEvent(
        String filename,
        String source,
        int chunkCount,
        OffsetDateTime uploadedAt
) {
}

package com.ticketmind.model.dto;

public record KnowledgeChunkHit(
        Long chunkId,
        String source,
        String content,
        double score
) {
}

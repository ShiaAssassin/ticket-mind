package com.ticketmind.model.dto;

public record SkillSearchResult(
        String skillName,
        String sourcePath,
        double score,
        String content
) {
}

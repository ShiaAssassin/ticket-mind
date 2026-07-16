package com.ticketmind.model.dto;

import com.ticketmind.model.entity.IntentType;

public record IntentRecognitionResult(
        IntentType intentType,
        double confidence,
        MatchSource matchSource
) {

    public enum MatchSource {
        RULE,
        AGENT
    }
}

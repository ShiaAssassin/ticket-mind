package com.ticketmind.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ticketmind.agent.core.IntentJudgeAgent;
import com.ticketmind.model.dto.IntentRecognitionResult;
import com.ticketmind.model.entity.IntentType;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class IntentRecognitionServiceTest {

    private final IntentJudgeAgent intentJudgeAgent = Mockito.mock(IntentJudgeAgent.class);
    private final IntentRecognitionService intentRecognitionService =
            new IntentRecognitionService(intentJudgeAgent);

    @Test
    void shouldReturnRuleIntentWhenStrongKeywordMatched() {
        IntentRecognitionResult result = intentRecognitionService.recognize("帮我买票，今天就提交订单");

        assertEquals(IntentType.TICKET_BOOKING, result.intentType());
        assertEquals(IntentRecognitionResult.MatchSource.RULE, result.matchSource());
        assertTrue(result.confidence() >= 0.86D);
        verify(intentJudgeAgent, never()).judge("帮我买票，今天就提交订单");
    }

    @Test
    void shouldFallbackToAgentWhenRuleMatchIsNotEnough() {
        when(intentJudgeAgent.judge("我想周末出去散散心")).thenReturn(IntentType.TRIP_PLANNING);

        IntentRecognitionResult result = intentRecognitionService.recognize("我想周末出去散散心");

        assertEquals(IntentType.TRIP_PLANNING, result.intentType());
        assertEquals(IntentRecognitionResult.MatchSource.AGENT, result.matchSource());
        assertEquals(0.60D, result.confidence());
        verify(intentJudgeAgent).judge("我想周末出去散散心");
    }

    @Test
    void shouldReturnInquiryIntentWhenMultipleWeakKeywordsMatched() {
        IntentRecognitionResult result = intentRecognitionService.recognize("帮我查询一下明天的车次和票价");

        assertEquals(IntentType.INFORMATION_INQUIRY, result.intentType());
        assertEquals(IntentRecognitionResult.MatchSource.RULE, result.matchSource());
        assertTrue(result.confidence() >= 0.68D);
        verify(intentJudgeAgent, never()).judge("帮我查询一下明天的车次和票价");
    }
}

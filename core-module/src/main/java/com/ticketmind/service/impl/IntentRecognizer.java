package com.ticketmind.service.impl;

import com.ticketmind.agent.core.IntentJudgeAgent;
import com.ticketmind.model.dto.IntentRecognitionResult;
import com.ticketmind.model.dto.IntentRecognitionResult.MatchSource;
import com.ticketmind.model.entity.IntentType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class IntentRecognizer {

    private static final double BLANK_MESSAGE_CONFIDENCE = 0.20D;
    private static final double AGENT_FALLBACK_CONFIDENCE = 0.60D;

    private final IntentJudgeAgent intentJudgeAgent;

    public IntentRecognitionResult recognize(String userMessage) {
        if (!StringUtils.hasText(userMessage)) {
            return new IntentRecognitionResult(
                    IntentType.NON_BUSINESS_CHAT,
                    BLANK_MESSAGE_CONFIDENCE,
                    MatchSource.RULE
            );
        }

        String normalizedMessage = normalize(userMessage);
        RuleMatch ruleMatch = matchByRule(normalizedMessage);
        if (ruleMatch.enough()) {
            return new IntentRecognitionResult(
                    ruleMatch.intentType(),
                    ruleMatch.confidence(),
                    MatchSource.RULE
            );
        }

        IntentType intentType = intentJudgeAgent.judge(userMessage);
        return new IntentRecognitionResult(intentType, AGENT_FALLBACK_CONFIDENCE, MatchSource.AGENT);
    }

    private RuleMatch matchByRule(String message) {
        RuleMatch bestMatch = RuleMatch.none();
        for (Map.Entry<IntentType, KeywordRule> entry : rules().entrySet()) {
            RuleMatch currentMatch = buildRuleMatch(entry.getKey(), entry.getValue(), message);
            if (currentMatch.betterThan(bestMatch)) {
                bestMatch = currentMatch;
            }
        }
        return bestMatch;
    }

    private RuleMatch buildRuleMatch(IntentType intentType, KeywordRule rule, String message) {
        int strongHits = countMatches(message, rule.strongKeywords());
        int weakHits = countMatches(message, rule.weakKeywords());
        int score = strongHits * 3 + weakHits;
        boolean enough = strongHits > 0 || weakHits >= 2;
        double confidence = calculateRuleConfidence(strongHits, weakHits, enough);
        return new RuleMatch(intentType, score, confidence, enough);
    }

    private double calculateRuleConfidence(int strongHits, int weakHits, boolean enough) {
        if (!enough) {
            return 0D;
        }
        if (strongHits > 0) {
            return Math.min(0.98D, 0.86D + (strongHits - 1) * 0.04D + weakHits * 0.02D);
        }
        return Math.min(0.82D, 0.68D + (weakHits - 2) * 0.06D);
    }

    private int countMatches(String message, List<String> keywords) {
        int matches = 0;
        for (String keyword : keywords) {
            if (message.contains(keyword)) {
                matches++;
            }
        }
        return matches;
    }

    private String normalize(String message) {
        return message.trim().toLowerCase(Locale.ROOT);
    }

    private Map<IntentType, KeywordRule> rules() {
        Map<IntentType, KeywordRule> rules = new LinkedHashMap<>();
        rules.put(IntentType.TICKET_BOOKING, new KeywordRule(
                List.of("帮我买票", "帮我订票", "提交订单", "立即购票", "马上买票", "现在买票", "帮我抢票", "开始抢票", "候补下单", "帮我候补"),
                List.of("买票", "订票", "购票", "下单", "抢票", "候补", "锁座", "占座", "出票")
        ));
        rules.put(IntentType.ORDER_MANAGEMENT, new KeywordRule(
                List.of("查询订单", "查看订单", "取消订单", "支付订单", "申请退票", "申请改签", "订单状态", "未支付订单"),
                List.of("订单", "退票", "改签", "取消", "支付", "退款", "重试支付", "待支付", "已支付")
        ));
        rules.put(IntentType.ACCOUNT_MANAGEMENT, new KeywordRule(
                List.of("登录不上", "无法登录", "账号异常", "忘记密码", "重置密码", "实名认证", "乘车人管理", "添加乘车人"),
                List.of("登录", "注册", "账号", "账户", "密码", "实名", "认证", "乘车人", "手机号", "绑定")
        ));
        rules.put(IntentType.TRIP_PLANNING, new KeywordRule(
                List.of("帮我规划行程", "帮我安排行程", "推荐路线", "推荐车次", "中转方案", "出行方案"),
                List.of("规划", "行程", "路线", "方案", "怎么去", "怎么走", "中转", "推荐", "安排", "往返")
        ));
        rules.put(IntentType.INFORMATION_INQUIRY, new KeywordRule(
                List.of("票价多少", "余票多少", "还有票吗", "什么时候放票", "列车时刻", "购票规则", "退票规则", "改签规则"),
                List.of("查询", "查一下", "车次", "时刻", "票价", "余票", "规则", "多久", "几点", "多久到", "发车", "到达")
        ));
        rules.put(IntentType.NON_BUSINESS_CHAT, new KeywordRule(
                List.of("你好", "在吗", "谢谢你", "辛苦了", "早上好", "晚上好", "午安"),
                List.of("你好", "谢谢", "再见", "哈哈", "好的", "嗯嗯", "hi", "hello")
        ));
        return rules;
    }

    private record KeywordRule(List<String> strongKeywords, List<String> weakKeywords) {
    }

    private record RuleMatch(IntentType intentType, int score, double confidence, boolean enough) {

        private static RuleMatch none() {
            return new RuleMatch(IntentType.NON_BUSINESS_CHAT, -1, 0D, false);
        }

        private boolean betterThan(RuleMatch other) {
            return this.score > other.score
                    || (this.score == other.score && Double.compare(this.confidence, other.confidence) > 0);
        }
    }
}

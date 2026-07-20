package com.ticketmind.mcpserver.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class TicketRuleService {

    public Map<String, Object> queryRules(String ruleTopic) {
        String normalizedTopic = StringUtils.hasText(ruleTopic)
                ? ruleTopic.trim().toLowerCase(Locale.ROOT)
                : "";

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("topic", StringUtils.hasText(ruleTopic) ? ruleTopic.trim() : "通用");

        if (containsAny(normalizedTopic, "退票", "refund")) {
            result.put("summary", "退票通常会按照距开车时间的不同收取梯度手续费，已开车车票一般不能直接按普通规则退票。");
            result.put("highlights", new String[]{
                    "开车前办理退票的灵活度最高。",
                    "越接近发车时间，手续费通常越高。",
                    "具体费率和特殊车票规则以 12306 当次订单页面为准。"
            });
            return result;
        }

        if (containsAny(normalizedTopic, "改签", "change")) {
            result.put("summary", "改签通常要求原车票仍然有效，并且目标车次在可售范围内；是否补差或退差取决于新旧票价。");
            result.put("highlights", new String[]{
                    "改签前先确认目标车次是否有票。",
                    "席别、日期或车次变化可能触发补差价或退差价。",
                    "部分特殊场景需要在指定时间窗口内处理。"
            });
            return result;
        }

        if (containsAny(normalizedTopic, "候补", "waitlist")) {
            result.put("summary", "候补购票适用于目标车次无票场景，系统会在有票回流时按规则尝试兑现。");
            result.put("highlights", new String[]{
                    "可同时提交多个车次和席别提升兑现概率。",
                    "候补结果依赖退票回流和放票情况，不保证成功。",
                    "提交前需确认乘车人、日期和车次范围。"
            });
            return result;
        }

        result.put("summary", "购票、改签、退票、候补等规则会随车次、票种和时间窗口变化，最终以 12306 下单页和订单页展示为准。");
        result.put("highlights", new String[]{
                "购票前先确认实名信息和乘车人信息完整。",
                "临近开车时退改限制通常更多。",
                "涉及学生票、儿童票等特殊票种时，需要额外核验适用条件。"
        });
        return result;
    }

    private boolean containsAny(String source, String... keywords) {
        for (String keyword : keywords) {
            if (source.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}

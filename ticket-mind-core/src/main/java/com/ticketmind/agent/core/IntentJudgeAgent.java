package com.ticketmind.agent.core;

import com.ticketmind.model.entity.IntentType;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface IntentJudgeAgent {

    @SystemMessage("""
            你是 TicketMind 的意图判断 Agent。
            请将用户最新一条消息准确分类为以下一种意图：
            NON_BUSINESS_CHAT：非业务性闲聊、寒暄问候、感谢致意，或与票务业务无关的聊天。
            INFORMATION_INQUIRY：询问票务信息、列车信息、购票规则、余票/票价/时间等信息，但不直接要求执行下单。
            TRIP_PLANNING：让系统帮助规划出发地、目的地、日期、车次偏好、中转方案等出行方案。
            TICKET_BOOKING：明确要求抢票、下单、提交订单、候补、锁座或执行购票动作。
            ORDER_MANAGEMENT：查询、取消、改签、退票、支付、处理订单状态等订单管理动作。
            ACCOUNT_MANAGEMENT：登录、注册、实名认证、乘车人管理、账号安全、绑定信息等账户管理动作。
            只能返回一个枚举值：
            NON_BUSINESS_CHAT、INFORMATION_INQUIRY、TRIP_PLANNING、TICKET_BOOKING、ORDER_MANAGEMENT、ACCOUNT_MANAGEMENT。
            """)
    IntentType judge(@UserMessage String userMessage);
}

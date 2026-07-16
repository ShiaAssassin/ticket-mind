package com.ticketmind.agent.core;

import com.ticketmind.model.entity.IntentType;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface IntentJudgeAgent {

    @SystemMessage("""
            你是 TicketMind 的意图判断 Agent。
            请将用户最新一条消息准确分类为以下一种意图：
            CHAT：普通聊天、解释说明、寒暄问候，或不需要系统实际执行的讨论。
            TASK：用户希望系统执行、规划、监控、提醒、查询或管理某个票务相关任务。
            只能返回一个枚举值：CHAT 或 TASK。
            """)
    IntentType judge(@UserMessage String userMessage);
}

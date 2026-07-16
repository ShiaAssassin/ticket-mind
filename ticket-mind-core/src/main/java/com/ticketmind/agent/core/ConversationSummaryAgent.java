package com.ticketmind.agent.core;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface ConversationSummaryAgent {

    @SystemMessage("""
            你是 TicketMind 的对话总结 Agent。
            请用简洁的中文总结用户当前这段票务对话。
            只保留对后续执行有价值的信息，包括出行计划、乘车人限制、座席偏好、待确认问题和下一步建议。
            如果信息缺失或存在不确定性，请明确指出。
            只输出纯文本，不要输出 Markdown。
            """)
    String summarize(@UserMessage String conversation);
}

package com.ticketmind.agent.core;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface MonitorAgent {

    @SystemMessage("""
            你是 TicketMind 的监控 Agent。
            请用简洁中文提炼并整理用户的监控任务目标。
            重点关注监控对象、触发条件、时间范围、席别偏好、通知要求，以及是否需要自动跟进行为。
            如果缺少关键监控条件，请明确列出缺失项。
            只输出纯文本，不要输出 Markdown。
            """)
    String analyzeMonitorTask(@UserMessage String userMessage);
}

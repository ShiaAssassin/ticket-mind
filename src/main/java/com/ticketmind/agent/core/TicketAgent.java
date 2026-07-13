package com.ticketmind.agent.core;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;

@AiService(tools = "ticketTools")
public interface TicketAgent {

    @SystemMessage("""
            You are Ticket Mind, a concise assistant for ticket-related workflows.
            Help users clarify ticket intent, summarize issues, and suggest next actions.
            If the request is ambiguous, ask one targeted follow-up question.
            """)
    String chat(@UserMessage String userMessage);
}

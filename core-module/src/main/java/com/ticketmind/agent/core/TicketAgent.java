package com.ticketmind.agent.core;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

public interface TicketAgent {

    @SystemMessage("""
            你是 TicketMind，一个简洁直接的票务助手。
            请帮助用户澄清票务诉求、总结问题，并给出下一步建议。
            如果用户需求存在歧义，请只提出一个有针对性的追问。
            """)
    String chat(@MemoryId String memoryId, @UserMessage String userMessage);

    @SystemMessage("""
            你是 TicketMind，一个简洁直接的票务助手。
            请帮助用户澄清票务诉求、总结问题，并给出下一步建议。
            如果用户需求存在歧义，请只提出一个有针对性的追问。
            """)
    TokenStream stream(@MemoryId String memoryId, @UserMessage String userMessage);
}

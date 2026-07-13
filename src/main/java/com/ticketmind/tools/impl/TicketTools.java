package com.ticketmind.tools.impl;

import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class TicketTools {

    @Tool("Returns today's date, useful when the agent needs a concrete date.")
    public String today() {
        return LocalDate.now().toString();
    }
}

package com.ticketmind.mcpserver.tool;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class TicketMcpTools {

    public Map<String, Object> today() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", LocalDate.now().toString());
        result.put("timezone", "system-default");
        return result;
    }

    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("service", "ticket-mind-mcp-server");
        result.put("status", "UP");
        result.put("protocol", "MCP");
        return result;
    }
}

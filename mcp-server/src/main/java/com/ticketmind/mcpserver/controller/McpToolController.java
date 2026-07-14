package com.ticketmind.mcpserver.controller;

import com.ticketmind.mcpserver.tool.TicketMcpTools;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/internal/tools")
public class McpToolController {

    private final TicketMcpTools ticketMcpTools;

    public McpToolController(TicketMcpTools ticketMcpTools) {
        this.ticketMcpTools = ticketMcpTools;
    }

    @GetMapping("/today")
    public Map<String, Object> today() {
        return ticketMcpTools.today();
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return ticketMcpTools.health();
    }
}

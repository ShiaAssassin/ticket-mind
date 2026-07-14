package com.ticketmind.mcpserver.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.ticketmind.mcpserver.service.McpServer;
import com.ticketmind.mcpserver.tool.TicketMcpTools;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/internal/tools")
@AllArgsConstructor
public class McpController {

    private final McpServer mcpServer;

    @PostMapping(value = "/mcp", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> handle(@RequestBody JsonNode request) {
        return mcpServer.handle(request);
    }

}

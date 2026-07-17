package com.ticketmind.agent.tool.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketmind.config.AgentProperties;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class McpToolExecutor {

    private final ObjectMapper objectMapper;
    private final ObjectProvider<McpClient> mcpClientProvider;
    private final AgentProperties agentProperties;

    public String executeTool(String toolName) {
        return executeTool(toolName, Map.of());
    }

    public String executeTool(String toolName, Map<String, Object> arguments) {
        McpClient mcpClient = mcpClientProvider.getIfAvailable();
        if (mcpClient == null) {
            return unavailableMessage();
        }

        try {
            ToolExecutionRequest request = ToolExecutionRequest.builder()
                    .id(UUID.randomUUID().toString())
                    .name(toolName)
                    .arguments(objectMapper.writeValueAsString(arguments == null ? Map.of() : arguments))
                    .build();
            return mcpClient.executeTool(request);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Failed to serialize MCP tool arguments for " + toolName, exception);
        } catch (Exception exception) {
            return "MCP tool execution failed for '" + toolName + "': " + safeMessage(exception);
        }
    }

    public List<ToolSpecification> listTools() {
        McpClient mcpClient = mcpClientProvider.getIfAvailable();
        if (mcpClient == null) {
            return List.of();
        }
        return mcpClient.listTools();
    }

    private String unavailableMessage() {
        if (!agentProperties.getMcp().isEnabled()) {
            return "MCP client is disabled. Configure ticket-mind.mcp.enabled=true and ticket-mind.mcp.sse-url before using remote tools.";
        }
        return "MCP client is unavailable. Check ticket-mind.mcp.sse-url and confirm the MCP server is reachable.";
    }

    private String safeMessage(Exception exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }
}

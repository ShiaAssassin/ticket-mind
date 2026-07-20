package com.ticketmind.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketmind.config.AgentProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class McpToolGateway {

    private final ObjectMapper objectMapper;
    private final AgentProperties agentProperties;
    private final HttpClient httpClient;
    private final AtomicLong requestId = new AtomicLong(1L);

    public McpToolGateway(ObjectMapper objectMapper, AgentProperties agentProperties) {
        this.objectMapper = objectMapper;
        this.agentProperties = agentProperties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(resolveTimeout(agentProperties))
                .build();
    }

    public String call(String toolName, Map<String, Object> arguments) {
        AgentProperties.Mcp mcp = agentProperties.getMcp();
        if (!mcp.isEnabled()) {
            throw new IllegalStateException("MCP is disabled. Set ticket-mind.mcp.enabled=true first.");
        }

        String rpcUrl = resolveRpcUrl(mcp);
        if (!StringUtils.hasText(rpcUrl)) {
            throw new IllegalStateException("ticket-mind.mcp.rpc-url must be configured when MCP is enabled.");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jsonrpc", "2.0");
        payload.put("id", requestId.getAndIncrement());
        payload.put("method", "tools/call");
        payload.put("params", Map.of(
                "name", toolName,
                "arguments", arguments == null ? Map.of() : arguments
        ));

        try {
            String body = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder(URI.create(rpcUrl))
                    .timeout(resolveTimeout(agentProperties))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("MCP request failed with HTTP status " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode error = root.path("error");
            if (!error.isMissingNode() && !error.isNull()) {
                throw new IllegalStateException(error.path("message").asText("MCP tool call failed"));
            }

            JsonNode content = root.path("result").path("content");
            if (!content.isArray() || content.isEmpty()) {
                return response.body();
            }

            JsonNode firstItem = content.get(0);
            if (firstItem.path("type").asText("").equals("text")) {
                return firstItem.path("text").asText(response.body());
            }
            return firstItem.toPrettyString();
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Failed to call MCP tool: " + toolName, ex);
        }
    }

    private Duration resolveTimeout(AgentProperties agentProperties) {
        Duration timeout = agentProperties.getMcp().getTimeout();
        return timeout == null ? Duration.ofSeconds(60) : timeout;
    }

    private String resolveRpcUrl(AgentProperties.Mcp mcp) {
        if (StringUtils.hasText(mcp.getRpcUrl())) {
            return mcp.getRpcUrl().trim();
        }
        return StringUtils.hasText(mcp.getSseUrl()) ? mcp.getSseUrl().trim() : "";
    }
}

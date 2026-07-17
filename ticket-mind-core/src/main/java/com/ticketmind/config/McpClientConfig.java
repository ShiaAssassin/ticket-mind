package com.ticketmind.config;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@ConditionalOnProperty(prefix = "ticket-mind.mcp", name = "enabled", havingValue = "true")
public class McpClientConfig {

    @Bean(destroyMethod = "close")
    public McpClient ticketMindMcpClient(AgentProperties agentProperties) {
        AgentProperties.Mcp config = agentProperties.getMcp();
        if (!StringUtils.hasText(config.getSseUrl())) {
            throw new IllegalStateException("ticket-mind.mcp.sse-url must be configured when MCP is enabled");
        }

        HttpMcpTransport transport = new HttpMcpTransport.Builder()
                .sseUrl(config.getSseUrl())
                .timeout(config.getTimeout())
                .logRequests(config.isLogRequests())
                .logResponses(config.isLogResponses())
                .build();

        return new DefaultMcpClient.Builder()
                .transport(transport)
                .clientName(config.getClientName())
                .clientVersion(config.getClientVersion())
                .protocolVersion(config.getProtocolVersion())
                .initializationTimeout(config.getInitializationTimeout())
                .toolExecutionTimeout(config.getToolExecutionTimeout())
                .resourcesTimeout(config.getResourcesTimeout())
                .promptsTimeout(config.getPromptsTimeout())
                .pingTimeout(config.getPingTimeout())
                .reconnectInterval(config.getReconnectInterval())
                .build();
    }
}

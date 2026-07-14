package com.ticketmind.mcpserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ticket-mind.mcp")
public class McpServerProperties {

    /**
     * Transport mode reserved for MCP integration wiring.
     * Current default keeps the service deployable over HTTP.
     */
    private String transport = "sse";

    /**
     * Public server name exposed to MCP clients.
     */
    private String serverName = "ticket-mind-mcp-server";

    /**
     * Public server version exposed to MCP clients.
     */
    private String serverVersion = "0.0.1-SNAPSHOT";

    public String getTransport() {
        return transport;
    }

    public void setTransport(String transport) {
        this.transport = transport;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public String getServerVersion() {
        return serverVersion;
    }

    public void setServerVersion(String serverVersion) {
        this.serverVersion = serverVersion;
    }
}

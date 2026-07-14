package com.ticketmind.mcpserver.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(McpServerProperties.class)
public class McpServerConfig {
}

package com.ticketmind.agent.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PlanTools {

    private final McpToolGateway mcpToolGateway;

    public PlanTools(McpToolGateway mcpToolGateway) {
        this.mcpToolGateway = mcpToolGateway;
    }

    @Tool("规划出行方案")
    public String planTrip(@P("出发地") String departure,
                           @P("目的地") String destination,
                           @P("出发日期") String date,
                           @P("出行偏好") String preference) {
        return mcpToolGateway.call("plan_trip", Map.of(
                "departure", departure,
                "destination", destination,
                "date", date,
                "preference", preference
        ));
    }
}

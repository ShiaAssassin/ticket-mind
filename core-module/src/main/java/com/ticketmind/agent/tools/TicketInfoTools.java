package com.ticketmind.agent.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class TicketInfoTools {

    private final McpToolGateway mcpToolGateway;

    public TicketInfoTools(McpToolGateway mcpToolGateway) {
        this.mcpToolGateway = mcpToolGateway;
    }

    @Tool("查询列车、余票、票价和时刻信息")
    public String queryTickets(@P("出发站") String departureStation,
                               @P("到达站") String arrivalStation,
                               @P("出发日期") String date,
                               @P("席别偏好") String seatType) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("date", date);
        arguments.put("fromStation", departureStation);
        arguments.put("toStation", arrivalStation);
        arguments.put("seatTypePreference", seatType);
        return mcpToolGateway.call("query_ticket_options", arguments);
    }

    @Tool("查询购票、退票和改签规则")
    public String queryTicketRules(@P("规则主题") String ruleTopic) {
        return mcpToolGateway.call("query_ticket_rules", Map.of("ruleTopic", ruleTopic));
    }
}

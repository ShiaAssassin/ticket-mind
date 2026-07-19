package com.ticketmind.mcpserver.tool;

import com.ticketmind.mcpserver.model.TicketsResponse;
import com.ticketmind.mcpserver.service.RailwayCrawlerService;
import com.ticketmind.mcpserver.service.StationCodeService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class TicketMcpTools {

    private final RailwayCrawlerService railwayCrawlerService;
    private final StationCodeService stationCodeService;

    public TicketMcpTools(RailwayCrawlerService railwayCrawlerService, StationCodeService stationCodeService) {
        this.railwayCrawlerService = railwayCrawlerService;
        this.stationCodeService = stationCodeService;
    }

    public Map<String, Object> today() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", LocalDate.now(RailwayCrawlerService.SHANGHAI_ZONE).toString());
        result.put("timezone", RailwayCrawlerService.SHANGHAI_ZONE.toString());
        return result;
    }

    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("service", "ticket-mind-mcp-server");
        result.put("status", "UP");
        result.put("protocol", "MCP");
        result.put("timestamp", Instant.now().toString());
        return result;
    }

    public Map<String, Object> queryTickets(String date, String fromStation, String toStation, String trainFilterFlags) {
        if (!StringUtils.hasText(date) || !StringUtils.hasText(fromStation) || !StringUtils.hasText(toStation)) {
            throw new IllegalArgumentException("date, fromStation and toStation are required");
        }

        LocalDate queryDate;
        try {
            queryDate = LocalDate.parse(date);
        } catch (Exception e) {
            throw new IllegalArgumentException("date must use yyyy-MM-dd format");
        }

        if (queryDate.isBefore(LocalDate.now(RailwayCrawlerService.SHANGHAI_ZONE))) {
            throw new IllegalArgumentException("date must not be earlier than today in Asia/Shanghai");
        }

        String fromStationCode = stationCodeService.resolveStationCode(fromStation)
                .orElseThrow(() -> new IllegalArgumentException("unknown fromStation: " + fromStation));
        String toStationCode = stationCodeService.resolveStationCode(toStation)
                .orElseThrow(() -> new IllegalArgumentException("unknown toStation: " + toStation));

        TicketsResponse response = railwayCrawlerService.queryTickets(
                date,
                fromStationCode,
                toStationCode,
                trainFilterFlags == null ? "" : trainFilterFlags.trim());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("query", Map.of(
                "date", date,
                "fromStation", stationCodeService.stationNameOrCode(fromStationCode),
                "fromStationTelecode", fromStationCode,
                "toStation", stationCodeService.stationNameOrCode(toStationCode),
                "toStationTelecode", toStationCode,
                "trainFilterFlags", trainFilterFlags == null ? "" : trainFilterFlags.trim()
        ));
        result.put("tickets", response.tickets());
        if (StringUtils.hasText(response.message())) {
            result.put("message", response.message());
        }
        if (StringUtils.hasText(response.error())) {
            result.put("error", response.error());
            result.put("httpStatus", response.httpStatus());
        }
        return result;
    }
}

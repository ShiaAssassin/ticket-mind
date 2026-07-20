package com.ticketmind.mcpserver.tool;

import com.ticketmind.mcpserver.model.dto.SendNotificationEmailRequest;
import com.ticketmind.mcpserver.model.dto.SendNotificationEmailResponse;
import com.ticketmind.mcpserver.model.TicketsResponse;
import com.ticketmind.mcpserver.service.NotificationEmailService;
import com.ticketmind.mcpserver.service.RailwayCrawlerService;
import com.ticketmind.mcpserver.service.StationCodeService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class TicketMcpTools {

    private static final Pattern SIMPLE_EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    private final RailwayCrawlerService railwayCrawlerService;
    private final StationCodeService stationCodeService;
    private final NotificationEmailService notificationEmailService;

    public TicketMcpTools(RailwayCrawlerService railwayCrawlerService,
                          StationCodeService stationCodeService,
                          NotificationEmailService notificationEmailService) {
        this.railwayCrawlerService = railwayCrawlerService;
        this.stationCodeService = stationCodeService;
        this.notificationEmailService = notificationEmailService;
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

    public SendNotificationEmailResponse sendNotificationEmail(String recipient, String subject, String content) {
        if (!StringUtils.hasText(recipient) || !StringUtils.hasText(subject) || !StringUtils.hasText(content)) {
            throw new IllegalArgumentException("recipient, subject and content are required");
        }
        if (!SIMPLE_EMAIL_PATTERN.matcher(recipient.trim()).matches()) {
            throw new IllegalArgumentException("recipient must be a valid email address");
        }

        SendNotificationEmailRequest request = new SendNotificationEmailRequest();
        request.setRecipient(recipient);
        request.setSubject(subject);
        request.setContent(content);
        return notificationEmailService.send(request);
    }
}

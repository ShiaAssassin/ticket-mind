package com.ticketmind.mcpserver.tool;

import com.ticketmind.mcpserver.model.dto.SendNotificationEmailRequest;
import com.ticketmind.mcpserver.model.dto.SendNotificationEmailResponse;
import com.ticketmind.mcpserver.model.TicketsResponse;
import com.ticketmind.mcpserver.service.NotificationEmailService;
import com.ticketmind.mcpserver.service.RailwayCrawlerService;
import com.ticketmind.mcpserver.service.StationCodeService;
import com.ticketmind.mcpserver.service.TicketRuleService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class TicketMcpTools {

    private static final Pattern SIMPLE_EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    private final RailwayCrawlerService railwayCrawlerService;
    private final StationCodeService stationCodeService;
    private final NotificationEmailService notificationEmailService;
    private final TicketRuleService ticketRuleService;

    public TicketMcpTools(RailwayCrawlerService railwayCrawlerService,
                          StationCodeService stationCodeService,
                          NotificationEmailService notificationEmailService,
                          TicketRuleService ticketRuleService) {
        this.railwayCrawlerService = railwayCrawlerService;
        this.stationCodeService = stationCodeService;
        this.notificationEmailService = notificationEmailService;
        this.ticketRuleService = ticketRuleService;
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

    public Map<String, Object> queryTicketOptions(String date,
                                                  String fromStation,
                                                  String toStation,
                                                  String seatTypePreference) {
        Map<String, Object> result = queryTickets(date, fromStation, toStation, "");
        Object ticketsObject = result.get("tickets");
        if (!(ticketsObject instanceof List<?> tickets)) {
            return result;
        }

        String normalizedPreference = normalizeSeatPreference(seatTypePreference);
        List<?> filteredTickets = tickets;
        if (StringUtils.hasText(normalizedPreference)) {
            filteredTickets = tickets.stream()
                    .filter(ticket -> ticket instanceof com.ticketmind.mcpserver.model.TicketInfo ticketInfo
                            && ticketInfo.prices() != null
                            && ticketInfo.prices().stream().anyMatch(price ->
                            containsIgnoreCase(price.seatName(), normalizedPreference)
                                    || containsIgnoreCase(price.shortName(), normalizedPreference)
                                    || containsIgnoreCase(price.seatTypeCode(), normalizedPreference)))
                    .toList();
        }

        List<?> sortedTickets = filteredTickets.stream()
                .sorted(Comparator.comparing(ticket -> ticket instanceof com.ticketmind.mcpserver.model.TicketInfo ticketInfo
                        ? ticketInfo.startTime()
                        : ""))
                .limit(20)
                .toList();

        result.put("seatTypePreference", seatTypePreference == null ? "" : seatTypePreference.trim());
        result.put("tickets", sortedTickets);
        result.put("ticketCount", sortedTickets.size());
        if (StringUtils.hasText(normalizedPreference) && sortedTickets.isEmpty()) {
            result.put("message", "未找到符合席别偏好的车次，可放宽席别后重试。");
        }
        return result;
    }

    public Map<String, Object> queryTicketRules(String ruleTopic) {
        return ticketRuleService.queryRules(ruleTopic);
    }

    public Map<String, Object> sendNotification(String channel, String recipient, String content) {
        if (!StringUtils.hasText(channel)) {
            throw new IllegalArgumentException("channel is required");
        }
        if (!"email".equalsIgnoreCase(channel.trim()) && !"mail".equalsIgnoreCase(channel.trim())
                && !"邮件".equals(channel.trim()) && !"邮箱".equals(channel.trim())) {
            throw new IllegalArgumentException("unsupported channel: only email is supported currently");
        }

        SendNotificationEmailResponse response = sendNotificationEmail(
                recipient,
                "TicketMind 通知",
                content
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("channel", "email");
        result.put("recipient", recipient);
        result.put("content", content);
        result.put("delivery", response);
        return result;
    }

    public Map<String, Object> planTrip(String departure,
                                        String destination,
                                        String date,
                                        String preference) {
        Map<String, Object> ticketResult = queryTicketOptions(date, departure, destination, preference);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("departure", departure);
        result.put("destination", destination);
        result.put("date", date);
        result.put("preference", preference == null ? "" : preference.trim());
        result.put("strategy", "当前仅支持基于直达余票的出行方案推荐。");
        result.put("options", ticketResult.get("tickets"));
        result.put("optionCount", ticketResult.getOrDefault("ticketCount", 0));
        result.put("message", ticketResult.getOrDefault("message", "已按出发时间排序返回候选车次。"));
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

    private String normalizeSeatPreference(String seatTypePreference) {
        return StringUtils.hasText(seatTypePreference) ? seatTypePreference.trim().toLowerCase() : "";
    }

    private boolean containsIgnoreCase(String source, String target) {
        return StringUtils.hasText(source)
                && StringUtils.hasText(target)
                && source.toLowerCase().contains(target.toLowerCase());
    }
}

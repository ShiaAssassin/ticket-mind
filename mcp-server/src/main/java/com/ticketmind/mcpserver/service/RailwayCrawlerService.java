package com.ticketmind.mcpserver.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketmind.mcpserver.model.PriceInfo;
import com.ticketmind.mcpserver.model.TicketInfo;
import com.ticketmind.mcpserver.model.TicketsResponse;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RailwayCrawlerService {

    public static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");

    private static final String API_BASE = "https://kyfw.12306.cn";
    private static final String LEFT_TICKET_URL = API_BASE + "/otn/leftTicket/query";
    private static final String LEFT_TICKET_REFERER = API_BASE + "/otn/leftTicket/init";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final String SEAT_TYPES_PATH = "station-data/seat_types.json";
    private static final String DW_FLAGS_PATH = "station-data/dw_flags.json";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final List<SeatType> seatTypes;
    private final List<String> dwFlags;

    public RailwayCrawlerService(ObjectMapper objectMapper) throws IOException {
        this.objectMapper = objectMapper;
        this.seatTypes = List.copyOf(List.of(objectMapper.readValue(
                new ClassPathResource(SEAT_TYPES_PATH).getInputStream(),
                SeatType[].class
        )));
        this.dwFlags = List.copyOf(List.of(objectMapper.readValue(
                new ClassPathResource(DW_FLAGS_PATH).getInputStream(),
                String[].class
        )));
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public TicketsResponse queryTickets(String date, String fromStationCode, String toStationCode, String trainFilterFlags) {
        Map<String, String> cookies = getCookie(API_BASE);

        Map<String, String> queryParams = new LinkedHashMap<>();
        queryParams.put("leftTicketDTO.train_date", date);
        queryParams.put("leftTicketDTO.from_station", fromStationCode);
        queryParams.put("leftTicketDTO.to_station", toStationCode);
        queryParams.put("purpose_codes", "ADULT");

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", USER_AGENT);
        headers.put("Accept", "application/json, text/javascript, */*; q=0.01");
        headers.put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        headers.put("Referer", LEFT_TICKET_REFERER);
        headers.put("X-Requested-With", "XMLHttpRequest");
        if (!cookies.isEmpty()) {
            headers.put("Cookie", formatCookies(cookies));
        }

        JsonNode response = getJson(LEFT_TICKET_URL, queryParams, headers);
        if (response == null) {
            return TicketsResponse.failure(500, "查询车票信息失败", null);
        }

        int httpStatus = response.path("httpstatus").asInt(0);
        if (httpStatus != 200) {
            String message = response.path("messages").isMissingNode() ? "未知错误" : response.path("messages").toString();
            return TicketsResponse.failure(400, "请求失败", message);
        }

        JsonNode resultArray = response.path("data").path("result");
        if (!resultArray.isArray() || resultArray.isEmpty()) {
            return TicketsResponse.empty("没有找到符合条件的车票信息");
        }

        Map<String, String> stationMap = parseStationMap(response.path("data").path("map"));
        List<TicketInfo> tickets = parseTicketsData(resultArray, stationMap);
        if (StringUtils.hasText(trainFilterFlags)) {
            tickets = filterTicketsByTrainTypes(tickets, trainFilterFlags);
        }

        return TicketsResponse.success(tickets);
    }

    private JsonNode getJson(String url, Map<String, String> params, Map<String, String> headers) {
        try {
            String body = getText(url, params, headers);
            if (!StringUtils.hasText(body)) {
                return null;
            }
            return objectMapper.readTree(body);
        } catch (Exception e) {
            return null;
        }
    }

    private String getText(String url, Map<String, String> params, Map<String, String> headers)
            throws IOException, InterruptedException {

        URI uri = buildUri(url, params);
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(Duration.ofSeconds(30));

        if (headers != null) {
            headers.forEach(requestBuilder::header);
        }

        HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return response.body();
    }

    private Map<String, String> getCookie(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .GET()
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .header("Accept-Encoding", "gzip, deflate, br")
                    .header("Connection", "keep-alive")
                    .header("Upgrade-Insecure-Requests", "1")
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            List<String> setCookieHeaders = response.headers().allValues("set-cookie");
            return parseCookies(setCookieHeaders);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private Map<String, String> parseCookies(List<String> cookies) {
        Map<String, String> cookieRecord = new LinkedHashMap<>();
        if (cookies == null || cookies.isEmpty()) {
            return cookieRecord;
        }

        for (String cookie : cookies) {
            if (!StringUtils.hasText(cookie)) {
                continue;
            }

            String keyValuePart = cookie.split(";", 2)[0];
            String[] parts = keyValuePart.split("=", 2);
            if (parts.length == 2 && StringUtils.hasText(parts[0]) && StringUtils.hasText(parts[1])) {
                cookieRecord.put(parts[0].trim(), parts[1].trim());
            }
        }

        return cookieRecord;
    }

    private String formatCookies(Map<String, String> cookies) {
        List<String> cookieParts = new ArrayList<>();
        cookies.forEach((key, value) -> cookieParts.add(key + "=" + value));
        return String.join("; ", cookieParts);
    }

    private URI buildUri(String url, Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return URI.create(url);
        }

        List<String> pairs = new ArrayList<>();
        params.forEach((key, value) -> pairs.add(encode(key) + "=" + encode(value)));
        return URI.create(url + "?" + String.join("&", pairs));
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private Map<String, String> parseStationMap(JsonNode mapNode) {
        Map<String, String> stationMap = new LinkedHashMap<>();
        if (mapNode == null || !mapNode.isObject()) {
            return stationMap;
        }

        mapNode.fields().forEachRemaining(entry -> stationMap.put(entry.getKey(), entry.getValue().asText()));
        return stationMap;
    }

    private List<TicketInfo> parseTicketsData(JsonNode resultArray, Map<String, String> stationMap) {
        List<TicketInfo> tickets = new ArrayList<>();

        for (JsonNode ticketNode : resultArray) {
            String ticketString = ticketNode.asText();
            String[] parts = ticketString.split("\\|", -1);
            if (parts.length < 30) {
                continue;
            }

            try {
                tickets.add(new TicketInfo(
                        getPart(parts, 2),
                        getPart(parts, 3),
                        getPart(parts, 8),
                        getPart(parts, 9),
                        getPart(parts, 10),
                        getStationName(stationMap, getPart(parts, 6)),
                        getStationName(stationMap, getPart(parts, 7)),
                        getPart(parts, 6),
                        getPart(parts, 7),
                        extractPrices(parts),
                        extractDWFlags(getPart(parts, 46))
                ));
            } catch (Exception e) {
                // Ignore malformed ticket rows from 12306 and continue parsing the rest.
            }
        }

        return tickets;
    }

    private String getPart(String[] parts, int index) {
        return parts.length > index ? parts[index] : "";
    }

    private String getStationName(Map<String, String> stationMap, String stationCode) {
        return stationMap.getOrDefault(stationCode, stationCode);
    }

    private List<PriceInfo> extractPrices(String[] parts) {
        List<PriceInfo> prices = new ArrayList<>();

        for (SeatType seatType : seatTypes) {
            String num = getPart(parts, seatType.index());
            if (StringUtils.hasText(num) && !"无".equals(num)) {
                prices.add(new PriceInfo(seatType.name(), seatType.shortName(), seatType.code(), num, null, null));
            }
        }

        return prices;
    }

    private List<String> extractDWFlags(String dwFlagStr) {
        List<String> flags = new ArrayList<>();
        if (!StringUtils.hasText(dwFlagStr)) {
            return flags;
        }

        String[] flagsArray = dwFlagStr.split("#");
        for (String flag : flagsArray) {
            for (String dwFlag : dwFlags) {
                if (flag.contains(dwFlag)) {
                    flags.add(dwFlag);
                    break;
                }
            }
        }

        return flags;
    }

    private List<TicketInfo> filterTicketsByTrainTypes(List<TicketInfo> tickets, String trainFilterFlags) {
        String[] filters = trainFilterFlags.split("");
        return tickets.stream()
                .filter(ticket -> {
                    for (String filter : filters) {
                        if (matchTrainType(ticket, filter)) {
                            return true;
                        }
                    }
                    return false;
                })
                .toList();
    }

    private boolean matchTrainType(TicketInfo ticket, String trainType) {
        String trainCode = ticket.startTrainCode();
        if (!StringUtils.hasText(trainCode)) {
            return false;
        }

        return switch (trainType.toUpperCase()) {
            case "G" -> trainCode.startsWith("G") || trainCode.startsWith("C");
            case "D" -> trainCode.startsWith("D");
            case "Z" -> trainCode.startsWith("Z");
            case "T" -> trainCode.startsWith("T");
            case "K" -> trainCode.startsWith("K");
            case "O" -> !startsWithAny(trainCode, "G", "D", "C", "Z", "T", "K");
            case "F" -> ticket.dwFlag() != null && ticket.dwFlag().contains("复兴号");
            case "S" -> ticket.dwFlag() != null && ticket.dwFlag().contains("智能动车组");
            default -> false;
        };
    }

    private boolean startsWithAny(String value, String... prefixes) {
        for (String prefix : prefixes) {
            if (value.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private record SeatType(int index, String name, String shortName, String code) {
    }
}

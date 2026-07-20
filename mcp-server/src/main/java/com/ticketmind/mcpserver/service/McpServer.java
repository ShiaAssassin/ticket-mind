package com.ticketmind.mcpserver.service;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketmind.common.JsonRpcErrorCode;
import com.ticketmind.mcpserver.config.McpServerProperties;
import com.ticketmind.mcpserver.tool.TicketMcpTools;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * multimodalAgent MCP JSON-RPC 工具服务端。
 */
@Service
@AllArgsConstructor
public class McpServer {
    private final ObjectMapper objectMapper;
    private final TicketMcpTools ticketMcpTools;
    private final McpServerProperties properties;

    public Map<String, Object> handle(JsonNode request) {
        Object id = jsonId(request.path("id"));
        try {
            validateRequest(request);
            String method = request.path("method").asText("");
            Object result = switch (method) {
                case "initialize" -> initialize();
                case "tools/list" -> toolsList();
                case "tools/call" -> toolsCall(request.path("params"));
                default -> throw new McpException(JsonRpcErrorCode.METHOD_NOT_FOUND,
                        "Method not found: " + method);
            };
            return response(id, result);
        } catch (IllegalArgumentException exception) {
            return error(id, JsonRpcErrorCode.INVALID_PARAMS.getCode(), exception.getMessage());
        } catch (McpException exception) {
            return error(id, exception.code().getCode(), exception.getMessage());
        } catch (Exception exception) {
            return error(id, JsonRpcErrorCode.INTERNAL_ERROR.getCode(),
                    JsonRpcErrorCode.INTERNAL_ERROR.getMessage());
        }
    }

    private Map<String, Object> initialize() {
        return Map.of(
                "protocolVersion", "2024-11-05",
                "serverInfo", Map.of("name", properties.getServerName(), "version", properties.getServerVersion()),
                "capabilities", Map.of("tools", Map.of()));
    }

    private Map<String, Object> toolsList() {
        return Map.of("tools", List.of(
                Map.of(
                        "name", "today",
                        "description", "获取当前日期，时区固定为 Asia/Shanghai。",
                        "inputSchema", objectSchema(Map.of(), List.of())
                ),
                Map.of(
                        "name", "health",
                        "description", "检查 TicketMind MCP 服务健康状态。",
                        "inputSchema", objectSchema(Map.of(), List.of())
                ),
                Map.of(
                        "name", "query_tickets",
                        "description", "查询 12306 余票信息。fromStation 和 toStation 支持中文站名或 12306 电报码。",
                        "inputSchema", objectSchema(
                                Map.of(
                                        "date", Map.of(
                                                "type", "string",
                                                "description", "查询日期，格式 yyyy-MM-dd，不能早于 Asia/Shanghai 当前日期。"
                                        ),
                                        "fromStation", Map.of(
                                                "type", "string",
                                                "description", "出发站中文名或 12306 电报码，例如 北京南 或 VNP。"
                                        ),
                                        "toStation", Map.of(
                                                "type", "string",
                                                "description", "到达站中文名或 12306 电报码，例如 上海虹桥 或 AOH。"
                                        ),
                                        "trainFilterFlags", Map.of(
                                                "type", "string",
                                                "description", "可选车次过滤标记：G/D/Z/T/K/O/F/S，可组合，例如 G 或 GD。"
                                        )
                                ),
                                List.of("date", "fromStation", "toStation")
                        )
                ),
                Map.of(
                        "name", "query_ticket_options",
                        "description", "查询并筛选符合席别偏好的车票候选，返回适合直接推荐给用户的车次列表。",
                        "inputSchema", objectSchema(
                                Map.of(
                                        "date", Map.of("type", "string", "description", "查询日期，格式 yyyy-MM-dd。"),
                                        "fromStation", Map.of("type", "string", "description", "出发站中文名或电报码。"),
                                        "toStation", Map.of("type", "string", "description", "到达站中文名或电报码。"),
                                        "seatTypePreference", Map.of("type", "string", "description", "可选席别偏好，例如 二等座、一等座、硬卧。")
                                ),
                                List.of("date", "fromStation", "toStation")
                        )
                ),
                Map.of(
                        "name", "query_ticket_rules",
                        "description", "查询购票、退票、改签、候补等常见规则摘要。",
                        "inputSchema", objectSchema(
                                Map.of(
                                        "ruleTopic", Map.of("type", "string", "description", "规则主题，例如 退票、改签、候补。")
                                ),
                                List.of("ruleTopic")
                        )
                ),
                Map.of(
                        "name", "plan_trip",
                        "description", "根据出发地、目的地、日期和偏好返回直达出行方案建议。",
                        "inputSchema", objectSchema(
                                Map.of(
                                        "departure", Map.of("type", "string", "description", "出发地。"),
                                        "destination", Map.of("type", "string", "description", "目的地。"),
                                        "date", Map.of("type", "string", "description", "出发日期，格式 yyyy-MM-dd。"),
                                        "preference", Map.of("type", "string", "description", "出行偏好，例如 二等座、早班车。")
                                ),
                                List.of("departure", "destination", "date")
                        )
                ),
                Map.of(
                        "name", "send_notification",
                        "description", "发送票务通知。目前仅支持 email 渠道。",
                        "inputSchema", objectSchema(
                                Map.of(
                                        "channel", Map.of("type", "string", "description", "通知渠道，当前仅支持 email。"),
                                        "recipient", Map.of("type", "string", "description", "接收人，例如邮箱地址。"),
                                        "content", Map.of("type", "string", "description", "通知正文内容。")
                                ),
                                List.of("channel", "recipient", "content")
                        )
                ),
                Map.of(
                        "name", "send_notification_email",
                        "description", "发送通知邮件，适用于票务提醒、状态通知等场景。",
                        "inputSchema", objectSchema(
                                Map.of(
                                        "recipient", Map.of(
                                                "type", "string",
                                                "description", "收件人邮箱地址。"
                                        ),
                                        "subject", Map.of(
                                                "type", "string",
                                                "description", "邮件主题。"
                                        ),
                                        "content", Map.of(
                                                "type", "string",
                                                "description", "邮件正文内容，按纯文本发送。"
                                        )
                                ),
                                List.of("recipient", "subject", "content")
                        )
                )
        ));
    }

    private Map<String, Object> toolsCall(JsonNode params) throws JsonProcessingException {
        requireObject(params, "params");
        String name = params.path("name").asText("");
        if (name.isBlank()) {
            throw new McpException(JsonRpcErrorCode.INVALID_PARAMS, "Invalid params: name is required");
        }
        JsonNode arguments = params.path("arguments");
        return switch (name) {
            case "today" -> toolJson(ticketMcpTools.today());
            case "health" -> toolJson(ticketMcpTools.health());
            case "query_tickets" -> {
                requireObject(arguments, "arguments");
                yield toolJson(ticketMcpTools.queryTickets(
                        requireText(arguments, "date"),
                        requireText(arguments, "fromStation"),
                        requireText(arguments, "toStation"),
                        optionalText(arguments, "trainFilterFlags")
                ));
            }
            case "query_ticket_options" -> {
                requireObject(arguments, "arguments");
                yield toolJson(ticketMcpTools.queryTicketOptions(
                        requireText(arguments, "date"),
                        requireText(arguments, "fromStation"),
                        requireText(arguments, "toStation"),
                        optionalText(arguments, "seatTypePreference")
                ));
            }
            case "query_ticket_rules" -> {
                requireObject(arguments, "arguments");
                yield toolJson(ticketMcpTools.queryTicketRules(
                        requireText(arguments, "ruleTopic")
                ));
            }
            case "plan_trip" -> {
                requireObject(arguments, "arguments");
                yield toolJson(ticketMcpTools.planTrip(
                        requireText(arguments, "departure"),
                        requireText(arguments, "destination"),
                        requireText(arguments, "date"),
                        optionalText(arguments, "preference")
                ));
            }
            case "send_notification" -> {
                requireObject(arguments, "arguments");
                yield toolJson(ticketMcpTools.sendNotification(
                        requireText(arguments, "channel"),
                        requireText(arguments, "recipient"),
                        requireText(arguments, "content")
                ));
            }
            case "send_notification_email" -> {
                requireObject(arguments, "arguments");
                yield toolJson(ticketMcpTools.sendNotificationEmail(
                        requireText(arguments, "recipient"),
                        requireText(arguments, "subject"),
                        requireText(arguments, "content")
                ));
            }
            default -> throw new McpException(JsonRpcErrorCode.METHOD_NOT_FOUND,
                    "Method not found: " + name);
        };
    }

    private Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        return schema;
    }

    private Map<String, Object> toolJson(Object value) throws JsonProcessingException {
        return toolText(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value));
    }

    private Map<String, Object> toolText(String text) {
        return Map.of("content", List.of(Map.of("type", "text", "text", text)));
    }

    private Map<String, Object> response(Object id, Object result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("result", result);
        return response;
    }

    private Map<String, Object> error(Object id, int code, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("error", Map.of("code", code, "message", message == null ? "" : message));
        return response;
    }

    private Object jsonId(JsonNode idNode) {
        if (idNode == null || idNode.isMissingNode() || idNode.isNull()) {
            return null;
        }
        if (idNode.isNumber()) {
            return idNode.asLong();
        }
        return idNode.asText();
    }

    private void validateRequest(JsonNode request) {
        requireObject(request, "request");
        if (!"2.0".equals(request.path("jsonrpc").asText())) {
            throw new McpException(JsonRpcErrorCode.INVALID_REQUEST, "Invalid Request");
        }
        if (request.path("method").asText("").isBlank()) {
            throw new McpException(JsonRpcErrorCode.INVALID_REQUEST, "Invalid Request");
        }
    }

    private void requireObject(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.isObject()) {
            throw new McpException(JsonRpcErrorCode.INVALID_PARAMS,
                    "Invalid params: " + fieldName + " must be an object");
        }
    }

    private String requireText(JsonNode node, String fieldName) {
        String value = optionalText(node, fieldName);
        if (value.isBlank()) {
            throw new McpException(JsonRpcErrorCode.INVALID_PARAMS,
                    "Invalid params: " + fieldName + " is required");
        }
        return value;
    }

    private String optionalText(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return "";
        }
        if (!value.isTextual()) {
            throw new McpException(JsonRpcErrorCode.INVALID_PARAMS,
                    "Invalid params: " + fieldName + " must be a string");
        }
        return value.asText().trim();
    }

    private static class McpException extends RuntimeException {

        private final JsonRpcErrorCode code;

        McpException(JsonRpcErrorCode code, String message) {
            super(message == null || message.isBlank() ? code.getMessage() : message);
            this.code = code;
        }

        JsonRpcErrorCode code() {
            return code;
        }
    }
}

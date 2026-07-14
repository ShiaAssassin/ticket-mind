package com.ticketmind.mcpserver.service;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketmind.common.JsonRpcErrorCode;
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
                "serverInfo", Map.of("name", "ticket-mind-mcp-server", "version", "0.1.0"),
                "capabilities", Map.of("tools", Map.of()));
    }

    private Map<String, Object> toolsList() {
        return Map.of("tools", List.of());
    }

    private Map<String, Object> toolsCall(JsonNode params) {
        requireObject(params, "params");
        String name = params.path("name").asText("");
        if (name.isBlank()) {
            throw new McpException(JsonRpcErrorCode.INVALID_PARAMS, "Invalid params: name is required");
        }
        return switch (name) {
            case " " -> null;
            default -> throw new McpException(JsonRpcErrorCode.METHOD_NOT_FOUND,
                    "Method not found: " + name);
        };
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

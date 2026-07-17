package com.ticketmind.agent.tool.mcp;

import java.util.LinkedHashMap;
import java.util.Map;

public final class McpToolArguments {

    public static Map<String, Object> of(Object... keyValues) {
        if (keyValues == null || keyValues.length == 0) {
            return Map.of();
        }
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("keyValues length must be even");
        }

        Map<String, Object> arguments = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            Object key = keyValues[i];
            if (key == null) {
                throw new IllegalArgumentException("argument key cannot be null");
            }
            Object value = keyValues[i + 1];
            if (value != null) {
                arguments.put(key.toString(), value);
            }
        }
        return arguments;
    }
}

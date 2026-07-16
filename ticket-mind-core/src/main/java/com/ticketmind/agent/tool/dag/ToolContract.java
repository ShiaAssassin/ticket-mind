package com.ticketmind.agent.tool.dag;

import java.util.Set;

public record ToolContract(
        String toolName,
        Set<String> inputKeys,
        Set<String> outputKeys
) {

    public ToolContract {
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("toolName must not be blank");
        }
        inputKeys = normalize(inputKeys);
        outputKeys = normalize(outputKeys);
    }

    private static Set<String> normalize(Set<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return Set.of();
        }
        return keys.stream()
                .filter(key -> key != null && !key.isBlank())
                .map(String::trim)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}

package com.ticketmind.agent.tool;

import java.util.Set;

public record ToolDagEdge(
        String fromTool,
        String toTool,
        Set<String> matchedKeys
) {
}

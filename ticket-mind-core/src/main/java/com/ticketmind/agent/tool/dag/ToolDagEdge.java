package com.ticketmind.agent.tool.dag;

import java.util.Set;

public record ToolDagEdge(
        String fromTool,
        String toTool,
        Set<String> matchedKeys
) {
}

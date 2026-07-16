package com.ticketmind.agent.tool;

import java.util.List;

public record ToolDag(
        List<ToolDagNode> nodes,
        List<ToolDagEdge> edges
) {
}

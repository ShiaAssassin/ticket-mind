package com.ticketmind.agent.tool.dag;

import java.util.List;

public record ToolDag(
        List<ToolDagNode> nodes,
        List<ToolDagEdge> edges
) {
}

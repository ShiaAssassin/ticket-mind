package com.ticketmind.agent.tool.dag;

import java.util.Set;

public record ToolDagNode(
        String toolName,
        Set<String> inputKeys,
        Set<String> outputKeys
) {

    static ToolDagNode from(ToolContract contract) {
        return new ToolDagNode(contract.toolName(), contract.inputKeys(), contract.outputKeys());
    }
}

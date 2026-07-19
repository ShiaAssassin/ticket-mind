package com.ticketmind.agent.tool.dag;

import com.ticketmind.common.BusinessException;
import com.ticketmind.common.ResultCode;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

@Component
public class ToolDagBuilder {

    public ToolDag build(List<ToolContract> contracts) {
        if (contracts == null || contracts.isEmpty()) {
            return new ToolDag(List.of(), List.of());
        }

        Map<String, ToolContract> uniqueContracts = new LinkedHashMap<>();
        for (ToolContract contract : contracts) {
            if (uniqueContracts.putIfAbsent(contract.toolName(), contract) != null) {
                throw new BusinessException(ResultCode.INVALID_PARAMETER_FORMAT,
                        "Duplicate tool name in DAG: " + contract.toolName());
            }
        }

        List<ToolDagEdge> edges = buildEdges(uniqueContracts);
        List<ToolDagNode> sortedNodes = topologicalSort(uniqueContracts, edges);
        return new ToolDag(sortedNodes, edges);
    }

    private List<ToolDagEdge> buildEdges(Map<String, ToolContract> contracts) {
        List<ToolDagEdge> edges = new ArrayList<>();
        List<ToolContract> tools = new ArrayList<>(contracts.values());
        for (ToolContract producer : tools) {
            for (ToolContract consumer : tools) {
                if (producer.toolName().equals(consumer.toolName())) {
                    continue;
                }
                Set<String> matchedKeys = intersection(producer.outputKeys(), consumer.inputKeys());
                if (!matchedKeys.isEmpty()) {
                    edges.add(new ToolDagEdge(producer.toolName(), consumer.toolName(), matchedKeys));
                }
            }
        }
        return List.copyOf(edges);
    }

    private List<ToolDagNode> topologicalSort(Map<String, ToolContract> contracts, List<ToolDagEdge> edges) {
        Map<String, Integer> indegree = new HashMap<>();
        Map<String, List<String>> adjacency = new HashMap<>();
        contracts.keySet().forEach(toolName -> {
            indegree.put(toolName, 0);
            adjacency.put(toolName, new ArrayList<>());
        });

        for (ToolDagEdge edge : edges) {
            adjacency.get(edge.fromTool()).add(edge.toTool());
            indegree.compute(edge.toTool(), (key, value) -> value == null ? 1 : value + 1);
        }

        Queue<String> ready = new ArrayDeque<>();
        contracts.keySet().stream()
                .filter(toolName -> indegree.get(toolName) == 0)
                .forEach(ready::add);

        List<ToolDagNode> sorted = new ArrayList<>();
        while (!ready.isEmpty()) {
            String toolName = ready.remove();
            sorted.add(ToolDagNode.from(contracts.get(toolName)));
            for (String nextTool : adjacency.get(toolName)) {
                int nextIndegree = indegree.compute(nextTool, (key, value) -> value - 1);
                if (nextIndegree == 0) {
                    ready.add(nextTool);
                }
            }
        }

        if (sorted.size() != contracts.size()) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER_FORMAT,
                    "Tool input/output dependencies contain a cycle");
        }
        return List.copyOf(sorted);
    }

    private Set<String> intersection(Set<String> left, Set<String> right) {
        Set<String> smaller = left.size() <= right.size() ? left : right;
        Set<String> larger = left.size() <= right.size() ? right : left;
        Set<String> result = new LinkedHashSet<>();
        for (String key : smaller) {
            if (larger.contains(key)) {
                result.add(key);
            }
        }
        return Set.copyOf(result);
    }
}

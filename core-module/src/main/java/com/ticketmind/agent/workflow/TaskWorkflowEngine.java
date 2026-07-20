package com.ticketmind.agent.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketmind.agent.core.*;
import com.ticketmind.agent.memory.SystemPromptMemoryManager;
import com.ticketmind.model.dto.*;
import com.ticketmind.model.entity.IntentType;
import com.ticketmind.service.impl.IntentRecognizer;
import lombok.RequiredArgsConstructor;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.*;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Component
@RequiredArgsConstructor
public class TaskWorkflowEngine {

    private static final String RECOGNIZE_INTENT = "recognize_intent";
    private static final String DIRECT_REPLY = "direct_reply";
    private static final String PLAN_TASKS = "plan_tasks";
    private static final String EXECUTE_READY_TASK = "execute_ready_task";
    private static final String FINALIZE = "finalize";

    private static final String ROUTE_DIRECT = "direct";
    private static final String ROUTE_TASK = "task";
    private static final String ROUTE_CONTINUE = "continue";
    private static final String ROUTE_FINISH = "finish";

    private final TicketAgent ticketAgent;

    private final TaskOrchestrationAgent taskOrchestrationAgent;

    private final BusinessExecutionAgent businessExecutionAgent;

    private final MonitorAgent monitorAgent;

    private final NotificationAgent notificationAgent;

    private final SummaryAgent summaryAgent;

    private final SystemPromptMemoryManager systemPromptMemoryManager;

    private final IntentRecognizer intentRecognizer;

    private final TaskPlanStore taskPlanStore;

    private final TaskTemplateCatalog taskTemplateCatalog;

    private final ObjectMapper objectMapper;

    private volatile CompiledGraph<TaskWorkflowState> compiledGraph;

    public String run(String memoryId, String userMessage) {
        try {
            Optional<TaskWorkflowState> output = graph().invoke(Map.of(
                    TaskWorkflowState.MEMORY_ID, memoryId,
                    TaskWorkflowState.USER_MESSAGE, userMessage
            ));
            TaskWorkflowState state = output.orElseThrow(() -> new IllegalStateException("task workflow returned empty state"));
            return state.finalAnswer();
        } catch (Exception ex) {
            return ticketAgent.chat(memoryId, systemPromptMemories(memoryId), userMessage);
        }
    }

    private CompiledGraph<TaskWorkflowState> graph() throws GraphStateException {
        CompiledGraph<TaskWorkflowState> localGraph = compiledGraph;
        if (localGraph == null) {
            synchronized (this) {
                localGraph = compiledGraph;
                if (localGraph == null) {
                    localGraph = buildGraph().compile();
                    compiledGraph = localGraph;
                }
            }
        }
        return localGraph;
    }

    private StateGraph<TaskWorkflowState> buildGraph() throws GraphStateException {
        return new StateGraph<>(TaskWorkflowState::new)
                .addNode(RECOGNIZE_INTENT, node_async(this::recognizeIntent))
                .addNode(DIRECT_REPLY, node_async(this::directReply))
                .addNode(PLAN_TASKS, node_async(this::planTasks))
                .addNode(EXECUTE_READY_TASK, node_async(this::executeReadyTask))
                .addNode(FINALIZE, node_async(this::finalizePlan))
                .addEdge(START, RECOGNIZE_INTENT)
                .addConditionalEdges(RECOGNIZE_INTENT, edge_async(this::routeAfterIntent), Map.of(
                        ROUTE_DIRECT, DIRECT_REPLY,
                        ROUTE_TASK, PLAN_TASKS
                ))
                .addEdge(DIRECT_REPLY, END)
                .addConditionalEdges(PLAN_TASKS, edge_async(this::routeAfterPlan), Map.of(
                        ROUTE_CONTINUE, EXECUTE_READY_TASK,
                        ROUTE_FINISH, FINALIZE
                ))
                .addConditionalEdges(EXECUTE_READY_TASK, edge_async(this::routeAfterExecution), Map.of(
                        ROUTE_CONTINUE, EXECUTE_READY_TASK,
                        ROUTE_FINISH, FINALIZE
                ))
                .addEdge(FINALIZE, END);
    }

    private Map<String, Object> recognizeIntent(TaskWorkflowState state) {
        IntentRecognitionResult result = intentRecognizer.recognize(state.userMessage());
        return Map.of(TaskWorkflowState.INTENT_TYPE, result.intentType());
    }

    private String routeAfterIntent(TaskWorkflowState state) {
        return state.intentType() == IntentType.NON_BUSINESS_CHAT ? ROUTE_DIRECT : ROUTE_TASK;
    }

    private Map<String, Object> directReply(TaskWorkflowState state) {
        return Map.of(TaskWorkflowState.FINAL_ANSWER,
                ticketAgent.chat(state.memoryId(), systemPromptMemories(state.memoryId()), state.userMessage()));
    }

    private Map<String, Object> planTasks(TaskWorkflowState state) {
        List<TaskPlanItem> predefinedTasks = taskTemplateCatalog.load(state.intentType());
        TaskPlanSnapshot basePlan = taskPlanStore.create(
                state.intentType().name(),
                state.userMessage(),
                "",
                predefinedTasks
        );
        List<TaskPlanItem> additionalTasks = requestAdditionalTasks(state, basePlan);
        TaskPlanSnapshot planned = additionalTasks.isEmpty()
                ? basePlan
                : taskPlanStore.save(new TaskPlanSnapshot(
                        basePlan.planId(),
                        basePlan.taskType(),
                        basePlan.userMessage(),
                        basePlan.summary(),
                        basePlan.status(),
                        mergeTasks(basePlan.items(), additionalTasks),
                        basePlan.createdAt(),
                        basePlan.updatedAt()
                ));

        return Map.of(TaskWorkflowState.PLAN, markReadyTasks(planned));
    }

    private String routeAfterPlan(TaskWorkflowState state) {
        return hasReadyTask(state.plan()) ? ROUTE_CONTINUE : ROUTE_FINISH;
    }

    private Map<String, Object> executeReadyTask(TaskWorkflowState state) {
        TaskPlanSnapshot snapshot = state.plan();
        TaskPlanItem task = nextReadyTask(snapshot);
        if (task == null) {
            return Map.of(TaskWorkflowState.PLAN, snapshot);
        }

        TaskPlanSnapshot inProgressSnapshot = withStatus(replaceTask(
                snapshot,
                taskPlanStore.updateTask(task, TaskExecutionStatus.IN_PROGRESS, task.result())
        ), TaskPlanStatus.RUNNING);
        inProgressSnapshot = taskPlanStore.save(inProgressSnapshot);

        TaskPlanItem completedTask;
        try {
            String result = executeTask(state, inProgressSnapshot, task);
            completedTask = taskPlanStore.updateTask(task, statusFromResult(result), result);
        } catch (Exception ex) {
            completedTask = taskPlanStore.updateTask(task, TaskExecutionStatus.FAILED, ex.getMessage());
        }

        TaskPlanSnapshot updatedSnapshot = markReadyTasks(replaceTask(inProgressSnapshot, completedTask));
        return Map.of(TaskWorkflowState.PLAN, updatedSnapshot);
    }

    private String routeAfterExecution(TaskWorkflowState state) {
        TaskPlanSnapshot snapshot = state.plan();
        if (hasReadyTask(snapshot)) {
            return ROUTE_CONTINUE;
        }
        return ROUTE_FINISH;
    }

    private Map<String, Object> finalizePlan(TaskWorkflowState state) {
        TaskPlanSnapshot snapshot = state.plan();
        if (snapshot == null) {
            return Map.of(TaskWorkflowState.FINAL_ANSWER,
                    ticketAgent.chat(state.memoryId(), systemPromptMemories(state.memoryId()), state.userMessage()));
        }

        TaskPlanStatus finalStatus = finalStatus(snapshot);
        String summary = summarizeFinalResult(state, snapshot, finalStatus);
        TaskPlanSnapshot completed = taskPlanStore.complete(snapshot, summary, finalStatus);
        return Map.of(
                TaskWorkflowState.PLAN, completed,
                TaskWorkflowState.FINAL_STATUS, finalStatus,
                TaskWorkflowState.FINAL_ANSWER, summary
        );
    }

    private TaskPlanItem task(String taskCode,
                              String title,
                              String description,
                              TaskAssignee assignee,
                              List<String> dependencies,
                              boolean predefined,
                              OffsetDateTime now) {
        return new TaskPlanItem(taskCode, title, description, assignee, dependencies,
                dependencies == null || dependencies.isEmpty() ? TaskExecutionStatus.READY : TaskExecutionStatus.PENDING,
                predefined, "", now, now);
    }

    private List<TaskPlanItem> requestAdditionalTasks(TaskWorkflowState state, TaskPlanSnapshot basePlan) {
        try {
            String response = taskOrchestrationAgent.suggestAdditionalTasks(planningContext(state, basePlan));
            return parseAdditionalTasks(response, basePlan);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String planningContext(TaskWorkflowState state, TaskPlanSnapshot basePlan) {
        return """
                用户请求：%s
                任务类型：%s
                已有任务：%s
                可分派 Agent：BUSINESS_EXECUTOR、MONITOR、NOTIFICATION、SUMMARY
                """.formatted(state.userMessage(), state.intentType(), formatTasks(basePlan.items()));
    }

    private List<TaskPlanItem> parseAdditionalTasks(String response, TaskPlanSnapshot basePlan) throws Exception {
        if (!StringUtils.hasText(response)) {
            return List.of();
        }
        Set<String> existingCodes = taskCodes(basePlan.items());
        JsonNode tasksNode = objectMapper.readTree(response).path("tasks");
        if (!tasksNode.isArray()) {
            return List.of();
        }

        OffsetDateTime now = OffsetDateTime.now();
        List<TaskPlanItem> tasks = new ArrayList<>();
        for (JsonNode taskNode : tasksNode) {
            String taskCode = text(taskNode, "taskCode");
            if (!StringUtils.hasText(taskCode) || existingCodes.contains(taskCode)) {
                continue;
            }

            TaskAssignee assignee = parseAssignee(text(taskNode, "assignee"));
            if (assignee == TaskAssignee.ORCHESTRATOR) {
                continue;
            }
            List<String> dependencies = stringArray(taskNode.path("dependencyCodes"));
            tasks.add(task(
                    taskCode.strip(),
                    textOrDefault(taskNode, "title", taskCode.strip()),
                    textOrDefault(taskNode, "description", ""),
                    assignee,
                    dependencies,
                    false,
                    now
            ));
            existingCodes.add(taskCode);
        }
        return List.copyOf(tasks);
    }

    private TaskAssignee parseAssignee(String value) {
        try {
            return TaskAssignee.valueOf(value);
        } catch (Exception ex) {
            return TaskAssignee.BUSINESS_EXECUTOR;
        }
    }

    private List<TaskPlanItem> mergeTasks(List<TaskPlanItem> predefinedTasks, List<TaskPlanItem> additionalTasks) {
        List<TaskPlanItem> mergedTasks = new ArrayList<>();
        mergedTasks.addAll(predefinedTasks == null ? List.of() : predefinedTasks);
        mergedTasks.addAll(additionalTasks == null ? List.of() : additionalTasks);
        return List.copyOf(mergedTasks);
    }

    private TaskPlanSnapshot markReadyTasks(TaskPlanSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        Set<String> completedCodes = new LinkedHashSet<>();
        for (TaskPlanItem item : snapshot.items()) {
            if (item.status() == TaskExecutionStatus.COMPLETED) {
                completedCodes.add(item.taskCode());
            }
        }

        List<TaskPlanItem> updatedItems = new ArrayList<>();
        boolean changed = false;
        for (TaskPlanItem item : snapshot.items()) {
            if (item.status() == TaskExecutionStatus.PENDING && completedCodes.containsAll(item.dependencies())) {
                updatedItems.add(taskPlanStore.updateTask(item, TaskExecutionStatus.READY, item.result()));
                changed = true;
            } else {
                updatedItems.add(item);
            }
        }

        TaskPlanSnapshot updatedSnapshot = changed
                ? new TaskPlanSnapshot(snapshot.planId(), snapshot.taskType(), snapshot.userMessage(), snapshot.summary(),
                snapshot.status(), List.copyOf(updatedItems), snapshot.createdAt(), snapshot.updatedAt())
                : snapshot;
        return taskPlanStore.save(updatedSnapshot);
    }

    private TaskPlanSnapshot replaceTask(TaskPlanSnapshot snapshot, TaskPlanItem updatedTask) {
        List<TaskPlanItem> updatedItems = snapshot.items().stream()
                .map(item -> item.taskCode().equals(updatedTask.taskCode()) ? updatedTask : item)
                .toList();
        return new TaskPlanSnapshot(snapshot.planId(), snapshot.taskType(), snapshot.userMessage(), snapshot.summary(),
                snapshot.status(), updatedItems, snapshot.createdAt(), snapshot.updatedAt());
    }

    private TaskPlanSnapshot withStatus(TaskPlanSnapshot snapshot, TaskPlanStatus status) {
        return new TaskPlanSnapshot(snapshot.planId(), snapshot.taskType(), snapshot.userMessage(), snapshot.summary(),
                status, snapshot.items(), snapshot.createdAt(), snapshot.updatedAt());
    }

    private TaskPlanItem nextReadyTask(TaskPlanSnapshot snapshot) {
        if (snapshot == null || snapshot.items() == null) {
            return null;
        }
        return snapshot.items().stream()
                .filter(item -> item.status() == TaskExecutionStatus.READY)
                .findFirst()
                .orElse(null);
    }

    private boolean hasReadyTask(TaskPlanSnapshot snapshot) {
        return nextReadyTask(snapshot) != null;
    }

    private String executeTask(TaskWorkflowState state, TaskPlanSnapshot snapshot, TaskPlanItem task) {
        String executionContext = executionContext(state, snapshot, task);
        return switch (task.assignee()) {
            case BUSINESS_EXECUTOR -> businessExecutionAgent.execute(
                    state.memoryId(),
                    systemPromptMemories(state.memoryId()),
                    executionContext
            );
            case MONITOR -> monitorAgent.execute(executionContext);
            case NOTIFICATION -> notificationAgent.notify(executionContext);
            case SUMMARY -> summaryAgent.summarize(executionContext);
            case ORCHESTRATOR -> taskOrchestrationAgent.suggestAdditionalTasks(executionContext);
        };
    }

    private String executionContext(TaskWorkflowState state, TaskPlanSnapshot snapshot, TaskPlanItem task) {
        return """
                用户原始请求：%s
                当前任务计划：%s
                当前任务：
                - taskCode: %s
                - title: %s
                - description: %s
                - assignee: %s
                已完成任务结果：%s
                """.formatted(
                state.userMessage(),
                snapshot.planId(),
                task.taskCode(),
                task.title(),
                task.description(),
                task.assignee(),
                completedTaskResults(snapshot.items())
        );
    }

    private TaskExecutionStatus statusFromResult(String result) {
        if (!StringUtils.hasText(result)) {
            return TaskExecutionStatus.FAILED;
        }
        String normalized = result.toLowerCase();
        if (normalized.contains("needs_clarification")) {
            return TaskExecutionStatus.BLOCKED;
        }
        if (normalized.contains("\"status\":\"failed\"") || normalized.contains("\"status\": \"failed\"")) {
            return TaskExecutionStatus.FAILED;
        }
        return TaskExecutionStatus.COMPLETED;
    }

    private TaskPlanStatus finalStatus(TaskPlanSnapshot snapshot) {
        if (snapshot.items().stream().anyMatch(item -> item.status() == TaskExecutionStatus.BLOCKED)) {
            return TaskPlanStatus.BLOCKED;
        }
        if (snapshot.items().stream().anyMatch(item -> item.status() == TaskExecutionStatus.FAILED)) {
            return TaskPlanStatus.FAILED;
        }
        if (snapshot.items().stream().allMatch(item -> item.status() == TaskExecutionStatus.COMPLETED
                || item.status() == TaskExecutionStatus.SKIPPED)) {
            return TaskPlanStatus.COMPLETED;
        }
        return TaskPlanStatus.BLOCKED;
    }

    private String summarizeFinalResult(TaskWorkflowState state, TaskPlanSnapshot snapshot, TaskPlanStatus status) {
        String prompt = """
                请基于任务执行结果生成给用户的最终回复。
                用户原始请求：%s
                计划状态：%s
                任务结果：%s
                """.formatted(state.userMessage(), status, completedTaskResults(snapshot.items()));
        try {
            return summaryAgent.summarize(prompt);
        } catch (Exception ex) {
            return fallbackSummary(snapshot, status);
        }
    }

    private String fallbackSummary(TaskPlanSnapshot snapshot, TaskPlanStatus status) {
        return objectJson(Map.of(
                "status", status.name(),
                "planId", snapshot.planId(),
                "summary", "任务已执行，详情见 tasks。",
                "tasks", snapshot.items().stream()
                        .map(item -> Map.of(
                                "taskCode", item.taskCode(),
                                "title", item.title(),
                                "status", item.status().name(),
                                "result", item.result() == null ? "" : item.result()
                        ))
                        .toList()
        ));
    }

    private String objectJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{\"status\":\"FAILED\",\"summary\":\"任务结果序列化失败\"}";
        }
    }

    private String formatTasks(List<TaskPlanItem> tasks) {
        return objectJson((tasks == null ? List.<TaskPlanItem>of() : tasks).stream()
                .map(item -> {
                    Map<String, Object> value = new LinkedHashMap<>();
                    value.put("taskCode", item.taskCode());
                    value.put("title", item.title());
                    value.put("description", item.description());
                    value.put("assignee", item.assignee());
                    value.put("dependencies", item.dependencies());
                    value.put("status", item.status());
                    return value;
                })
                .toList());
    }

    private String completedTaskResults(List<TaskPlanItem> tasks) {
        return objectJson((tasks == null ? List.<TaskPlanItem>of() : tasks).stream()
                .filter(item -> StringUtils.hasText(item.result()))
                .map(item -> Map.of(
                        "taskCode", item.taskCode(),
                        "title", item.title(),
                        "status", item.status().name(),
                        "result", item.result()
                ))
                .toList());
    }

    private String systemPromptMemories(String memoryId) {
        return systemPromptMemoryManager.renderForSystemPrompt(memoryId);
    }

    private Set<String> taskCodes(List<TaskPlanItem> items) {
        Set<String> codes = new LinkedHashSet<>();
        for (TaskPlanItem item : items == null ? List.<TaskPlanItem>of() : items) {
            codes.add(item.taskCode());
        }
        return codes;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : "";
    }

    private String textOrDefault(JsonNode node, String field, String defaultValue) {
        String value = text(node, field);
        return StringUtils.hasText(value) ? value.strip() : defaultValue;
    }

    private List<String> stringArray(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isTextual() && StringUtils.hasText(item.asText())) {
                values.add(item.asText().strip());
            }
        }
        return List.copyOf(values);
    }
}

package com.ticketmind.agent.workflow;

import com.ticketmind.model.dto.TaskPlanSnapshot;
import com.ticketmind.model.dto.TaskPlanStatus;
import com.ticketmind.model.entity.IntentType;
import org.bsc.langgraph4j.state.AgentState;

import java.util.Map;

public class TaskWorkflowState extends AgentState {

    public static final String MEMORY_ID = "memoryId";
    public static final String USER_MESSAGE = "userMessage";
    public static final String INTENT_TYPE = "intentType";
    public static final String PLAN = "plan";
    public static final String FINAL_ANSWER = "finalAnswer";
    public static final String FINAL_STATUS = "finalStatus";
    public static final String ERROR = "error";

    public TaskWorkflowState() {
        super(Map.of());
    }

    public TaskWorkflowState(Map<String, Object> initData) {
        super(initData == null ? Map.of() : initData);
    }

    public String memoryId() {
        return stringValue(MEMORY_ID);
    }

    public String userMessage() {
        return stringValue(USER_MESSAGE);
    }

    public IntentType intentType() {
        Object value = get(INTENT_TYPE);
        if (value instanceof IntentType intentType) {
            return intentType;
        }
        return IntentType.NON_BUSINESS_CHAT;
    }

    public TaskPlanSnapshot plan() {
        Object value = get(PLAN);
        if (value instanceof TaskPlanSnapshot snapshot) {
            return snapshot;
        }
        return null;
    }

    public String finalAnswer() {
        return stringValue(FINAL_ANSWER);
    }

    public TaskPlanStatus finalStatus() {
        Object value = get(FINAL_STATUS);
        if (value instanceof TaskPlanStatus status) {
            return status;
        }
        return TaskPlanStatus.COMPLETED;
    }

    public String error() {
        return stringValue(ERROR);
    }

    private String stringValue(String key) {
        Object value = get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private Object get(String key) {
        return data().get(key);
    }
}

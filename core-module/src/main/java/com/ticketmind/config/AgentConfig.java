package com.ticketmind.config;

import com.ticketmind.agent.core.BusinessExecutionAgent;
import com.ticketmind.agent.core.SummaryAgent;
import com.ticketmind.agent.core.IntentJudgeAgent;
import com.ticketmind.agent.core.MonitorAgent;
import com.ticketmind.agent.core.NotificationAgent;
import com.ticketmind.agent.core.SystemPromptMemoryAgent;
import com.ticketmind.agent.core.TaskOrchestrationAgent;
import com.ticketmind.agent.core.TicketAgent;
import com.ticketmind.agent.tools.NotifyTools;
import com.ticketmind.agent.tools.PlanTools;
import com.ticketmind.agent.tools.TicketInfoTools;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfig {

    @Bean
    public TicketAgent ticketAgent(ChatModel chatModel,
                                   StreamingChatModel streamingChatModel,
                                   ChatMemoryStore chatMemoryStore,
                                   AgentProperties properties,
                                   PlanTools planTools,
                                   TicketInfoTools ticketInfoTools) {
        return AiServices.builder(TicketAgent.class)
                .chatModel(chatModel)
                .streamingChatModel(streamingChatModel)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId)
                        .maxMessages(chatMemoryMaxMessages(properties))
                        .chatMemoryStore(chatMemoryStore)
                        .build())
                .tools(planTools, ticketInfoTools)
                .build();
    }

    @Bean
    public BusinessExecutionAgent businessExecutionAgent(ChatModel chatModel,
                                                        ChatMemoryStore chatMemoryStore,
                                                        AgentProperties properties,
                                                        PlanTools planTools,
                                                        TicketInfoTools ticketInfoTools) {
        return AiServices.builder(BusinessExecutionAgent.class)
                .chatModel(chatModel)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id("biz-" + memoryId)
                        .maxMessages(chatMemoryMaxMessages(properties))
                        .chatMemoryStore(chatMemoryStore)
                        .build())
                .tools(planTools, ticketInfoTools)
                .build();
    }

    private int chatMemoryMaxMessages(AgentProperties properties) {
        int configuredLimit = properties.getChat().getHistoryLimit() * 2 + 8;
        int compactThreshold = properties.getContextCompact().getMessageThreshold() + 8;
        return Math.max(16, Math.max(configuredLimit, compactThreshold));
    }

    @Bean
    public SummaryAgent summaryAgent(
            ChatModel chatModel) {
        return AiServices.builder(SummaryAgent.class)
                .chatModel(chatModel)
                .build();
    }

    @Bean
    public IntentJudgeAgent intentJudgeAgent(ChatModel chatModel) {
        return AiServices.builder(IntentJudgeAgent.class)
                .chatModel(chatModel)
                .build();
    }

    @Bean
    public TaskOrchestrationAgent taskOrchestrationAgent(ChatModel chatModel) {
        return AiServices.builder(TaskOrchestrationAgent.class)
                .chatModel(chatModel)
                .build();
    }

    @Bean
    public MonitorAgent monitorAgent(ChatModel chatModel,
                                     NotifyTools notifyTools,
                                     PlanTools planTools,
                                     TicketInfoTools ticketInfoTools) {
        return AiServices.builder(MonitorAgent.class)
                .chatModel(chatModel)
                .tools(notifyTools, planTools, ticketInfoTools)
                .build();
    }

    @Bean
    public NotificationAgent notificationAgent(ChatModel chatModel,
                                               NotifyTools notifyTools) {
        return AiServices.builder(NotificationAgent.class)
                .chatModel(chatModel)
                .tools(notifyTools)
                .build();
    }

    @Bean
    public SystemPromptMemoryAgent systemPromptMemoryAgent(ChatModel chatModel) {
        return AiServices.builder(SystemPromptMemoryAgent.class)
                .chatModel(chatModel)
                .build();
    }
}

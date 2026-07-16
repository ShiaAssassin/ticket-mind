package com.ticketmind.config;

import com.ticketmind.agent.core.ConversationSummaryAgent;
import com.ticketmind.agent.core.IntentJudgeAgent;
import com.ticketmind.agent.core.MonitorAgent;
import com.ticketmind.agent.core.NotificationAgent;
import com.ticketmind.agent.core.TicketAgent;
import com.ticketmind.agent.tool.registry.AccessTools;
import com.ticketmind.agent.tool.registry.ExternalInfoTools;
import com.ticketmind.agent.tool.registry.NotifyTools;
import com.ticketmind.agent.tool.registry.OrderTools;
import com.ticketmind.agent.tool.registry.PlanTools;
import com.ticketmind.agent.tool.registry.TicketInfoTools;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfig {

    @Bean
    public TicketAgent ticketAgent(ChatModel chatModel,
                                   AccessTools accessTools,
                                   ExternalInfoTools externalInfoTools,
                                   OrderTools orderTools,
                                   PlanTools planTools,
                                   TicketInfoTools ticketInfoTools) {
        return AiServices.builder(TicketAgent.class)
                .chatModel(chatModel)
                .tools(accessTools, externalInfoTools, orderTools, planTools, ticketInfoTools)
                .build();
    }

    @Bean
    public ConversationSummaryAgent conversationSummaryAgent(
            ChatModel chatModel) {
        return AiServices.builder(ConversationSummaryAgent.class)
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
}

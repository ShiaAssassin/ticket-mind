package com.ticketmind.config;

import com.ticketmind.agent.core.ConversationSummaryAgent;
import com.ticketmind.agent.core.IntentJudgeAgent;
import com.ticketmind.agent.core.MonitorAgent;
import com.ticketmind.agent.core.NotificationAgent;
import com.ticketmind.agent.core.TicketAgent;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfig {

    @Bean
    public List<Object> ticketAgentTools() {
        // TODO 这里补充 TicketAgent 需要的票务查询、订单处理、候补与下单工具。
        return List.of();
    }

    @Bean
    public List<Object> conversationSummaryAgentTools() {
        // TODO 这里补充 ConversationSummaryAgent 需要的会话历史、用户画像或知识检索工具。
        return List.of();
    }

    @Bean
    public List<Object> intentJudgeAgentTools() {
        // TODO 这里补充 IntentJudgeAgent 需要的意图标签、上下文辅助判断工具。
        return List.of();
    }

    @Bean
    public List<Object> monitorAgentTools() {
        // TODO 这里补充 MonitorAgent 需要的余票监控、时间窗口与任务配置工具。
        return List.of();
    }

    @Bean
    public List<Object> notificationAgentTools() {
        // TODO 这里补充 NotificationAgent 需要的通知渠道、模板渲染和事件格式化工具。
        return List.of();
    }

    @Bean
    public TicketAgent ticketAgent(ChatModel chatModel,
                                   @Qualifier("ticketAgentTools") List<Object> tools) {
        return buildAgent(chatModel, TicketAgent.class, tools);
    }

    @Bean
    public ConversationSummaryAgent conversationSummaryAgent(
            ChatModel chatModel,
            @Qualifier("conversationSummaryAgentTools") List<Object> tools) {
        return buildAgent(chatModel, ConversationSummaryAgent.class, tools);
    }

    @Bean
    public IntentJudgeAgent intentJudgeAgent(ChatModel chatModel,
                                             @Qualifier("intentJudgeAgentTools") List<Object> tools) {
        return buildAgent(chatModel, IntentJudgeAgent.class, tools);
    }

    @Bean
    public MonitorAgent monitorAgent(ChatModel chatModel,
                                     @Qualifier("monitorAgentTools") List<Object> tools) {
        return buildAgent(chatModel, MonitorAgent.class, tools);
    }

    @Bean
    public NotificationAgent notificationAgent(ChatModel chatModel,
                                               @Qualifier("notificationAgentTools") List<Object> tools) {
        return buildAgent(chatModel, NotificationAgent.class, tools);
    }

    private <T> T buildAgent(ChatModel chatModel, Class<T> agentType, List<Object> tools) {
        AiServices<T> builder = AiServices.builder(agentType).chatModel(chatModel);
        if (!tools.isEmpty()) {
            builder.tools(tools);
        }
        return builder.build();
    }
}

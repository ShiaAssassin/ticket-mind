package com.ticketmind.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
@ConditionalOnProperty(prefix = "ticket-mind.rabbitmq", name = "enabled", havingValue = "true")
public class RabbitMqConfig {

    @Bean
    public TopicExchange knowledgeUploadExchange(AgentProperties agentProperties) {
        return new TopicExchange(agentProperties.getRabbitmq().getKnowledgeUploadExchange(), true, false);
    }

    @Bean
    public Queue knowledgeUploadQueue(AgentProperties agentProperties) {
        return new Queue(agentProperties.getRabbitmq().getKnowledgeUploadQueue(), true);
    }

    @Bean
    public Binding knowledgeUploadBinding(
            @Qualifier("knowledgeUploadQueue") Queue knowledgeUploadQueue,
            @Qualifier("knowledgeUploadExchange") TopicExchange knowledgeUploadExchange,
            AgentProperties agentProperties
    ) {
        return BindingBuilder.bind(knowledgeUploadQueue)
                .to(knowledgeUploadExchange)
                .with(agentProperties.getRabbitmq().getKnowledgeUploadRoutingKey());
    }

    @Bean
    public TopicExchange todoListArchiveExchange(AgentProperties agentProperties) {
        return new TopicExchange(agentProperties.getRabbitmq().getTodoListArchiveExchange(), true, false);
    }

    @Bean
    public Queue todoListArchiveQueue(AgentProperties agentProperties) {
        return new Queue(agentProperties.getRabbitmq().getTodoListArchiveQueue(), true);
    }

    @Bean
    public Binding todoListArchiveBinding(
            @Qualifier("todoListArchiveQueue") Queue todoListArchiveQueue,
            @Qualifier("todoListArchiveExchange") TopicExchange todoListArchiveExchange,
            AgentProperties agentProperties
    ) {
        return BindingBuilder.bind(todoListArchiveQueue)
                .to(todoListArchiveExchange)
                .with(agentProperties.getRabbitmq().getTodoListArchiveRoutingKey());
    }

    @Bean
    public MessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}

package com.ticketmind.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
            Queue knowledgeUploadQueue,
            TopicExchange knowledgeUploadExchange,
            AgentProperties agentProperties
    ) {
        return BindingBuilder.bind(knowledgeUploadQueue)
                .to(knowledgeUploadExchange)
                .with(agentProperties.getRabbitmq().getKnowledgeUploadRoutingKey());
    }

    @Bean
    public MessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}

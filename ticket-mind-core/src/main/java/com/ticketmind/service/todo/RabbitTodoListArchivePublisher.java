package com.ticketmind.service.todo;

import com.ticketmind.common.BusinessException;
import com.ticketmind.common.ResultCode;
import com.ticketmind.config.AgentProperties;
import com.ticketmind.model.dto.TodoListArchiveEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ticket-mind.rabbitmq", name = "enabled", havingValue = "true")
public class RabbitTodoListArchivePublisher implements TodoListArchivePublisher {

    private final RabbitTemplate rabbitTemplate;

    private final AgentProperties agentProperties;

    @Override
    public void publish(TodoListArchiveEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    agentProperties.getRabbitmq().getTodoListArchiveExchange(),
                    agentProperties.getRabbitmq().getTodoListArchiveRoutingKey(),
                    event
            );
        } catch (AmqpException ex) {
            throw new BusinessException(ResultCode.MESSAGE_QUEUE_ERROR, "发送 TodoList 归档消息失败");
        }
    }
}

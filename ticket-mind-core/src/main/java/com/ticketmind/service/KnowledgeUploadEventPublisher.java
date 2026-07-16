package com.ticketmind.service;

import com.ticketmind.common.BusinessException;
import com.ticketmind.common.ResultCode;
import com.ticketmind.config.AgentProperties;
import com.ticketmind.model.dto.KnowledgeDocumentUploadedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ticket-mind.rabbitmq", name = "enabled", havingValue = "true")
public class KnowledgeUploadEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    private final AgentProperties agentProperties;

    public void publishAfterCommit(KnowledgeDocumentUploadedEvent event) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish(event);
                }
            });
            return;
        }
        publish(event);
    }

    private void publish(KnowledgeDocumentUploadedEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    agentProperties.getRabbitmq().getKnowledgeUploadExchange(),
                    agentProperties.getRabbitmq().getKnowledgeUploadRoutingKey(),
                    event
            );
        } catch (AmqpException ex) {
            throw new BusinessException(ResultCode.MESSAGE_QUEUE_ERROR, "发送知识库上传消息失败");
        }
    }
}

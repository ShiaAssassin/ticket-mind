package com.ticketmind.service.knowledge;

import com.ticketmind.model.dto.KnowledgeDocumentUploadedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "ticket-mind.rabbitmq", name = "enabled", havingValue = "true")
public class KnowledgeUploadEventListener {

    @RabbitListener(queues = "${ticket-mind.rabbitmq.knowledge-upload-queue}")
    public void onKnowledgeDocumentUploaded(KnowledgeDocumentUploadedEvent event) {
        log.info(
                "Received knowledge upload event, source={}, filename={}, chunkCount={}",
                event.source(),
                event.filename(),
                event.chunkCount()
        );
    }
}

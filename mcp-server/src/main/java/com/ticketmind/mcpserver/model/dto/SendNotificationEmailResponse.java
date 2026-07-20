package com.ticketmind.mcpserver.model.dto;

import java.time.Instant;

public class SendNotificationEmailResponse {

    private final String recipient;
    private final String subject;
    private final String status;
    private final Instant sentAt;

    public SendNotificationEmailResponse(String recipient, String subject, String status, Instant sentAt) {
        this.recipient = recipient;
        this.subject = subject;
        this.status = status;
        this.sentAt = sentAt;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getSubject() {
        return subject;
    }

    public String getStatus() {
        return status;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}

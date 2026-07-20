package com.ticketmind.mcpserver.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SendNotificationEmailRequest {

    @NotBlank
    @Email
    @Size(max = 200)
    private String recipient;

    @NotBlank
    @Size(max = 200)
    private String subject;

    @NotBlank
    @Size(max = 10000)
    private String content;

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}

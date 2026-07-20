package com.ticketmind.mcpserver.controller;

import com.ticketmind.common.Result;
import com.ticketmind.mcpserver.model.dto.SendNotificationEmailRequest;
import com.ticketmind.mcpserver.model.dto.SendNotificationEmailResponse;
import com.ticketmind.mcpserver.service.NotificationEmailService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/notifications")
public class NotificationController {

    private final NotificationEmailService notificationEmailService;

    public NotificationController(NotificationEmailService notificationEmailService) {
        this.notificationEmailService = notificationEmailService;
    }

    @PostMapping("/email")
    public ResponseEntity<Result<SendNotificationEmailResponse>> sendNotificationEmail(
            @Valid @RequestBody SendNotificationEmailRequest request) {
        return ResponseEntity.ok(Result.success(notificationEmailService.send(request)));
    }
}

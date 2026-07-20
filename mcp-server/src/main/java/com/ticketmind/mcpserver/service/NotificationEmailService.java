package com.ticketmind.mcpserver.service;

import com.ticketmind.common.BusinessException;
import com.ticketmind.common.ResultCode;
import com.ticketmind.mcpserver.config.NotificationMailProperties;
import com.ticketmind.mcpserver.model.dto.SendNotificationEmailRequest;
import com.ticketmind.mcpserver.model.dto.SendNotificationEmailResponse;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;

@Service
public class NotificationEmailService {

    private final JavaMailSender mailSender;
    private final NotificationMailProperties mailProperties;

    public NotificationEmailService(JavaMailSender mailSender, NotificationMailProperties mailProperties) {
        this.mailSender = mailSender;
        this.mailProperties = mailProperties;
    }

    public SendNotificationEmailResponse send(SendNotificationEmailRequest request) {
        if (!StringUtils.hasText(mailProperties.getFromAddress())) {
            throw new BusinessException(ResultCode.MAIL_SERVICE_ERROR, "邮件发件人未配置");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailProperties.getFromAddress().trim());
        message.setTo(request.getRecipient().trim());
        message.setSubject(request.getSubject().trim());
        message.setText(request.getContent());

        try {
            mailSender.send(message);
        } catch (MailException exception) {
            throw new BusinessException(ResultCode.MAIL_SERVICE_ERROR, "发送通知邮件失败");
        }

        return new SendNotificationEmailResponse(
                request.getRecipient().trim(),
                request.getSubject().trim(),
                "SENT",
                Instant.now()
        );
    }
}

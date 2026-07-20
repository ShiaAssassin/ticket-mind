package com.ticketmind.mcpserver.config;

import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

@Configuration
@EnableConfigurationProperties({
        McpServerProperties.class,
        NotificationMailProperties.class,
        MailProperties.class
})
public class McpServerConfig {

    @Bean
    public JavaMailSender javaMailSender(MailProperties mailProperties) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(mailProperties.getHost());
        mailSender.setPort(mailProperties.getPort());
        mailSender.setUsername(mailProperties.getUsername());
        mailSender.setPassword(mailProperties.getPassword());
        mailSender.setDefaultEncoding(StandardCharsets.UTF_8.name());

        Properties javaMailProperties = new Properties();
        javaMailProperties.putAll(mailProperties.getProperties());
        mailSender.setJavaMailProperties(javaMailProperties);
        return mailSender;
    }
}

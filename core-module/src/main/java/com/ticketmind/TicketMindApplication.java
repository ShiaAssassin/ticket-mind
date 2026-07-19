package com.ticketmind;

import com.ticketmind.config.AgentProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = {
        "com.ticketmind",
        "com.ticketmind.common"
})
@EnableConfigurationProperties(AgentProperties.class)
public class TicketMindApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketMindApplication.class, args);
    }
}

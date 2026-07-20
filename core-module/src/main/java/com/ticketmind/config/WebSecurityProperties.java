package com.ticketmind.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "security")
public class WebSecurityProperties {

    private Jwt jwt = new Jwt();

    private Permit permit = new Permit();

    @Getter
    @Setter
    public static class Jwt {
        private String secret;
        private String issuer = "ticket-mind";
        private Duration accessTokenTtl = Duration.ofHours(2);
        private Duration refreshTokenTtl = Duration.ofDays(14);
    }

    @Getter
    @Setter
    public static class Permit {
        private List<RequestMatcherProperties> matchers = new ArrayList<>();
        private List<String> ips = new ArrayList<>();
    }


    @Getter
    @Setter
    public static class RequestMatcherProperties {
        private String pattern;
        private String method;
    }
}

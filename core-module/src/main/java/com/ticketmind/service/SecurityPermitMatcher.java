package com.ticketmind.service;

import com.ticketmind.config.WebSecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.Locale;

@Component
public class SecurityPermitMatcher {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String X_REAL_IP = "X-Real-IP";

    private final WebSecurityProperties properties;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public SecurityPermitMatcher(WebSecurityProperties properties) {
        this.properties = properties;
    }

    public boolean isPermitted(HttpServletRequest request) {
        return isPermittedIp(resolveClientIp(request)) || isPermittedPath(request);
    }

    private boolean isPermittedPath(HttpServletRequest request) {
        String requestPath = request.getRequestURI();
        String requestMethod = request.getMethod();

        return properties.getPermit().getMatchers().stream()
                .anyMatch(matcher -> isMethodMatched(matcher.getMethod(), requestMethod)
                        && pathMatcher.match(matcher.getPattern(), requestPath));
    }

    private boolean isMethodMatched(String configuredMethod, String requestMethod) {
        String method = configuredMethod.trim().toUpperCase(Locale.ROOT);
        return method.equals(HttpMethod.valueOf(requestMethod).name());
    }

    private boolean isPermittedIp(String clientIp) {
        return properties.getPermit().getIps().stream()
                .map(String::trim)
                .anyMatch(permitIp -> permitIp.equals(clientIp));
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader(X_FORWARDED_FOR);
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        String realIp = request.getHeader(X_REAL_IP);
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }

        return request.getRemoteAddr();
    }
}

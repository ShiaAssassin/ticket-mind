package com.ticketmind.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketmind.common.Result;
import com.ticketmind.common.ResultCode;
import com.ticketmind.context.UserContextHolder;
import com.ticketmind.model.entity.JwtTokenClaims;
import com.ticketmind.model.entity.TokenType;
import com.ticketmind.service.JwtTokenService;
import com.ticketmind.service.SecurityPermitMatcher;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService jwtTokenService;

    private final SecurityPermitMatcher securityPermitMatcher;

    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler)
            throws IOException {
        if (securityPermitMatcher.isPermitted(request)) {
            return true;
        }

        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            writeError(response, ResultCode.MISSING_TOKEN);
            return false;
        }

        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (!StringUtils.hasText(token)) {
            writeError(response, ResultCode.MISSING_TOKEN);
            return false;
        }

        try {
            JwtTokenClaims claims = jwtTokenService.parseToken(token);
            if (claims.tokenType() != TokenType.ACCESS_TOKEN) {
                writeError(response, ResultCode.INVALID_TOKEN);
                return false;
            }
            UserContextHolder.setUser(claims.userId(), claims.username());
            return true;
        } catch (ExpiredJwtException ex) {
            writeError(response, ResultCode.ACCESS_TOKEN_EXPIRED);
            return false;
        } catch (JwtException | IllegalArgumentException ex) {
            writeError(response, ResultCode.INVALID_TOKEN);
            return false;
        }
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                                @NonNull HttpServletResponse response,
                                @NonNull Object handler,
                                Exception ex) {
        UserContextHolder.clear();
    }

    private void writeError(HttpServletResponse response, ResultCode resultCode) throws IOException {
        response.setStatus(resultCode.getHttpStatus().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Result.fail(resultCode));
    }
}

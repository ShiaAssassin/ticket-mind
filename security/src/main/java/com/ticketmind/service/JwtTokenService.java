package com.ticketmind.service;

import com.ticketmind.config.WebSecurityProperties;
import com.ticketmind.model.entity.JwtTokenClaims;
import com.ticketmind.model.entity.TokenType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtTokenService {

    private static final String USER_ID_CLAIM = "userId";
    private static final String USERNAME_CLAIM = "username";
    private static final String TOKEN_TYPE_CLAIM = "tokenType";

    private final WebSecurityProperties properties;

    public JwtTokenService(WebSecurityProperties properties) {
        this.properties = properties;
    }

    public String createAccessToken(Long userId, String username) {
        return createToken(userId, username, TokenType.ACCESS_TOKEN);
    }

    public String createRefreshToken(Long userId, String username) {
        return createToken(userId, username, TokenType.REFRESH_TOKEN);
    }

    public JwtTokenClaims parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey())
                .requireIssuer(properties.getJwt().getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        Long userId = claims.get(USER_ID_CLAIM, Long.class);
        String username = claims.get(USERNAME_CLAIM, String.class);
        TokenType tokenType = TokenType.valueOf(claims.get(TOKEN_TYPE_CLAIM, String.class));
        return new JwtTokenClaims(userId, username, tokenType);
    }

    private String createToken(Long userId, String username, TokenType tokenType) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(tokenType == TokenType.ACCESS_TOKEN
                ? properties.getJwt().getAccessTokenTtl()
                : properties.getJwt().getRefreshTokenTtl());

        return Jwts.builder()
                .issuer(properties.getJwt().getIssuer())
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim(USER_ID_CLAIM, userId)
                .claim(USERNAME_CLAIM, username)
                .claim(TOKEN_TYPE_CLAIM, tokenType.name())
                .signWith(secretKey())
                .compact();
    }

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
    }
}

package com.ticketmind.service.impl;

import com.ticketmind.common.BusinessException;
import com.ticketmind.common.ResultCode;
import com.ticketmind.model.dto.LoginRequest;
import com.ticketmind.model.dto.LoginResponse;
import com.ticketmind.model.dto.RefreshTokenRequest;
import com.ticketmind.model.dto.RefreshTokenResponse;
import com.ticketmind.model.dto.RegisterRequest;
import com.ticketmind.model.entity.JwtTokenClaims;
import com.ticketmind.model.entity.TokenType;
import com.ticketmind.model.entity.UserAccount;
import com.ticketmind.repository.UserAccountRepository;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserAccountRepository userAccountRepository;

    private final JwtTokenService jwtTokenService;

    @Transactional
    public LoginResponse register(RegisterRequest request) {
        String username = request.username().trim();
        if (userAccountRepository.existsByUsername(username)) {
            throw new BusinessException(ResultCode.USERNAME_ALREADY_EXISTS);
        }

        String displayName = StringUtils.hasText(request.displayName())
                ? request.displayName().trim()
                : username;

        UserAccount userAccount = new UserAccount();
        userAccount.setUsername(username);
        userAccount.setPassword(request.password());
        userAccount.setDisplayName(displayName);
        userAccount.setEnabled(true);

        UserAccount savedUser = userAccountRepository.save(userAccount);
        String accessToken = jwtTokenService.createAccessToken(savedUser.getId(), savedUser.getUsername());
        String refreshToken = jwtTokenService.createRefreshToken(savedUser.getId(), savedUser.getUsername());

        return new LoginResponse(
                savedUser.getId(),
                savedUser.getUsername(),
                savedUser.getDisplayName(),
                accessToken,
                refreshToken
        );
    }

    public LoginResponse login(LoginRequest request) {
        UserAccount userAccount = userAccountRepository.findByUsername(request.username())
                .filter(user -> user.isEnabled() && user.getPassword().equals(request.password()))
                .orElseThrow(() -> new BusinessException(ResultCode.INVALID_USERNAME_OR_PASSWORD));

        String accessToken = jwtTokenService.createAccessToken(userAccount.getId(), userAccount.getUsername());
        String refreshToken = jwtTokenService.createRefreshToken(userAccount.getId(), userAccount.getUsername());

        return new LoginResponse(
                userAccount.getId(),
                userAccount.getUsername(),
                userAccount.getDisplayName(),
                accessToken,
                refreshToken
        );
    }

    public RefreshTokenResponse refreshAccessToken(RefreshTokenRequest request) {
        JwtTokenClaims claims;
        try {
            claims = jwtTokenService.parseToken(request.refreshToken());
        } catch (ExpiredJwtException ex) {
            throw new BusinessException(ResultCode.REFRESH_TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BusinessException(ResultCode.INVALID_TOKEN);
        }

        if (claims.tokenType() != TokenType.REFRESH_TOKEN) {
            throw new BusinessException(ResultCode.INVALID_TOKEN);
        }

        String accessToken = jwtTokenService.createAccessToken(claims.userId(), claims.username());
        return new RefreshTokenResponse(accessToken);
    }
}

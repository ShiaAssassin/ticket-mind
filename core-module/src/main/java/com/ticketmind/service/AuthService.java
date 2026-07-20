package com.ticketmind.service;

import com.ticketmind.common.BusinessException;
import com.ticketmind.common.ResultCode;
import com.ticketmind.model.dto.LoginRequest;
import com.ticketmind.model.dto.LoginResponse;
import com.ticketmind.model.dto.RefreshTokenRequest;
import com.ticketmind.model.dto.RefreshTokenResponse;
import com.ticketmind.model.entity.JwtTokenClaims;
import com.ticketmind.model.entity.TokenType;
import com.ticketmind.model.entity.UserAccount;
import com.ticketmind.repository.UserAccountRepository;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserAccountRepository userAccountRepository;

    private final JwtTokenService jwtTokenService;

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

package com.ticketmind.controller;

import com.ticketmind.common.Result;
import com.ticketmind.model.dto.LoginRequest;
import com.ticketmind.model.dto.LoginResponse;
import com.ticketmind.model.dto.RefreshTokenRequest;
import com.ticketmind.model.dto.RefreshTokenResponse;
import com.ticketmind.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    @PostMapping("/refresh")
    public Result<RefreshTokenResponse> refreshAccessToken(@RequestBody RefreshTokenRequest request) {
        return Result.success(authService.refreshAccessToken(request));
    }
}

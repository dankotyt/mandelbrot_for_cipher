package com.cipher.online.controller;

import com.cipher.common.api.AuthApi;
import com.cipher.common.dto.AuthResponse;
import com.cipher.common.dto.LoginRequest;
import com.cipher.common.dto.NonceRequest;
import com.cipher.common.dto.NonceResponse;
import com.cipher.online.service.AuthServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthController implements AuthApi {

    private final AuthServiceImpl authService;

    @Override
    public NonceResponse requestNonce(@RequestBody NonceRequest request) {
        return authService.requestNonce(request);
    }

    @Override
    public AuthResponse login(@RequestBody LoginRequest request) {
        return authService.login(request);
    }
}

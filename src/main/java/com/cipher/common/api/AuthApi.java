package com.cipher.common.api;

import com.cipher.common.dto.AuthResponse;
import com.cipher.common.dto.LoginRequest;
import com.cipher.common.dto.NonceRequest;
import com.cipher.common.dto.NonceResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


public interface AuthApi {
    @PostMapping("/api/auth/nonce")
    NonceResponse requestNonce(@RequestBody NonceRequest request);

    @PostMapping("/api/auth/login")
    AuthResponse login(@RequestBody LoginRequest request);
}




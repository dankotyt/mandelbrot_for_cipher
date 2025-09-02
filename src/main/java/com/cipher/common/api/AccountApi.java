package com.cipher.common.api;

import com.cipher.common.dto.AuthRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

public interface AccountApi {
    @PostMapping("/api/accounts/generate")
    ResponseEntity<Void> generateAccount(@RequestBody AuthRequest request);
}

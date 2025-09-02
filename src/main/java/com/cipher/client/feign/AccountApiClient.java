package com.cipher.client.feign;

import com.cipher.common.dto.AuthRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "account-client", url = "${server.url}")
public interface AccountApiClient {
    @PostMapping("/api/accounts/generate")
    ResponseEntity<Void> generateAccount(@RequestBody AuthRequest request);
}

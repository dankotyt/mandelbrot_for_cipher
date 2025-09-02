package com.cipher.online.controller;

import com.cipher.common.api.AccountApi;
import com.cipher.common.dto.AuthRequest;
import com.cipher.online.service.AccountServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequiredArgsConstructor
public class AccountController implements AccountApi {

    private final AccountServiceImpl accountService;

    @Override
    public ResponseEntity<Void> generateAccount(AuthRequest request) {
        return accountService.generateAccount(request);
    }
}

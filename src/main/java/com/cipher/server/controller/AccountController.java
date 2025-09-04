package com.cipher.server.controller;

import com.cipher.common.api.AccountApi;
import com.cipher.common.dto.AuthRequest;
import com.cipher.server.service.AccountServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST контроллер для операций с аккаунтами.
 * Реализует {@link AccountApi} интерфейс и обрабатывает HTTP запросы.
 */
@RestController
@RequiredArgsConstructor
public class AccountController implements AccountApi {

    private final AccountServiceImpl accountService;

    /**
     * Обрабатывает запрос на создание нового аккаунта.
     * Делегирует выполнение сервису {@link AccountServiceImpl}.
     *
     * @param request запрос на создание аккаунта
     * @return ResponseEntity с соответствующим HTTP статусом
     */
    @Override
    public ResponseEntity<Void> generateAccount(AuthRequest request) {
        return accountService.generateAccount(request);
    }
}

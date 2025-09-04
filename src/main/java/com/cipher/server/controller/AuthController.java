package com.cipher.server.controller;

import com.cipher.common.api.AuthApi;
import com.cipher.common.dto.AuthResponse;
import com.cipher.common.dto.LoginRequest;
import com.cipher.common.dto.NonceRequest;
import com.cipher.common.dto.NonceResponse;
import com.cipher.server.service.AuthServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST контроллер для аутентификации и авторизации.
 * Реализует {@link AuthApi} интерфейс и обрабатывает HTTP запросы.
 */
@RestController
@RequiredArgsConstructor
public class AuthController implements AuthApi {

    private final AuthServiceImpl authService;

    /**
     * Обрабатывает запрос на получение nonce для аутентификации.
     * Делегирует выполнение сервису {@link AuthServiceImpl}.
     *
     * @param request запрос nonce
     * @return ответ с одноразовым числом
     */
    @Override
    public NonceResponse requestNonce(@RequestBody NonceRequest request) {
        return authService.requestNonce(request);
    }

    /**
     * Обрабатывает запрос на аутентификацию пользователя.
     * Делегирует выполнение сервису {@link AuthServiceImpl}.
     *
     * @param request запрос на вход
     * @return ответ аутентификации с access token
     */
    @Override
    public AuthResponse login(@RequestBody LoginRequest request) {
        return authService.login(request);
    }
}

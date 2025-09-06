package com.cipher.common.api;

import com.cipher.common.dto.AuthRequest;
import com.cipher.server.controller.AccountController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

/**
 * API интерфейс для управления аккаунтами.
 * Определяет контракт REST endpoints для операций с аккаунтами.
 * Реализуется на сервере в {@link AccountController} и используется клиентом через Feign.
 */
public interface AccountApi {

    /**
     * Создает новый аккаунт пользователя на основе предоставленных учетных данных.
     *
     * @param request запрос на создание аккаунта, содержащий идентификатор пользователя и публичный ключ
     * @return ResponseEntity с пустым телом и HTTP статусом:
     *         - 201 Created при успешном создании
     *         - 409 Conflict если аккаунт уже существует
     *         - 400 Bad Request при невалидных данных
     * @throws HttpClientErrorException при ошибках клиента (4xx)
     * @throws HttpServerErrorException при ошибках сервера (5xx)
     */
    @PostMapping("/api/accounts/generate")
    ResponseEntity<Void> generateAccount(@RequestBody AuthRequest request);
}

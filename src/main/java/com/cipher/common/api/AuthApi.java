package com.cipher.common.api;

import com.cipher.common.dto.AuthResponse;
import com.cipher.common.dto.LoginRequest;
import com.cipher.common.dto.NonceRequest;
import com.cipher.common.dto.NonceResponse;
import com.cipher.server.controller.AuthController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

/**
 * API интерфейс для аутентификации и авторизации.
 * Определяет контракт REST endpoints для процессов входа и аутентификации.
 * Реализуется на сервере в {@link AuthController} и используется клиентом через Feign.
 */
public interface AuthApi {

    /**
     * Запрашивает одноразовое число (nonce) для процесса аутентификации.
     * Nonce используется для предотвращения replay-атак.
     *
     * @param request запрос nonce, содержащий идентификатор пользователя
     * @return ответ со сгенерированным nonce
     * @throws HttpClientErrorException если пользователь не найден (404)
     * @throws HttpServerErrorException при внутренних ошибках сервера
     */
    @PostMapping("/api/auth/nonce")
    NonceResponse requestNonce(@RequestBody NonceRequest request);

    /**
     * Выполняет аутентификацию пользователя с помощью подписанного nonce.
     *
     * @param request запрос на вход, содержащий идентификатор пользователя и криптографическую подпись
     * @return ответ аутентификации с access token и данными пользователя
     * @throws HttpClientErrorException при неверной подписи (401) или истекшем nonce
     * @throws HttpServerErrorException при внутренних ошибках сервера
     */
    @PostMapping("/api/auth/login")
    AuthResponse login(@RequestBody LoginRequest request);
}




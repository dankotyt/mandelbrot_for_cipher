package com.cipher.client.feign;

import com.cipher.common.dto.AuthResponse;
import com.cipher.common.dto.LoginRequest;
import com.cipher.common.dto.NonceRequest;
import com.cipher.common.dto.NonceResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

/**
 * Feign клиент для работы с API аутентификации.
 * Выполняет HTTP-запросы к сервису аутентификации и авторизации.
 *
 * @see FeignClient
 */
@FeignClient(name = "auth-client", url = "${server.url}")
public interface AuthApiClient {

    /**
     * Запрашивает nonce (одноразовое число) для процесса аутентификации.
     *
     * @param request запрос nonce, содержащий идентификатор пользователя
     * @return ответ с nonce для подписи
     * @throws HttpClientErrorException в случае ошибок клиента (4xx)
     * @throws HttpServerErrorException в случае ошибок сервера (5xx)
     */
    @PostMapping("/api/auth/nonce")
    NonceResponse requestNonce(@RequestBody NonceRequest request);

    /**
     * Выполняет аутентификацию пользователя с помощью подписанного nonce.
     *
     * @param request запрос на вход, содержащий идентификатор пользователя и подпись
     * @return ответ аутентификации с access token
     * @throws HttpClientErrorException в случае ошибок клиента (4xx)
     * @throws HttpServerErrorException в случае ошибок сервера (5xx)
     */
    @PostMapping("/api/auth/login")
    AuthResponse login(@RequestBody LoginRequest request);
}

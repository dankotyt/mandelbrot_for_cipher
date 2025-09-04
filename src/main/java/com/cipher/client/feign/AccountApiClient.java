package com.cipher.client.feign;

import com.cipher.common.dto.AuthRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

/**
 * Feign клиент для работы с API аккаунтов.
 * Выполняет HTTP-запросы к сервису управления аккаунтами.
 *
 * @see FeignClient
 */
@FeignClient(name = "account-client", url = "${server.url}")
public interface AccountApiClient {

    /**
     * Генерирует новый аккаунт на сервере.
     *
     * @param request запрос на генерацию аккаунта, содержащий идентификатор пользователя и публичный ключ
     * @return ResponseEntity с пустым телом и статусом ответа
     * @throws HttpClientErrorException в случае ошибок клиента (4xx)
     * @throws HttpServerErrorException в случае ошибок сервера (5xx)
     */
    @PostMapping("/api/accounts/generate")
    ResponseEntity<Void> generateAccount(@RequestBody AuthRequest request);
}

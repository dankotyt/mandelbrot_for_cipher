package com.cipher.server.handler;

import com.cipher.common.exception.AccountAlreadyExistsException;
import com.cipher.common.exception.SeedNotFoundException;
import com.cipher.server.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * Глобальный обработчик исключений для REST контроллеров.
 * Обрабатывает исключения, возникающие в процессе выполнения запросов,
 * и возвращает стандартизированные ответы об ошибках.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Обрабатывает исключение при попытке создания уже существующего аккаунта.
     *
     * @param ex исключение AccountAlreadyExistsException
     * @return ResponseEntity с HTTP статусом 409 Conflict и деталями ошибки
     */
    @ExceptionHandler(AccountAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleAccountExists(AccountAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("ACCOUNT_EXISTS", ex.getMessage()));
    }

    /**
     * Обрабатывает исключение при отсутствии аккаунта пользователя.
     *
     * @param ex исключение SeedNotFoundException
     * @return ResponseEntity с HTTP статусом 404 Not Found и деталями ошибки
     */
    @ExceptionHandler(SeedNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSeedNotFound(SeedNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("ACCOUNT_NOT_FOUND", ex.getMessage()));
    }

    /**
     * Обрабатывает исключения безопасности (неверная подпись, истекший nonce и т.д.).
     *
     * @param ex исключение SecurityException
     * @return ResponseEntity с HTTP статусом 401 Unauthorized и деталями ошибки
     */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ErrorResponse> handleSecurityException(SecurityException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("SECURITY_ERROR", ex.getMessage()));
    }

    /**
     * Обрабатывает все непредвиденные исключения.
     *
     * @param ex исключение Exception
     * @return ResponseEntity с HTTP статусом 500 Internal Server Error
     *         и общим сообщением об ошибке
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_ERROR", "Внутренняя ошибка сервера"));
    }
}

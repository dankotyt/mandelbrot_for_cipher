package com.cipher.server.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * Стандартный ответ об ошибке для REST API.
 * Используется для унифицированной обработки ошибок в контроллерах.
 */
@Getter
@Setter
@AllArgsConstructor
public class ErrorResponse {
    private String code;
    private String message;
}

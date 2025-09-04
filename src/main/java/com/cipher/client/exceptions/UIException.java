package com.cipher.client.exceptions;

public class UIException extends RuntimeException {
    public UIException(String message) {
        super(message);
    }

    public UIException(String message, Throwable cause) {
        super(message, cause);
    }
}

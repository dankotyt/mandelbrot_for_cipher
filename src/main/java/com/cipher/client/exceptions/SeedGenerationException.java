package com.cipher.client.exceptions;

public class SeedGenerationException extends RuntimeException {
    public SeedGenerationException(String message) {
        super(message);
    }

    public SeedGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}

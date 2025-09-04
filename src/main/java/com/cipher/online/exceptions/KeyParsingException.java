package com.cipher.online.exceptions;

public class KeyParsingException extends RuntimeException {
  public KeyParsingException(String message) {
    super(message);
  }

  public KeyParsingException(String message, Throwable cause) {
    super(message, cause);
  }
}

package com.cipher.common.dto;

/**
 * Ответ с одноразовым числом (nonce) для аутентификации.
 *
 * @param nonce одноразовое число в виде строки
 */
public record NonceResponse(String nonce) {}

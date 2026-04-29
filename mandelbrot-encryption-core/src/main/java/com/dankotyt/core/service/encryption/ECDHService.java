package com.dankotyt.core.service.encryption;

import com.dankotyt.core.model.ECDHKeyPair;

import java.math.BigInteger;

/**
 * Сервис криптографических операций на эллиптической кривой, включая
 * генерацию ключей, сериализацию/десериализацию публичных точек и вычисление общего секрета.
 *
 * @author dankotyt
 * @since 1.1.0
 */
public interface ECDHService {
    /**
     * Генерирует новую пару ключей ECDH.
     *
     * @return новая пара ключей с заполненными приватной и публичной компонентами.
     */
    ECDHKeyPair generateKeyPair();

    /**
     * Сериализует публичный ключ из переданной пары.
     *
     * @param keyPair пара ключей, из которой берётся публичная точка.
     * @return байтовый массив в формате [длинаX][X][длинаY][Y].
     */
    byte[] serializePublicKey(ECDHKeyPair keyPair);

    /**
     * Сериализует публичный ключ, представленный массивом BigInteger [x, y].
     *
     * @param publicKey точка [x, y].
     * @return байтовый массив (длинаX + X + длинаY + Y).
     */
    byte[] serializePublicKey(BigInteger[] publicKey);

    /**
     * Восстанавливает точку публичного ключа из байтового представления.
     *
     * @param bytes сериализованная точка.
     * @return массив BigInteger [x, y].
     */
    BigInteger[] deserializePublicKey(byte[] bytes);

    /**
     * Вычисляет общий секрет, используя локальный приватный ключ и публичный ключ пира,
     * и сохраняет результат в переданной локальной паре.
     *
     * @param localKeys           локальная пара ключей (в неё будет записан общий секрет).
     * @param otherPublicKeyBytes сериализованный публичный ключ удалённой стороны.
     */
    void computeSharedSecret(ECDHKeyPair localKeys, byte[] otherPublicKeyBytes);
}
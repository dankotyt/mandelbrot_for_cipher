package com.dankotyt.core.service.network;

import com.dankotyt.core.model.ECDHKeyPair;
import java.net.InetAddress;
import java.util.Map;

/**
 * Управляет ключами ECDH для множества пиров.
 * Хранит локальные ключи и общие секреты, вычисленные для каждого удалённого узла.
 *
 * @author dankotyt
 * @since 1.1.0
 */
public interface CryptoKeyManager {

    /**
     * Возвращает общий секрет (master seed), согласованный с указанным пиром.
     *
     * @param peerAddress адрес пира.
     * @return байтовый массив общего секрета.
     * @throws IllegalStateException если для пира нет секрета.
     */
    byte[] getMasterSeedFromDH(InetAddress peerAddress);

    /**
     * Генерирует новую локальную пару ключей и сохраняет её.
     */
    void generateNewKeys();

    /**
     * Текущая локальная пара ключей.
     *
     * @return объект {@link ECDHKeyPair}.
     */
    ECDHKeyPair getCurrentKeys();

    /**
     * Регистрирует пира, вычисляет общий секрет на основе локального приватного ключа
     * и публичного ключа пира, сохраняет результат.
     *
     * @param peerAddress адрес пира.
     * @param peerKeyPair ключевая пара пира (содержит публичный ключ).
     * @throws IllegalArgumentException если адрес или ключ равны null.
     */
    void addPeer(InetAddress peerAddress, ECDHKeyPair peerKeyPair);

    /**
     * Удаляет пира и безопасно стирает его общий секрет.
     *
     * @param peerAddress адрес пира.
     * @throws IllegalArgumentException если адрес null.
     */
    void removePeer(InetAddress peerAddress);

    /**
     * Проверяет наличие активного общего секрета для пира.
     *
     * @param peerAddress адрес пира.
     * @return true, если есть непустой sharedSecretBytes.
     * @throws IllegalArgumentException если адрес null.
     */
    boolean hasPeer(InetAddress peerAddress);

    /**
     * Возвращает независимую копию всех активных пиров и их ключевых пар.
     *
     * @return копия Map (InetAddress -> ECDHKeyPair).
     */
    Map<InetAddress, ECDHKeyPair> getActivePeers();
}
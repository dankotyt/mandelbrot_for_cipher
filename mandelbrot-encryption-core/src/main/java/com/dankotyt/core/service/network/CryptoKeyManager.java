package com.dankotyt.core.service.network;

import com.dankotyt.core.model.ECDHKeyPair;
import java.net.InetAddress;
import java.util.Map;

public interface CryptoKeyManager {
    /**
     * Возвращает общий секрет (seed) для указанного пира.
     */
    byte[] getMasterSeedFromDH(InetAddress peerAddress);

    /**
     * Генерирует новую локальную пару ключей и сохраняет её.
     */
    void generateNewKeys();

    /**
     * Текущая локальная пара ключей.
     */
    ECDHKeyPair getCurrentKeys();

    /**
     * Регистрирует пира с его ключами и вычисляет общий секрет.
     */
    void addPeer(InetAddress peerAddress, ECDHKeyPair peerKeyPair);

    /**
     * Удаляет пира и затирает его ключи.
     */
    void removePeer(InetAddress peerAddress);

    /**
     * Проверяет, есть ли активные ключи для пира.
     */
    boolean hasPeer(InetAddress peerAddress);

    /**
     * Возвращает копию мапы активных пиров и их статусов (необязательно).
     */
    Map<InetAddress, ECDHKeyPair> getActivePeers();
}
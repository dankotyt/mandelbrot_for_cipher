package com.dankotyt.core.service.network.impl;

import com.dankotyt.core.model.ECDHKeyPair;
import com.dankotyt.core.service.encryption.ECDHService;
import com.dankotyt.core.service.network.CryptoKeyManager;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Реализация {@link CryptoKeyManager} на основе ECDH.
 * Хранит локальную пару ключей и для каждого зарегистрированного пира
 * независимую копию с вычисленным общим секретом.
 *
 * @author dankotyt
 * @since 1.1.0
 */
@Service
public class ECDHCryptoKeyManagerImpl implements CryptoKeyManager {
    private final ECDHService ecdhService;
    private volatile ECDHKeyPair currentKeys;
    private final Map<InetAddress, ECDHKeyPair> peers = new ConcurrentHashMap<>();

    /**
     * Создаёт менеджер и немедленно генерирует начальную локальную пару ключей.
     *
     * @param ecdhService сервис для операций на эллиптической кривой.
     */
    public ECDHCryptoKeyManagerImpl(ECDHService ecdhService) {
        this.ecdhService = ecdhService;
        generateNewKeys();
    }

    /**
     * Возвращает общий секрет (байты) для зарегистрированного пира.
     *
     * @param peerAddress адрес пира.
     * @return байтовый массив shared secret.
     * @throws IllegalStateException если пир не зарегистрирован или секрет не вычислен.
     */
    @Override
    public byte[] getMasterSeedFromDH(InetAddress peerAddress) {
        ECDHKeyPair peerKeys = peers.get(peerAddress);
        if (peerKeys == null || peerKeys.getSharedSecretBytes() == null) {
            throw new IllegalStateException("No shared secret for peer: " + peerAddress);
        }
        return peerKeys.getSharedSecretBytes();
    }

    /**
     * Генерирует новую локальную пару ключей.
     */
    @Override
    public void generateNewKeys() {
        currentKeys = ecdhService.generateKeyPair();
    }

    /**
     * Возвращает текущую локальную пару ключей.
     */
    @Override
    public ECDHKeyPair getCurrentKeys() {
        return currentKeys;
    }

    /**
     * Регистрирует пира: вычисляет общий секрет и сохраняет копию локальных ключей.
     *
     * @param peerAddress  адрес пира.
     * @param peerKeyPair  ключи пира (содержат публичный ключ).
     * @throws IllegalArgumentException если аргументы null.
     */
    @Override
    public void addPeer(InetAddress peerAddress, ECDHKeyPair peerKeyPair) {
        if (peerAddress == null || peerKeyPair == null) {
            throw new IllegalArgumentException("Peer address or key pair cannot be null");
        }
        BigInteger[] clonedPublicKey = currentKeys.getPublicKey().clone();
        ECDHKeyPair keysForPeer = new ECDHKeyPair(
                currentKeys.getPrivateKey(),
                clonedPublicKey,
                currentKeys.getCreationTime()
        );

        ecdhService.computeSharedSecret(keysForPeer,
                ecdhService.serializePublicKey(peerKeyPair.getPublicKey()));

        peers.put(peerAddress, keysForPeer);
    }

    /**
     * Удаляет пира и затирает его общий секрет.
     *
     * @param peerAddress адрес пира.
     * @throws IllegalArgumentException если null.
     */
    @Override
    public void removePeer(InetAddress peerAddress) {
        if (peerAddress == null) {
            throw new IllegalArgumentException("Peer address cannot be null");
        }
        ECDHKeyPair removed = peers.remove(peerAddress);
        if (removed != null) {
            removed.invalidate();
        }
    }

    /**
     * Проверяет, есть ли у пира вычисленный общий секрет.
     *
     * @param peerAddress адрес пира.
     * @return true если секрет присутствует.
     * @throws IllegalArgumentException если адрес null.
     */
    @Override
    public boolean hasPeer(InetAddress peerAddress) {
        if (peerAddress == null) {
            throw new IllegalArgumentException("Peer address cannot be null");
        }
        ECDHKeyPair peerKeys = peers.get(peerAddress);
        return peerKeys != null && peerKeys.getSharedSecretBytes() != null;
    }

    /**
     * Возвращает независимую копию Map всех активных пиров.
     *
     * @return новый HashMap, содержащий текущие записи.
     */
    @Override
    public Map<InetAddress, ECDHKeyPair> getActivePeers() {
        return new HashMap<>(peers);
    }
}
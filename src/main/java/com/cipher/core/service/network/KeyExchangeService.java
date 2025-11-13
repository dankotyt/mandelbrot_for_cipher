package com.cipher.core.service.network;

import com.cipher.core.model.ECDHKeyExchange;

import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface KeyExchangeService {

    // Основные методы для использования в ImageEncrypt/ImageDecrypt
    byte[] getMasterSeedFromDH(InetAddress peerAddress);
    boolean performKeyExchange(InetAddress peerAddress);
    CompletableFuture<Boolean> performKeyExchangeAsync(InetAddress peerAddress);
    void generateNewKeys();
    ECDHKeyExchange getCurrentKeys();

    // Управление соединениями
    void addConnection(InetAddress peerAddress, ECDHKeyExchange keys);
    void closeConnection(InetAddress peerAddress);
    void closeAllConnections();
    Map<InetAddress, String> getActiveConnections();

    // Статус и информация
    boolean isConnectedTo(InetAddress peerAddress);
    String getConnectionStatus(InetAddress peerAddress);

    // Обработка входящих ключей
    void processIncomingKeyExchange(InetAddress peerAddress, byte[] publicKey);

}
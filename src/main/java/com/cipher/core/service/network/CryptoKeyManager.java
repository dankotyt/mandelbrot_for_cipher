package com.cipher.core.service.network;

import com.cipher.core.model.ECDHKeyExchange;

import java.net.InetAddress;
import java.util.Map;

public interface KeyExchangeService {

    // Основные методы для использования в ImageEncrypt/ImageDecrypt
    byte[] getMasterSeedFromDH(InetAddress peerAddress);
    void generateNewKeys();
    ECDHKeyExchange getCurrentKeys();

    // Управление соединениями
    void addConnection(InetAddress peerAddress, ECDHKeyExchange keys);
    void closeConnection(InetAddress peerAddress);
    void closeAllConnections();
    Map<InetAddress, String> getActiveConnections();

    // Статус и информация
    void setConnectedPeer(InetAddress peerAddress);
    InetAddress getConnectedPeer();
    boolean isConnectedTo(InetAddress peerAddress);
    String getConnectionStatus(InetAddress peerAddress);

    ECDHKeyExchange getPeerKeys(String peerIp);
    boolean hasKeysForPeer(String peerIp);
    void removePeerKeys(String peerIp);
    void sendKeyInvalidation(InetAddress peerAddress);
}
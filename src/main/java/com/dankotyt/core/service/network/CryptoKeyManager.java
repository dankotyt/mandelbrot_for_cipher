package com.dankotyt.core.service.network;

import com.dankotyt.core.model.ECDHKeyPair;

import java.net.InetAddress;
import java.util.Map;

public interface CryptoKeyManager {

    byte[] getMasterSeedFromDH(InetAddress peerAddress);
    void generateNewKeys();
    ECDHKeyPair getCurrentKeys();

    // Управление соединениями
    void addConnection(InetAddress peerAddress, ECDHKeyPair keys);
    void closeConnection(InetAddress peerAddress);
    void closeAllConnections();
    Map<InetAddress, String> getActiveConnections();

    // Статус и информация
    void setConnectedPeer(InetAddress peerAddress);
    InetAddress getConnectedPeer();
    boolean isConnectedTo(InetAddress peerAddress);
    String getConnectionStatus(InetAddress peerAddress);

    ECDHKeyPair getPeerKeys(String peerIp);
    boolean hasKeysForPeer(String peerIp);
    void removePeerKeys(String peerIp);
    void sendKeyInvalidation(InetAddress peerAddress);
}
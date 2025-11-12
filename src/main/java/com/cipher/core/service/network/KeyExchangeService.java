package com.cipher.core.service.network;

import com.cipher.core.model.ECDHKeyExchange;

import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface KeyExchangeService {
    ECDHKeyExchange getCurrentKeys();
    byte[] getMasterSeedFromDH(InetAddress peerAddress);
    boolean performKeyExchange(InetAddress peerAddress);
    CompletableFuture<Boolean> performKeyExchangeAsync(InetAddress peerAddress);
    void generateNewKeys();
    void addConnection(InetAddress peerAddress, ECDHKeyExchange keys);
    void closeConnection(InetAddress peerAddress);
    void closeAllConnections();
    Map<InetAddress, String> getActiveConnections();
    boolean isConnectedTo(InetAddress peerAddress);
    String getConnectionStatus(InetAddress peerAddress);
    void processIncomingKeyExchange(InetAddress peerAddress, byte[] publicKey);
}
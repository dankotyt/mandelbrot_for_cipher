package com.cipher.client;

import com.cipher.core.service.KeyExchangeService;
import com.cipher.core.service.NetworkDiscoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class PeerConnector {

    private final KeyExchangeService keyExchangeService;
    private final NetworkDiscoveryService discoveryService;
    private final DiscoveryClient discoveryClient;
    private final KeyExchangeClient keyExchangeClient;

    public CompletableFuture<Boolean> connectToPeer(InetAddress peerAddress) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Проверяем доступность пира
                if (!keyExchangeClient.testConnection(peerAddress)) {
                    log.warn("Peer {} is not reachable", peerAddress.getHostAddress());
                    return false;
                }

                // Выполняем обмен ключами
                boolean success = keyExchangeClient.performKeyExchange(peerAddress);
                if (success) {
                    log.info("Successfully connected to peer: {}", peerAddress.getHostAddress());
                    discoveryService.onPeerConnected(peerAddress);
                }
                return success;

            } catch (Exception e) {
                log.error("Connection to peer {} failed: {}", peerAddress.getHostAddress(), e.getMessage());
                return false;
            }
        });
    }

    public void disconnectFromPeer(InetAddress peerAddress) {
        keyExchangeService.closeConnection(peerAddress);
        discoveryService.onPeerDisconnected(peerAddress);
        log.info("Disconnected from peer: {}", peerAddress.getHostAddress());
    }

    public Set<InetAddress> getConnectedPeers() {
        return keyExchangeService.getActiveConnections().keySet();
    }

    public boolean isConnectedTo(InetAddress peerAddress) {
        return keyExchangeService.isConnectedTo(peerAddress);
    }
}
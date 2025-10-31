package com.cipher.core.service.impl;

import com.cipher.core.model.ConnectionStatus;
import com.cipher.core.model.DHKeyExchange;
import com.cipher.core.model.PeerInfo;
import com.cipher.core.service.ConnectionManager;
import com.cipher.core.service.KeyExchangeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class NetworkKeyExchangeServiceImpl implements KeyExchangeService {

    private final AtomicReference<DHKeyExchange> currentKeyExchange = new AtomicReference<>();
    private final Map<InetAddress, PeerInfo> activeConnections = new ConcurrentHashMap<>();
    private final ConnectionManager connectionManager;

    public NetworkKeyExchangeServiceImpl(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
        generateNewKeys();
    }

    @Override
    public byte[] getMasterSeedFromDH(InetAddress peerAddress) {
        PeerInfo peerInfo = activeConnections.get(peerAddress);
        if (peerInfo != null && peerInfo.getDhKeys() != null) {
            byte[] sharedSecret = peerInfo.getDhKeys().getSharedSecretBytes();
            if (sharedSecret != null) {
                return sharedSecret;
            }
        }
        throw new IllegalStateException("No active connection or shared secret for peer: " + peerAddress);
    }

    @Override
    public boolean performKeyExchange(InetAddress peerAddress) {
        try {
            // Используем синхронную версию с таймаутом
            return connectionManager.initiateKeyExchange(peerAddress)
                    .get(30, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.error("Key exchange timeout for {}: {}", peerAddress, e.getMessage());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Key exchange interrupted for {}: {}", peerAddress, e.getMessage());
            return false;
        } catch (ExecutionException e) {
            log.error("Key exchange execution failed for {}: {}", peerAddress, e.getMessage());
            return false;
        }
    }

    @Override
    public CompletableFuture<Boolean> performKeyExchangeAsync(InetAddress peerAddress) {
        return connectionManager.initiateKeyExchange(peerAddress);
    }

    @Override
    public void generateNewKeys() {
        DHKeyExchange newKeys = new DHKeyExchange();
        currentKeyExchange.set(newKeys);

        activeConnections.forEach((peer, peerInfo) -> {
            connectionManager.sendKeyInvalidation(peer);
            performKeyExchangeAsync(peer);
        });

        log.info("Generated new DH keys");
    }

    @Override
    public void closeConnection(InetAddress peerAddress) {
        PeerInfo removed = activeConnections.remove(peerAddress);
        if (removed != null) {
            connectionManager.closeConnection(peerAddress);
            log.info("Closed connection to: {}", peerAddress);
        }
    }

    @Override
    public void closeAllConnections() {
        if (!activeConnections.isEmpty()) {
            log.info("Closing all connections...");

            Map<InetAddress, PeerInfo> connectionsToClose = new ConcurrentHashMap<>(activeConnections);

            connectionsToClose.keySet().forEach(connectionManager::closeConnection);

            activeConnections.clear();
            log.info("All connections closed");
        }
    }

    @Override
    public Map<InetAddress, String> getActiveConnections() {
        Map<InetAddress, String> result = new ConcurrentHashMap<>();
        activeConnections.forEach((address, info) ->
                result.put(address, info.getStatus().name()));
        return result;
    }

    @Override
    public boolean isConnectedTo(InetAddress peerAddress) {
        return activeConnections.containsKey(peerAddress) &&
                activeConnections.get(peerAddress).getStatus() == ConnectionStatus.CONNECTED;
    }

    @Override
    public String getConnectionStatus(InetAddress peerAddress) {
        PeerInfo info = activeConnections.get(peerAddress);
        return info != null ? info.getStatus().name() : "DISCONNECTED";
    }

    @Override
    public void processIncomingKeyExchange(InetAddress peerAddress, byte[] publicKey) {
        connectionManager.handleIncomingKeyExchange(peerAddress, publicKey);
    }

    // Внутренние методы для использования ConnectionManager и KeyExchangeManager
    public void addActiveConnection(InetAddress peerAddress, PeerInfo peerInfo) {
        activeConnections.put(peerAddress, peerInfo);
        log.info("Added active connection to: {}", peerAddress.getHostAddress());
    }

    public DHKeyExchange getCurrentKeys() {
        return currentKeyExchange.get();
    }

    public PeerInfo getPeerInfo(InetAddress peerAddress) {
        return activeConnections.get(peerAddress);
    }

    public void updatePeerStatus(InetAddress peerAddress, ConnectionStatus status) {
        PeerInfo peerInfo = activeConnections.get(peerAddress);
        if (peerInfo != null) {
            peerInfo.setStatus(status);
            peerInfo.updateLastSeen();
            log.debug("Updated status for {}: {}", peerAddress.getHostAddress(), status);
        }
    }

    public void cleanupExpiredConnections(long timeoutMs) {
        activeConnections.entrySet().removeIf(entry -> {
            PeerInfo info = entry.getValue();
            boolean expired = info.isExpired(timeoutMs);
            if (expired) {
                log.info("Removed expired connection: {}", entry.getKey().getHostAddress());
                connectionManager.closeConnection(entry.getKey());
            }
            return expired;
        });
    }

    public int getActiveConnectionsCount() {
        return activeConnections.size();
    }

    public void setDhKeysForPeer(InetAddress peerAddress, DHKeyExchange dhKeys) {
        PeerInfo peerInfo = activeConnections.get(peerAddress);
        if (peerInfo != null) {
            peerInfo.setDhKeys(dhKeys);
            peerInfo.updateKeyExchangeTime();
            log.info("Set DH keys for peer: {}", peerAddress.getHostAddress());
        }
    }
}
package com.cipher.core.service;

import com.cipher.client.KeyExchangeClient;
import com.cipher.core.model.PeerInfo;
import com.cipher.core.service.impl.NetworkKeyExchangeServiceImpl;
import com.cipher.server.handler.ClientConnectionHandler;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConnectionManager {
    private final NetworkKeyExchangeServiceImpl keyExchangeService;
    private final KeyExchangeClient keyExchangeClient;

    private final Map<InetAddress, PeerInfo> connectedPeers = new ConcurrentHashMap<>();
    private final ExecutorService connectionExecutor = Executors.newCachedThreadPool();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private ServerSocket keyExchangeServerSocket;
    private Thread serverThread;
    @Getter @Setter
    private InetAddress connectedPeer;

    public void start() {
        if (running.compareAndSet(false, true)) {
            startKeyExchangeServer();
            log.info("Connection manager started");
        }
    }

    public void stop() {
        if (running.compareAndSet(true, false)) {
            stopKeyExchangeServer();
            closeAllConnections();
            connectionExecutor.shutdown();
            log.info("Connection manager stopped");
        }
    }

    public CompletableFuture<Boolean> initiateKeyExchange(InetAddress peerAddress) {
        if (!running.get()) {
            log.error("Connection manager is not running");
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Initiating key exchange with {}", peerAddress.getHostAddress());

                boolean success = keyExchangeClient.performKeyExchange(peerAddress);
                if (success) {
                    PeerInfo peerInfo = new PeerInfo(peerAddress);
                    connectedPeers.put(peerAddress, peerInfo);
                    keyExchangeService.addActiveConnection(peerAddress, peerInfo);
                    log.info("Key exchange successful with {}", peerAddress.getHostAddress());
                } else {
                    log.error("Key exchange failed with {}", peerAddress.getHostAddress());
                }
                return success;
            } catch (Exception e) {
                log.error("Key exchange failed for {}: {}", peerAddress, e.getMessage());
                return false;
            }
        }, connectionExecutor);
    }

    public void handleIncomingKeyExchange(InetAddress peerAddress, byte[] publicKey) {
        if (!running.get()) {
            log.warn("Connection manager is not running, ignoring incoming key exchange");
            return;
        }

        connectionExecutor.submit(() -> {
            try {
                log.info("Handling incoming key exchange from {}", peerAddress.getHostAddress());

                // Создаем PeerInfo и добавляем в connectedPeers
                PeerInfo peerInfo = new PeerInfo(peerAddress);
                connectedPeers.put(peerAddress, peerInfo);
                keyExchangeService.addActiveConnection(peerAddress, peerInfo);

                log.info("Incoming key exchange processed for {}", peerAddress.getHostAddress());
            } catch (Exception e) {
                log.error("Failed to handle incoming key exchange from {}: {}", peerAddress, e.getMessage());
            }
        });
    }

    public void closeConnection(InetAddress peerAddress) {
        PeerInfo removed = connectedPeers.remove(peerAddress);
        if (removed != null) {
            keyExchangeService.closeConnection(peerAddress);
            // Отправляем уведомление об инвалидации
            keyExchangeClient.sendKeyInvalidation(peerAddress);
            log.info("Connection closed to: {}", peerAddress.getHostAddress());
        }
    }

    public void closeAllConnections() {
        if (!connectedPeers.isEmpty()) {
            log.info("Closing all connections...");

            // Создаем копию для безопасной итерации
            Map<InetAddress, PeerInfo> peersToClose = new ConcurrentHashMap<>(connectedPeers);

            // Отправляем уведомления всем пирам
            peersToClose.keySet().forEach(keyExchangeClient::sendKeyInvalidation);

            // Закрываем соединения
            peersToClose.keySet().forEach(peer -> {
                connectedPeers.remove(peer);
                keyExchangeService.closeConnection(peer);
            });

            log.info("All connections closed");
        }
    }

    public void sendKeyInvalidation(InetAddress peerAddress) {
        if (connectedPeers.containsKey(peerAddress)) {
            keyExchangeClient.sendKeyInvalidation(peerAddress);
        }
    }

    public void sendKeyInvalidationToAll() {
        connectedPeers.keySet().forEach(this::sendKeyInvalidation);
    }

    public boolean isConnectedTo(InetAddress peerAddress) {
        return connectedPeers.containsKey(peerAddress);
    }

    public PeerInfo getConnectionInfo(InetAddress peerAddress) {
        return connectedPeers.get(peerAddress);
    }

    public Map<InetAddress, PeerInfo> getConnectedPeers() {
        return new ConcurrentHashMap<>(connectedPeers);
    }

    public void cleanupExpiredPeers(long timeoutMs) {
        long currentTime = System.currentTimeMillis();
        connectedPeers.entrySet().removeIf(entry -> {
            PeerInfo info = entry.getValue();
            boolean expired = info.isExpired(timeoutMs);
            if (expired) {
                log.info("Removed expired peer: {}", entry.getKey().getHostAddress());
                keyExchangeService.closeConnection(entry.getKey());
                // Отправляем уведомление об инвалидации
                keyExchangeClient.sendKeyInvalidation(entry.getKey());
            }
            return expired;
        });
    }

    public void performPeriodicKeyRefresh() {
        connectionExecutor.submit(() -> {
            connectedPeers.forEach((peer, info) -> {
                if (info.isKeyExchangeExpired(24 * 60 * 60 * 1000)) { // 24 часа
                    log.info("Refreshing keys for peer: {}", peer.getHostAddress());
                    initiateKeyExchange(peer);
                }
            });
        });
    }

    private void startKeyExchangeServer() {
        serverThread = new Thread(() -> {
            try {
                keyExchangeServerSocket = new ServerSocket(8889); // KEY_EXCHANGE_PORT
                keyExchangeServerSocket.setSoTimeout(1000);
                log.info("Key exchange server started on port 8889");

                while (running.get() && !Thread.currentThread().isInterrupted()) {
                    try {
                        Socket clientSocket = keyExchangeServerSocket.accept();
                        log.info("Incoming connection from: {}", clientSocket.getInetAddress().getHostAddress());

                        ClientConnectionHandler handler = new ClientConnectionHandler(
                                clientSocket, keyExchangeService);
                        connectionExecutor.submit(handler);
                    } catch (java.net.SocketTimeoutException e) {
                        // continue - нормальное поведение для accept с таймаутом
                    } catch (Exception e) {
                        if (running.get()) {
                            log.error("Error accepting connection: {}", e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                if (running.get()) {
                    log.error("Key exchange server error: {}", e.getMessage());
                }
            } finally {
                closeServerSocket();
                log.info("Key exchange server stopped");
            }
        }, "KeyExchange-Server");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    private void stopKeyExchangeServer() {
        if (serverThread != null) {
            serverThread.interrupt();
            try {
                serverThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        closeServerSocket();
    }

    private void closeServerSocket() {
        if (keyExchangeServerSocket != null && !keyExchangeServerSocket.isClosed()) {
            try {
                keyExchangeServerSocket.close();
            } catch (Exception e) {
                log.warn("Error closing server socket: {}", e.getMessage());
            }
        }
    }
}
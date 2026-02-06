package com.cipher.core.service.network;

import com.cipher.core.model.PeerInfo;
import com.cipher.client.handler.ClientConnectionHandler;
import com.cipher.client.handler.ClientConnectionHandlerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConnectionManager {
    private final KeyExchangeService keyExchangeService;
    private final ClientConnectionHandlerFactory handlerFactory;

    private final Map<InetAddress, PeerInfo> connectedPeers = new ConcurrentHashMap<>();
    private final ExecutorService connectionExecutor = Executors.newCachedThreadPool();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private ServerSocket keyExchangeServerSocket;
    private Thread serverThread;
    private final Map<String, InetAddress> persistentConnectedPeers = new ConcurrentHashMap<>();
    private InetAddress lastConnectedPeer;

    private InetAddress connectedPeer;

    public void setConnectedPeer(InetAddress peerAddress) {
        this.lastConnectedPeer = peerAddress;
        if (peerAddress != null) {
            persistentConnectedPeers.put(peerAddress.getHostAddress(), peerAddress);
        }
        log.info("✅ Установлен подключенный пир: {}",
                peerAddress != null ? peerAddress.getHostAddress() : "null");
    }

    public InetAddress getConnectedPeer() {
        // Сначала пробуем получить последний подключенный пир
        if (lastConnectedPeer != null &&
                keyExchangeService.isConnectedTo(lastConnectedPeer)) {
            return lastConnectedPeer;
        }

        // Если последний не доступен, ищем любого доступного пира
        for (InetAddress peer : persistentConnectedPeers.values()) {
            if (keyExchangeService.isConnectedTo(peer)) {
                lastConnectedPeer = peer; // Обновляем последний доступный
                log.info("🔄 Восстановлен подключенный пир из хранилища: {}",
                        peer.getHostAddress());
                return peer;
            }
        }

        log.warn("❌ Нет доступных подключенных пиров в хранилище");
        return null;
    }

    /**
     * Получает всех подключенных пиров
     */
    public List<InetAddress> getAllConnectedPeers() {
        return persistentConnectedPeers.values().stream()
                .filter(keyExchangeService::isConnectedTo)
                .collect(Collectors.toList());
    }

    /**
     * Очищает информацию о пире
     */
    public void removePeer(InetAddress peerAddress) {
        if (peerAddress != null) {
            persistentConnectedPeers.remove(peerAddress.getHostAddress());
            if (peerAddress.equals(lastConnectedPeer)) {
                lastConnectedPeer = null;
            }
            log.info("🗑️ Удален пир из хранилища: {}", peerAddress.getHostAddress());
        }
    }

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

    public void closeAllConnections() {
        if (!connectedPeers.isEmpty()) {
            log.info("Closing all connections...");

            Map<InetAddress, PeerInfo> peersToClose = new ConcurrentHashMap<>(connectedPeers);

            peersToClose.keySet().forEach(keyExchangeService::sendKeyInvalidation);

            peersToClose.keySet().forEach(peer -> {
                connectedPeers.remove(peer);
                keyExchangeService.closeConnection(peer);
            });

            log.info("All connections closed");
        }
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

                        ClientConnectionHandler handler = handlerFactory.createHandler(clientSocket);
                        connectionExecutor.submit(handler);

                    } catch (java.net.SocketTimeoutException e) {

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

    public boolean hasConnectedPeer() {
        return connectedPeer != null;
    }
}
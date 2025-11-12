package com.cipher.core.service.network;

import com.cipher.client.service.localNetwork.KeyExchangeClient;
import com.cipher.core.model.ConnectionStatus;
import com.cipher.core.model.PeerInfo;
import com.cipher.client.handler.ClientConnectionHandler;
import com.cipher.client.handler.ClientConnectionHandlerFactory;
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
    private final KeyExchangeService keyExchangeService;
    private final KeyExchangeClient keyExchangeClient;
    private final ClientConnectionHandlerFactory handlerFactory;

    private final Map<InetAddress, PeerInfo> connectedPeers = new ConcurrentHashMap<>();
    private final Map<InetAddress, Socket> peerSockets = new ConcurrentHashMap<>();
    private final ExecutorService connectionExecutor = Executors.newCachedThreadPool();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private ServerSocket keyExchangeServerSocket;
    private Thread serverThread;
    @Getter @Setter
    private InetAddress connectedPeer;

    public PeerInfo getPeerInfo(InetAddress peerAddress) {
        return connectedPeers.get(peerAddress);
    }

    public boolean isConnectedTo(InetAddress peerAddress) {
        PeerInfo peerInfo = connectedPeers.get(peerAddress);
        return peerInfo != null && peerInfo.getStatus() == ConnectionStatus.CONNECTED;
    }

    public Socket getSocketForPeer(InetAddress peerAddress) {
        return peerSockets.get(peerAddress);
    }

    public void addPeerSocket(InetAddress peerAddress, Socket socket) {
        peerSockets.put(peerAddress, socket);
        log.debug("🔌 Сокет добавлен для: {}", peerAddress.getHostAddress());
    }

    public void removePeerSocket(InetAddress peerAddress) {
        Socket removed = peerSockets.remove(peerAddress);
        if (removed != null) {
            try {
                if (!removed.isClosed()) {
                    removed.close();
                }
            } catch (Exception e) {
                log.warn("Ошибка при закрытии сокета: {}", e.getMessage());
            }
        }
        log.debug("🔌 Сокет удален для: {}", peerAddress.getHostAddress());
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

            peersToClose.keySet().forEach(keyExchangeClient::sendKeyInvalidation);

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

    public Map<InetAddress, PeerInfo> getConnectedPeers() {
        return new ConcurrentHashMap<>(connectedPeers);
    }

    public void cleanupExpiredPeers(long timeoutMs) {
        connectedPeers.entrySet().removeIf(entry -> {
            PeerInfo info = entry.getValue();
            boolean expired = info.isExpired(timeoutMs);
            if (expired) {
                log.info("Removed expired peer: {}", entry.getKey().getHostAddress());
                keyExchangeService.closeConnection(entry.getKey());
                keyExchangeClient.sendKeyInvalidation(entry.getKey());
            }
            return expired;
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
                        InetAddress clientAddress = clientSocket.getInetAddress();
                        log.info("Incoming connection from: {}", clientSocket.getInetAddress().getHostAddress());
                        addPeerSocket(clientAddress, clientSocket);
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
}
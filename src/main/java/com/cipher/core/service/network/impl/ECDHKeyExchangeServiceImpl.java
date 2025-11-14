package com.cipher.core.service.network.impl;

import com.cipher.client.service.localNetwork.KeyExchangeClient;
import com.cipher.core.model.ConnectionStatus;
import com.cipher.core.model.ECDHKeyExchange;
import com.cipher.core.model.PeerInfo;
import com.cipher.core.service.network.KeyExchangeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class ECDHKeyExchangeServiceImpl implements KeyExchangeService {

    private final AtomicReference<ECDHKeyExchange> currentKeyExchange = new AtomicReference<>();
    private final Map<InetAddress, PeerInfo> activeConnections = new ConcurrentHashMap<>();
    private final Map<InetAddress, Boolean> connectionInProgress = new ConcurrentHashMap<>();
    private final KeyExchangeClient keyExchangeClient;

    public ECDHKeyExchangeServiceImpl(KeyExchangeClient keyExchangeClient) {
        this.keyExchangeClient = keyExchangeClient;
        generateNewKeys();
    }

    @Override
    public ECDHKeyExchange getCurrentKeys() {
        return currentKeyExchange.get();
    }

    @Override
    public byte[] getMasterSeedFromDH(InetAddress peerAddress) {
        try {
            PeerInfo peerInfo = activeConnections.get(peerAddress);
            if (peerInfo != null && peerInfo.getEcdhKeys() != null) {
                byte[] sharedSecret = peerInfo.getEcdhKeys().getSharedSecretBytes();
                if (sharedSecret != null && sharedSecret.length > 0) {
                    log.debug("ECDH мастер-сид получен для пира: {}, длина: {} байт",
                            peerAddress.getHostAddress(), sharedSecret.length);
                    return sharedSecret;
                }
            }

            throw new IllegalStateException("Нет активного ECDH соединения или общего секрета для пира: " + peerAddress);

        } catch (Exception e) {
            log.error("Ошибка при получении ECDH мастер-сида для {}: {}",
                    peerAddress.getHostAddress(), e.getMessage(), e);
            throw new RuntimeException("Не удалось получить ECDH мастер-сид", e);
        }
    }

    @Override
    public boolean performKeyExchange(InetAddress peerAddress) {
        if (connectionInProgress.putIfAbsent(peerAddress, true) != null) {
            log.debug("Подключение к {} уже выполняется, пропускаем", peerAddress.getHostAddress());
            return false;
        }

        try {
            log.info("Выполнение ECDH обмена ключами с: {}", peerAddress.getHostAddress());

            if (isConnectedTo(peerAddress)) {
                log.debug("Уже подключены к {}, пропускаем обмен ключами", peerAddress.getHostAddress());
                return true;
            }

            ECDHKeyExchange ourKeys = currentKeyExchange.get();
            boolean success = keyExchangeClient.performKeyExchange(peerAddress, ourKeys);

            if (success) {
                updatePeerConnection(peerAddress);
                log.info("ECDH обмен ключами успешно завершен с: {}", peerAddress.getHostAddress());
            } else {
                log.error("ECDH обмен ключами не удался с: {}", peerAddress.getHostAddress());
            }

            return success;

        } catch (Exception e) {
            log.error("Ошибка при выполнении ECDH обмена ключами с {}: {}",
                    peerAddress.getHostAddress(), e.getMessage(), e);
            return false;
        } finally {
            connectionInProgress.remove(peerAddress);
        }
    }

    @Override
    public CompletableFuture<Boolean> performKeyExchangeAsync(InetAddress peerAddress) {
        return CompletableFuture.supplyAsync(() -> performKeyExchange(peerAddress))
                .orTimeout(30, TimeUnit.SECONDS)
                .exceptionally(throwable -> {
                    log.error("Асинхронный ECDH обмен ключами не удался с {}: {}",
                            peerAddress.getHostAddress(), throwable.getMessage());
                    connectionInProgress.remove(peerAddress);
                    return false;
                });
    }

    private void updatePeerConnection(InetAddress peerAddress) {
        try {
            ECDHKeyExchange currentKeys = currentKeyExchange.get();
            if (currentKeys == null) {
                log.warn("Текущие ECDH ключи не найдены, генерируем новые");
                generateNewKeys();
                currentKeys = currentKeyExchange.get();
            }

            PeerInfo peerInfo = activeConnections.computeIfAbsent(peerAddress, PeerInfo::new);
            peerInfo.setEcdhKeys(currentKeys);
            peerInfo.setStatus(ConnectionStatus.CONNECTED);
            peerInfo.updateLastSeen();
            peerInfo.updateKeyExchangeTime();

            log.info("ECDH информация о пире обновлена: {}", peerAddress.getHostAddress());

        } catch (Exception e) {
            log.error("Ошибка при обновлении ECDH информации о пире {}: {}",
                    peerAddress.getHostAddress(), e.getMessage(), e);
        }
    }

    @Override
    public void generateNewKeys() {
        ECDHKeyExchange newKeys = new ECDHKeyExchange();
        currentKeyExchange.set(newKeys);
        log.info("Generated new ECDH keys");

        activeConnections.keySet().forEach(keyExchangeClient::sendKeyInvalidation);
    }

    @Override
    public void addConnection(InetAddress peerAddress, ECDHKeyExchange keys) {
        if (connectionInProgress.putIfAbsent(peerAddress, true) != null) {
            log.debug("Входящее подключение от {} уже обрабатывается", peerAddress.getHostAddress());
            return;
        }

        try {
            if (keys == null) {
                throw new IllegalArgumentException("Expected ECDHKeyExchange instance");
            }

            if (isConnectedTo(peerAddress)) {
                log.debug("Подключение к {} уже существует, обновляем ключи", peerAddress.getHostAddress());
            }

            PeerInfo peerInfo = new PeerInfo(peerAddress);
            peerInfo.setEcdhKeys(keys);
            peerInfo.setStatus(ConnectionStatus.CONNECTED);
            peerInfo.updateLastSeen();
            peerInfo.updateKeyExchangeTime();

            activeConnections.put(peerAddress, peerInfo);
            log.info("Добавлено ECDH соединение с: {}", peerAddress.getHostAddress());

        } catch (Exception e) {
            log.error("Ошибка при добавлении ECDH соединения с {}: {}",
                    peerAddress.getHostAddress(), e.getMessage());
        } finally {
            connectionInProgress.remove(peerAddress);
        }
    }

    @Override
    public void closeConnection(InetAddress peerAddress) {
        PeerInfo removed = activeConnections.remove(peerAddress);
        if (removed != null) {
            keyExchangeClient.sendKeyInvalidation(peerAddress);
            log.info("Closed ECDH connection to: {}", peerAddress);
        }
    }

    @Override
    public void closeAllConnections() {
        if (!activeConnections.isEmpty()) {
            log.info("Closing all ECDH connections...");
            activeConnections.keySet().forEach(keyExchangeClient::sendKeyInvalidation);
            activeConnections.clear();
            log.info("All ECDH connections closed");
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
        updatePeerConnection(peerAddress);
    }

    // Внутренние методы
    public void addActiveConnection(InetAddress peerAddress, PeerInfo peerInfo) {
        activeConnections.put(peerAddress, peerInfo);
        log.info("Added active ECDH connection to: {}", peerAddress.getHostAddress());
    }

    public PeerInfo getPeerInfo(InetAddress peerAddress) {
        return activeConnections.get(peerAddress);
    }

    public void updatePeerStatus(InetAddress peerAddress, ConnectionStatus status) {
        PeerInfo peerInfo = activeConnections.get(peerAddress);
        if (peerInfo != null) {
            peerInfo.setStatus(status);
            peerInfo.updateLastSeen();
            log.debug("Updated ECDH status for {}: {}", peerAddress.getHostAddress(), status);
        }
    }

    public void cleanupExpiredConnections(long timeoutMs) {
        activeConnections.entrySet().removeIf(entry -> {
            PeerInfo info = entry.getValue();
            boolean expired = info.isExpired(timeoutMs);
            if (expired) {
                log.info("Removed expired ECDH connection: {}", entry.getKey().getHostAddress());
                keyExchangeClient.sendKeyInvalidation(entry.getKey());
            }
            return expired;
        });
    }

    public int getActiveConnectionsCount() {
        return activeConnections.size();
    }

    public void setEcdhKeysForPeer(InetAddress peerAddress, ECDHKeyExchange ecdhKeys) {
        PeerInfo peerInfo = activeConnections.get(peerAddress);
        if (peerInfo != null) {
            peerInfo.setEcdhKeys(ecdhKeys);
            peerInfo.updateKeyExchangeTime();
            log.info("Set ECDH keys for peer: {}", peerAddress.getHostAddress());
        }
    }

    public boolean isConnectionInProgress(InetAddress peerAddress) {
        return connectionInProgress.containsKey(peerAddress);
    }
}

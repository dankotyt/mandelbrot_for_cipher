package com.cipher.core.service.network.impl;

import com.cipher.client.service.localNetwork.KeyExchangeClient;
import com.cipher.core.model.ConnectionStatus;
import com.cipher.core.model.DHKeyExchange;
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
public class NetworkKeyExchangeServiceImpl implements KeyExchangeService {

    private final AtomicReference<DHKeyExchange> currentKeyExchange = new AtomicReference<>();
    private final Map<InetAddress, PeerInfo> activeConnections = new ConcurrentHashMap<>();
    private final KeyExchangeClient keyExchangeClient;

    public NetworkKeyExchangeServiceImpl(KeyExchangeClient keyExchangeClient) {
        this.keyExchangeClient = keyExchangeClient;
        generateNewKeys();
    }

    @Override
    public DHKeyExchange getCurrentKeys() {
        return currentKeyExchange.get();
    }

    @Override
    public byte[] getMasterSeedFromDH(InetAddress peerAddress) {
        try {
            PeerInfo peerInfo = activeConnections.get(peerAddress);
            if (peerInfo != null && peerInfo.getDhKeys() != null) {
                byte[] sharedSecret = peerInfo.getDhKeys().getSharedSecretBytes();
                if (sharedSecret != null && sharedSecret.length > 0) {
                    log.debug("Мастер-сид получен для пира: {}, длина: {} байт",
                            peerAddress.getHostAddress(), sharedSecret.length);
                    return sharedSecret;
                }
            }

            throw new IllegalStateException("Нет активного соединения или общего секрета для пира: " + peerAddress);

        } catch (Exception e) {
            log.error("Ошибка при получении мастер-сида для {}: {}",
                    peerAddress.getHostAddress(), e.getMessage(), e);
            throw new RuntimeException("Не удалось получить мастер-сид", e);
        }
    }

    public boolean performKeyExchange(InetAddress peerAddress) {
        try {
            log.info("Выполнение обмена ключами DH с: {}", peerAddress.getHostAddress());
            DHKeyExchange ourKeys = currentKeyExchange.get();
            boolean success = keyExchangeClient.performKeyExchange(peerAddress, ourKeys);

            if (success) {
                // Обновляем информацию о пире
                updatePeerConnection(peerAddress);
                log.info("Обмен ключами успешно завершен с: {}", peerAddress.getHostAddress());
            } else {
                log.error("Обмен ключами не удался с: {}", peerAddress.getHostAddress());
            }

            return success;

        } catch (Exception e) {
            log.error("Ошибка при выполнении обмена ключами с {}: {}",
                    peerAddress.getHostAddress(), e.getMessage(), e);
            return false;
        }
    }

    @Override
    public CompletableFuture<Boolean> performKeyExchangeAsync(InetAddress peerAddress) {
        return CompletableFuture.supplyAsync(() -> performKeyExchange(peerAddress))
                .orTimeout(30, TimeUnit.SECONDS)
                .exceptionally(throwable -> {
                    log.error("Асинхронный обмен ключами не удался с {}: {}",
                            peerAddress.getHostAddress(), throwable.getMessage());
                    return false;
                });
    }

    private void updatePeerConnection(InetAddress peerAddress) {
        try {
            // Получаем текущие ключи
            DHKeyExchange currentKeys = currentKeyExchange.get();
            if (currentKeys == null) {
                log.warn("Текущие ключи не найдены, генерируем новые");
                generateNewKeys();
                currentKeys = currentKeyExchange.get();
            }

            // Создаем или обновляем информацию о пире
            PeerInfo peerInfo = activeConnections.computeIfAbsent(peerAddress,
                    PeerInfo::new);

            peerInfo.setDhKeys(currentKeys);
            peerInfo.setStatus(ConnectionStatus.CONNECTED);
            peerInfo.updateLastSeen();
            peerInfo.updateKeyExchangeTime();

            log.info("Информация о пире обновлена: {}", peerAddress.getHostAddress());

        } catch (Exception e) {
            log.error("Ошибка при обновлении информации о пире {}: {}",
                    peerAddress.getHostAddress(), e.getMessage(), e);
        }
    }

    @Override
    public void generateNewKeys() {
        DHKeyExchange newKeys = new DHKeyExchange();
        currentKeyExchange.set(newKeys);
        log.info("Generated new DH keys");

        activeConnections.keySet().forEach(keyExchangeClient::sendKeyInvalidation);
    }

    @Override
    public void addConnection(InetAddress peerAddress, DHKeyExchange keys) {
        try {
            PeerInfo peerInfo = new PeerInfo(peerAddress);
            peerInfo.setDhKeys(keys);
            peerInfo.setStatus(ConnectionStatus.CONNECTED);
            peerInfo.updateLastSeen();
            peerInfo.updateKeyExchangeTime();

            activeConnections.put(peerAddress, peerInfo);
            log.info("Добавлено соединение с: {}", peerAddress.getHostAddress());

        } catch (Exception e) {
            log.error("Ошибка при добавлении соединения с {}: {}",
                    peerAddress.getHostAddress(), e.getMessage());
        }
    }

    @Override
    public void closeConnection(InetAddress peerAddress) {
        PeerInfo removed = activeConnections.remove(peerAddress);
        if (removed != null) {
            keyExchangeClient.sendKeyInvalidation(peerAddress);
            log.info("Closed connection to: {}", peerAddress);
        }
    }

    @Override
    public void closeAllConnections() {
        if (!activeConnections.isEmpty()) {
            log.info("Closing all connections...");
            activeConnections.keySet().forEach(keyExchangeClient::sendKeyInvalidation);
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
        updatePeerConnection(peerAddress);
    }

    // Внутренние методы для использования ConnectionManager и KeyExchangeManager
    public void addActiveConnection(InetAddress peerAddress, PeerInfo peerInfo) {
        activeConnections.put(peerAddress, peerInfo);
        log.info("Added active connection to: {}", peerAddress.getHostAddress());
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
                keyExchangeClient.sendKeyInvalidation(entry.getKey());
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
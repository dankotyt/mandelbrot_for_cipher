package com.cipher.core.service.network;

import com.cipher.client.service.localNetwork.DiscoveryServer;
import com.cipher.client.service.localNetwork.DiscoveryClient;
import com.cipher.client.service.localNetwork.KeyExchangeServer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * NetworkBootstrap - "Запускатор сетевых сервисов"
 * Что делает: Запускает и останавливает все сетевые серверы
 * Единственная ответственность - управление жизненным циклом сетевых серверов
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NetworkBootstrap {

    private final DiscoveryServer discoveryServer;
    private final DiscoveryClient discoveryClient;
    private final KeyExchangeServer keyExchangeServer;

    @PostConstruct
    public void startNetworkServices() {
        log.info("🚀 Запуск сетевых сервисов...");

        keyExchangeServer.startServer();
        discoveryServer.start();
        discoveryClient.start();

        log.info("✅ Сетевые сервисы запущены");
    }

    @PreDestroy
    public void stopNetworkServices() {
        log.info("🛑 Остановка сетевых сервисов...");

        keyExchangeServer.stopServer();
        discoveryServer.stop();
        discoveryClient.stop();

        log.info("✅ Сетевые сервисы остановлены");
    }
}

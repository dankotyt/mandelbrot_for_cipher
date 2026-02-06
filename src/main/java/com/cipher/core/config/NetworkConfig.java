package com.cipher.core.config;

import com.cipher.client.service.localNetwork.DiscoveryClient;
import com.cipher.core.listener.DeviceDiscoveryEventListener;
import com.cipher.core.service.network.ConnectionManager;
import com.cipher.core.service.network.KeyExchangeService;
import com.cipher.core.service.network.NetworkDiscoveryService;
import com.cipher.core.service.network.impl.ECDHKeyExchangeServiceImpl;
import com.cipher.client.service.localNetwork.DiscoveryServer;
import com.cipher.client.handler.ClientConnectionHandlerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NetworkConfig {

    @Bean
    public DiscoveryServer discoveryServer() {
        return new DiscoveryServer();
    }

    @Bean
    public DiscoveryClient discoveryClient(NetworkDiscoveryService discoveryService,
                                           DeviceDiscoveryEventListener deviceDiscoveryEventListener) {
        return new DiscoveryClient(discoveryService, deviceDiscoveryEventListener);
    }

    @Bean
    public ConnectionManager connectionManager(KeyExchangeService keyExchangeService,
                                               ClientConnectionHandlerFactory handlerFactory) {
        return new ConnectionManager(keyExchangeService, handlerFactory);
    }

    @Bean
    public ClientConnectionHandlerFactory clientConnectionHandlerFactory(
            KeyExchangeService keyExchangeService) {
        return new ClientConnectionHandlerFactory(keyExchangeService);
    }

    @Deprecated
    @Bean
    public KeyExchangeService keyExchangeService() {
        return new ECDHKeyExchangeServiceImpl();
    }
}
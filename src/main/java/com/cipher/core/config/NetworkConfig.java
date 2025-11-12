package com.cipher.core.config;

import com.cipher.client.service.localNetwork.DiscoveryClient;
import com.cipher.client.service.localNetwork.KeyExchangeClient;
import com.cipher.core.service.chat.IncomingMessageHandler;
import com.cipher.core.service.network.ConnectionManager;
import com.cipher.core.service.network.KeyExchangeService;
import com.cipher.core.service.network.NetworkDiscoveryService;
import com.cipher.core.service.network.impl.ECDHKeyExchangeServiceImpl;
import com.cipher.core.utils.NetworkManager;
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
    public DiscoveryClient discoveryClient(NetworkDiscoveryService discoveryService) {
        return new DiscoveryClient(discoveryService);
    }

    @Bean
    public ConnectionManager connectionManager(KeyExchangeService keyExchangeService,
                                               KeyExchangeClient keyExchangeClient, ClientConnectionHandlerFactory handlerFactory) {
        return new ConnectionManager(keyExchangeService, keyExchangeClient, handlerFactory);
    }

    @Bean
    public KeyExchangeService keyExchangeService(KeyExchangeClient keyExchangeClient) {
        return new ECDHKeyExchangeServiceImpl(keyExchangeClient);
    }

    @Bean
    public NetworkManager networkManager(NetworkDiscoveryService networkDiscoveryService,
                                         KeyExchangeService keyExchangeService,
                                         ConnectionManager connectionManager) {
        return new NetworkManager(networkDiscoveryService, keyExchangeService, connectionManager);
    }

    @Bean
    public ClientConnectionHandlerFactory clientConnectionHandlerFactory(
            KeyExchangeService keyExchangeService, IncomingMessageHandler incomingMessageHandler) {
        return new ClientConnectionHandlerFactory(keyExchangeService, incomingMessageHandler );
    }
}
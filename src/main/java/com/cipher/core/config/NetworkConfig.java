package com.cipher.core.config;

import com.cipher.client.DiscoveryClient;
import com.cipher.client.KeyExchangeClient;
import com.cipher.client.PeerConnector;
import com.cipher.core.service.ConnectionManager;
import com.cipher.core.service.KeyExchangeService;
import com.cipher.core.service.NetworkDiscoveryService;
import com.cipher.core.service.impl.NetworkDiscoveryServiceImpl;
import com.cipher.core.service.impl.NetworkKeyExchangeServiceImpl;
import com.cipher.core.utils.NetworkManager;
import com.cipher.server.DiscoveryServer;
import com.cipher.server.handler.ClientConnectionHandler;
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
    public KeyExchangeClient keyExchangeClient(NetworkKeyExchangeServiceImpl keyExchangeService) {
        return new KeyExchangeClient(keyExchangeService);
    }

    @Bean
    public PeerConnector peerConnector(KeyExchangeService keyExchangeService,
                                       NetworkDiscoveryService discoveryService,
                                       DiscoveryClient discoveryClient,
                                       KeyExchangeClient keyExchangeClient) {
        return new PeerConnector(keyExchangeService, discoveryService, discoveryClient, keyExchangeClient);
    }


    @Bean
    public ConnectionManager connectionManager(NetworkKeyExchangeServiceImpl keyExchangeService,
                                               KeyExchangeClient keyExchangeClient) {
        return new ConnectionManager(keyExchangeService, keyExchangeClient);
    }

    @Bean
    public NetworkKeyExchangeServiceImpl keyExchangeService(KeyExchangeClient keyExchangeClient) {
        return new NetworkKeyExchangeServiceImpl(keyExchangeClient);
    }

    @Bean
    public NetworkManager networkManager(NetworkDiscoveryService networkDiscoveryService,
                                         KeyExchangeService keyExchangeService,
                                         PeerConnector peerConnector,
                                         ConnectionManager connectionManager) {
        return new NetworkManager(networkDiscoveryService, keyExchangeService, peerConnector, connectionManager);
    }

    @Bean
    public ClientConnectionHandler clientConnectionHandler(NetworkKeyExchangeServiceImpl keyExchangeService) {
        return new ClientConnectionHandler(null, keyExchangeService);
    }
}
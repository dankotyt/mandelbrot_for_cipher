package com.cipher.server.handler;

import com.cipher.core.service.impl.NetworkKeyExchangeServiceImpl;
import org.springframework.stereotype.Component;

import java.net.Socket;

@Component
public class ClientConnectionHandlerFactory {

    private final NetworkKeyExchangeServiceImpl keyExchangeService;

    public ClientConnectionHandlerFactory(NetworkKeyExchangeServiceImpl keyExchangeService) {
        this.keyExchangeService = keyExchangeService;
    }

    public ClientConnectionHandler createHandler(Socket clientSocket) {
        return new ClientConnectionHandler(clientSocket, keyExchangeService);
    }
}

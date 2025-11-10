package com.cipher.server.handler;

import com.cipher.core.service.network.impl.NetworkKeyExchangeServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.Socket;

@Component
@RequiredArgsConstructor
public class ClientConnectionHandlerFactory {

    private final NetworkKeyExchangeServiceImpl keyExchangeService;

    public ClientConnectionHandler createHandler(Socket clientSocket) {
        return new ClientConnectionHandler(clientSocket, keyExchangeService);
    }
}

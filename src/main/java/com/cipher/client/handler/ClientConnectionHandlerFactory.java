package com.cipher.client.handler;

import com.cipher.core.service.network.KeyExchangeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.Socket;

@Component
@RequiredArgsConstructor
public class ClientConnectionHandlerFactory {

    private final KeyExchangeService keyExchangeService;

    public ClientConnectionHandler createHandler(Socket clientSocket) {
        return new ClientConnectionHandler(clientSocket, keyExchangeService);
    }
}

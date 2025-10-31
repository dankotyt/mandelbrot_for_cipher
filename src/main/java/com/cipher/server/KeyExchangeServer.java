package com.cipher.server;

import com.cipher.core.service.impl.NetworkKeyExchangeServiceImpl;
import com.cipher.server.handler.ClientConnectionHandler;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

@RequiredArgsConstructor
public class KeyExchangeServer implements Runnable {
    private final NetworkKeyExchangeServiceImpl networkKeyExchangeService;
    private final int port;

    private volatile boolean running = true;

    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setSoTimeout(1000);

            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    // Обработка входящего соединения
                    new Thread(new ClientConnectionHandler(clientSocket, networkKeyExchangeService)).start();
                } catch (java.net.SocketTimeoutException e) {
                    continue;
                } catch (IOException e) {
                    if (running) {
                        System.err.println("Ошибка при принятии соединения: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Не удалось запустить сервер обмена ключами: " + e.getMessage());
        }
    }

    public void stop() {
        running = false;
    }
}

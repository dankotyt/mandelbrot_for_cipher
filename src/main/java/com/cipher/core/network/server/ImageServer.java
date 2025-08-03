package com.cipher.core.network.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

import com.cipher.core.network.client.ClientHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImageServer {
    private static final Logger logger = LoggerFactory.getLogger(ImageServer.class);
    private static final int PORT = 12345;
    private static Map<String, Socket> clients = new HashMap<>();

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            logger.info("Сервер запущен на порту {}", PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                String clientIp = clientSocket.getInetAddress().getHostAddress();
                clients.put(clientIp, clientSocket); // Регистрируем клиента
                logger.info("Клиент подключен: {}", clientIp);

                // Запускаем обработчик клиента в отдельном потоке
                new Thread(new ClientHandler(clientSocket, clients)).start();
            }
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }
}

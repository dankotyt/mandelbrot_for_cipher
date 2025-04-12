package Model;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class ImageServer {
    private static final int PORT = 12345;
    private static Map<String, Socket> clients = new HashMap<>();

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Сервер запущен на порту " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                String clientIp = clientSocket.getInetAddress().getHostAddress();
                clients.put(clientIp, clientSocket); // Регистрируем клиента
                System.out.println("Клиент подключен: " + clientIp);

                // Запускаем обработчик клиента в отдельном потоке
                new Thread(new ClientHandler(clientSocket, clients)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

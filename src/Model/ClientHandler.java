package Model;

import java.io.*;
import java.net.Socket;
import java.util.Map;

public class ClientHandler implements Runnable {
    private Socket clientSocket;
    private String clientIp;
    private Map<String, Socket> clients;

    public ClientHandler(Socket socket, Map<String, Socket> clients) {
        this.clientSocket = socket;
        this.clientIp = socket.getInetAddress().getHostAddress();
        this.clients = clients;
    }

    @Override
    public void run() {
        try (InputStream inputStream = clientSocket.getInputStream();
             OutputStream outputStream = clientSocket.getOutputStream();
             DataInputStream dataInputStream = new DataInputStream(inputStream);
             DataOutputStream dataOutputStream = new DataOutputStream(outputStream)) {

            // Ожидаем команду от клиента
            String command = dataInputStream.readUTF();
            if (command.equals("SEND_IMAGE")) {
                // Получаем изображение
                byte[] buffer = new byte[4096];
                int bytesRead;
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    byteArrayOutputStream.write(buffer, 0, bytesRead);
                }
                byte[] imageData = byteArrayOutputStream.toByteArray();

                // Пересылаем изображение другому клиенту
                String targetIp = dataInputStream.readUTF();
                Socket targetSocket = clients.get(targetIp);
                if (targetSocket != null) {
                    OutputStream targetOutputStream = targetSocket.getOutputStream();
                    targetOutputStream.write(imageData);
                    targetOutputStream.flush();
                    System.out.println("Изображение отправлено клиенту: " + targetIp);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            clients.remove(clientIp);
            System.out.println("Клиент отключен: " + clientIp);
        }
    }
}

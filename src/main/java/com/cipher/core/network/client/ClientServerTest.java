package com.cipher.core.network.client;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import com.cipher.core.network.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientServerTest {
    private static final Logger logger = LoggerFactory.getLogger(ClientServerTest.class);

    private static int serverPort = 12345;

    public ClientServerTest(int port) {
        serverPort = port;
    }

    private static String getProjectRootPath() {
        return new File("").getAbsolutePath() + File.separator;
    }

    private static String getTempPath() {
        return getProjectRootPath() + "temp" + File.separator;
    }

    private static String filePath = "C:/Danil/Desktop/encrypted_image_paths.txt";
    private static String imagePath;

    private static List<String> readImagePathsFromFile() {
        String userDesktop = System.getProperty("user.home") + File.separator + "Desktop";
        File file = new File(userDesktop, "encrypted_image_paths.txt");
        List<String> imagePaths = new ArrayList<>();

        if (!file.exists()) {
            logger.info("Файл не найден: {}", file.getAbsolutePath());
            return imagePaths; // Возвращаем пустой список, если файла нет
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                imagePaths.add(line);
            }
            logger.info("Пути к изображениям успешно прочитаны из файла: {}", file.getAbsolutePath());

            // Сохраняем первый путь в переменную imagePath
            if (!imagePaths.isEmpty()) {
                imagePath = imagePaths.get(0);
            }
        } catch (IOException e) {
            logger.error("Ошибка при чтении из файла: {}", e.getMessage());
        }
        return imagePaths;
    }

    public static void main(String[] args) {
        // Allow some time for the previous server to shutdown
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Start the server in a separate thread
        Server server = new Server(serverPort);
        Thread serverThread = new Thread(server);
        serverThread.start();

        // Allow some time for the server to start
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Test scenario: Client A sends a file to recipient "userB"
        // Client B receives the file

        // Client A: Send file
        Thread clientAThread = new Thread(() -> {
            try {
                // Client A connects to the server
                Client clientA = new Client();
                clientA.connect("localhost", Server.getPORT());

                // Читаем пути из файла
                readImagePathsFromFile();

                // Проверяем, что путь к изображению был прочитан
                if (imagePath == null) {
                    logger.error("Ошибка: путь к изображению не найден.");
                    clientA.disconnect();
                    return;
                }

                // Prepare the file to send
                File fileToSend = new File(imagePath);

                // Проверяем, существует ли файл
                if (!fileToSend.exists()) {
                    logger.error("Ошибка: файл не существует: {}", imagePath);
                    clientA.disconnect();
                    return;
                }

                // Send the file with command "SEND" and specify the recipient
                clientA.out.writeUTF("SEND");
                clientA.out.writeUTF("userB"); // Recipient username
                clientA.out.writeUTF(fileToSend.getName());
                clientA.out.writeLong(fileToSend.length());

                // Send file content
                try (FileInputStream fis = new FileInputStream(fileToSend)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        clientA.out.write(buffer, 0, bytesRead);
                    }
                }

                // Receive acknowledgment
                String response = clientA.in.readUTF();
                synchronized (System.out) {
                   logger.info("Client A: File sent"); // Changed from "File received" to "File sent"
                }

                // Disconnect
                clientA.disconnect();
            } catch (IOException e) {
                logger.error(e.getMessage());
            }
        });
        clientAThread.start();

        // Client B: Receive file
        Thread clientBThread = new Thread(() -> {
            try {
                // Client B connects to the server
                Client clientB = new Client();
                clientB.connect("localhost", Server.getPORT());

                // Wait for a bit before sending the RECEIVE command
                Thread.sleep(200);

                // Request the file with command "RECEIVE"
                clientB.out.writeUTF("RECEIVE");
                clientB.out.writeUTF("userB"); // Client B's username

                // Wait for the file to be ready
                Server.waitForFile("userB");

                // Wait for the "READY" signal from the server
                String readySignal = clientB.in.readUTF();
                if (!"READY".equals(readySignal)) {
                    throw new IOException("Expected READY signal, but received: " + readySignal);
                }

                // Receive file
                String fileName = clientB.in.readUTF();
                long fileSize = clientB.in.readLong();

                // Save the received file
                File receivedFile = new File(getProjectRootPath() + "resources" + File.separator + "received_encrypted_image.png"); // Replace with actual path
                //File receivedFile = new File();
                try (FileOutputStream fos = new FileOutputStream(receivedFile)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    long totalBytesRead = 0;
                    while (totalBytesRead < fileSize && (bytesRead = clientB.in.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                        totalBytesRead += bytesRead;
                    }
                }

                synchronized (System.out) {
                    logger.info("Client B: File received: {}", fileName);
                }

                // Verify file integrity (optional)
                // Compare receivedFile with fileToSend to ensure they match

                // Disconnect
                clientB.disconnect();
            } catch (IOException | InterruptedException e) {
                logger.error(e.getMessage());
            }
        });
        clientBThread.start();

        try {
            clientAThread.join();
            clientBThread.join();
            server.stop();
            serverThread.join();
        } catch (InterruptedException e) {
            logger.error(e.getMessage());
        }

        logger.info("Test finished");
    }
}
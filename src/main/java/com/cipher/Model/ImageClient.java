package com.cipher.Model;

import java.io.*;
import java.net.*;

public class ImageClient {
    private static final String SERVER_IP = "localhost";
    private static final int SERVER_PORT = 12345;

    public void sendImage(String imagePath, String targetIp) {
        try (Socket socket = new Socket(SERVER_IP, SERVER_PORT);
             OutputStream outputStream = socket.getOutputStream();
             InputStream inputStream = socket.getInputStream();
             DataOutputStream dataOutputStream = new DataOutputStream(outputStream);
             DataInputStream dataInputStream = new DataInputStream(inputStream)) {

            // Отправляем свой IP
            String clientIp = socket.getInetAddress().getHostAddress();
            dataOutputStream.writeUTF(clientIp);

            // Отправляем команду и изображение
            dataOutputStream.writeUTF("SEND_IMAGE");
            File imageFile = new File(imagePath);
            FileInputStream fileInputStream = new FileInputStream(imageFile);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            fileInputStream.close();

            // Указываем IP целевого клиента
            dataOutputStream.writeUTF(targetIp);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void receiveImage(String savePath) {
        try (Socket socket = new Socket(SERVER_IP, SERVER_PORT);
             InputStream inputStream = socket.getInputStream();
             OutputStream outputStream = socket.getOutputStream();
             DataOutputStream dataOutputStream = new DataOutputStream(outputStream);
             DataInputStream dataInputStream = new DataInputStream(inputStream)) {

            // Отправляем свой IP
            String clientIp = socket.getInetAddress().getHostAddress();
            dataOutputStream.writeUTF(clientIp);

            // Указываем, что хотим получить изображение
            dataOutputStream.writeUTF("RECEIVE_IMAGE");

            // Получаем изображение
            FileOutputStream fileOutputStream = new FileOutputStream(savePath);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                fileOutputStream.write(buffer, 0, bytesRead);
            }
            fileOutputStream.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

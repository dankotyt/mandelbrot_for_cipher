package Model;

import java.io.*;
import java.net.*;

public class Client {
    private Socket socket;
    public DataInputStream in;
    public DataOutputStream out;

    public void connect(String host, int port) throws IOException {
        socket = new Socket(host, port);
        System.out.println("Client connected to server.");
        in = new DataInputStream(socket.getInputStream());
        out = new DataOutputStream(socket.getOutputStream());
    }

    public void disconnect() {
        try {
            if (socket != null) {
                socket.close();
            }
            if (in != null) {
                in.close();
            }
            if (out != null) {
                out.close();
            }
            System.out.println("Client disconnected from server.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendFile(String filePath, String recipient) throws IOException {
        File file = new File(filePath);
        out.writeUTF("SEND");
        out.writeUTF(recipient);
        out.writeUTF(file.getName());
        out.writeLong(file.length());
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[4096];
            int bytes_read;
            while ((bytes_read = fis.read(buffer)) != -1) {
                out.write(buffer, 0, bytes_read);
            }
        }
        System.out.println("File sent to server.");
    }

    public void receiveFile(String savePath, String userKey) throws IOException {
        out.writeUTF("RECEIVE");
        out.writeUTF(userKey);

        // Wait for the "READY" signal from the server
        String readySignal = in.readUTF();
        if (!"READY".equals(readySignal)) {
            throw new IOException("Expected READY signal, but received: " + readySignal);
        }

        String fileName = in.readUTF();
        long fileSize = in.readLong();
        try (FileOutputStream fos = new FileOutputStream(savePath + File.separator + fileName)) {
            byte[] buffer = new byte[4096];
            int bytes_read;
            long totalBytesRead = 0;
            while (totalBytesRead < fileSize && (bytes_read = in.read(buffer)) != -1) {
                fos.write(buffer, 0, bytes_read);
                totalBytesRead += bytes_read;
            }
        }
        System.out.println("File received from server.");
    }
}
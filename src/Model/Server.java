package Model;

import java.io.*;
import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.HashMap;
import java.util.Map;

public class Server implements Runnable {
    //static final int PORT = 12345;
    private static Map<String, User> users = new HashMap<>();
    private ServerSocket serverSocket;
    private ExecutorService executor;
    private volatile boolean running = true;
    private Thread serverThread;
    public static int serverPort = 12345;

    public Server(int port) {
        serverPort = port;
    }

    public static int getPORT() {
        return serverPort;
    }

    public static void main(String[] args) {
        Server server = new Server(serverPort);
        Thread serverThread = new Thread(server);
        server.serverThread = serverThread;
        serverThread.start();
    }

    @Override
    public void run() {
        executor = Executors.newFixedThreadPool(10);
        try {
            // Check if the port is already in use
            if (!isPortAvailable(serverPort)) {
                System.err.println("Port " + serverPort + " is already in use. Model.Server cannot start.");
                return;
            }

            serverSocket = new ServerSocket(serverPort);
            System.out.println("Model.Server is listening on port " + serverPort);
            while (running) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected");
                executor.execute(new ClientHandler(clientSocket));
            }
        } catch (IOException e) {
            if (running) { // Only print if it's not a shutdown exception
                e.printStackTrace();
            }
        } finally {
            executor.shutdown();
            try {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    serverSocket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean isPortAvailable(int port) {
        try (ServerSocket ss = new ServerSocket(port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close(); // This will cause serverSocket.accept() to throw an exception, exiting the loop
            }
            if(serverThread != null) {
                serverThread.interrupt();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        executor.shutdownNow();
    }


    public static void waitForFile(String userKey) {
        User user = users.get(userKey);
        if (user != null) {
            synchronized (user.fileReadyLock) {
                while (!user.isFileReady()) {
                    try {
                        user.fileReadyLock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }


    private static class ClientHandler implements Runnable {
        private Socket socket;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try (
                    DataInputStream dis = new DataInputStream(socket.getInputStream());
                    DataOutputStream dos = new DataOutputStream(socket.getOutputStream())
            ) {
                String command = dis.readUTF();
                if ("SEND".equals(command)) {
                    String recipient = dis.readUTF();
                    String fileName = dis.readUTF();
                    long fileSize = dis.readLong();
                    byte[] fileBytes = new byte[(int) fileSize];
                    dis.readFully(fileBytes);
                    // Store file for recipient
                    User user = users.computeIfAbsent(recipient, k -> new User());
                    user.addFile(fileName, fileBytes);
                    dos.writeUTF("File received");
                    synchronized (user.fileReadyLock) {
                        user.setFileReady(true);
                        user.fileReadyLock.notifyAll();
                    }
                } else if ("RECEIVE".equals(command)) {
                    String userKey = dis.readUTF();
                    User user = users.get(userKey);
                    if (user != null && !user.getFiles().isEmpty()) {
                        String fileName = user.getFiles().keySet().iterator().next();
                        byte[] fileBytes = user.getFiles().get(fileName);

                        synchronized (user.fileReadyLock) {
                            // Send the "READY" signal before sending the file details
                            dos.writeUTF("READY");
                        }

                        dos.writeUTF(fileName);
                        dos.writeLong(fileBytes.length);
                        dos.write(fileBytes);
                        user.getFiles().remove(fileName);
                        synchronized (user.fileReadyLock) {
                            user.setFileReady(false);
                        }
                    } else {
                        dos.writeUTF("No files available");
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static class User {
        private Map<String, byte[]> files = new HashMap<>();
        private boolean fileReady = false;
        public final Object fileReadyLock = new Object();

        public void addFile(String fileName, byte[] fileBytes) {
            files.put(fileName, fileBytes);
        }

        public Map<String, byte[]> getFiles() {
            return files;
        }

        public boolean isFileReady() {
            return fileReady;
        }

        public void setFileReady(boolean fileReady) {
            this.fileReady = fileReady;
        }
    }
}
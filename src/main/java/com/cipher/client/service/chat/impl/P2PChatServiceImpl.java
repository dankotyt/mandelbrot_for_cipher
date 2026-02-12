package com.cipher.client.service.chat.impl;

import com.cipher.client.service.chat.ChatService;
import com.cipher.client.utils.ChatEncryptionUtil;
import com.cipher.common.dto.chat.ChatMessageDTO;
import com.cipher.client.utils.NetworkConstants;
import com.cipher.core.service.network.KeyExchangeService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@Slf4j
@RequiredArgsConstructor
@Scope("singleton")
public class P2PChatServiceImpl implements ChatService {

    private final ChatEncryptionUtil encryptionUtil;
    private final KeyExchangeService keyExchangeService;

    private Socket socket;
    private ObjectOutputStream outputStream;
    private ObjectInputStream inputStream;
    private ServerSocket serverSocket;

    private volatile boolean connected = false;
    private volatile String connectedPeerIp;

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final List<ChatListener> listeners = new CopyOnWriteArrayList<>();

    @PostConstruct
    public void init() {
        try {
            serverSocket = new ServerSocket(NetworkConstants.CHAT_PORT);
            executor.submit(this::acceptConnections);
            log.info("Chat server started on port {}", NetworkConstants.CHAT_PORT);
        } catch (IOException e) {
            log.error("Failed to start chat server", e);
        }
    }

    @PreDestroy
    public void cleanup() {
        executor.shutdownNow();
        disconnect();
    }

    @Override
    public boolean connectToPeer(String peerIp) {
        if (isConnected() && peerIp.equals(connectedPeerIp)) {
            return true;
        }

        disconnect();

        // Детерминированный выбор роли
        boolean asClient = isClientRole(peerIp);
        log.info("Connecting to {} as {}", peerIp, asClient ? "CLIENT" : "SERVER");

        boolean success = asClient ? connectAsClient(peerIp) : waitForConnection(peerIp);

        if (success) {
            keyExchangeService.setConnectedPeer(toInetAddress(peerIp));
            notifyConnectionStatusChanged(true, "Connected to " + peerIp);
        }

        return success;
    }

    private boolean isClientRole(String peerIp) {
        try {
            return InetAddress.getLocalHost().getHostAddress().compareTo(peerIp) < 0;
        } catch (UnknownHostException e) {
            return true;
        }
    }

    private boolean connectAsClient(String peerIp) {
        try {
            Socket s = new Socket();
            s.connect(new InetSocketAddress(peerIp, NetworkConstants.CHAT_PORT), 10000);
            initConnection(s, peerIp);
            return true;
        } catch (IOException e) {
            log.warn("Client connection failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean waitForConnection(String expectedPeerIp) {
        long deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline && !connected) {
            try {
                Thread.sleep(500);
                if (connected && expectedPeerIp.equals(connectedPeerIp)) {
                    return true;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return false;
    }

    private void acceptConnections() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Socket client = serverSocket.accept();
                String peerIp = client.getInetAddress().getHostAddress();

                if (!connected) {
                    initConnection(client, peerIp);
                    notifyIncomingConnection(peerIp);
                } else {
                    client.close();
                }
            } catch (IOException e) {
                log.error("Accept failed", e);
            }
        }
    }

    private void initConnection(Socket s, String peerIp) throws IOException {
        this.socket = s;
        this.connectedPeerIp = peerIp;
        this.outputStream = new ObjectOutputStream(s.getOutputStream());
        this.inputStream = new ObjectInputStream(s.getInputStream());
        this.connected = true;

        executor.submit(this::receiveMessages);
    }

    private void receiveMessages() {
        while (isConnected()) {
            try {
                Object obj = inputStream.readObject();
                if (obj instanceof ChatMessageDTO encrypted) {
                    ChatMessageDTO decrypted = encryptionUtil.decryptMessage(encrypted, connectedPeerIp);

                    if (decrypted.isText()) {
                        listeners.forEach(l -> l.onMessageReceived(decrypted));
                    } else if (decrypted.isImage()) {
                        listeners.forEach(l -> l.onImageReceived(decrypted));
                    }
                }
            } catch (EOFException e) {
                break;
            } catch (Exception e) {
                if (isConnected()) {
                    log.error("Receive error", e);
                }
                break;
            }
        }
        disconnect();
    }

    @Override
    public void sendMessage(String message) {
        send(ChatMessageDTO.builder()
                .content(message)
                .type(ChatMessageDTO.MessageType.TEXT)
                .timestamp(LocalDateTime.now())
                .sender(System.getProperty("user.name"))
                .encrypted(true)
                .build());
    }

    @Override
    public void sendImage(byte[] data, String fileName) {
        send(ChatMessageDTO.builder()
                .type(ChatMessageDTO.MessageType.IMAGE)
                .fileName(fileName)
                .fileData(data)
                .fileSize(data.length)
                .timestamp(LocalDateTime.now())
                .sender(System.getProperty("user.name"))
                .encrypted(true)
                .build());
    }

    @Override
    public void sendFile(byte[] fileData, String fileName) {
        send(ChatMessageDTO.builder()
                .type(ChatMessageDTO.MessageType.FILE)
                .fileName(fileName)
                .fileData(fileData)
                .fileSize(fileData.length)
                .timestamp(LocalDateTime.now())
                .sender(System.getProperty("user.name"))
                .encrypted(true)
                .build());
    }

    private void send(ChatMessageDTO msg) {
        if (!isConnected()) {
            notifyError("Not connected");
            return;
        }

        try {
            outputStream.writeObject(encryptionUtil.encryptMessage(msg, connectedPeerIp));
            outputStream.flush();
            outputStream.reset();
        } catch (IOException e) {
            log.error("Send failed", e);
            disconnect();
        }
    }

    @Override
    public boolean isConnected() {
        return connected && socket != null && socket.isConnected() && !socket.isClosed();
    }

    @Override
    public void disconnect() {
        connected = false;
        connectedPeerIp = null;

        closeQuietly(outputStream);
        closeQuietly(inputStream);
        closeQuietly(socket);

        notifyConnectionStatusChanged(false, "Disconnected");
    }

    private void closeQuietly(AutoCloseable closeable) {
        try {
            if (closeable != null) closeable.close();
        } catch (Exception ignored) {}
    }

    private InetAddress toInetAddress(String ip) {
        try {
            return InetAddress.getByName(ip);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    @Override public void addListener(ChatListener l) { listeners.add(l); }
    @Override public void removeListener(ChatListener l) { listeners.remove(l); }

    private void notifyConnectionStatusChanged(boolean connected, String info) {
        listeners.forEach(l -> l.onConnectionStatusChanged(connected, info));
    }

    private void notifyIncomingConnection(String peerIp) {
        listeners.forEach(l -> l.onIncomingConnection(peerIp));
    }

    private void notifyError(String error) {
        listeners.forEach(l -> l.onError(error));
    }
}

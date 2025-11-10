package com.cipher.client.service;

import com.cipher.common.utils.NetworkConstants;
import com.cipher.core.model.DHKeyExchange;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

@Slf4j
@Component
@NoArgsConstructor
public class KeyExchangeClient {

    public boolean performKeyExchange(InetAddress peerAddress, DHKeyExchange ourKeys) {
        log.info("Initiating key exchange with {}", peerAddress.getHostAddress());

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(peerAddress, NetworkConstants.KEY_EXCHANGE_PORT), 10000);
            socket.setSoTimeout(30000);

            try (DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                 DataInputStream in = new DataInputStream(socket.getInputStream())) {

                if (ourKeys == null) {
                    log.error("No current keys available");
                    return false;
                }

                // Отправляем наш публичный ключ
                byte[] ourPublicKey = ourKeys.getPublicKeyBytes();
                out.writeInt(ourPublicKey.length);
                out.write(ourPublicKey);
                out.flush();

                // Получаем публичный ключ пира
                int keyLength = in.readInt();
                if (keyLength <= 0 || keyLength > 10000) {
                    throw new IOException("Invalid key length: " + keyLength);
                }

                byte[] peerPublicKeyBytes = new byte[keyLength];
                in.readFully(peerPublicKeyBytes);

                // Вычисляем общий секрет
                ourKeys.computeSharedSecret(DHKeyExchange.publicKeyFromBytes(peerPublicKeyBytes));

                log.info("Key exchange completed successfully with {}", peerAddress.getHostAddress());
                return true;

            } catch (SocketTimeoutException e) {
                log.error("Key exchange timeout with {}: {}", peerAddress.getHostAddress(), e.getMessage());
                return false;
            }

        } catch (IOException e) {
            log.error("Key exchange failed with {}: {}", peerAddress.getHostAddress(), e.getMessage());
            return false;
        }
    }

    public void sendKeyInvalidation(InetAddress peerAddress) {
        new Thread(() -> {
            log.info("Sending key invalidation to {}", peerAddress.getHostAddress());

            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(peerAddress, NetworkConstants.KEY_EXCHANGE_PORT), 5000);
                socket.setSoTimeout(5000);

                try (DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
                    out.writeUTF(NetworkConstants.KEY_INVALIDATION_MESSAGE);
                    out.flush();
                    log.info("Key invalidation sent to {}", peerAddress.getHostAddress());
                }

            } catch (IOException e) {
                log.warn("Failed to send key invalidation to {}: {}",
                        peerAddress.getHostAddress(), e.getMessage());
            }
        }, "Key-Invalidation-Sender").start();
    }

    public boolean testConnection(InetAddress peerAddress) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(peerAddress, NetworkConstants.KEY_EXCHANGE_PORT), 3000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}

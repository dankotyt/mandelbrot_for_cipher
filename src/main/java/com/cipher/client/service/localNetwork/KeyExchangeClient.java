package com.cipher.client.service.localNetwork;

import com.cipher.common.utils.NetworkConstants;
import com.cipher.core.model.ECDHKeyExchange;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

@Slf4j
@Component
@RequiredArgsConstructor
public class KeyExchangeClient {

    public ECDHKeyExchange performKeyExchange(InetAddress peerAddress) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(peerAddress, NetworkConstants.KEY_EXCHANGE_PORT), 10000);

            try (DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                 DataInputStream in = new DataInputStream(socket.getInputStream())) {

                // СОЗДАЕМ НОВЫЕ ключи для этого соединения
                ECDHKeyExchange clientKeys = new ECDHKeyExchange();

                // Отправляем наш публичный ключ
                byte[] ourPublicKey = clientKeys.getPublicKeyBytes();
                out.writeInt(ourPublicKey.length);
                out.write(ourPublicKey);
                out.flush();

                int keyLength = in.readInt();
                byte[] peerPublicKeyBytes = new byte[keyLength];
                in.readFully(peerPublicKeyBytes);

                clientKeys.computeSharedSecret(peerPublicKeyBytes);

                log.info("✅ ECDH обмен ключами успешен с: {}", peerAddress.getHostAddress());
                return clientKeys;

            }
        } catch (IOException e) {
            log.error("❌ ECDH key exchange failed with {}: {}",
                    peerAddress.getHostAddress(), e.getMessage());
            return null;
        }
    }

    public void sendKeyInvalidation(InetAddress peerAddress) {
        new Thread(() -> {
            log.info("Sending ECDH key invalidation to {}", peerAddress.getHostAddress());

            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(peerAddress, NetworkConstants.KEY_EXCHANGE_PORT), 5000);
                socket.setSoTimeout(5000);

                try (DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
                    out.writeUTF(NetworkConstants.KEY_INVALIDATION_MESSAGE);
                    out.flush();
                    log.info("ECDH key invalidation sent to {}", peerAddress.getHostAddress());
                }

            } catch (IOException e) {
                log.warn("Failed to send ECDH key invalidation to {}: {}",
                        peerAddress.getHostAddress(), e.getMessage());
            }
        }, "ECDH-Key-Invalidation-Sender").start();
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

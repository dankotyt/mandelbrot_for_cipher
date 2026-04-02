package com.cipher.core.service.network.impl;

import com.cipher.core.model.ECDHKeyPair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ECDHCryptoKeyManagerImplTest {

    private ECDHCryptoKeyManagerImpl keyManager;

    @BeforeEach
    void setUp() throws Exception {
        keyManager = new ECDHCryptoKeyManagerImpl();
        keyManager.init();
        // Даем время на запуск сервера
        Thread.sleep(100);
    }

    @Test
    void generateNewKeys_shouldCreateNewKeys() {
        ECDHKeyPair oldKeys = keyManager.getCurrentKeys();

        keyManager.generateNewKeys();

        ECDHKeyPair newKeys = keyManager.getCurrentKeys();
        assertNotNull(newKeys);
        assertNotSame(oldKeys, newKeys);
    }

    @Test
    void getCurrentKeys_shouldReturnNotNull() {
        ECDHKeyPair keys = keyManager.getCurrentKeys();
        assertNotNull(keys);
    }

    @Test
    void addConnection_and_getConnectionStatus_shouldWork() throws Exception {
        InetAddress peerAddress = InetAddress.getByName("127.0.0.1");
        ECDHKeyPair peerKeys = new ECDHKeyPair();

        keyManager.addConnection(peerAddress, peerKeys);

        assertTrue(keyManager.isConnectedTo(peerAddress));
        assertEquals("CONNECTED", keyManager.getConnectionStatus(peerAddress));
    }

    @Test
    void addConnection_withNullKeys_shouldNotThrow() throws Exception {
        InetAddress peerAddress = InetAddress.getByName("127.0.0.1");

        assertDoesNotThrow(() -> keyManager.addConnection(peerAddress, null));
    }

    @Test
    void closeConnection_shouldRemoveConnection() throws Exception {
        InetAddress peerAddress = InetAddress.getByName("127.0.0.1");
        ECDHKeyPair peerKeys = new ECDHKeyPair();
        keyManager.addConnection(peerAddress, peerKeys);

        keyManager.closeConnection(peerAddress);

        assertFalse(keyManager.isConnectedTo(peerAddress));
    }

    @Test
    void closeAllConnections_shouldRemoveAll() throws Exception {
        InetAddress peer1 = InetAddress.getByName("127.0.0.1");
        InetAddress peer2 = InetAddress.getByName("127.0.0.2");
        ECDHKeyPair peerKeys = new ECDHKeyPair();

        keyManager.addConnection(peer1, peerKeys);
        keyManager.addConnection(peer2, peerKeys);

        keyManager.closeAllConnections();

        assertFalse(keyManager.isConnectedTo(peer1));
        assertFalse(keyManager.isConnectedTo(peer2));
    }

    @Test
    void getActiveConnections_shouldReturnMap() throws Exception {
        InetAddress peer1 = InetAddress.getByName("127.0.0.1");
        InetAddress peer2 = InetAddress.getByName("127.0.0.2");
        ECDHKeyPair peerKeys = new ECDHKeyPair();

        keyManager.addConnection(peer1, peerKeys);
        keyManager.addConnection(peer2, peerKeys);

        Map<InetAddress, String> connections = keyManager.getActiveConnections();

        assertEquals(2, connections.size());
        assertTrue(connections.containsKey(peer1));
        assertTrue(connections.containsKey(peer2));
        assertEquals("CONNECTED", connections.get(peer1));
    }

    @Test
    void setConnectedPeer_and_getConnectedPeer_shouldWork() throws Exception {
        InetAddress peerAddress = InetAddress.getByName("192.168.1.100");

        keyManager.setConnectedPeer(peerAddress);

        assertEquals(peerAddress, keyManager.getConnectedPeer());
    }

    @Test
    void setConnectedPeer_withNull_shouldClear() {
        keyManager.setConnectedPeer(null);

        assertNull(keyManager.getConnectedPeer());
    }

    @Test
    void isConnectedTo_withNonExistentPeer_shouldReturnFalse() throws Exception {
        InetAddress peerAddress = InetAddress.getByName("192.168.1.200");

        assertFalse(keyManager.isConnectedTo(peerAddress));
    }

    @Test
    void getConnectionStatus_withNonExistentPeer_shouldReturnDisconnected() throws Exception {
        InetAddress peerAddress = InetAddress.getByName("192.168.1.200");

        assertEquals("DISCONNECTED", keyManager.getConnectionStatus(peerAddress));
    }

    @Test
    void hasKeysForPeer_shouldReturnCorrectValue() throws Exception {
        String peerIp = "192.168.1.100";
        ECDHKeyPair keys = new ECDHKeyPair();
        InetAddress peerAddress = InetAddress.getByName(peerIp);

        assertFalse(keyManager.hasKeysForPeer(peerIp));

        keyManager.addConnection(peerAddress, keys);

        assertTrue(keyManager.hasKeysForPeer(peerIp));
    }

    @Test
    void removePeerKeys_shouldRemoveKeys() throws Exception {
        String peerIp = "192.168.1.100";
        ECDHKeyPair keys = new ECDHKeyPair();
        InetAddress peerAddress = InetAddress.getByName(peerIp);
        keyManager.addConnection(peerAddress, keys);

        assertTrue(keyManager.hasKeysForPeer(peerIp));

        keyManager.removePeerKeys(peerIp);

        assertFalse(keyManager.hasKeysForPeer(peerIp));
        assertNull(keyManager.getPeerKeys(peerIp));
    }

    @Test
    void getMasterSeedFromDH_whenConnected_shouldReturnSeed() throws Exception {
        InetAddress peerAddress = InetAddress.getByName("127.0.0.1");

        // Создаем два ключа для обмена
        ECDHKeyPair localKeys = new ECDHKeyPair();
        ECDHKeyPair remoteKeys = new ECDHKeyPair();

        // Получаем публичный ключ удаленной стороны в виде byte[]
        byte[] remotePublicKey = remoteKeys.getPublicKeyBytes(); // или getPublicKey().getEncoded()
        // Выполняем обмен ключами
        localKeys.computeSharedSecret(remotePublicKey);

        keyManager.addConnection(peerAddress, localKeys);

        // Даем время на установку соединения
        Thread.sleep(100);

        byte[] seed = keyManager.getMasterSeedFromDH(peerAddress);

        assertNotNull(seed);
        assertTrue(seed.length > 0);
    }

    @Test
    void getMasterSeedFromDH_whenNotConnected_shouldThrowRuntimeException() throws Exception {
        InetAddress peerAddress = InetAddress.getByName("192.168.1.200");

        // Метод выбрасывает RuntimeException, а не IllegalStateException
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            keyManager.getMasterSeedFromDH(peerAddress);
        });

        assertTrue(exception.getMessage().contains("Не удалось получить ECDH мастер-сид") ||
                exception.getMessage().contains("Нет активного ECDH соединения"));
    }

    @Test
    void getMasterSeedFromDH_whenConnectedButNoSharedSecret_shouldThrow() throws Exception {
        InetAddress peerAddress = InetAddress.getByName("127.0.0.1");
        // Создаем ключи без shared secret
        ECDHKeyPair peerKeys = new ECDHKeyPair();
        keyManager.addConnection(peerAddress, peerKeys);

        // Даем время на установку соединения
        Thread.sleep(100);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            keyManager.getMasterSeedFromDH(peerAddress);
        });

        assertNotNull(exception);
    }

    @Test
    void sendKeyInvalidation_shouldNotThrow() throws Exception {
        InetAddress peerAddress = InetAddress.getByName("127.0.0.1");

        // Метод асинхронный, просто проверяем что не выбрасывает исключение
        assertDoesNotThrow(() -> keyManager.sendKeyInvalidation(peerAddress));

        // Даем время на выполнение асинхронной операции
        Thread.sleep(100);
    }

    @Test
    void cleanup_shouldNotThrow() {
        assertDoesNotThrow(() -> keyManager.cleanup());
    }

    @Test
    void getPeerKeys_whenNotExists_shouldReturnNull() {
        ECDHKeyPair keys = keyManager.getPeerKeys("nonexistent");
        assertNull(keys);
    }

    @Test
    void addConnection_withExistingConnection_shouldUpdate() throws Exception {
        InetAddress peerAddress = InetAddress.getByName("127.0.0.1");
        ECDHKeyPair peerKeys1 = new ECDHKeyPair();
        ECDHKeyPair peerKeys2 = new ECDHKeyPair();

        keyManager.addConnection(peerAddress, peerKeys1);
        keyManager.addConnection(peerAddress, peerKeys2);

        assertTrue(keyManager.isConnectedTo(peerAddress));
    }

    @Test
    void closeConnection_withNonExistentPeer_shouldNotThrow() throws Exception {
        InetAddress peerAddress = InetAddress.getByName("192.168.1.200");

        assertDoesNotThrow(() -> keyManager.closeConnection(peerAddress));
    }

    @Test
    void getMasterSeedFromDH_afterConnectionClosed_shouldThrow() throws Exception {
        InetAddress peerAddress = InetAddress.getByName("127.0.0.1");
        ECDHKeyPair peerKeys = new ECDHKeyPair();
        keyManager.addConnection(peerAddress, peerKeys);

        keyManager.closeConnection(peerAddress);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            keyManager.getMasterSeedFromDH(peerAddress);
        });

        assertNotNull(exception);
    }
}
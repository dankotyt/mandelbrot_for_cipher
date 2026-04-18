package com.dankotyt.core.service.network.impl;

import com.dankotyt.core.model.ECDHKeyPair;
import com.dankotyt.core.service.encryption.ECDHService;
import com.dankotyt.core.service.encryption.impl.ECDHServiceImpl;
import com.dankotyt.core.service.network.CryptoKeyManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ECDHCryptoKeyManagerImplTest {

    private CryptoKeyManager keyManager;
    private ECDHService ecdhService;

    @BeforeEach
    void setUp() throws Exception {
        ecdhService = new ECDHServiceImpl();
        keyManager = new ECDHCryptoKeyManagerImpl(ecdhService);
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
        assertNotNull(keyManager.getCurrentKeys());
    }

    @Test
    void addConnection_and_getConnectionStatus_shouldWork() throws Exception {
        InetAddress peerAddress = InetAddress.getByName("127.0.0.1");
        ECDHKeyPair peerKeys = ecdhService.generateKeyPair();
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
        ECDHKeyPair peerKeys = ecdhService.generateKeyPair();
        keyManager.addConnection(peerAddress, peerKeys);
        keyManager.closeConnection(peerAddress);
        assertFalse(keyManager.isConnectedTo(peerAddress));
    }

    @Test
    void closeAllConnections_shouldRemoveAll() throws Exception {
        InetAddress peer1 = InetAddress.getByName("127.0.0.1");
        InetAddress peer2 = InetAddress.getByName("127.0.0.2");
        ECDHKeyPair peerKeys = ecdhService.generateKeyPair();
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
        ECDHKeyPair peerKeys = ecdhService.generateKeyPair();
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
        ECDHKeyPair keys = ecdhService.generateKeyPair();
        InetAddress peerAddress = InetAddress.getByName(peerIp);
        assertFalse(keyManager.hasKeysForPeer(peerIp));
        keyManager.addConnection(peerAddress, keys);
        assertTrue(keyManager.hasKeysForPeer(peerIp));
    }

    @Test
    void removePeerKeys_shouldRemoveKeys() throws Exception {
        String peerIp = "192.168.1.100";
        ECDHKeyPair keys = ecdhService.generateKeyPair();
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
        ECDHKeyPair localKeys = ecdhService.generateKeyPair();
        ECDHKeyPair remoteKeys = ecdhService.generateKeyPair();
        byte[] remotePublicKey = ecdhService.serializePublicKey(remoteKeys);
        ecdhService.computeSharedSecret(localKeys, remotePublicKey);
        keyManager.addConnection(peerAddress, localKeys);
        Thread.sleep(100);
        byte[] seed = keyManager.getMasterSeedFromDH(peerAddress);
        assertNotNull(seed);
        assertTrue(seed.length > 0);
    }

    @Test
    void getMasterSeedFromDH_whenNotConnected_shouldThrowRuntimeException() throws Exception {
        InetAddress peerAddress = InetAddress.getByName("192.168.1.200");
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> keyManager.getMasterSeedFromDH(peerAddress));
        assertNotNull(exception);
    }

    @Test
    void getMasterSeedFromDH_whenConnectedButNoSharedSecret_shouldThrow() throws Exception {
        InetAddress peerAddress = InetAddress.getByName("127.0.0.1");
        ECDHKeyPair peerKeys = ecdhService.generateKeyPair();
        keyManager.addConnection(peerAddress, peerKeys);
        Thread.sleep(100);
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> keyManager.getMasterSeedFromDH(peerAddress));
        assertNotNull(exception);
    }

    @Test
    void sendKeyInvalidation_shouldNotThrow() throws Exception {
        InetAddress peerAddress = InetAddress.getByName("127.0.0.1");
        assertDoesNotThrow(() -> keyManager.sendKeyInvalidation(peerAddress));
        Thread.sleep(100);
    }

    @Test
    void getPeerKeys_whenNotExists_shouldReturnNull() {
        assertNull(keyManager.getPeerKeys("nonexistent"));
    }

    @Test
    void addConnection_withExistingConnection_shouldUpdate() throws Exception {
        InetAddress peerAddress = InetAddress.getByName("127.0.0.1");
        ECDHKeyPair peerKeys1 = ecdhService.generateKeyPair();
        ECDHKeyPair peerKeys2 = ecdhService.generateKeyPair();
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
        ECDHKeyPair peerKeys = ecdhService.generateKeyPair();
        keyManager.addConnection(peerAddress, peerKeys);
        keyManager.closeConnection(peerAddress);
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> keyManager.getMasterSeedFromDH(peerAddress));
        assertNotNull(exception);
    }
}
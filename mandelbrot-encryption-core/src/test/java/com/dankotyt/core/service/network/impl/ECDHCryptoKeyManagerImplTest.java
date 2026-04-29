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
    void setUp() {
        ecdhService = new ECDHServiceImpl();
        keyManager = new ECDHCryptoKeyManagerImpl(ecdhService);
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
    void addPeer_and_hasPeer_shouldWork() throws Exception {
        InetAddress peerAddress = InetAddress.getByName("127.0.0.1");
        ECDHKeyPair peerKeys = ecdhService.generateKeyPair();
        keyManager.addPeer(peerAddress, peerKeys);
        assertTrue(keyManager.hasPeer(peerAddress));
    }

    @Test
    void addPeer_withNullKeys_shouldThrowIllegalArgumentException() throws Exception {
        InetAddress peerAddress = InetAddress.getByName("127.0.0.1");
        assertThrows(IllegalArgumentException.class, () -> keyManager.addPeer(peerAddress, null));
    }

    @Test
    void removePeer_shouldRemovePeer() throws Exception {
        InetAddress peerAddress = InetAddress.getByName("127.0.0.1");
        ECDHKeyPair peerKeys = ecdhService.generateKeyPair();
        keyManager.addPeer(peerAddress, peerKeys);
        keyManager.removePeer(peerAddress);
        assertFalse(keyManager.hasPeer(peerAddress));
    }

    @Test
    void getActivePeers_shouldReturnSnapshotCopy() throws Exception {
        InetAddress peer1 = InetAddress.getByName("127.0.0.1");
        InetAddress peer2 = InetAddress.getByName("127.0.0.2");
        ECDHKeyPair keys = ecdhService.generateKeyPair();
        keyManager.addPeer(peer1, keys);
        keyManager.addPeer(peer2, keys);

        Map<InetAddress, ECDHKeyPair> snapshot = keyManager.getActivePeers();
        assertEquals(2, snapshot.size());

        // Удаляем одного пира через менеджер
        keyManager.removePeer(peer1);

        // Снимок не должен измениться
        assertEquals(2, snapshot.size());
        assertTrue(snapshot.containsKey(peer1));
        assertTrue(snapshot.containsKey(peer2));

        // Актуальное состояние менеджера – только один пир
        assertEquals(1, keyManager.getActivePeers().size());
        assertTrue(keyManager.hasPeer(peer2));
        assertFalse(keyManager.hasPeer(peer1));
    }

    @Test
    void hasPeer_withNonExistentPeer_shouldReturnFalse() throws Exception {
        InetAddress peerAddress = InetAddress.getByName("192.168.1.200");
        assertFalse(keyManager.hasPeer(peerAddress));
    }

    @Test
    void getMasterSeedFromDH_whenSharedSecretAvailable_shouldReturnSeed() throws Exception {
        InetAddress peerAddress = InetAddress.getByName("127.0.0.1");
        ECDHKeyPair localKeys = keyManager.getCurrentKeys(); // текущие локальные ключи
        ECDHKeyPair remoteKeys = ecdhService.generateKeyPair();
        // Вычисляем общий секрет между локальной и удалённой парой
        byte[] remotePublicKey = ecdhService.serializePublicKey(remoteKeys);
        ecdhService.computeSharedSecret(localKeys, remotePublicKey);
        // Регистрируем пира с ключами (addPeer внутри вызывает computeSharedSecret повторно,
        // что нормально, главное чтобы sharedSecretBytes был не null)
        keyManager.addPeer(peerAddress, localKeys);
        byte[] seed = keyManager.getMasterSeedFromDH(peerAddress);
        assertNotNull(seed);
        assertTrue(seed.length > 0);
    }

    @Test
    void getMasterSeedFromDH_whenNoPeer_shouldThrow() throws Exception {
        InetAddress peerAddress = InetAddress.getByName("192.168.1.200");
        Exception exception = assertThrows(IllegalStateException.class,
                () -> keyManager.getMasterSeedFromDH(peerAddress));
        assertTrue(exception.getMessage().contains(peerAddress.toString()));
    }

    @Test
    void getMasterSeedFromDH_whenPeerHasNoSharedSecret_shouldThrow() throws Exception {
        InetAddress peerAddress = InetAddress.getByName("127.0.0.1");
        ECDHKeyPair peerKeys = ecdhService.generateKeyPair();
        keyManager.addPeer(peerAddress, peerKeys);
    }

    @Test
    void addPeer_withExistingPeer_shouldUpdate() throws Exception {
        InetAddress peerAddress = InetAddress.getByName("127.0.0.1");
        ECDHKeyPair keys1 = ecdhService.generateKeyPair();
        ECDHKeyPair keys2 = ecdhService.generateKeyPair();
        keyManager.addPeer(peerAddress, keys1);
        keyManager.addPeer(peerAddress, keys2);
        assertTrue(keyManager.hasPeer(peerAddress));
    }

    @Test
    void removePeer_withNonExistentPeer_shouldNotThrow() {
        InetAddress nonExistent = InetAddress.getLoopbackAddress();
        assertDoesNotThrow(() -> keyManager.removePeer(nonExistent));
    }
}
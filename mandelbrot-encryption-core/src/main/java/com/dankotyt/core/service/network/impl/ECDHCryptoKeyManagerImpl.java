package com.dankotyt.core.service.network.impl;

import com.dankotyt.core.model.ECDHKeyPair;
import com.dankotyt.core.service.encryption.ECDHService;
import com.dankotyt.core.service.network.CryptoKeyManager;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ECDHCryptoKeyManagerImpl implements CryptoKeyManager {
    private final ECDHService ecdhService;
    private volatile ECDHKeyPair currentKeys;
    private final Map<InetAddress, ECDHKeyPair> peers = new ConcurrentHashMap<>();

    public ECDHCryptoKeyManagerImpl(ECDHService ecdhService) {
        this.ecdhService = ecdhService;
        generateNewKeys();
    }

    @Override
    public byte[] getMasterSeedFromDH(InetAddress peerAddress) {
        ECDHKeyPair peerKeys = peers.get(peerAddress);
        if (peerKeys == null || peerKeys.getSharedSecretBytes() == null) {
            throw new IllegalStateException("No shared secret for peer: " + peerAddress);
        }
        return peerKeys.getSharedSecretBytes();
    }

    @Override
    public void generateNewKeys() {
        currentKeys = ecdhService.generateKeyPair();
    }

    @Override
    public ECDHKeyPair getCurrentKeys() {
        return currentKeys;
    }

    @Override
    public void addPeer(InetAddress peerAddress, ECDHKeyPair peerKeyPair) {
        if (peerAddress == null || peerKeyPair == null) {
            throw new IllegalArgumentException("Peer address or key pair cannot be null");
        }
        BigInteger[] clonedPublicKey = currentKeys.getPublicKey().clone();
        ECDHKeyPair keysForPeer = new ECDHKeyPair(
                currentKeys.getPrivateKey(),
                clonedPublicKey,
                currentKeys.getCreationTime()
        );

        ecdhService.computeSharedSecret(keysForPeer,
                ecdhService.serializePublicKey(peerKeyPair.getPublicKey()));

        peers.put(peerAddress, keysForPeer);
    }

    @Override
    public void removePeer(InetAddress peerAddress) {
        if (peerAddress == null) {
            throw new IllegalArgumentException("Peer address cannot be null");
        }
        ECDHKeyPair removed = peers.remove(peerAddress);
        if (removed != null) {
            removed.invalidate();
        }
    }

    @Override
    public boolean hasPeer(InetAddress peerAddress) {
        if (peerAddress == null) {
            throw new IllegalArgumentException("Peer address cannot be null");
        }
        ECDHKeyPair peerKeys = peers.get(peerAddress);
        return peerKeys != null && peerKeys.getSharedSecretBytes() != null;
    }

    @Override
    public Map<InetAddress, ECDHKeyPair> getActivePeers() {
        return new HashMap<>(peers);
    }
}
package com.cipher.core.service.network;

import java.net.InetAddress;
import java.util.Set;

public interface PeerDiscoveryService {

    /**
     * Вызывается при обнаружении нового пира в сети
     */
    void onPeerDiscovered(InetAddress peerAddress);

    /**
     * Вызывается при успешном установлении соединения с пиром
     */
    void onPeerConnected(InetAddress peerAddress);

    /**
     * Вызывается при отключении пира
     */
    void onPeerDisconnected(InetAddress peerAddress);

    /**
     * Возвращает список всех обнаруженных пиров
     */
    Set<InetAddress> getDiscoveredPeers();

    /**
     * Возвращает список подключенных пиров
     */
    Set<InetAddress> getConnectedPeers();

    /**
     * Начать широковещательное оповещение о себе
     */
    void broadcastPresence();

    /**
     * Остановить обнаружение
     */
    void stopDiscovery();

    void initialize();
}

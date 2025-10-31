package com.cipher.core.service;

import java.net.InetAddress;
import java.util.Set;

public interface NetworkDiscoveryService {

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
}

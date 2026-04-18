package com.dankotyt.core.service.network;

import java.net.InetAddress;
import java.util.Set;

public interface PeerDiscoveryService {

    /**
     * Вызывается при обнаружении нового пира в сети
     */
    void onPeerDiscovered(InetAddress peerAddress);

    /**
     * Вызывается при отключении пира
     */
    void onPeerDisconnected(InetAddress peerAddress);

    /**
     * Возвращает список всех обнаруженных пиров
     */
    Set<InetAddress> getDiscoveredPeers();

    /**
     * Позволяет вручную найти пир по ip-адресу
     * */
    void manuallyAddPeer(String peerAddress);

    /**
     * Остановить обнаружение
     */
    void clear();
}

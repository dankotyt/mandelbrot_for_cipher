package com.cipher.core.service.network;

import com.cipher.core.dto.DeviceDTO;

import java.net.*;
import java.util.List;


public interface NetworkService {
    DeviceDTO getCurrentDevice();
    /**
     * ВОЗВРАЩАЕТ ТОЛЬКО УЖЕ ОБНАРУЖЕННЫЕ УСТРОЙСТВА
     * (через Discovery механизм)
     */
    List<DeviceDTO> getDiscoveredDevices();
    boolean isAppRunning(String ip);
    String getLocalIpAddress() throws SocketException;

    /**
     * Проводит активное сканирование по всей сети
     */
    @Deprecated
    List<DeviceDTO> discoverLocalDevices();
}

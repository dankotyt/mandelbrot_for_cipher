package com.cipher.client.utils;

public class NetworkConstants {
    public static final String BROADCAST_ADDRESS = "255.255.255.255";
    public static final int DISCOVERY_PORT = 8888;
    public static final int KEY_EXCHANGE_PORT = 8889;    // Только бинарный обмен ключами
    public static final int KEY_INVALIDATION_PORT = 8890; // Только инвалидация
    public static final byte MSG_KEY_EXCHANGE = 0x01;
    public static final byte MSG_KEY_INVALIDATION = 0x02;
    public static final byte MSG_DISCOVERY = 0x03;
    public static final byte MSG_GOODBYE = 0x04;
    public static final long ANNOUNCE_INTERVAL_MS = 5000;
    public static final int GOODBYE_DELAY_MS = 1000;
    public static final int APP_PORT = 25565;
    public static final int CONNECTION_PORT = 25565;
    public static final int CHAT_PORT = 25566;
}

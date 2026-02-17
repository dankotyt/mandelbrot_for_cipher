package com.cipher.client.utils;

public class NetworkConstants {
    public static final String BROADCAST_ADDRESS = "255.255.255.255";

    public static final int DISCOVERY_PORT = 8888;
    public static final int SIGNED_PACKET_PORT = 8889;
    public static final int KEY_INVALIDATION_PORT = 8890;
    public static final int APP_PORT = 25565;
    public static final int CONNECTION_PORT = 25565;
    public static final int CHAT_PORT = 25566;

    public static final byte MSG_KEY_INVALIDATION = 0x02;
    public static final byte MSG_DISCOVERY = 0x03;
    public static final byte MSG_GOODBYE = 0x04;

    public static final long ANNOUNCE_INTERVAL_MS = 5000;
    public static final int GOODBYE_DELAY_MS = 1000;
}

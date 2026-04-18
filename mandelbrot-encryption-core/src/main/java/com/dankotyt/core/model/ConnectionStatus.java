package com.dankotyt.core.model;

public enum ConnectionStatus {
    CONNECTED, KEY_EXCHANGE_IN_PROGRESS, DISCONNECTED, KEY_INVALIDATED;

    @Override
    public String toString() {
        return this.name();
    }
}


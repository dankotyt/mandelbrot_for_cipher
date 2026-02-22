package com.cipher.common.dto.chat;

import lombok.Builder;
import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Builder
@Getter
public class ChatMessageDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String content;
    private LocalDateTime timestamp;
    private String sender;
    private boolean encrypted;
    private MessageType type;
    private byte[] fileData;
    private String fileName;
    private long fileSize;

    public enum MessageType {
        TEXT, IMAGE, FILE, SYSTEM
    }

    public boolean isImage() {
        return type == MessageType.IMAGE;
    }

    public boolean isText() {
        return type == MessageType.TEXT;
    }

    public boolean isFile() { return type == MessageType.FILE; }

    public boolean hasFile() {
        return fileData != null && fileData.length > 0;
    }
}
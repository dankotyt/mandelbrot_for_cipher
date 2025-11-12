package com.cipher.common.dto.chat;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Builder
@Getter
public class ChatMessageDTO {
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

    public ChatMessageDTO withFileData(byte[] newFileData) {
        return new ChatMessageDTO(
                content,
                timestamp,
                sender,
                encrypted,
                type,
                newFileData,
                fileName,
                newFileData != null ? newFileData.length : 0
        );
    }

    public boolean isImage() {
        return type == MessageType.IMAGE;
    }

    public boolean isText() {
        return type == MessageType.TEXT;
    }

    public boolean hasFile() {
        return fileData != null && fileData.length > 0;
    }
}

package com.cipher.client.service.chat;

import javafx.scene.layout.HBox;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatHistoryService {
    private final Map<String, List<HBox>> chatHistories = new ConcurrentHashMap<>();
    @Getter
    private String currentChatId;

    public void setCurrentChat(String peerIp) {
        this.currentChatId = peerIp;
        chatHistories.putIfAbsent(peerIp, new ArrayList<>());
    }

    public void addMessage(String peerIp, HBox messageBox) {
        chatHistories.computeIfAbsent(peerIp, k -> new ArrayList<>()).add(messageBox);
    }

    public List<HBox> getMessages(String peerIp) {
        return chatHistories.getOrDefault(peerIp, new ArrayList<>());
    }

    public void clearChat(String peerIp) {
        chatHistories.remove(peerIp);
    }
}

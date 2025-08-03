package com.cipher.core.network.server;

import com.cipher.core.network.client.ImageClient;

public class ServerController {
    private ImageClient imageClient;

    public ServerController() {
        this.imageClient = new ImageClient();
    }

    public void sendImage(String imagePath, String targetIp) {
        imageClient.sendImage(imagePath, targetIp);
    }

    public void receiveImage(String savePath) {
        imageClient.receiveImage(savePath);
    }
}

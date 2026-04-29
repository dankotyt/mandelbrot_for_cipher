package com.dankotyt.core.service.encryption;

import java.awt.image.BufferedImage;
import java.io.File;
import java.net.InetAddress;

public interface ImageDecryptor {
    BufferedImage decryptImage(File encryptedFile, InetAddress peerAddress) throws Exception;
}
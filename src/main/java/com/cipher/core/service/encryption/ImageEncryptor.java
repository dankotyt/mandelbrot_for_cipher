package com.cipher.core.service.encryption;

import java.awt.image.BufferedImage;

import com.cipher.core.dto.encryption.EncryptedData;
import javafx.geometry.Rectangle2D;

public interface ImageEncryptor {
    void prepareSession(byte[] sharedSecret) throws Exception;
    BufferedImage generateNextFractal(int width, int height);
    EncryptedData encryptWhole(BufferedImage originalImage) throws Exception;
    EncryptedData encryptPart(BufferedImage originalImage, Rectangle2D selectedArea) throws Exception;
}
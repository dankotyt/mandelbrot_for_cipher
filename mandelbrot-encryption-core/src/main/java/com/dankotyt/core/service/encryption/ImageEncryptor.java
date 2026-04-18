package com.dankotyt.core.service.encryption;

import com.dankotyt.core.dto.encryption.EncryptedData;
import javafx.geometry.Rectangle2D;

import java.awt.image.BufferedImage;

public interface ImageEncryptor {
    void prepareSession(byte[] sharedSecret) throws Exception;
    BufferedImage generateNextFractal(int width, int height);
    EncryptedData encryptWhole(BufferedImage originalImage) throws Exception;
    EncryptedData encryptPart(BufferedImage originalImage, Rectangle2D selectedArea) throws Exception;
}
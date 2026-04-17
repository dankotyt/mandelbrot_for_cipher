package com.cipher.core.service.encryption;

import java.awt.image.BufferedImage;
import javafx.geometry.Rectangle2D;

public interface ImageEncryptor {
    void prepareSession(byte[] sharedSecret) throws Exception;
    BufferedImage generateNextFractal(int width, int height);
    void encryptWhole(BufferedImage originalImage) throws Exception;
    void encryptPart(BufferedImage originalImage, Rectangle2D selectedArea) throws Exception;
}
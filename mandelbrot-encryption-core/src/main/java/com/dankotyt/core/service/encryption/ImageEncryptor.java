package com.dankotyt.core.service.encryption;

import com.dankotyt.core.dto.encryption.EncryptedData;

import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public interface ImageEncryptor {
    void prepareSession(byte[] sharedSecret) throws InvalidKeyException, NoSuchAlgorithmException;
    BufferedImage generateNextFractal(int width, int height);
    EncryptedData encryptWhole(BufferedImage originalImage);
    EncryptedData encryptPart(BufferedImage originalImage, Rectangle2D selectedArea);
    EncryptedData encryptPart(BufferedImage originalImage, int sx, int sy, int areaWidth, int areaHeight);
}
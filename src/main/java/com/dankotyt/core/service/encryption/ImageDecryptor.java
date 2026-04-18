package com.dankotyt.core.service.encryption;

import java.awt.image.BufferedImage;
import java.io.File;

public interface ImageDecryptor {
    BufferedImage decryptImage(File encryptedFile) throws Exception;
}
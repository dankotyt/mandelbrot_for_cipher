package com.cipher.core.dto;

import com.cipher.core.dto.neww.SegmentationParams;

public record KeyDecoderParams(MandelbrotParams mandelbrotParams, SegmentationParams segmentationParams,
                               int startX, int startY, byte[] encryptedMasterSeed, byte[] iv, byte[] salt) { }

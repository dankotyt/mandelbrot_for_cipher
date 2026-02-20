package com.cipher.core.dto;

import com.cipher.core.dto.segmentation.SegmentationParams;

@Deprecated
public record KeyDecoderParams(MandelbrotParams mandelbrotParams, SegmentationParams segmentationParams,
                               int startX, int startY, byte[] encryptedMasterSeed, byte[] iv, byte[] salt) { }

package com.cipher.core.dto.neww;

import java.awt.image.BufferedImage;
import java.util.Map;

public record SegmentationResult(
        BufferedImage shuffledImage,
        int segmentSize,
        int paddedWidth,
        int paddedHeight,
        Map<Integer, Integer> segmentMapping
) {}

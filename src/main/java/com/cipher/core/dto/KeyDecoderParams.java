package com.cipher.core.dto;

import java.util.Map;

public record KeyDecoderParams(int startMandelbrotWidth, int startMandelbrotHeight,
                               double zoom, double offsetX, double offsetY, int maxIter,
                               int segmentWidthSize, int segmentHeightSize, Map<Integer, Integer> segmentMapping,
                               int startX, int startY, int width, int height) {
}

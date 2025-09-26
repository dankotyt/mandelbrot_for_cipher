package com.cipher.core.dto.neww;

import java.util.Map;

public record SegmentationParams(int segmentSize, int paddedWidth, int paddedHeight,
                                 Map<Integer, Integer> segmentMapping) {
}

package com.cipher.core.dto.segmentation;

import java.util.Map;

public record SegmentationParams(int segmentSize, Map<Integer, Integer> segmentMapping) {
}

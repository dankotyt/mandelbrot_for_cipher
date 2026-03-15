package com.cipher.core.dto.segmentation;

import java.util.Map;

@Deprecated
public record SegmentationParams(int segmentSize, Map<Integer, Integer> segmentMapping) {
}

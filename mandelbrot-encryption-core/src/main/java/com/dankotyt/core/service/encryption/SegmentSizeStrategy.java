package com.dankotyt.core.service.encryption;

public interface SegmentSizeStrategy {
    int determineSegmentSize(int imageWidth, int imageHeight);
}
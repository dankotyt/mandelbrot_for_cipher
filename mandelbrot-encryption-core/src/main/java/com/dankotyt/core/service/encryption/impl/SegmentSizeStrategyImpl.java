package com.dankotyt.core.service.encryption.impl;

import com.dankotyt.core.service.encryption.SegmentSizeStrategy;

public class SegmentSizeStrategyImpl implements SegmentSizeStrategy {
    private final int smallImageThreshold;
    private final int mediumImageThreshold;
    private final int smallSegmentSize;
    private final int mediumSegmentSize;
    private final int largeSegmentSize;

    private static final int DEFAULT_SMALL_THRESHOLD = 768;
    private static final int DEFAULT_MEDIUM_THRESHOLD = 1920;
    private static final int DEFAULT_SMALL_SEGMENT = 1;
    private static final int DEFAULT_MEDIUM_SEGMENT = 4;
    private static final int DEFAULT_LARGE_SEGMENT = 16;

    public SegmentSizeStrategyImpl() {
        this.smallImageThreshold = DEFAULT_SMALL_THRESHOLD;
        this.mediumImageThreshold = DEFAULT_MEDIUM_THRESHOLD;
        this.smallSegmentSize = DEFAULT_SMALL_SEGMENT;
        this.mediumSegmentSize = DEFAULT_MEDIUM_SEGMENT;
        this.largeSegmentSize = DEFAULT_LARGE_SEGMENT;
    }

    public SegmentSizeStrategyImpl(int smallImageThreshold, int mediumImageThreshold,
                                   int smallSegmentSize, int mediumSegmentSize, int largeSegmentSize) {
        this.smallImageThreshold = smallImageThreshold;
        this.mediumImageThreshold = mediumImageThreshold;
        this.smallSegmentSize = smallSegmentSize;
        this.mediumSegmentSize = mediumSegmentSize;
        this.largeSegmentSize = largeSegmentSize;
    }

    @Override
    public int determineSegmentSize(int imageWidth, int imageHeight) {
        int maxDim = Math.max(imageWidth, imageHeight);
        if (maxDim <= smallImageThreshold) return smallSegmentSize;
        else if (maxDim <= mediumImageThreshold) return mediumSegmentSize;
        else return largeSegmentSize;
    }
}
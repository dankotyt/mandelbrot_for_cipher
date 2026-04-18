package com.dankotyt.core.service.encryption;

import com.dankotyt.core.dto.segmentation.SegmentationResult;

import java.awt.image.BufferedImage;
import java.security.SecureRandom;

public interface SegmentShuffler {
    SegmentationResult segmentAndShuffle(BufferedImage image, SecureRandom prng);
    BufferedImage unshuffle(BufferedImage shuffledImage, int originalWidth, int originalHeight, SecureRandom prng);
}
package com.cipher.core.dto.neww;

import com.cipher.core.dto.MandelbrotParams;

public record EncryptionParams(
        EncryptionArea area,
        SegmentationParams segmentation,
        MandelbrotParams mandelbrot
) {
    public int getOriginalWidth() {
        return area.width();
    }

    public int getOriginalHeight() {
        return area.height();
    }
}

package com.cipher.core.dto;

public record MandelbrotParams(int startMandelbrotWidth, int startMandelbrotHeight,
                               double zoom, double offsetX, double offsetY, int maxIter) {

    public MandelbrotParams withSize(int newWidth, int newHeight) {
        return new MandelbrotParams(
                newWidth,
                newHeight,
                this.zoom,
                this.offsetX,
                this.offsetY,
                this.maxIter
        );
    }
}

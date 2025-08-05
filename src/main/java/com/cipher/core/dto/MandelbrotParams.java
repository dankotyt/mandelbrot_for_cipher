package com.cipher.core.dto;

public record MandelbrotParams(int startMandelbrotWidth, int startMandelbrotHeight,
                               double zoom, double offsetX, double offsetY, int maxIter) { }

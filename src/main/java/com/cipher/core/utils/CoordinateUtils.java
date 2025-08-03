package com.cipher.core.utils;

import javafx.geometry.Rectangle2D;
import javafx.scene.canvas.Canvas;

public class CoordinateUtils {
    private final Canvas canvas;

    public CoordinateUtils(Canvas canvas) {
        this.canvas = canvas;
    }

    public Rectangle2D convertCanvasToImageCoords(
            double canvasX, double canvasY, double canvasWidth, double canvasHeight,
            double imgWidth, double imgHeight) {

        double scaleX = imgWidth / canvas.getWidth();
        double scaleY = imgHeight / canvas.getHeight();

        double x = canvasX * scaleX;
        double y = canvasY * scaleY;
        double width = Math.abs(canvasWidth) * scaleX;
        double height = Math.abs(canvasHeight) * scaleY;

        return new Rectangle2D(x, y, width, height);
    }
}

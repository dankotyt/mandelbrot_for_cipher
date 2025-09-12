package com.cipher.core.service;

import com.cipher.core.dto.MandelbrotParams;
import com.cipher.core.utils.BinaryFile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
@RequiredArgsConstructor
public class EncryptionService {
    private final BinaryFile binaryFile;
    private static String getProjectRootPath() {
        return new File("").getAbsolutePath() + File.separator;
    }
    private static String getTempPath() {
        return getProjectRootPath() + "temp" + File.separator;
    }

    public void saveMandelbrotParameters(int width, int height, double zoom,
                                         int iterations, double x, double y) {
        if (zoom <= 0 || iterations <= 0 || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Некорректные данные");
        }

        MandelbrotParams params = new MandelbrotParams(width, height, zoom, x, y, iterations);

        binaryFile.saveMandelbrotParamsToBinaryFile(
                getTempPath() + "mandelbrot_params.bin",
                params
        );
    }
}

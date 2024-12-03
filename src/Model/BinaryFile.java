package Model;

import java.io.DataOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class BinaryFile {
    protected static void saveMandelbrotParamsToBinaryFile(String filePath, int startMandelbrotWidth, int startMandelbrotHeight, double ZOOM, double offsetX, double offsetY, int MAX_ITER) {
        try (DataOutputStream dos = new DataOutputStream(Files.newOutputStream(Paths.get(filePath)))) {
            dos.writeInt(startMandelbrotWidth);
            dos.writeInt(startMandelbrotHeight);
            dos.writeDouble(ZOOM);
            dos.writeDouble(offsetX);
            dos.writeDouble(offsetY);
            dos.writeInt(MAX_ITER);
            System.out.println("Параметры сохранены в файл " + filePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
        // Вывод параметров шифрования
        System.out.println("Параметры шифрования:");
        System.out.println("Start Model.Mandelbrot Width: " + startMandelbrotWidth);
        System.out.println("Start Model.Mandelbrot Height: " + startMandelbrotHeight);
        System.out.println("ZOOM: " + ZOOM);
        System.out.println("offsetX: " + offsetX);
        System.out.println("offsetY: " + offsetY);
        System.out.println("MAX_ITER: " + MAX_ITER);
    }

    protected static Object[] loadMandelbrotParamsFromBinaryFile(String filePath) {
        Object[] params = new Object[6];
        try (DataInputStream dis = new DataInputStream(Files.newInputStream(Paths.get(filePath)))) {
            params[0] = dis.readInt(); //startMandelbrotWidth
            params[1] = dis.readInt(); //startMandelbrotHeight
            params[2] = dis.readDouble(); // ZOOM
            params[3] = dis.readDouble(); // offsetX
            params[4] = dis.readDouble(); // offsetY
            params[5] = dis.readInt();    // MAX_ITER
        } catch (IOException e) {
            e.printStackTrace();
        }
        return params;
    }

    protected static void saveKeyDecoderToBinaryFile(String filePath, int startMandelbrotWidth, int startMandelbrotHeight, double ZOOM, double offsetX, double offsetY, int MAX_ITER, int segmentWidthSize, int segmentHeightSize, Map<Integer, Integer> segmentMapping, int startX, int startY, int width, int height) {
        try (DataOutputStream dos = new DataOutputStream(Files.newOutputStream(Paths.get(filePath)))) {
            dos.writeInt(startMandelbrotWidth);
            dos.writeInt(startMandelbrotHeight);
            dos.writeDouble(ZOOM);
            dos.writeDouble(offsetX);
            dos.writeDouble(offsetY);
            dos.writeInt(MAX_ITER);
            dos.writeInt(segmentWidthSize);
            dos.writeInt(segmentHeightSize);
            dos.writeInt(segmentMapping.size());
            for (Map.Entry<Integer, Integer> entry : segmentMapping.entrySet()) {
                dos.writeInt(entry.getKey());
                dos.writeInt(entry.getValue());
            }
            dos.writeInt(startX);
            dos.writeInt(startY);
            dos.writeInt(width);
            dos.writeInt(height);
            System.out.println("Параметры сохранены в файл " + filePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
        // Вывод параметров шифрования
        System.out.println("Параметры шифрования:");
        System.out.println("Start Model.Mandelbrot Width: " + startMandelbrotWidth);
        System.out.println("Start Model.Mandelbrot Height: " + startMandelbrotHeight);
        System.out.println("ZOOM: " + ZOOM);
        System.out.println("offsetX: " + offsetX);
        System.out.println("offsetY: " + offsetY);
        System.out.println("MAX_ITER: " + MAX_ITER);
        System.out.println("segmentWidthSize: " + segmentWidthSize);
        System.out.println("segmentHeightSize: " + segmentHeightSize);
        System.out.println("segmentMapping size: " + segmentMapping.size());
        System.out.println("startX: " + startX);
        System.out.println("startY: " + startY);
        System.out.println("width: " + width);
        System.out.println("height: " + height);
    }

    public static Object[] loadKeyDecoderFromBinaryFile(String filePath) {
        Object[] params = new Object[13];
        try (DataInputStream dis = new DataInputStream(Files.newInputStream(Paths.get(filePath)))) {
            params[0] = dis.readInt();
            params[1] = dis.readInt();
            params[2] = dis.readDouble();
            params[3] = dis.readDouble();
            params[4] = dis.readDouble();
            params[5] = dis.readInt();
            params[6] = dis.readInt();
            params[7] = dis.readInt();
            int segmentCount = dis.readInt();
            Map<Integer, Integer> segmentMapping = new HashMap<>();
            for (int i = 0; i < segmentCount; i++) {
                int key = dis.readInt();
                int value = dis.readInt();
                segmentMapping.put(key, value);
            }
            params[8] = segmentMapping;
            params[9] = dis.readInt();
            params[10] = dis.readInt();
            params[11] = dis.readInt();
            params[12] = dis.readInt();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return params;
    }
}

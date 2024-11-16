import java.io.*;
import java.net.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Client {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 12345;
    //private static final String PROJECT_PATH = "C:/Users/r10021998/ideaProjects/mandelbrot_for_cipher-master/";
    private static final String PROJECT_PATH = "C:/Users/Danil/ideaProjects/mandelbrot_for_cipher/";

    public static void main(String[] args) {
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
            System.out.println("Подключено к серверу: " + socket);

            // Создаем потоки для чтения и записи данных
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            // Загружаем зашифрованное изображение
            BufferedImage encryptedImage = ImageIO.read(new File(PROJECT_PATH + "resources/encrypted_image.png"));

            // Загружаем параметры из файла mandelbrot_params.bin
            Object[] params = loadParametersFromBinaryFile(PROJECT_PATH + "resources/mandelbrot_params.bin");
            double ZOOM = (double) params[0];
            double offsetX = (double) params[1];
            double offsetY = (double) params[2];
            int MAX_ITER = (int) params[3];
            int segmentWidthSize = (int) params[4];
            int segmentHeightSize = (int) params[5];
            int[] segmentIndices = (int[]) params[6];

            // Отправляем зашифрованное изображение и параметры на сервер
            ImageIO.write(encryptedImage, "png", out);
            out.writeDouble(ZOOM);
            out.writeDouble(offsetX);
            out.writeDouble(offsetY);
            out.writeInt(MAX_ITER);
            out.writeInt(segmentWidthSize);
            out.writeInt(segmentHeightSize);
            out.writeObject(segmentIndices);

            // Получаем расшифрованное изображение от сервера
            BufferedImage decryptedImage = ImageIO.read(in);

            // Сохраняем расшифрованное изображение
            ImageIO.write(decryptedImage, "png", new File(PROJECT_PATH + "resources/decrypted_image.png"));

            System.out.println("Расшифрованное изображение сохранено как decrypted_image.png");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Загружает значения ZOOM, offsetX, offsetY, MAX_ITER, segmentWidthSize, segmentHeightSize и segmentIndices из двоичного файла.
     *
     * @param filePath Путь к файлу для загрузки параметров.
     * @return Массив значений [ZOOM, offsetX, offsetY, MAX_ITER, segmentWidthSize, segmentHeightSize, segmentIndices].
     */
    public static Object[] loadParametersFromBinaryFile(String filePath) {
        Object[] params = new Object[7];
        try (DataInputStream dis = new DataInputStream(Files.newInputStream(Paths.get(filePath)))) {
            params[0] = dis.readDouble();
            params[1] = dis.readDouble();
            params[2] = dis.readDouble();
            params[3] = dis.readInt();
            params[4] = dis.readInt();
            params[5] = dis.readInt();
            int segmentCount = dis.readInt();
            int[] segmentIndices = new int[segmentCount];
            for (int i = 0; i < segmentCount; i++) {
                segmentIndices[i] = dis.readInt();
            }
            params[6] = segmentIndices;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return params;
    }
}
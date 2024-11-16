import java.io.*;
import java.net.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

public class Server {
    private static final int PORT = 12345;

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Сервер запущен на порту " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Клиент подключен: " + clientSocket);

                // Создаем потоки для чтения и записи данных
                ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());
                ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());

                // Получаем зашифрованное изображение и ключ
                BufferedImage encryptedImage = ImageIO.read(in);
                double[][] mandelbrotMatrix = (double[][]) in.readObject();
                int segmentWidthSize = in.readInt();
                int segmentHeightSize = in.readInt();
                int[] segmentIndices = (int[]) in.readObject();

                // Дешифруем изображение
                Model_ImageMatrix encryptedModel = new Model_ImageMatrix(encryptedImage, encryptedImage.getHeight(), encryptedImage.getWidth());
                BufferedImage unshuffledImage = encryptedModel.unshuffledSegments(encryptedImage, segmentIndices, segmentWidthSize, segmentHeightSize);
                encryptedModel = new Model_ImageMatrix(unshuffledImage, unshuffledImage.getHeight(), unshuffledImage.getWidth());
                encryptedModel.translatePixelsToNumbers(unshuffledImage.getHeight(), unshuffledImage.getWidth());
                double[][] decryptedMatrix = encryptedModel.decryptImage(encryptedModel.getImageMatrix(), mandelbrotMatrix);
                BufferedImage decryptedImage = encryptedModel.matrixToImage(decryptedMatrix, unshuffledImage.getWidth(), unshuffledImage.getHeight());

                // Отправляем расшифрованное изображение обратно клиенту
                ImageIO.write(decryptedImage, "png", out);

                // Закрываем соединение
                clientSocket.close();
                System.out.println("Клиент отключен: " + clientSocket);
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
}
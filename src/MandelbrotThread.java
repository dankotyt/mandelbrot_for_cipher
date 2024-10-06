import java.awt.image.BufferedImage;

/**
 * Класс MandelbrotThread реализует интерфейс Runnable и используется для генерации части изображения множества Мандельброта
 * в отдельном потоке.
 */
public class MandelbrotThread implements Runnable {

    private int x; // Координата X для генерации
    private int getWidth; // Ширина изображения
    private int getHeight; // Высота изображения
    private double ZOOM; // Уровень масштабирования
    private int MAX_ITER; // Максимальное количество итераций
    private double offsetX; // Смещение по оси X
    private double offsetY; // Смещение по оси Y
    private BufferedImage image; // Изображение для записи результатов

    /**
     * Конструктор класса MandelbrotThread.
     *
     * @param x Координата X для генерации.
     * @param getWidth Ширина изображения.
     * @param getHeight Высота изображения.
     * @param ZOOM Уровень масштабирования.
     * @param MAX_ITER Максимальное количество итераций.
     * @param offsetX Смещение по оси X.
     * @param offsetY Смещение по оси Y.
     * @param image Изображение для записи результатов.
     */
    public MandelbrotThread(int x, int getWidth, int getHeight, double ZOOM, int MAX_ITER, double offsetX, double offsetY, BufferedImage image) {
        this.x = x;
        this.getWidth = getWidth;
        this.getHeight = getHeight;
        this.ZOOM = ZOOM;
        this.MAX_ITER = MAX_ITER;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.image = image;
    }

    /**
     * Метод run выполняет генерацию части изображения множества Мандельброта.
     */
    @Override
    public void run() {
        for (int y = 0; y < getHeight; y++) {
            double zx = 0, zy = 0;
            double cX = (x - getWidth / 2) / ZOOM + offsetX;
            double cY = (y - getHeight / 2) / ZOOM + offsetY;
            int i = MAX_ITER;
            while (zx * zx + zy * zy < 4 && i > 0) {
                double tmp = zx * zx - zy * zy + cX;
                zy = 2.0 * zx * zy + cY;
                zx = tmp;
                i--;
            }
            int color = i | (i << 10);
            image.setRGB(x, y, i > 0 ? color : 0);
        }
    }
}
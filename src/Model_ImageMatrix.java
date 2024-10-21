import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;

/**
 * @author Boykov Iliya
 */
public class Model_ImageMatrix {
    private byte[] pixels;
    private double[][] imageMatrix;

    /**
     * Constructor of the Model class with params
     * @param my_image Buffered image from input
     * @param my_height image height in pixels
     * @param my_width image width in pixels
     */
    Model_ImageMatrix(BufferedImage my_image, int my_height, int my_width) {
        this.pixels = ((DataBufferByte) my_image.getRaster().getDataBuffer()).getData();
        this.imageMatrix = new double[my_height][my_width];
    }

    /**
     * pixels field setter
     * @param my_pixels image as a vector of pixels
     */
    public void setPixels(byte[] my_pixels) {
        this.pixels = my_pixels;
    }

    /**
     * pixels field getter
     * @return pixels field with type byte[]
     */
    public byte[] getPixels() {
        return this.pixels;
    }

    /**
     * imageMatrix field setter
     * @param my_imageMatrix matrix of numbers which are translation of pixels in image
     */
    public void setImageMatrix(double[][] my_imageMatrix) {
        this.imageMatrix = my_imageMatrix;
    }

    /**
     * imageMatrix field getter
     * @return imageMatrix field with type double[][] (2-dim matrix)
     */
    public double[][] getImageMatrix() {
        return this.imageMatrix;
    }

    /**
     * Method which translates image in pixels to matrix of numbers
     * @param my_height height of image in pixels
     * @param my_width width of image in pixels
     */
    public void translatePixelsToNumbers(int my_height, int my_width) {
        for (int i = 0; i < my_height; i++) {
            for (int j = 0; j < my_width; j++) {
                imageMatrix[i][j] = pixels[i * my_width + j] & 0xFF; // Translate byte to int
            }
        }
    }
}
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

public class MandelbrotController {
    private MandelbrotModel model;
    private MandelbrotView view;
    private int numberSave = 0;

    public MandelbrotController(MandelbrotModel model, MandelbrotView view) {
        this.model = model;
        this.view = view;
    }

    public void generateImage() {
        BufferedImage image = model.generateImage(view.getWidth(), view.getHeight());
        if (model.checkImageDiversity(image)) {
            view.setImage(image);
            saveImagePrompt();
        } else {
            generateImage();
        }
    }

    private void saveImagePrompt() {
        int option = JOptionPane.showConfirmDialog(view, "Хотите сохранить изображение на рабочий стол?", "Сохранить изображение", JOptionPane.YES_NO_OPTION);
        if (option == JOptionPane.YES_OPTION) {
            saveImageToDesktop(view.getImage());
        } else {
            generateImage();
        }
    }

    private void saveImageToDesktop(BufferedImage image) {
        String desktopPath = "resources";
        numberSave++;
        File file = new File(desktopPath + File.separator + "mandelbrot" + numberSave + ".png");
        try {
            ImageIO.write(image, "png", file);
            JOptionPane.showMessageDialog(view, "Изображение сохранено на рабочий стол: " + file.getAbsolutePath());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(view, "Ошибка при сохранении изображения: " + e.getMessage());
        }
    }

    public void showImageMatrix() {
        BufferedImage image = view.getImage();
        Model_ImageMatrix imageMatrix = model.getImageMatrix(image, image.getHeight(), image.getWidth());
        view.showMatrix("Image Matrix", imageMatrix.getImageMatrix());
    }
}
import Model.ImageDecrypt;
import Model.Mandelbrot;
import Model.ImageEncrypt;
import View.*;

import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.text.View;

public class Controller {
    private static final String PROJECT_PATH = "C:/Users/Danil/ideaProjects/mandelbrot_for_cipher/";
    private static BufferedImage originalImage;

    public static void main(String[] args) {
        try {
//            FrameInterface frameInterface = new FrameInterface();
//            frameInterface.initializeScreenSize();
//            frameInterface.createMainFrame();
            // Загрузка изображения
            originalImage = ImageIO.read(new File(PROJECT_PATH + "resources/input.png"));
            int width = originalImage.getWidth();
            int height = originalImage.getHeight();

            Mandelbrot mandelbrot = new Mandelbrot();
            JFrame frame = new JFrame("Model.Mandelbrot Set");
            frame.add(mandelbrot);
            frame.setSize(width, height);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setVisible(true);
            mandelbrot.createOrUseMandelbrot();

            ImageEncrypt encryptOrDecrypt = new ImageEncrypt();
            encryptOrDecrypt.encryptWholeOrSelected();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

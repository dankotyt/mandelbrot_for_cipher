package View;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * @author Boykov Iliya
 */
public class ImageView {
    private String title;
    private BufferedImage image;

    /**
     * Constructor with params
     * @param my_title string containing the title of window
     * @param my_image BufferedImage from input which will be shown
     */
    public ImageView(String my_title, BufferedImage my_image) {
        title = my_title;
        image = my_image;
    }

    /**
     * Method to display images in window
     */
    public void showImage() {
        JFrame frame = new JFrame(title);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().setLayout(new BorderLayout());

        JLabel label = new JLabel(new ImageIcon(image));
        frame.getContentPane().add(label, BorderLayout.CENTER);

        frame.pack();
        frame.setVisible(true);
    }
}
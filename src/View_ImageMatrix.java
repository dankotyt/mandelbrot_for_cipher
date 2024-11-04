import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * @author Boykov Iliya
 */
public class View_ImageMatrix {
    private String title;
    private BufferedImage image;
    private double[][] matrix;

    /**
     * Constructor with params
     * @param my_title string containing the title of window
     * @param my_image BufferedImage from input which will be shown
     * @param my_matrix matrix of type double[][] which contains pixels of image which will be shown
     */
    View_ImageMatrix(String my_title, BufferedImage my_image, double[][] my_matrix) {
        title = my_title;
        image = my_image;
        matrix = my_matrix;
    }

//    /**
//     * title field setter
//     * @param my_title String containing the title of window
//     */
//    public void setTitle(String my_title) {
//        this.title = my_title;
//    }
//
//    /**
//     * title field getter
//     * @return field title of type String which is a window title
//     */
//    public String getTitle() {
//        return this.title;
//    }
//
//    /**
//     * image field setter
//     * @param my_image BufferedImage from input which will be shown in window
//     */
//    public void setImage(BufferedImage my_image) {
//        this.image = my_image;
//    }
//
//    /**
//     * image field getter
//     * @return value of image which is a BufferedImage and shown in window
//     */
//    public BufferedImage getImage() {
//        return this.image;
//    }
//
//    /**
//     * matrix field setter
//     * @param my_matrix matrix of type double[][] which contains every pixel of image in int type
//     */
//    public void setMatrix(double[][] my_matrix) {
//        this.matrix = my_matrix;
//    }
//
//    /**
//     * matrix field getter
//     * @return matrix of type double[][] which contains every pixel of image in int type
//     */
//    public double[][] getMatrix() {
//        return this.matrix;
//    }

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

    /**
     * Method to display matrices in window
     */
    public void showMatrix() {
        JFrame frame = new JFrame(title);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().setLayout(new BorderLayout());

        JTextArea textArea = new JTextArea();
        textArea.setEditable(false);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix[i].length; j++) {
                sb.append(String.format("%.2f ", matrix[i][j]));
            }
            sb.append("\n");
        }

        textArea.setText(sb.toString());
        frame.getContentPane().add(new JScrollPane(textArea), BorderLayout.CENTER);

        frame.pack();
        frame.setVisible(true);
    }
}
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class MandelbrotView extends JPanel {
    private BufferedImage image;

    public void setImage(BufferedImage image) {
        this.image = image;
        repaint();
    }

    public BufferedImage getImage() {
        return this.image;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (image != null) {
            g.drawImage(image, 0, 0, null);
        }
    }

    public void showImage(String title) {
        JFrame frame = new JFrame(title);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().setLayout(new BorderLayout());

        JLabel label = new JLabel(new ImageIcon(image));
        frame.getContentPane().add(label, BorderLayout.CENTER);

        frame.pack();
        frame.setVisible(true);
    }

    public void showMatrix(String title, double[][] matrix) {
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
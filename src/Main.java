import javax.swing.*;

public static void main(String[] args) {
    SwingUtilities.invokeLater(() -> {
        JFrame frame = new JFrame("Mandelbrot");
        Mandelbrot mandelbrot = new Mandelbrot();
        frame.add(mandelbrot);
        frame.setSize(1024, 720);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);

        mandelbrot.generateImage();
    });
}



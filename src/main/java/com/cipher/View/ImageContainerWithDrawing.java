package com.cipher.View;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Класс ImageContainerWithDrawing представляет собой контейнер для изображения с возможностью рисования прямоугольников на изображении.
 * Контейнер использует JLayeredPane для отображения изображения и рисования прямоугольников поверх него.
 *
 * @author Илья
 * @version 0.1
 */
public class ImageContainerWithDrawing extends JLayeredPane {
    private Point startPoint;
    private Point endPoint;
    private boolean drawingRectangle = false;
    private JPanel drawingPanel;
    private List<Rectangle2D> rectangles = new ArrayList<>();

    /**
     * Конструктор класса ImageContainerWithDrawing.
     *
     * @param myIcon Изображение, которое будет отображаться в контейнере. Если изображение равно null, будет отображено сообщение "Изображение не найдено".
     */
    public ImageContainerWithDrawing(ImageIcon myIcon) {
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.WHITE, 2),
                BorderFactory.createEmptyBorder(2, 2, 2, 2)
        ));

        JPanel imagePanel = new JPanel(new BorderLayout());
        try {
            if (myIcon != null) {
                JLabel image = new JLabel(myIcon);
                image.setPreferredSize(new Dimension(1024, 720));
                imagePanel.add(image, BorderLayout.CENTER);
            } else {
                JLabel noImageLabel = new JLabel("Изображение не найдено");
                noImageLabel.setFont(new Font("Serif", Font.BOLD, 24));
                noImageLabel.setPreferredSize(new Dimension(1024, 720));
                noImageLabel.setForeground(Color.WHITE);
                noImageLabel.setHorizontalAlignment(JLabel.CENTER);
                noImageLabel.setVerticalAlignment(JLabel.CENTER);
                imagePanel.add(noImageLabel, BorderLayout.CENTER);
                imagePanel.setBackground(Color.BLACK);
            }
            imagePanel.setBounds(0, 0, 1024, 720);
            add(imagePanel, JLayeredPane.DEFAULT_LAYER);
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(null, "Ошибка при создании панели с изображением: " + ex.getMessage());
        }

        drawingPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                try {
                    super.paintComponent(g);
                    Graphics2D g2d = (Graphics2D) g;

                    for (Rectangle2D rect : rectangles) {
                        g2d.setColor(new Color(0xeb3471));
                        g2d.draw(rect);

                        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
                        g2d.fill(rect);
                        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
                    }

                    if (drawingRectangle && startPoint != null && endPoint != null) {
                        int x = Math.min(startPoint.x, endPoint.x);
                        int y = Math.min(startPoint.y, endPoint.y);
                        int width = Math.abs(startPoint.x - endPoint.x);
                        int height = Math.abs(startPoint.y - endPoint.y);
                        g2d.setColor(new Color(0xeb3471));
                        g2d.drawRect(x, y, width, height);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(null, "Ошибка при отрисовке панели: " + ex.getMessage());
                }
            }
        };
        drawingPanel.setOpaque(false);
        drawingPanel.setBounds(0, 0, imagePanel.getWidth(), imagePanel.getHeight());
        add(drawingPanel, JLayeredPane.PALETTE_LAYER);

        drawingPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                try {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        startPoint = e.getPoint();
                        endPoint = e.getPoint();
                        drawingRectangle = true;
                        repaint();
                    } else if (SwingUtilities.isRightMouseButton(e)) {
                        rectangles.clear();
                        repaint();
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(null, "Ошибка при обработке нажатия мыши: " + ex.getMessage());
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                try {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        endPoint = e.getPoint();
                        drawingRectangle = false;
                        if (startPoint != null && endPoint != null && !startPoint.equals(endPoint)) {
                            int x = Math.min(startPoint.x, endPoint.x);
                            int y = Math.min(startPoint.y, endPoint.y);
                            int width = Math.abs(startPoint.x - endPoint.x);
                            int height = Math.abs(startPoint.y - endPoint.y);
                            rectangles.add(new Rectangle2D.Double(x, y, width, height));
                        }
                        repaint();
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(null, "Ошибка при обработке отпускания мыши: " + ex.getMessage());
                }
            }
        });

        drawingPanel.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                try {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        endPoint = e.getPoint();
                        repaint();
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(null, "Ошибка при обработке движения мыши: " + ex.getMessage());
                }
            }
        });
    }

    // Метод для получения выделенной области
    public Rectangle2D getSelectedRectangle() {
        if (!rectangles.isEmpty()) {
            return rectangles.get(rectangles.size() - 1);
        }
        return null;
    }

    public void clearRectangles() {
        rectangles.clear();
        repaint();
    }

    /**
     * Переопределенный метод для размещения компонентов.
     * Устанавливает границы всех компонентов равными границам контейнера.
     */
    @Override
    public void doLayout() {
        try {
            super.doLayout();
            for (Component comp : getComponents()) {
                comp.setBounds(0, 0, getWidth(), getHeight());
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(null, "Ошибка при размещении компонентов: " + ex.getMessage());
        }
    }

    /**
     * Проверяет, есть ли на панели хотя бы один прямоугольник.
     *
     * @return true, если на панели есть хотя бы один прямоугольник, иначе false.
     */
    public boolean hasRectangle() {
        return !rectangles.isEmpty();
    }
}
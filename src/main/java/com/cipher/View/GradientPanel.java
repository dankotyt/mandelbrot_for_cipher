package com.cipher.View;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Rectangle2D;

/**
 * Класс GradientPanel представляет собой пользовательскую панель с градиентным фоном.
 * Градиент заполняет всю панель, изменяясь от startColor до endColor.
 *
 * @author Илья
 * @version 0.1
 */
public class GradientPanel extends JPanel {
    private final Color startColor;
    private final Color endColor;

    /**
     * Конструктор класса GradientPanel.
     *
     * @param startColor Начальный цвет градиента.
     * @param endColor   Конечный цвет градиента.
     */
    public GradientPanel(Color startColor, Color endColor) {
        this.startColor = startColor;
        this.endColor = endColor;
    }

    /**
     * Переопределенный метод отрисовки компонента.
     *
     * @param g Графический контекст отрисовки.
     */
    @Override
    protected void paintComponent(Graphics g) {
        try {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            int w = getWidth();
            int h = getHeight();
            GradientPaint gp = new GradientPaint(0, 0, startColor, 0, h, endColor);
            g2d.setPaint(gp);
            g2d.fill(new Rectangle2D.Double(0, 0, w, h));
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(null, "Ошибка при отрисовке панели: " + ex.getMessage());
        }
    }
}
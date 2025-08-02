package com.cipher.View;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Класс CustomButton представляет собой пользовательскую кнопку с расширенным функционалом.
 * Кнопка имеет градиентный фон, который меняется при нажатии, и не имеет стандартных границ и фокуса.
 *
 * @author Илья
 * @version 0.1
 */
public class CustomButton extends JButton {
    private boolean pressed = false;

    /**
     * Конструктор класса CustomButton.
     *
     * @param text Текст, который будет отображаться на кнопке.
     */
    public CustomButton(String text) {
        super(text);
        setContentAreaFilled(false);
        setFocusPainted(false);
        setBorderPainted(false);
        setForeground(Color.WHITE);
        setFont(new Font("Arial", Font.BOLD, 14));

        addMouseListener(new MouseAdapter() {
            /**
             * Обработчик нажатия кнопки мыши.
             *
             * @param e Объект события мыши.
             */
            @Override
            public void mousePressed(MouseEvent e) {
                try {
                    pressed = true;
                    repaint();
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(null, "Ошибка при обработке нажатия кнопки: " + ex.getMessage());
                }
            }

            /**
             * Обработчик отпускания кнопки мыши.
             *
             * @param e Объект события мыши.
             */
            @Override
            public void mouseReleased(MouseEvent e) {
                try {
                    pressed = false;
                    repaint();
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(null, "Ошибка при обработке отпускания кнопки: " + ex.getMessage());
                }
            }
        });
    }

    /**
     * Переопределенный метод отрисовки компонента.
     *
     * @param g Графический контекст отрисовки.
     */
    @Override
    protected void paintComponent(Graphics g) {
        try {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (pressed) {
                GradientPaint gradient = new GradientPaint(0, 0, new Color(0x257dcf), getWidth(), getHeight(), new Color(0x0773d9));
                g2d.setPaint(gradient);
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
            } else {
                g2d.setColor(new Color(0, 0, 0, 0));
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
            }

            g2d.dispose();
            super.paintComponent(g);
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(null, "Ошибка при отрисовке компонента: " + ex.getMessage());
        }
    }

    /**
     * Переопределенный метод отрисовки границы компонента.
     *
     * @param g Графический контекст отрисовки.
     */
    @Override
    protected void paintBorder(Graphics g) {
        try {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (!pressed) {
                g2d.setColor(Color.WHITE);
                g2d.setStroke(new BasicStroke(2));
                g2d.drawRoundRect(4, 4, getWidth() - 8, getHeight() - 8, 10, 10);
            }
            g2d.dispose();
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(null, "Ошибка при отрисовке границы компонента: " + ex.getMessage());
        }
    }
}
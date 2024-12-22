package View;

import javax.swing.*;
import javax.swing.plaf.basic.BasicProgressBarUI;
import java.awt.*;

/**
 * Класс GradientProgressBarUI представляет собой пользовательскую реализацию интерфейса UI для JProgressBar.
 * Эта реализация добавляет градиентный фон для заполненной части полосы загрузки.
 *
 * @author Илья
 * @version 0.1
 */
public class GradientProgressBarUI extends BasicProgressBarUI {

    /**
     * Переопределенный метод отрисовки заполненной части полосы загрузки.
     *
     * @param g Графический контекст отрисовки.
     * @param c Компонент, который нужно отрисовать (JProgressBar).
     */
    @Override
    protected void paintDeterminate(Graphics g, JComponent c) {
        try {
            if (!(g instanceof Graphics2D)) {
                return;
            }

            Graphics2D g2d = (Graphics2D) g;
            Insets b = progressBar.getInsets();
            int barRectWidth = progressBar.getWidth() - b.right - b.left;
            int barRectHeight = progressBar.getHeight() - b.top - b.bottom;
            if (barRectWidth <= 0 || barRectHeight <= 0) {
                return;
            }

            int amountFull = getAmountFull(b, barRectWidth, barRectHeight);
            if (amountFull > 0) {
                GradientPaint gradient = new GradientPaint(0, 0, new Color(0x83bcf2), barRectWidth, 0, new Color(0x3d9cf5));
                g2d.setPaint(gradient);
                g2d.fillRect(b.left, b.top, amountFull, barRectHeight);
            }

            if (progressBar.isStringPainted()) {
                paintString(g, b.left, b.top, barRectWidth, barRectHeight, amountFull, b);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(null, "Ошибка при отрисовке полосы загрузки: " + ex.getMessage());
        }
    }
}
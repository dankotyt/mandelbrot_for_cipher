package View;

import javax.swing.*;
import java.awt.*;

/**
 * Класс TransparentPanel представляет собой пользовательскую панель с прозрачностью.
 * Эта панель может использоваться для создания полупрозрачных элементов интерфейса.
 *
 * @author Илья
 * @version 0.1
 */
class TransparentPanel extends JPanel {

    /**
     * Конструктор класса TransparentPanel.
     *
     * @param layout Менеджер компоновки, который будет использоваться для этой панели.
     */
    public TransparentPanel(LayoutManager layout) {
        super(layout);
        setOpaque(false);
    }

    /**
     * Переопределенный метод для отрисовки компонента.
     * Устанавливает прозрачность панели и заполняет её фоновым цветом.
     *
     * @param g Графический контекст для отрисовки.
     */
    @Override
    protected void paintComponent(Graphics g) {
        try {
            super.paintComponent(g);

            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.0f)); // 0.5f - полупрозрачность

            g2d.setColor(getBackground());
            g2d.fillRect(0, 0, getWidth(), getHeight());

            g2d.dispose();
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(null, "Ошибка при отрисовке прозрачной панели: " + ex.getMessage());
        }
    }
}
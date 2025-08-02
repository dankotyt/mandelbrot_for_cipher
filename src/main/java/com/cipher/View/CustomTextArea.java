package com.cipher.View;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;

/**
 * Пользовательский класс JTextArea с настройками внешнего вида.
 * Этот класс расширяет стандартный JTextArea и добавляет пользовательские стили,
 * такие как цвет фона, цвет текста, шрифт и скругленные границы.
 *
 * @author Илья
 * @version 0.1
 */
class CustomTextArea extends JTextArea {

    /**
     * Конструктор для создания экземпляра CustomTextArea с заданным количеством строк и столбцов.
     *
     * @param rows Количество строк в текстовой области.
     * @param columns Количество столбцов в текстовой области.
     */
    public CustomTextArea(int rows, int columns) {
        super(rows, columns);
        setEditable(false);
        setBackground(new Color(0x0c1d40)); // Тёмно-серый фон
        setForeground(Color.WHITE); // Белый цвет текста
        setFont(new Font("Monospaced", Font.BOLD, 14)); // Шрифт Monospaced, жирный, размер 14

        // Создание скругленных границ
        Border lineBorder = new LineBorder(Color.WHITE, 4, true); // Белая рамка шириной 4, скругленная
        Border emptyBorder = new EmptyBorder(10, 10, 10, 10); // Внутренний отступ для скругления
        setBorder(new CompoundBorder(lineBorder, emptyBorder));
    }
}
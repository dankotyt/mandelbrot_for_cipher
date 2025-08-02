package com.cipher.View;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;

/**
 * Пользовательский класс JTextField с настройками внешнего вида.
 * Этот класс расширяет стандартный JTextField, добавляя пользовательские стили,
 * такие как цвет фона, цвет текста, шрифт, выравнивание текста и скругленные границы.
 *
 * @author Илья
 * @version 0.1
 */
class CustomTextField extends JTextField {

    /**
     * Конструктор для создания экземпляра CustomTextField с заданным количеством столбцов.
     *
     * @param columns Количество столбцов для текстового поля.
     */
    public CustomTextField(int columns) {
        super(columns);
        setBackground(new Color(0x0c1d40)); // Тёмно-серый фон
        setForeground(Color.WHITE); // Белый цвет текста
        setFont(new Font("Monospaced", Font.BOLD, 14)); // Шрифт Monospaced, жирный, размер 14
        setHorizontalAlignment(JTextField.CENTER); // Выравнивание текста по центру

        // Создание скругленных границ
        Border lineBorder = new LineBorder(Color.WHITE, 4, true); // Белая рамка шириной 4, скругленная
        Border emptyBorder = new EmptyBorder(10, 10, 10, 10); // Внутренний отступ для скругления
        setBorder(new CompoundBorder(lineBorder, emptyBorder));
    }
}
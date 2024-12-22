package View;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.OutputStream;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import Model.ClientServerTest;

/**
 * Класс ServerInterface представляет собой графический интерфейс для работы с сервером, диспетчеризующим передачу зашифрованных
 * изображений и ключей для их расшифровки между различными клиентами на различных IP-адресах и портах.
 * Интерфейс состоит из нескольких панелей: экрана загрузки, экрана запуска сервера, экрана ввода порта и экрана с журналом действий сервера.
 *
 * @author Илья
 * @version 0.2
 */
public class ServerInterface {

    /**
     * Объект CardLayout, используемый для управления отображением различных панелей в главном окне.
     */
    private static CardLayout cardLayout;

    /**
     * Основная панель, содержащая все остальные панели, управляемые CardLayout.
     */
    private static JPanel mainPanel;

    /**
     * Ширина экрана, на котором отображается интерфейс.
     */
    private static int screenWidth;

    /**
     * Высота экрана, на котором отображается интерфейс.
     */
    private static int screenHeight;

    /**
     * Полоса загрузки, используемая для отображения прогресса загрузки или выполнения операций.
     */
    private static JProgressBar progressBar;

    /**
     * Цвет для верхней части градиента на панелях.
     */
    private static final int NORTH_COL = 0x011324;

    /**
     * Цвет для нижней части градиента на панелях.
     */
    private static final int SOUTH_COL = 0x011a30;

    /**
     * Шрифт, используемый для кнопок в интерфейсе.
     */
    private static final Font buttonFont = new Font("Arial", Font.BOLD, 16);

    /**
     * Размер кнопок в интерфейсе.
     */
    private static final Dimension buttonSize = new Dimension(400, 40);

    /**
     * Размер текстовых полей в интерфейсе.
     */
    private static final Dimension fieldSize = new Dimension(320, 42);

    /**
     * Точка входа в программу. Инициализирует размер экрана и создает главное окно.
     *
     * @param args Аргументы командной строки.
     */
    public static void main(String[] args) {
        initializeScreenSize();
        createMainFrame();
    }

    /**
     * Инициализирует размеры экрана, на котором будет отображаться интерфейс.
     * В случае ошибки устанавливает размеры по умолчанию (640x480).
     */
    private static void initializeScreenSize() {
        try {
            Toolkit toolkit = Toolkit.getDefaultToolkit();
            Dimension screenSize = toolkit.getScreenSize();
            screenWidth = screenSize.width;
            screenHeight = screenSize.height;
        } catch (Exception e) {
            System.err.println("Ошибка при получении размеров экрана: " + e.getMessage());
            screenWidth = 640;
            screenHeight = 480;
        }
    }

    /**
     * Создает главное окно приложения и настраивает его.
     */
    private static void createMainFrame() {
        try {
            JFrame mainFrame = new JFrame("Шифр Мандельброта");
            mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            mainFrame.setSize(screenWidth, screenHeight);
            mainFrame.setLocationRelativeTo(null);

            cardLayout = new CardLayout();
            mainPanel = new JPanel(cardLayout);
            mainFrame.add(mainPanel);
            mainFrame.setExtendedState(JFrame.MAXIMIZED_BOTH);

            createLoadingScreen(mainFrame);
        } catch (Exception e) {
            System.err.println("Ошибка при создании главного фрейма: " + e.getMessage());
        }
    }

    /**
     * Создает экран загрузки, который отображается при запуске приложения.
     *
     * @param mainFrame Главное окно приложения.
     */
    private static void createLoadingScreen(JFrame mainFrame) {
        try {
            JPanel loadingPanel = new GradientPanel(new Color(NORTH_COL), new Color(SOUTH_COL));
            loadingPanel.setLayout(new GridBagLayout());

            GridBagConstraints gbc = new GridBagConstraints();
            setGridConstraints(gbc, 0, 0, -1, -1, GridBagConstraints.HORIZONTAL);
            gbc.insets = new Insets(0, 0, 64, 0);

            JLabel loadingLabel = initializeNewLabel("Mandelbrot Model.Server", 96, 0);
            loadingLabel.setHorizontalAlignment(SwingConstants.CENTER);
            loadingPanel.add(loadingLabel, gbc);

            progressBar = new JProgressBar(0, 1000);
            progressBar.setIndeterminate(false);
            progressBar.setPreferredSize(new Dimension(screenWidth / 2, 32));
            progressBar.setUI(new GradientProgressBarUI());
            progressBar.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Color.WHITE, 1),
                    BorderFactory.createEmptyBorder(1, 1, 1, 1)
            ));

            setGridConstraints(gbc, -1, 1, -1, -1, -1);
            gbc.insets = new Insets(0, 0, 0, 0);
            loadingPanel.add(progressBar, gbc);

            mainPanel.add(loadingPanel, "LoadingPanel");
            cardLayout.show(mainPanel, "LoadingPanel");
            mainFrame.setVisible(true);

            // Используем SwingWorker для имитации загрузки
            SwingWorker<Void, Integer> worker = new SwingWorker<Void, Integer>() {
                @Override
                protected Void doInBackground() {
                    try {
                        for (int i = 0; i <= 1000; i++) {
                            publish(i);
                            Thread.sleep(1); // Задержка для имитации загрузки
                        }
                    } catch (InterruptedException e) {
                        System.err.println("Ошибка в SwingWorker: " + e.getMessage());
                        Thread.currentThread().interrupt();
                    }
                    return null;
                }

                @Override
                protected void process(java.util.List<Integer> chunks) {
                    for (int progress : chunks) {
                        progressBar.setValue(progress);
                    }
                }

                @Override
                protected void done() {
                    createStartPanel();
                    cardLayout.show(mainPanel, "StartPanel");
                }
            };

            worker.execute();
        } catch (Exception e) {
            System.err.println("Ошибка при создании экрана загрузки: " + e.getMessage());
        }
    }

    /**
     * Создает стартовую панель с кнопками для шифрования и расшифровки изображений.
     */
    private static void createStartPanel() {
        try {
            JPanel startPanel = new GradientPanel(new Color(NORTH_COL), new Color(SOUTH_COL));
            startPanel.setLayout(new GridBagLayout());

            JButton encryptButton = initializeNewButton("Запустить сервер", buttonSize, buttonFont,
                    e -> {createListeningPortPanel();
                        cardLayout.show(mainPanel, "ListeningPortPanel"); });

            GridBagConstraints constraints = new GridBagConstraints();
            constraints.insets = new Insets(10, 10, 10, 10);

            addComponent(startPanel, encryptButton, constraints, 0, 0, 1, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL);

            mainPanel.add(startPanel,"StartPanel");
        } catch (Exception e) {
            System.err.println("Ошибка при создании вкладки: " + e.getMessage());
        }
    }

    private static void createListeningPortPanel() {
        try {
            JPanel listeningPortPanel = new GradientPanel(new Color(NORTH_COL), new Color(SOUTH_COL));
            listeningPortPanel.setLayout(new GridBagLayout());

            JLabel portLabel = initializeNewLabel("Введите порт сервера:", 32, 0);
            JTextField portField = initializeNewTextField(20, fieldSize);
            JButton startServerButton = initializeNewButton("Запустить сервер", buttonSize, buttonFont,
                    e -> {
                        try {
                            int port = Integer.parseInt(portField.getText());
                            if (port <= 0 || port > 65535) {
                                throw new IllegalArgumentException("Введён некорректный порт.");
                            }

                            ClientServerTest newTest = new ClientServerTest(port);
                            newTest.main(null);

                            createServerConsolePanel();
                            System.out.println(getTimestamp() + "Model.Server is listening at " + port);
                            cardLayout.show(mainPanel, "ServerConsolePanel");
                        } catch (NumberFormatException ex) {
                            JOptionPane.showMessageDialog(null, "Некорректный формат порта.", "Ошибка!", JOptionPane.ERROR_MESSAGE);
                        } catch (IllegalArgumentException ex) {
                            JOptionPane.showMessageDialog(null, ex.getMessage(), "Ошибка!", JOptionPane.ERROR_MESSAGE);
                        }
                    });

            GridBagConstraints constraints = new GridBagConstraints();
            constraints.insets = new Insets(5, 10, 5, 10);

            addComponent(listeningPortPanel, portLabel, constraints, 0, 0, 2, -1, -1);
            addComponent(listeningPortPanel, portField, constraints, 1, 1, -1, -1, -1);
            addComponent(listeningPortPanel, startServerButton, constraints, 0, 2, 2, -1, -1);

            mainPanel.add(listeningPortPanel, "ListeningPortPanel");
        } catch (Exception e) {
            System.err.println("Ошибка при создании вкладки: " + e.getMessage());
        }
    }

    /**
     * Создает панель для отображения журнала действий сервера.
     */
    private static void createServerConsolePanel() {
        try {
            JPanel serverConsolePanel = new GradientPanel(new Color(NORTH_COL), new Color(SOUTH_COL));
            serverConsolePanel.setLayout(new GridBagLayout());

            JLabel consoleLabel = initializeNewLabel("Журнал действий сервера:", 32, 0);
            CustomTextArea consoleTextArea = new CustomTextArea(20, 60);
            JScrollPane scrollPane = new JScrollPane(consoleTextArea);

            JButton stopServerButton = initializeNewButton("Завершить работу сервера", buttonSize, buttonFont,
                    e -> {
                        // Здесь можно добавить логику для завершения работы сервера
                        System.out.println(getTimestamp() + "Model.Server is disabled.");
                    });

            JButton backButton = initializeNewButton("Вернуться назад", buttonSize, buttonFont,
                    e -> {
                        cardLayout.show(mainPanel, "StartPanel"); // Перенаправление на StartPanel
                    });

            GridBagConstraints constraints = new GridBagConstraints();
            constraints.insets = new Insets(5, 10, 5, 10);

            addComponent(serverConsolePanel, consoleLabel, constraints, 0, 0, 2, -1, -1);
            addComponent(serverConsolePanel, scrollPane, constraints, 0, 1, 2, -1, GridBagConstraints.BOTH);

            // Расположение кнопок слева и справа
            addComponent(serverConsolePanel, stopServerButton, constraints, 0, 2, 1, -1, -1);
            addComponent(serverConsolePanel, backButton, constraints, 1, 2, 1, -1, -1);

            // Перенаправление System.out в JTextArea
            System.setOut(new PrintStream(new OutputStream() {
                @Override
                public void write(int b) {
                    consoleTextArea.append(String.valueOf((char) b));
                }
            }));;

            mainPanel.add(serverConsolePanel, "ServerConsolePanel");
        } catch (Exception e) {
            System.err.println("Ошибка при создании вкладки: " + e.getMessage());
        }
    }

    /**
     * Инициализирует новое текстовое поле на заданное количество символов с заданным размером.
     *
     * @param my_columns Ожидаемое количество символов текстового поля.
     * @param my_size Размер текстового поля.
     * @return Инициализированное текстовое поле.
     */
    private static JTextField initializeNewTextField(int my_columns, Dimension my_size) {
        try {
            CustomTextField myField = new CustomTextField(my_columns);
            myField.setPreferredSize(my_size);
            return myField;
        } catch (Exception e) {
            System.err.println("Ошибка при инициализации текстового поля: " + e.getMessage());
            return null;
        }
    }

    /**
     * Инициализирует новую кнопку с заданным текстом, размером, шрифтом и обработчиком событий.
     *
     * @param my_text Текст на кнопке.
     * @param my_size Размер кнопки.
     * @param my_font Шрифт кнопки.
     * @param my_event Обработчик событий для кнопки.
     * @return Инициализированная кнопка.
     */
    private static JButton initializeNewButton(String my_text, Dimension my_size, Font my_font, ActionListener my_event) {
        try {
            JButton myButton = new CustomButton(my_text);
            myButton.setPreferredSize(my_size);
            myButton.setMinimumSize(my_size);
            myButton.setMaximumSize(my_size);
            myButton.setFont(my_font);

            myButton.addActionListener(e -> {
                try {
                    my_event.actionPerformed(e);
                } catch (Exception ex) {
                    System.err.println("Ошибка при обработке действия кнопки: " + ex.getMessage());
                    JOptionPane.showMessageDialog(null, "Произошла ошибка: " + ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
                }
            });

            return myButton;
        } catch (Exception e) {
            System.err.println("Ошибка при инициализации кнопки: " + e.getMessage());
            return null;
        }
    }

    /**
     * Инициализирует новый текстовый элемент с заданным текстом, размером шрифта и флагом стиля (курсив/обычный текст).
     *
     * @param my_text Текст для элемента.
     * @param my_size Размер шрифта.
     * @param my_flag Флаг стиля (0 - курсив и жирный, 1 - обычный).
     * @return Инициализированный текстовый элемент.
     */
    private static JLabel initializeNewLabel(String my_text, int my_size, int my_flag) {
        try {
            JLabel myLabel = new JLabel(my_text);
            if (my_flag == 0)
                myLabel.setFont(new Font("Serif", Font.ITALIC + Font.BOLD, my_size));
            else if (my_flag == 1)
                myLabel.setFont(new Font("Serif", Font.PLAIN, my_size));

            myLabel.setForeground(Color.WHITE);
            return myLabel;
        } catch (Exception e) {
            System.err.println("Ошибка при инициализации текста: " + e.getMessage());
            return null;
        }
    }

    /**
     * Устанавливает ограничения для GridBagConstraints.
     *
     * @param gbc Объект GridBagConstraints.
     * @param my_gridx Координата X в сетке.
     * @param my_gridy Координата Y в сетке.
     * @param my_gridwidth Ширина в сетке.
     * @param my_anchor Привязка компонента.
     * @param my_fill Заполнение компонента.
     */
    private static void setGridConstraints(GridBagConstraints gbc, int my_gridx, int my_gridy, int my_gridwidth, int my_anchor, int my_fill) {
        try {
            if (my_gridx != -1) {
                gbc.gridx = my_gridx;
            }
            if (my_gridy != -1) {
                gbc.gridy = my_gridy;
            }
            if (my_gridwidth != -1) {
                gbc.gridwidth = my_gridwidth;
            }
            if (my_anchor != -1) {
                gbc.anchor = my_anchor;
            }
            if (my_fill != -1) {
                gbc.fill = my_fill;
            }
        } catch (Exception e) {
            System.err.println("Ошибка при установке размеров макета: " + e.getMessage());
        }
    }

    /**
     * Добавляет компонент на панель с заданными ограничениями GridBagConstraints.
     *
     * @param my_panel Панель, на которую добавляется компонент.
     * @param my_component Компонент для добавления.
     * @param gbc Объект GridBagConstraints.
     * @param my_gridx Координата X в сетке.
     * @param my_gridy Координата Y в сетке.
     * @param my_gridwidth Ширина в сетке.
     * @param my_anchor Привязка компонента.
     * @param my_fill Заполнение компонента.
     */
    private static void addComponent(JPanel my_panel, Component my_component, GridBagConstraints gbc,
                                     int my_gridx, int my_gridy, int my_gridwidth, int my_anchor, int my_fill) {
        try {
            if (my_gridx != -1) {
                gbc.gridx = my_gridx;
            }
            if (my_gridy != -1) {
                gbc.gridy = my_gridy;
            }
            if (my_gridwidth != -1) {
                gbc.gridwidth = my_gridwidth;
            }
            if (my_anchor != -1) {
                gbc.anchor = my_anchor;
            }
            if (my_fill != -1) {
                gbc.fill = my_fill;
            }
            my_panel.add(my_component, gbc);
        } catch (Exception e) {
            System.err.println("Ошибка при добавлении компонента: " + e.getMessage());
        }
    }

    /**
     * Генерирует временный отпечаток в формате "yyyy-MM-dd HH:mm:ss".
     *
     * @return Временной отпечаток.
     */
    private static String getTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return "[" + sdf.format(new Date()) + "] ";
    }
}

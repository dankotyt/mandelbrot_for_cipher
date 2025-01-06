# КАК ЗАГРУЗИТЬ JAVAFX, ЧТОБЫ ПРОЕКТ РАБОТАЛ? 🚀

## Шаг 1: Установка JavaFX

1. Скачайте [JavaFX](https://gluonhq.com/products/javafx/) той же версии, что и установленный JDK
   > 💡 **Примечание:** Рекомендуется обновить JDK до последней версии и установить соответствующую версию JavaFX

2. Распакуйте архив на диск C: (рекомендуется рядом с папкой ideaProjects)

## Шаг 2: Настройка в Intellij IDEA

### Добавление зависимостей
1. Перейдите в `File` → `Project Structure` → `Modules` → `Dependencies`
2. Нажмите `+` под Module SDK
3. Выберите `JARs or Directories`
4. Укажите путь к распакованной папке JavaFX
5. Нажмите `Apply` → `OK`

### Настройка конфигурации
1. Перейдите в `Run` → `Edit Configurations` → `+` → `Application`
2. Заполните следующие поля:
   - **Name**: `JavaFX` (или любое другое)
   - **SDK**: Выберите нужную версию
   - **Main class**: Укажите ваш исполняющий класс (я временно использовал `View.JavaFX`, т.к. тестил запуск через него)

3. Добавьте VM options:
   ```bash
   --module-path path\to\javafx-sdk-23.0.1\lib --add-modules javafx.controls,javafx.fxml
   ```
   > ⚠️ **Важно:** Замените `path\to\` на актуальный путь к папке JavaFX на вашем компьютере
4. Сохраняем конфиг: `Apply` -> `OK`
5. Пробуем запустить проект!

Если возникли вопросы, их можно задать в Telegram: `@eximun`

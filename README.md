КАК ЗАГРУЗИТЬ JAVAFX, ЧТОБЫ ПРОЕКТ РАБОТАЛ?

1. Качаем JavaFX такой же версии, как установлен JDK (лучше лишний раз обновить JDK и установить последнюю версию JavaFX): https://gluonhq.com/products/javafx/
2. Распаковываем на диск С, куда-нибудь рядом с ideaProjects
3. Заходим в Intellij Idea
4. Переходим File -> Project Structure -> Modules -> Dependencies. Там нажимаем на "+", который находится под Module SDK
5. Выбираем JARs or Directories
6. Находим распакованную папку с JavaFX
7. Apply -> OK

Далее нужно прописать параметры конфигурации.

1. Переходим в Run -> Edit Configurations -> "+" -> Application
2. Даем название (Например, JavaFX); выбираем нужной версии SDK, если оно не выбрано автоматически; Main class - это ваш исполняющий класс (временно указал View.JavaFX, так как тестчу запуск через него)
3. Далее нужно задать VM options. Если их нет в окошке, то нажимаем на стрелочку возле Modify options и "add VM options"
4. Туда вводим: --module-path path\to\javafx-sdk-23.0.1\lib --add-modules javafx.controls,javafx.fxml (вместо path\to\ - путь, где лежит javafx-sdk-23.0.1\lib)
5. Сохраняем конфиг: Apply -> OK
6. Пробуем запустить проект

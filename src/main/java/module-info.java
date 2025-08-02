module com.cipher {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires javafx.swing;
    requires java.logging;
    requires org.slf4j;

    opens com.cipher to javafx.fxml;
    opens com.cipher.Model to javafx.graphics;
    opens com.cipher.View to javafx.graphics, javafx.fxml;

    exports com.cipher;
    exports com.cipher.Model;
    exports com.cipher.View;
}
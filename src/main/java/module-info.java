module com.cipher {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires javafx.swing;
    requires java.logging;
    requires org.slf4j;

    opens com.cipher.core.encryption to javafx.graphics;
    opens com.cipher.view.javafx to javafx.fxml;

    exports com.cipher.core.encryption;
    exports com.cipher.core.network.client;
    exports com.cipher.core.network.server;
    exports com.cipher.core.utils;
    exports com.cipher.core.threading;
    exports com.cipher.view.interfaces;
    exports com.cipher.view.javafx;
}
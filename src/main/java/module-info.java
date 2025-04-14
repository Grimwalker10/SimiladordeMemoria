module io.test.simuladormemoria {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.kordamp.bootstrapfx.core;

    opens io.test.simuladormemoria to javafx.fxml;
    exports io.test.simuladormemoria;
}
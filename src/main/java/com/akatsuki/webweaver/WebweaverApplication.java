package com.akatsuki.webweaver;

import javafx.application.Application;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WebweaverApplication {

    public static void main(String[] args) {
        // INSTEAD of SpringApplication.run(), we launch the JavaFX window!
        // The JavaFX window will automatically start Spring in its init() method.
        Application.launch(JavaFxApplication.class, args);
    }
}
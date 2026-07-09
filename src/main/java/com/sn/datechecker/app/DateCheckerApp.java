package com.sn.datechecker.app;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

/**
 * Main JavaFX Application for the ServiceNow Date Format Checker.
 */
public class DateCheckerApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        MainView mainView = new MainView(primaryStage);
        Scene scene = new Scene(mainView.getRoot(), 1100, 750);

        // Load CSS
        String css = getClass().getResource("/styles/app.css") != null
                ? getClass().getResource("/styles/app.css").toExternalForm()
                : null;
        if (css != null) {
            scene.getStylesheets().add(css);
        }

        primaryStage.setTitle("ServiceNow Date Format Checker");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

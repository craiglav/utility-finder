package com.utilityfinder.app;

import com.utilityfinder.repository.Database;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        Database.initialize();

        FXMLLoader loader = new FXMLLoader(
                App.class.getResource("/com/utilityfinder/ui/view/main.fxml"));
        Scene scene = new Scene(loader.load(), 1100, 700);
        scene.getStylesheets().add(
                App.class.getResource("/com/utilityfinder/ui/style/app.css").toExternalForm());

        stage.setTitle("Utility Rate Finder");
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

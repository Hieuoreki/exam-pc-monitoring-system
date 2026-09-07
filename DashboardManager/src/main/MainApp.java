package main; // (Hoặc package của bạn)

import javafx.application.Application;
import javafx.fxml.FXMLLoader; // Dùng để tải FXML
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        try {

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/main/ui/LoginView.fxml"));
            Parent root = loader.load();

            Scene scene = new Scene(root);
            
            primaryStage.setTitle("Đăng nhập Admin");
            primaryStage.setScene(scene);
            primaryStage.show();
            
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}
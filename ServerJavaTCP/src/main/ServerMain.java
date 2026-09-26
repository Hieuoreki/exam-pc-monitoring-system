package main;

import java.net.ServerSocket;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import main.core.ClientHandler;

public class ServerMain extends Application {
    private static final int PORT = 9999;

    @Override
    public void start(Stage primaryStage) {
        try {
            // 1. Chạy Socket Server trên luồng nền
            new Thread(this::startSocketServer).start();

            // 2. Nạp giao diện FXML từ package main.ui
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/main/ui/ServerView.fxml"));
            Parent root = loader.load();

            primaryStage.setTitle("MÁY CHỦ QUẢN TRỊ PHÒNG THI (SERVER)");
            primaryStage.setScene(new Scene(root, 1050, 550));
            primaryStage.setOnCloseRequest(e -> System.exit(0));
            primaryStage.show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startSocketServer() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("[SERVER] Đang lắng nghe Socket trên cổng: " + PORT);
            while (true) {
                var s = serverSocket.accept();
                new ClientHandler(s).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
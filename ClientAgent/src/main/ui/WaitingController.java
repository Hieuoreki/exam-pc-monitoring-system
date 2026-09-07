package main.ui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;

public class WaitingController {

    @FXML private Label infoLabel;
    private static Stage stage;

    public void setStudentInfo(String info) {
        if (infoLabel != null) {
            infoLabel.setText(info);
        }
    }

    public static void show(String message) {
        Platform.runLater(() -> {
            try {
                if (stage == null) {
                    FXMLLoader loader = new FXMLLoader(WaitingController.class.getResource("/main/ui/WaitingView.fxml"));
                    Parent root = loader.load();

                    WaitingController controller = loader.getController();
                    controller.setStudentInfo(message);

                    stage = new Stage();
                    stage.setTitle("Trạng thái phòng thi");
                    stage.setScene(new Scene(root));
                    stage.setResizable(false);
                }
                stage.show();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public static void close() {
        Platform.runLater(() -> {
            if (stage != null) {
                stage.close();
                stage = null;
            }
        });
    }
}
package main.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import main.ClientMain;

public class StudentLoginController {

    @FXML private TextField codeField;
    @FXML private TextField nameField;
    @FXML private Label statusLabel;
    @FXML private Button loginButton;

    @FXML
    private void handleLogin() {
        String code = codeField.getText().trim();
        String name = nameField.getText().trim();

        if (code.isEmpty() || name.isEmpty()) {
            statusLabel.setText("Vui lòng điền đủ Mã SV và Họ tên!");
            statusLabel.setStyle("-fx-text-fill: red;");
            return;
        }

        statusLabel.setText("Đang đăng ký vào phòng thi...");
        statusLabel.setStyle("-fx-text-fill: blue;");
        loginButton.setDisable(true);

        // Báo danh trực tiếp với Server TCP
        ClientMain.sendRegisterAgent(code, name);
    }

    public void setStatus(String msg, boolean isError) {
        statusLabel.setText(msg);
        statusLabel.setStyle(isError ? "-fx-text-fill: red;" : "-fx-text-fill: green;");
        loginButton.setDisable(false);
    }
}
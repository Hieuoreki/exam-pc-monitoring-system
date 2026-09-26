package main.ui;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import main.model.JsonMessage;
import main.service.SocketService;
import main.util.SessionManager;

public class LoginController implements Initializable {

    @FXML private TextField ipField;
    @FXML private TextField portField;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label statusLabel;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        ipField.setText("localhost");
        portField.setText("9999");
    }

    @FXML
    private void handleLoginButton() {
        String ip = ipField.getText().trim();
        String portStr = portField.getText().trim();
        String user = usernameField.getText().trim();
        String pass = passwordField.getText().trim();

        if (ip.isEmpty() || portStr.isEmpty() || user.isEmpty() || pass.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Vui lòng nhập đầy đủ thông tin kết nối và tài khoản!");
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.ERROR, "Cổng Port phải là số nguyên!");
            return;
        }

        // 1. Kết nối tới Server Socket
        boolean connected = SocketService.getInstance().connect(ip, port);
        if (!connected) {
            showAlert(Alert.AlertType.ERROR, "Không thể kết nối đến Máy chủ Server tại " + ip + ":" + port);
            return;
        }

        // Đăng ký callback lắng nghe phản hồi từ Server
        SocketService.getInstance().setMessageListener(this::handleServerResponse);

        // 2. Gửi gói tin PROCTOR_LOGIN
        Map<String, Object> payload = new HashMap<>();
        payload.put("username", user);
        payload.put("password", pass);
        SocketService.getInstance().sendMessage(new JsonMessage("PROCTOR_LOGIN", payload));

        if (statusLabel != null) statusLabel.setText("Đang xác thực thông tin...");
    }

    @FXML
    private void handleRegisterButton() {
        String ip = ipField.getText().trim();
        String portStr = portField.getText().trim();

        if (ip.isEmpty() || portStr.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Vui lòng nhập IP và Port của Server trước khi đăng ký!");
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.ERROR, "Port không hợp lệ!");
            return;
        }

        boolean connected = SocketService.getInstance().connect(ip, port);
        if (!connected) {
            showAlert(Alert.AlertType.ERROR, "Không kết nối được tới Server!");
            return;
        }

        Dialog<Map<String, String>> dialog = new Dialog<>();
        dialog.setTitle("Đăng Ký Tài Khoản Giám Thị");
        dialog.setHeaderText("Thông tin sẽ được gửi lên Server chờ Quản trị viên duyệt.");

        ButtonType btnSubmit = new ButtonType("Đăng Ký", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnSubmit, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField tfUser = new TextField();
        PasswordField tfPass = new PasswordField();
        TextField tfName = new TextField();

        grid.add(new Label("Tên đăng nhập:"), 0, 0); grid.add(tfUser, 1, 0);
        grid.add(new Label("Mật khẩu:"), 0, 1);      grid.add(tfPass, 1, 1);
        grid.add(new Label("Họ và tên:"), 0, 2);     grid.add(tfName, 1, 2);

        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(b -> (b == btnSubmit) ? Map.of("u", tfUser.getText().trim(), "p", tfPass.getText().trim(), "n", tfName.getText().trim()) : null);

        Optional<Map<String, String>> res = dialog.showAndWait();
        res.ifPresent(d -> {
            if (d.get("u").isEmpty() || d.get("p").isEmpty() || d.get("n").isEmpty()) {
                showAlert(Alert.AlertType.WARNING, "Không được để trống thông tin đăng ký!");
                return;
            }

            SocketService.getInstance().setMessageListener(this::handleServerResponse);

            Map<String, Object> regData = new HashMap<>();
            regData.put("username", d.get("u"));
            regData.put("password", d.get("p"));
            regData.put("fullName", d.get("n"));
            SocketService.getInstance().sendMessage(new JsonMessage("PROCTOR_REGISTER", regData));
        });
    }

    private void handleServerResponse(JsonMessage msg) {
        if (msg == null || msg.getType() == null) return;

        Platform.runLater(() -> {
            Map<String, Object> payload = msg.getPayload();

            if ("RESP_PROCTOR_LOGIN".equals(msg.getType())) {
                String status = (String) payload.get("status");

                if ("SUCCESS".equals(status)) {
                    SessionManager.loggedInFullName = (String) payload.get("fullName");
                    SessionManager.loggedInUsername = usernameField.getText().trim();
                    openDashboardScreen();
                } else if ("PENDING".equals(status)) {
                    showAlert(Alert.AlertType.INFORMATION, "Tài khoản của bạn đang trong danh sách Chờ Duyệt bởi Server. Vui lòng liên hệ quản trị viên!");
                } else if ("WRONG_PASS".equals(status)) {
                    showAlert(Alert.AlertType.ERROR, "Mật khẩu không chính xác!");
                } else if ("NOT_FOUND".equals(status)) {
                    showAlert(Alert.AlertType.ERROR, "Tài khoản không tồn tại trên hệ thống! Vui lòng đăng ký.");
                } else {
                    showAlert(Alert.AlertType.ERROR, "Đăng nhập thất bại!");
                }
            }

            if ("RESP_PROCTOR_REGISTER".equals(msg.getType())) {
                boolean ok = Boolean.TRUE.equals(payload.get("success"));
                String alertMsg = (String) payload.get("message");
                showAlert(ok ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR, alertMsg);
            }
        });
    }

    private void openDashboardScreen() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/main/ui/DashboardView.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage) usernameField.getScene().getWindow();
            stage.setTitle("BẢNG ĐIỀU KHIỂN GIÁM THỊ - " + SessionManager.loggedInFullName);
            stage.setScene(new Scene(root));
            stage.centerOnScreen();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showAlert(Alert.AlertType type, String content) {
        Alert alert = new Alert(type, content);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
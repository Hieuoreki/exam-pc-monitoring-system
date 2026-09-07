package main.ui;

import java.util.Map;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader; 
import javafx.scene.Parent; 
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage; 
import main.model.JsonMessage;
import main.service.SocketService;

public class LoginController {

    @FXML private TextField ipField;
    @FXML private TextField portField;
    @FXML private Button loginButton;
    @FXML private Label statusLabel;
    
    private SocketService socketService = SocketService.getInstance();

    @FXML
    private void handleLoginButton() {
        String ip = ipField.getText();
        int port;
        try {
             port = Integer.parseInt(portField.getText());
        } catch (NumberFormatException e) {
            statusLabel.setText("Lỗi: Port phải là số!");
            return;
        }
        
        statusLabel.setText("Đang kết nối...");
        
        try {
            socketService.connect(ip, port);
            JsonMessage registerMsg = new JsonMessage("REGISTER_ADMIN", Map.of());
            socketService.sendMessage(registerMsg);
            
            openDashboard();

        } catch (Exception e) {
            statusLabel.setText("Lỗi: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Hàm mới: Mở cửa sổ Dashboard và đóng cửa sổ Login
     */
    private void openDashboard() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/main/ui/DashboardView.fxml"));
            Parent dashboardRoot = loader.load();
            
            Stage dashboardStage = new Stage();
            dashboardStage.setTitle("Admin Dashboard");
            dashboardStage.setScene(new Scene(dashboardRoot));
            
            dashboardStage.show();
            
            Stage loginStage = (Stage) loginButton.getScene().getWindow();
            loginStage.close();
            
        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("Lỗi khi mở Dashboard!");
        }
    }
}
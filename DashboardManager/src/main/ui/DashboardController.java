package main.ui;

import java.util.Map;
import java.util.HashMap;
import java.util.Optional;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.ImageView;
import main.model.JsonMessage;
import main.service.SocketService;
import main.util.SessionManager;

public class DashboardController {

    @FXML private ListView<String> agentListView;
    @FXML private Label selectedAgentLabel;
    @FXML private Button lockButton;
    @FXML private Button unlockButton;
    @FXML private Button getConfigButton;
    @FXML private Button getProcessesButton;
    @FXML private Button takeScreenshotButton;
    @FXML private Button killProcessButton;
    @FXML private Button sendAlertMsgButton;
    
    @FXML private TextArea configTextArea;
    @FXML private ImageView screenshotView;
    @FXML private ListView<String> alertListView;

    private SocketService socketService = SocketService.getInstance();
    private String currentSelectedAgent = null;

    private String extractMachineName(String fullDisplayString) {
        if (fullDisplayString == null) return null;
        if (fullDisplayString.contains(" - ")) {
            return fullDisplayString.split(" - ")[0].trim();
        }
        return fullDisplayString.trim();
    }

    @FXML
    public void initialize() {
        agentListView.setItems(socketService.getAgentList());
        configTextArea.textProperty().bind(socketService.getConfigDataProperty());
        alertListView.setItems(socketService.getAlertList());
        screenshotView.imageProperty().bind(socketService.getScreenshotImageProperty());

        agentListView.getSelectionModel().selectedItemProperty().addListener(
            (observable, oldValue, newValue) -> {
                boolean disabled = (newValue == null);
                currentSelectedAgent = newValue;
                selectedAgentLabel.setText(disabled ? "Chưa chọn Agent nào" : "Đã chọn: " + newValue);

                lockButton.setDisable(disabled);
                unlockButton.setDisable(disabled);
                getConfigButton.setDisable(disabled);
                getProcessesButton.setDisable(disabled);
                takeScreenshotButton.setDisable(disabled);
                killProcessButton.setDisable(disabled);
                sendAlertMsgButton.setDisable(disabled);
            }
        );
    }

    @FXML
    private void handleLockButton() {
        if (currentSelectedAgent != null) {
            String target = extractMachineName(currentSelectedAgent);
            socketService.sendMessage(new JsonMessage("CMD_LOCK_MACHINE", Map.of("targetMachine", target)));
        }
    }

    @FXML
    private void handleUnlockButton() {
        if (currentSelectedAgent != null) {
            String target = extractMachineName(currentSelectedAgent);
            socketService.sendMessage(new JsonMessage("CMD_UNLOCK_MACHINE", Map.of("targetMachine", target)));
        }
    }

    @FXML
    private void handleGetConfigButton() {
        if (currentSelectedAgent != null) {
            String target = extractMachineName(currentSelectedAgent);
            socketService.getConfigDataProperty().set("Đang truy xuất cấu hình từ " + currentSelectedAgent + "...");
            socketService.sendMessage(new JsonMessage("CMD_GET_CONFIG", Map.of("targetMachine", target)));
        }
    }

    @FXML
    private void handleGetProcessesButton() {
        if (currentSelectedAgent != null) {
            String target = extractMachineName(currentSelectedAgent);
            socketService.getConfigDataProperty().set("Đang quét tiến trình từ " + currentSelectedAgent + "...");
            socketService.sendMessage(new JsonMessage("CMD_GET_PROCESSES", Map.of("targetMachine", target)));
        }
    }

    @FXML
    private void handleTakeScreenshotButton() {
        // 1. Kiểm tra máy thi đang chọn
        if (currentSelectedAgent == null || currentSelectedAgent.isEmpty()) {
            Alert alert = new Alert(AlertType.WARNING, "Vui lòng chọn một máy thi trước khi chụp màn hình!");
            alert.show();
            return;
        }

        // 2. Lấy tên máy chuẩn qua hàm extractMachineName sẵn có
        String targetMachine = extractMachineName(currentSelectedAgent);

        // 3. Đóng gói lệnh kèm TÊN GIÁM THỊ lấy từ SessionManager
        Map<String, Object> payload = new HashMap<>();
        payload.put("targetMachine", targetMachine);
        payload.put("proctorName", SessionManager.loggedInFullName);

        // 4. Gửi lệnh qua SocketService
        socketService.sendMessage(new JsonMessage("CMD_TAKE_SCREENSHOT", payload));
    }

    @FXML
    private void handleKillProcessButton() {
        if (currentSelectedAgent == null) return;
        String target = extractMachineName(currentSelectedAgent);

        TextInputDialog dialog = new TextInputDialog("chrome.exe");
        dialog.setTitle("Đóng tiến trình từ xa");
        dialog.setHeaderText("Máy trạm: " + target);
        dialog.setContentText("Nhập tên tiến trình cần tắt (vd: msedge.exe):");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(procName -> {
            if (!procName.trim().isEmpty()) {
                Map<String, Object> payload = Map.of(
                    "targetMachine", target,
                    "processName", procName.trim()
                );
                socketService.sendMessage(new JsonMessage("CMD_KILL_PROCESS", payload));
            }
        });
    }

    @FXML
    private void handleSendAlertMsgButton() {
        if (currentSelectedAgent == null) return;
        String target = extractMachineName(currentSelectedAgent);

        TextInputDialog dialog = new TextInputDialog("Thí sinh chú ý tập trung làm bài, không mở tab lạ!");
        dialog.setTitle("Gửi nhắc nhở thí sinh");
        dialog.setHeaderText("Gửi thông báo tới máy: " + target);
        dialog.setContentText("Nội dung tin nhắn:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(msgText -> {
            if (!msgText.trim().isEmpty()) {
                Map<String, Object> payload = Map.of(
                    "targetMachine", target,
                    "message", msgText.trim()
                );
                socketService.sendMessage(new JsonMessage("CMD_SEND_ALERT_MESSAGE", payload));
            }
        });
    }
}
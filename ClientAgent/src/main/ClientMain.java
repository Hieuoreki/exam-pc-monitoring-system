package main;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.stage.Stage;

import main.model.JsonMessage;
import main.ui.ScreenLocker;
import main.ui.StudentLoginController;
import main.ui.WaitingController;
import main.util.SystemMonitor;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

public class ClientMain extends Application {

    public static final String SERVER_IP = "localhost";
    public static final int SERVER_PORT = 9999;

    private static Gson gson = new Gson();
    private static PrintWriter writer;
    private static Socket socket;

    private static Stage loginStage;
    private static StudentLoginController loginController;

    private static String currentStudentCode = "";
    private static String currentStudentName = "";
    private static String fullMachineId = "";

    // Tập hợp lưu các tiến trình cấm đã gửi cảnh báo để tránh spam log
    private static Set<String> alertedProcesses = new HashSet<>();

    @Override
    public void start(Stage primaryStage) {
        Platform.setImplicitExit(false);
        loginStage = primaryStage;

        new Thread(this::startSocketConnection).start();
        showLoginScreen();
    }

    private void showLoginScreen() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/main/ui/StudentLoginView.fxml"));
            Parent root = loader.load();

            loginController = loader.getController();

            loginStage.setTitle("Điểm danh máy thi");
            loginStage.setScene(new Scene(root));
            loginStage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startSocketConnection() {
        try {
            socket = new Socket(SERVER_IP, SERVER_PORT);
            System.out.println("Đã kết nối tới Server: " + SERVER_IP);

            writer = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            String serverJson;
            while ((serverJson = reader.readLine()) != null) {
                System.out.println(">> [DEBUG] RAW RECV: " + serverJson);
                try {
                    JsonMessage msg = gson.fromJson(serverJson, JsonMessage.class);
                    processMessage(msg);
                } catch (JsonSyntaxException e) {
                    System.err.println("Lỗi JSON: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Platform.runLater(() -> {
                if (loginController != null) {
                    loginController.setStatus("Không kết nối được Server (Port: 9999)!", true);
                }
            });
        }
    }

    /**
     * Báo danh máy thi với Server
     */
    public static void sendRegisterAgent(String code, String name) {
        currentStudentCode = code;
        currentStudentName = name;

        String rawMachineName = System.getenv("COMPUTERNAME");
        if (rawMachineName == null) rawMachineName = "MAY_TEST";

        // Định danh máy nhất quán: có kèm mã SV để chạy thử nhiều client trên 1 máy
        fullMachineId = rawMachineName + (code != null && !code.isEmpty() ? "_" + code : "");

        Map<String, Object> payload = Map.of(
                "machineName", fullMachineId,
                "studentCode", code,
                "studentName", name
        );

        JsonMessage registerMsg = new JsonMessage("REGISTER_AGENT", payload);
        if (writer != null) {
            writer.println(gson.toJson(registerMsg));

            Platform.runLater(() -> {
                if (loginStage != null) loginStage.hide();
                WaitingController.show(name + " (" + code + ")");
            });

            // Bắt đầu luồng giám sát tiến trình vi phạm ngầm
            startProcessMonitor();
        }
    }

    /**
     * Luồng quét tiến trình cấm ngầm mỗi 3 giây (đã chống lặp log)
     */
    private static void startProcessMonitor() {
        Thread monitorThread = new Thread(() -> {
            List<String> blacklist = List.of(
                    "chrome.exe", 
                    "msedge.exe", 
                    "firefox.exe", 
                    "coccoc.exe",
                    "discord.exe", 
                    "zalo.exe", 
                    "telegram.exe",
                    "teamviewer.exe",
                    "ultraviewer.exe"
            );

            while (true) {
                try {
                    // Dùng trực tiếp hàm lấy tiến trình từ SystemMonitor
                    List<String> currentRunning = SystemMonitor.getRunningProcesses();

                    if (currentRunning != null) {
                        for (String blacklisted : blacklist) {
                            boolean isRunning = currentRunning.stream()
                                    .anyMatch(p -> p.equalsIgnoreCase(blacklisted));

                            if (isRunning) {
                                // Chỉ cảnh báo khi tiến trình cấm mới được bật
                                if (!alertedProcesses.contains(blacklisted.toLowerCase())) {
                                    alertedProcesses.add(blacklisted.toLowerCase());

                                    String time = java.time.LocalTime.now()
                                            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));

                                    Map<String, Object> payload = Map.of(
                                            "machineName", fullMachineId,
                                            "processName", blacklisted,
                                            "time", time
                                    );

                                    JsonMessage alertMsg = new JsonMessage("ALERT_PROCESS_VIOLATION", payload);
                                    if (writer != null) {
                                        writer.println(gson.toJson(alertMsg));
                                    }
                                    System.out.println("[CANH BAO] Phát hiện vi phạm: " + blacklisted + " lúc " + time);
                                }
                            } else {
                                // Nếu sinh viên tắt ứng dụng, gỡ khỏi danh sách theo dõi
                                alertedProcesses.remove(blacklisted.toLowerCase());
                            }
                        }
                    }

                    Thread.sleep(3000);

                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    System.err.println("Lỗi luồng giám sát: " + e.getMessage());
                }
            }
        });

        monitorThread.setDaemon(true);
        monitorThread.start();
    }

    /**
     * Xử lý các lệnh từ Server/Giám thị gửi xuống
     */
    private void processMessage(JsonMessage message) {
        if (message == null || message.getType() == null) return;

        Map<String, Object> payload = message.getPayload();

        switch (message.getType()) {
            case "CMD_LOCK_MACHINE":
            case "SERVER_CMD_LOCK":
                Platform.runLater(ScreenLocker::show);
                break;

            case "CMD_UNLOCK_MACHINE":
            case "SERVER_CMD_UNLOCK":
                Platform.runLater(ScreenLocker::hide);
                break;

            case "CMD_GET_CONFIG":
            case "SERVER_CMD_GET_CONFIG":
                Map<String, Object> config = SystemMonitor.getSystemConfig();
                config.put("machineName", fullMachineId);
                sendToServer(new JsonMessage("DATA_CONFIG", config));
                break;

            case "CMD_GET_PROCESSES":
            case "SERVER_CMD_GET_PROCESSES":
                List<String> processes = SystemMonitor.getRunningProcesses();
                Map<String, Object> procPayload = Map.of(
                        "machineName", fullMachineId,
                        "processes", processes
                );
                sendToServer(new JsonMessage("DATA_PROCESS_LIST", procPayload));
                break;

            case "CMD_TAKE_SCREENSHOT":
            case "SERVER_CMD_TAKE_SCREENSHOT":
                new Thread(() -> {
                    String base64Image = SystemMonitor.captureScreenBase64();
                    if (base64Image != null) {
                        Map<String, Object> shotPayload = Map.of(
                                "machineName", fullMachineId,
                                "imageData", base64Image
                        );
                        sendToServer(new JsonMessage("RESP_SCREENSHOT", shotPayload));
                        System.out.println("[CLIENT] Đã chụp ảnh màn hình và gửi về Server.");
                    }
                }).start();
                break;

            case "CMD_KILL_PROCESS":
            case "SERVER_CMD_KILL_PROCESS":
                if (payload != null && payload.get("processName") != null) {
                    String procToKill = (String) payload.get("processName");
                    boolean killed = SystemMonitor.killProcess(procToKill);
                    System.out.println("[CLIENT] Lệnh tắt tiến trình " + procToKill + ": " + (killed ? "THÀNH CÔNG" : "THẤT BẠI"));
                }
                break;

            case "CMD_SEND_ALERT_MESSAGE":
            case "SERVER_CMD_SEND_ALERT_MESSAGE":
                if (payload != null && payload.get("message") != null) {
                    String alertText = (String) payload.get("message");
                    Platform.runLater(() -> {
                        Alert alert = new Alert(AlertType.WARNING);
                        alert.setTitle("CẢNH BÁO TỪ GIÁM THỊ");
                        alert.setHeaderText("Nhắc nhở thí sinh!");
                        alert.setContentText(alertText);
                        Stage alertStage = (Stage) alert.getDialogPane().getScene().getWindow();
                        alertStage.setAlwaysOnTop(true);
                        alert.show();
                    });
                }
                break;

            default:
                System.out.println("Lệnh chưa hỗ trợ: " + message.getType());
        }
    }

    private static void sendToServer(JsonMessage msg) {
        if (writer != null) {
            writer.println(gson.toJson(msg));
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
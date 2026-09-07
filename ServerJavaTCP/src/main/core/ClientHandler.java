package main.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import main.database.DatabaseManager;
import main.model.JsonMessage;

public class ClientHandler extends Thread {

    private Socket clientSocket;
    private PrintWriter writer;
    private BufferedReader reader;

    private Gson gson = new Gson();
    private ConnectionManager manager = ConnectionManager.getInstance();

    private String agentMachineName = null;
    private String studentName = null;
    private String studentCode = null;

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
    }

    public PrintWriter getWriter() {
        return writer;
    }

    public Socket getClientSocket() {
        return clientSocket;
    }

    public String getClientInfo() {
        if (studentName != null && studentCode != null) {
            return agentMachineName + " - " + studentName + " (" + studentCode + ")";
        }
        return agentMachineName != null ? agentMachineName : "Unknown Client";
    }

    @Override
    public void run() {
        try {
            this.writer = new PrintWriter(clientSocket.getOutputStream(), true);
            this.reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

            System.out.println("Đang xử lý client: " + clientSocket.getInetAddress());

            String jsonString;
            while ((jsonString = reader.readLine()) != null) {
                try {
                    JsonMessage message = gson.fromJson(jsonString, JsonMessage.class);
                    processMessage(message);
                } catch (JsonSyntaxException e) {
                    System.err.println("Lỗi cú pháp JSON từ client: " + jsonString);
                }
            }
        } catch (Exception e) {
            System.out.println("Client " + clientSocket.getInetAddress() + " đã ngắt kết nối.");
        } finally {
            manager.removeClient(this);
            try {
                if (reader != null) reader.close();
                if (writer != null) writer.close();
                if (clientSocket != null) clientSocket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void processMessage(JsonMessage message) {
        if (message == null || message.getType() == null) return;

        Map<String, Object> payload = message.getPayload();

        switch (message.getType()) {
            case "REGISTER_AGENT":
                if (payload != null && payload.get("machineName") != null) {
                    this.agentMachineName = (String) payload.get("machineName");
                    this.studentName = (String) payload.get("studentName");
                    this.studentCode = (String) payload.get("studentCode");
                    manager.registerAgent(this.agentMachineName, this);
                }
                break;

            case "REGISTER_ADMIN":
                manager.registerAdmin(this);
                break;

            case "CMD_GET_AGENT_LIST":
                manager.sendAgentListToAdmin(this);
                break;

            case "CMD_LOCK_MACHINE":
            case "CMD_UNLOCK_MACHINE":
            case "CMD_GET_CONFIG":
            case "CMD_GET_PROCESSES":
            case "CMD_TAKE_SCREENSHOT":
            case "CMD_KILL_PROCESS":
            case "CMD_SEND_ALERT_MESSAGE":
                if (payload != null && payload.get("targetMachine") != null) {
                    String target = (String) payload.get("targetMachine");
                    manager.sendCommandToAgent(target, message);
                }
                break;

            case "DATA_CONFIG":
            case "DATA_PROCESS_LIST":
                manager.broadcastToAdmins(message);
                break;

            // XỬ LÝ NHẬN ẢNH CHỤP MÀN HÌNH TỪ CLIENT
            case "RESP_SCREENSHOT":
                if (payload != null && payload.get("imageData") != null) {
                    String base64Image = (String) payload.get("imageData");
                    String machine = (String) payload.get("machineName");
                    saveScreenshotToFileAndDB(machine, base64Image);

                    // Chuyển tiếp ảnh sang Dashboard để Giám thị xem
                    manager.broadcastToAdmins(message);
                }
                break;

            case "ALERT_PROCESS_VIOLATION":
                if (payload != null) {
                    String proc = (String) payload.get("processName");
                    String mName = (String) payload.get("machineName");
                    String time = (String) payload.get("time");
                    
                    DatabaseManager.getInstance().logViolation(mName, studentName, studentCode, proc, time);
                    manager.broadcastToAdmins(message);
                }
                break;

            default:
                System.out.println("Tin nhắn không hỗ trợ: " + message.getType());
        }
    }

    /**
     * Hàm giải mã Base64, ghi file .jpg vào thư mục screenshots và lưu đường dẫn vào SQLite
     */
    private void saveScreenshotToFileAndDB(String machineName, String base64Image) {
        try {
            // 1. Tạo thư mục 'screenshots' trong thư mục gốc project nếu chưa có
            File folder = new File("screenshots");
            if (!folder.exists()) {
                folder.mkdirs();
            }

            // 2. Tạo tên file định dạng: [MãSV]_[ThờiGian].jpg
            String code = (studentCode != null && !studentCode.isEmpty()) ? studentCode : machineName;
            String timeStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = code + "_" + timeStamp + ".jpg";
            File imageFile = new File(folder, fileName);

            // 3. Giải mã Base64 và ghi xuống ổ đĩa
            byte[] imageBytes = Base64.getDecoder().decode(base64Image);
            try (FileOutputStream fos = new FileOutputStream(imageFile)) {
                fos.write(imageBytes);
            }

            String relativePath = "screenshots/" + fileName;
            String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            System.out.println("[SERVER] Đã lưu file ảnh bằng chứng: " + imageFile.getAbsolutePath());

            // 4. Lưu đường dẫn vào SQLite
            DatabaseManager.getInstance().logScreenshot(machineName, studentName, studentCode, relativePath, currentTime);

        } catch (Exception e) {
            System.err.println("[SERVER] Lỗi khi lưu file ảnh chụp: " + e.getMessage());
        }
    }
}
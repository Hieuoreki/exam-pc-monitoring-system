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
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import main.ServerMain; // Dùng để gọi refresh bảng chờ duyệt trên UI Server nếu có
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
    private String proctorFullName = ""; // Tên hiển thị của giám thị nếu kết nối này là Admin

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

    /**
     * Tiện ích gửi JsonMessage an toàn về kết nối này
     */
    public void sendJson(JsonMessage msg) {
        if (writer != null) {
            writer.println(gson.toJson(msg));
        }
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
            // ================= 1. ĐĂNG KÝ / ĐĂNG NHẬP GIÁM THỊ (MỚI) =================
            case "PROCTOR_LOGIN":
                if (payload != null) {
                    String username = (String) payload.get("username");
                    String password = (String) payload.get("password");

                    String result = DatabaseManager.getInstance().checkLogin(username, password);
                    Map<String, Object> resp = new HashMap<>();
                    resp.put("status", result); // "SUCCESS", "PENDING", "WRONG_PASS", "NOT_FOUND"

                    if ("SUCCESS".equals(result)) {
                        this.proctorFullName = DatabaseManager.getInstance().getProctorFullName(username);
                        resp.put("fullName", this.proctorFullName);
                        // Đăng ký kết nối này là Admin vào ConnectionManager
                        manager.registerAdmin(this);
                    }
                    sendJson(new JsonMessage("RESP_PROCTOR_LOGIN", resp));
                }
                break;

            case "PROCTOR_REGISTER":
                if (payload != null) {
                    String username = (String) payload.get("username");
                    String password = (String) payload.get("password");
                    String fullName = (String) payload.get("fullName");

                    boolean ok = DatabaseManager.getInstance().registerProctor(username, password, fullName);
                    Map<String, Object> resp = new HashMap<>();
                    resp.put("success", ok);
                    resp.put("message", ok ? "Đăng ký thành công! Vui lòng chờ phê duyệt từ Server." 
                                           : "Tên đăng nhập đã tồn tại hoặc lỗi!");
                    sendJson(new JsonMessage("RESP_PROCTOR_REGISTER", resp));

                 // Khi có giám thị đăng ký mới từ xa, gọi Controller tự làm mới bảng:
                    if (main.ui.ServerController.getInstance() != null) {
                        main.ui.ServerController.getInstance().refreshProctorTables();
                    }
                }
                break;

            // ================= 2. ĐĂNG KÝ MÁY THI THÍ SINH =================
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

            // ================= 3. ĐIỀU PHỐI CÁC LỆNH =================
            case "CMD_TAKE_SCREENSHOT":
                if (payload != null && payload.get("targetMachine") != null) {
                    String target = (String) payload.get("targetMachine");
                    // Nếu gói tin chưa có tên giám thị, lấy tên của phiên đăng nhập này
                    if (!payload.containsKey("proctorName") && !this.proctorFullName.isEmpty()) {
                        payload.put("proctorName", this.proctorFullName);
                    }
                    manager.sendCommandToAgent(target, message);
                }
                break;

            case "CMD_LOCK_MACHINE":
            case "CMD_UNLOCK_MACHINE":
            case "CMD_GET_CONFIG":
            case "CMD_GET_PROCESSES":
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

            // ================= 4. NHẬN ẢNH CHỤP VÀ LƯU KÈM TÊN GIÁM THỊ =================
            case "RESP_SCREENSHOT":
                if (payload != null && payload.get("imageData") != null) {
                    String base64Image = (String) payload.get("imageData");
                    String machine = (String) payload.get("machineName");
                    String proctor = (String) payload.getOrDefault("proctorName", "Giám thị");

                    // Lưu ảnh ra ổ đĩa và ghi DB kèm tên Giám thị
                    saveScreenshotToFileAndDB(machine, base64Image, proctor);

                    // Chuyển tiếp ảnh sang Dashboard để Giám thị xem
                    manager.broadcastToAdmins(message);
                }
                break;

            // ================= 5. CẢNH BÁO VI PHẠM (CHỈ GHI & BÁO 1 LẦN CHO MỖI ĐỐI TƯỢNG) =================
            case "ALERT_PROCESS_VIOLATION":
                if (payload != null) {
                    String proc = (String) payload.get("processName");
                    String mName = (String) payload.get("machineName");
                    String time = (String) payload.get("time");
                    String sCode = (studentCode != null) ? studentCode : (String) payload.get("studentCode");
                    String sName = (studentName != null) ? studentName : (String) payload.get("studentName");

                    // Hàm logViolation trả về true NẾU ĐÂY LÀ VI PHẠM MỚI (chưa có trong DB)
                    boolean isNewViolation = DatabaseManager.getInstance().logViolation(mName, sName, sCode, proc, time);

                    // CHỈ PHÁT CẢNH BÁO SANG DASHBOARD KHI LÀ VI PHẠM MỚI
                    if (isNewViolation) {
                        System.out.println("[SERVER] Phát hiện vi phạm mới: " + sCode + " mở " + proc);
                        manager.broadcastToAdmins(message);
                    } else {
                        System.out.println("[SERVER] Bỏ qua: Sinh viên " + sCode + " đã bị ghi nhận mở " + proc + " trước đó.");
                    }
                }
                break;

            default:
                System.out.println("Tin nhắn không hỗ trợ: " + message.getType());
        }
    }

    /**
     * Hàm giải mã Base64, ghi file .jpg vào thư mục screenshots và lưu vào SQLite kèm tên Giám thị
     */
    private void saveScreenshotToFileAndDB(String machineName, String base64Image, String proctorName) {
        try {
            // 1. Tạo thư mục 'screenshots' nếu chưa có
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
            System.out.println("[SERVER] Đã lưu file ảnh do [" + proctorName + "] yêu cầu: " + imageFile.getAbsolutePath());

            // 4. Lưu đường dẫn và tên Giám thị vào SQLite
            DatabaseManager.getInstance().logScreenshot(machineName, studentName, studentCode, proctorName, relativePath, currentTime);

        } catch (Exception e) {
            System.err.println("[SERVER] Lỗi khi lưu file ảnh chụp: " + e.getMessage());
        }
    }
}
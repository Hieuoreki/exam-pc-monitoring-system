package main.service;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.image.Image;
import main.model.JsonMessage;

public class SocketService {

    private static SocketService instance;
    private Socket socket;
    private PrintWriter writer;
    private BufferedReader reader;
    private Gson gson = new Gson();

    private ObservableList<String> agentList = FXCollections.observableArrayList();
    private StringProperty configData = new SimpleStringProperty("Chưa có dữ liệu hệ thống...");
    private ObservableList<String> alertList = FXCollections.observableArrayList();
    
    // Property chứa ảnh chụp màn hình nhận từ máy thí sinh
    private ObjectProperty<Image> screenshotImage = new SimpleObjectProperty<>();

    private SocketService() {}

    public static synchronized SocketService getInstance() {
        if (instance == null) {
            instance = new SocketService();
        }
        return instance;
    }

    public ObservableList<String> getAgentList() {
        return agentList;
    }

    public StringProperty getConfigDataProperty() {
        return configData;
    }

    public ObservableList<String> getAlertList() {
        return alertList;
    }

    public ObjectProperty<Image> getScreenshotImageProperty() {
        return screenshotImage;
    }

    public void connect(String ip, int port) throws Exception {
        if (socket != null && socket.isConnected() && !socket.isClosed()) {
            return;
        }

        socket = new Socket(ip, port);
        writer = new PrintWriter(socket.getOutputStream(), true);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        startListening();
        startPolling();
    }

    public void sendMessage(JsonMessage message) {
        if (writer != null) {
            String jsonMsg = gson.toJson(message);
            writer.println(jsonMsg);
        }
    }

    private void startListening() {
        Thread listenerThread = new Thread(() -> {
            try {
                String serverJson;
                while ((serverJson = reader.readLine()) != null) {
                    try {
                        JsonMessage msg = gson.fromJson(serverJson, JsonMessage.class);
                        if (msg == null || msg.getType() == null) continue;

                        switch (msg.getType()) {
                            case "DATA_AGENT_LIST":
                                Object payloadAgents = msg.getPayload().get("agents");
                                updateAgentList(payloadAgents);
                                break;

                            case "DATA_CONFIG":
                                Map<String, Object> configMap = msg.getPayload();
                                StringBuilder sb = new StringBuilder();
                                sb.append("--- Cấu hình máy ---\n");
                                for (Map.Entry<String, Object> entry : configMap.entrySet()) {
                                    sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                                }
                                Platform.runLater(() -> configData.set(sb.toString()));
                                break;

                            case "DATA_PROCESS_LIST":
                                java.lang.reflect.Type type = new TypeToken<List<String>>(){}.getType();
                                List<String> processes = gson.fromJson(gson.toJson(msg.getPayload().get("processes")), type);

                                StringBuilder procSb = new StringBuilder();
                                procSb.append("--- Các tiến trình đang chạy ---\n");
                                if (processes != null) {
                                    for (String process : processes) {
                                        procSb.append(process).append("\n");
                                    }
                                }
                                Platform.runLater(() -> configData.set(procSb.toString()));
                                break;

                            case "ALERT_PROCESS_VIOLATION":
                                Map<String, Object> alertPayload = msg.getPayload();
                                String machine = (String) alertPayload.get("machineName");
                                String process = (String) alertPayload.get("processName");
                                String time = (String) alertPayload.get("time");
                                if (time == null) time = "??:??:??";

                                String alert = String.format("[%s] MÁY %s: Vi phạm mở %s", time, machine, process);
                                Platform.runLater(() -> alertList.add(0, alert));
                                break;

                            case "RESP_SCREENSHOT":
                                String base64Data = (String) msg.getPayload().get("imageData");
                                if (base64Data != null) {
                                    byte[] imgBytes = Base64.getDecoder().decode(base64Data);
                                    ByteArrayInputStream bais = new ByteArrayInputStream(imgBytes);
                                    Image img = new Image(bais);
                                    Platform.runLater(() -> screenshotImage.set(img));
                                }
                                break;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } catch (Exception e) {
                System.out.println("Mất kết nối với Server.");
            }
        });
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    private void startPolling() {
        Thread pollingThread = new Thread(() -> {
            while (true) {
                try {
                    sendMessage(new JsonMessage("CMD_GET_AGENT_LIST", null));
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    break;
                }
            }
        });
        pollingThread.setDaemon(true);
        pollingThread.start();
    }

    private void updateAgentList(Object payload) {
        if (payload instanceof List) {
            java.lang.reflect.Type type = new TypeToken<List<String>>(){}.getType();
            List<String> newNames = gson.fromJson(gson.toJson(payload), type);

            Platform.runLater(() -> {
                if (newNames == null) {
                    agentList.clear();
                    return;
                }

                // 1. Nếu danh sách không có gì thay đổi so với hiện tại -> KHÔNG LÀM GÌ CẢ (giữ nguyên lựa chọn)
                if (agentList.equals(newNames)) {
                    return;
                }

                // 2. Nếu có máy mới hoặc máy thoát ra -> Cập nhật thông minh không làm mất focus máy đang chọn
                // Xóa những máy không còn online
                agentList.removeIf(existing -> !newNames.contains(existing));

                // Thêm những máy mới kết nối vào danh sách
                for (String name : newNames) {
                    if (!agentList.contains(name)) {
                        agentList.add(name);
                    }
                }
            });
        }
    }
}
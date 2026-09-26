package main.service;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

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
    private ObjectProperty<Image> screenshotImage = new SimpleObjectProperty<>();

    // Listener nhận các phản hồi đăng nhập/đăng ký cho LoginController
    private Consumer<JsonMessage> messageListener;

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

    public void setMessageListener(Consumer<JsonMessage> listener) {
        this.messageListener = listener;
    }

    public boolean connect(String ip, int port) {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            socket = new Socket(ip, port);
            writer = new PrintWriter(socket.getOutputStream(), true);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            startListening();
            startPolling();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void sendMessage(JsonMessage message) {
        if (writer != null) {
            String jsonMsg = gson.toJson(message);
            writer.println(jsonMsg);
        }
    }

    // Thêm alias send() để tránh nhầm lẫn giữa send và sendMessage
    public void send(JsonMessage message) {
        sendMessage(message);
    }

    private void startListening() {
        Thread listenerThread = new Thread(() -> {
            try {
                String serverJson;
                while ((serverJson = reader.readLine()) != null) {
                    try {
                        JsonMessage msg = gson.fromJson(serverJson, JsonMessage.class);
                        if (msg == null || msg.getType() == null) continue;

                        // Chuyển tiếp tin nhắn cho listener (như LoginController) xử lý nếu có
                        if (messageListener != null) {
                            messageListener.accept(msg);
                        }

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

                if (agentList.equals(newNames)) {
                    return;
                }

                agentList.removeIf(existing -> !newNames.contains(existing));

                for (String name : newNames) {
                    if (!agentList.contains(name)) {
                        agentList.add(name);
                    }
                }
            });
        }
    }
}
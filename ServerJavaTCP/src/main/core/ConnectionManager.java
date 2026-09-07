package main.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.google.gson.Gson;
import main.model.JsonMessage;

public class ConnectionManager {
    private static ConnectionManager instance;
    private final Map<String, ClientHandler> agentMap = new ConcurrentHashMap<>();
    private final List<ClientHandler> adminList = new CopyOnWriteArrayList<>();
    private final Gson gson = new Gson();

    private ConnectionManager() {}

    public static synchronized ConnectionManager getInstance() {
        if (instance == null) {
            instance = new ConnectionManager();
        }
        return instance;
    }

    public void registerAgent(String machineName, ClientHandler handler) {
        agentMap.put(machineName, handler);
        System.out.println("Đăng ký Agent: " + machineName);
        System.out.println("--------------------------------------");
        broadcastAgentListToAdmins();
    }

    public void registerAdmin(ClientHandler handler) {
        adminList.add(handler);
        System.out.println("Đăng ký Admin: " + handler.getClientSocket().getInetAddress());
        sendAgentListToAdmin(handler);
    }

    public void removeClient(ClientHandler handler) {
        if (adminList.remove(handler)) {
            System.out.println("Admin ngắt kết nối: " + handler.getClientSocket().getInetAddress());
        } else {
            // Tìm và xóa agent theo handler
            agentMap.entrySet().removeIf(entry -> entry.getValue().equals(handler));
            System.out.println("Một Agent đã ngắt kết nối.");
        }
        System.out.println("--------------------------------------");
        broadcastAgentListToAdmins();
    }

    public void sendMessage(ClientHandler handler, JsonMessage message) {
        try {
            String jsonMsg = gson.toJson(message);
            jsonMsg = jsonMsg.replace("\n", " ").replace("\r", "");
            handler.getWriter().println(jsonMsg);
        } catch (Exception e) {
            System.out.println("Lỗi khi gửi tin nhắn cho: " + handler.getClientSocket().getInetAddress());
        }
    }

    public void broadcastToAdmins(JsonMessage message) {
        String jsonMsg = gson.toJson(message).replace("\n", " ").replace("\r", "");
        for (ClientHandler admin : adminList) {
            try {
                admin.getWriter().println(jsonMsg);
            } catch (Exception e) {
                System.out.println("Lỗi broadcast Admin: " + admin.getClientSocket().getInetAddress());
            }
        }
    }

    public void broadcastAgentListToAdmins() {
        List<String> agentDisplayNames = new ArrayList<>();
        for (ClientHandler handler : agentMap.values()) {
            if (handler != null) {
                agentDisplayNames.add(handler.getClientInfo());
            }
        }
        Map<String, Object> payload = Map.of("agents", agentDisplayNames);
        broadcastToAdmins(new JsonMessage("DATA_AGENT_LIST", payload));
    }

    public void sendAgentListToAdmin(ClientHandler admin) {
        List<String> agentDisplayNames = new ArrayList<>();
        for (ClientHandler handler : agentMap.values()) {
            if (handler != null) {
                agentDisplayNames.add(handler.getClientInfo());
            }
        }
        Map<String, Object> payload = Map.of("agents", agentDisplayNames);
        sendMessage(admin, new JsonMessage("DATA_AGENT_LIST", payload));
    }

    public void sendCommandToAgent(String machineName, JsonMessage message) {
        ClientHandler handler = agentMap.get(machineName);
        if (handler != null) {
            sendMessage(handler, message);
            System.out.println("Đã gửi lệnh [" + message.getType() + "] tới máy: " + machineName);
        } else {
            System.out.println("Không tìm thấy Agent [" + machineName + "] để gửi lệnh " + message.getType());
        }
    }
}
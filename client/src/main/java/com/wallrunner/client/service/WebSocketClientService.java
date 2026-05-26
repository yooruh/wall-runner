package com.wallrunner.client.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallrunner.shared.entity.GameState;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/**
 * WebSocket 客户端。
 *
 * 职责：
 * - 与服务器建立 WebSocket 连接。
 * - 处理消息分片（HttpClient.WebSocket 对大 JSON 拆分为多帧）。
 * - 心跳机制（每5秒 ping）。
 * - 单例模式，网络 I/O 与业务逻辑解耦。
 */
public class WebSocketClientService implements IWebSocketClient {

    private static final WebSocketClientService INSTANCE = new WebSocketClientService();
    public static WebSocketClientService getInstance() { return INSTANCE; }

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private WebSocket webSocket;
    private boolean connected = false;

    // 设置项
    private String playerName = "玩家";
    private boolean showFps = true;
    private boolean soundEnabled = true;
    private boolean predictionEnabled = true;
    private boolean showNames = true;
    private long latency = 0;
    private String currentRoomId;
    private String myId;
    private double timeBonusInterval = 5.0;
    private int timeBonusPoints = 10;
    private String fillColor = "";
    private String strokeColor = "";
    private double strokeWidth = 0.6;
    private java.util.Timer heartbeatTimer;

    // 【跨设备联机】服务器地址配置，默认 localhost，可改为局域网 IP
    private String serverAddress = "localhost";
    private int serverPort = 8080;

    // 纯内存 clientId，每个进程独立
    private final String clientId = UUID.randomUUID().toString();

    // 回调
    private Consumer<GameState> onStateReceived;
    private Consumer<Map<String, Object>> onMessage;
    private final List<Map<String, Object>> pendingMessages = new ArrayList<>();

    private WebSocketClientService() {}

    public String getClientId() { return clientId; }

    private Consumer<String> onConnectionError;

    public void setOnConnectionError(Consumer<String> callback) {
        this.onConnectionError = callback;
    }

    public boolean connect(String uri) {
        try {
            webSocket = httpClient.newWebSocketBuilder()
                    .buildAsync(URI.create(uri), new WsListener())
                    .join();
            connected = true;
            System.out.println("[WS Client] Connected, clientId=" + clientId);
            startHeartbeat();
            return true;
        } catch (Exception e) {
            System.err.println("[WS Client] Connect failed: " + e.getMessage());
            connected = false;
            // 触发连接错误回调，通知UI显示错误
            if (onConnectionError != null) {
                onConnectionError.accept("无法连接到服务器 " + uri + "，请检查地址是否正确或服务器是否运行。");
            }
            return false;
        }
    }

    public void disconnect() {
        stopHeartbeat();
        if (webSocket != null) {
            try {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "client leave");
            } catch (Exception ignored) {}
        }
        connected = false;
        myId = null;
        currentRoomId = null;
        pendingMessages.clear();
    }

    /**
     * 加载本地保存的颜色设置
     */
    public void loadSavedColors() {
        java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(com.wallrunner.client.controller.SettingsController.class);
        boolean autoColor = prefs.getBoolean("auto_color", true);
        if (!autoColor) {
            String fill = prefs.get("fill_color", "");
            String stroke = prefs.get("stroke_color", "");
            if (fill != null && !fill.isEmpty()) this.fillColor = fill;
            if (stroke != null && !stroke.isEmpty()) this.strokeColor = stroke;
        }
    }

    public boolean isConnected() { return connected; }

    public void joinDedicated(String name) {
        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("type", "mode_select");
        msg.put("mode", "dedicated");
        msg.put("name", name);
        msg.put("clientId", clientId);
        if (fillColor != null && !fillColor.isEmpty()) {
            msg.put("fillColor", fillColor);
            msg.put("strokeColor", strokeColor);
        }
        send(msg);
    }

    public void createRoom(String name) {
        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("type", "mode_select");
        msg.put("mode", "relay");
        msg.put("role", "create");
        msg.put("name", name);
        msg.put("clientId", clientId);
        if (fillColor != null && !fillColor.isEmpty()) {
            msg.put("fillColor", fillColor);
            msg.put("strokeColor", strokeColor);
        }
        send(msg);
    }

    public void createRoom(String name, String customRoomId) {
        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("type", "mode_select");
        msg.put("mode", "relay");
        msg.put("role", "create");
        msg.put("name", name);
        msg.put("clientId", clientId);
        if (customRoomId != null && !customRoomId.isEmpty()) {
            msg.put("roomId", customRoomId);
        }
        if (fillColor != null && !fillColor.isEmpty()) {
            msg.put("fillColor", fillColor);
            msg.put("strokeColor", strokeColor);
        }
        send(msg);
    }

    public void joinRoom(String roomId, String name) {
        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("type", "mode_select");
        msg.put("mode", "relay");
        msg.put("role", "join");
        msg.put("roomId", roomId);
        msg.put("name", name);
        msg.put("clientId", clientId);
        if (fillColor != null && !fillColor.isEmpty()) {
            msg.put("fillColor", fillColor);
            msg.put("strokeColor", strokeColor);
        }
        send(msg);
    }

    public void sendInput(String action) {
        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("type", "input");
        msg.put("action", action);
        msg.put("playerId", clientId);
        send(msg);
    }

    /**
     * 回复服务器 ping
     */
    public void sendPong() {
        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("type", "pong");
        msg.put("playerId", myId != null ? myId : clientId);
        msg.put("roomId", currentRoomId);
        msg.put("timestamp", System.currentTimeMillis());
        send(msg);
    }

    /**
     * 客户端主动断开（返回主菜单时调用）
     */
    public void sendDisconnect() {
        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("type", "disconnect");
        msg.put("playerId", myId != null ? myId : clientId);
        msg.put("roomId", currentRoomId);
        msg.put("timestamp", System.currentTimeMillis());
        send(msg);
        // 本地断开连接
        disconnect();
    }

    public void sendState(GameState state) {
        try {
            String json = mapper.writeValueAsString(state);
            send(Map.of("type", "state", "payload", json));
        } catch (Exception e) {
            System.err.println("[WS Client] Serialize state failed: " + e.getMessage());
        }
    }

    private void send(Map<String, Object> msg) {
        if (!connected || webSocket == null) return;
        try {
            String json = mapper.writeValueAsString(msg);
            webSocket.sendText(json, true);
        } catch (Exception e) {
            System.err.println("[WS Client] Send failed: " + e.getMessage());
        }
    }

    public void setOnStateReceived(Consumer<GameState> callback) { this.onStateReceived = callback; }

    public void setOnMessage(Consumer<Map<String, Object>> callback) {
        this.onMessage = callback;
        if (callback != null && !pendingMessages.isEmpty()) {
            List<Map<String, Object>> copy = new ArrayList<>(pendingMessages);
            pendingMessages.clear();
            for (Map<String, Object> msg : copy) {
                callback.accept(msg);
            }
        }
    }

    public String getPlayerName() { return playerName; }
    public void setPlayerName(String name) { this.playerName = name != null && !name.isEmpty() ? name : "玩家"; }
    public boolean isShowFps() { return showFps; }
    public void setShowFps(boolean v) { this.showFps = v; }
    public boolean isSoundEnabled() { return soundEnabled; }
    public void setSoundEnabled(boolean v) { this.soundEnabled = v; }
    public boolean isPredictionEnabled() { return predictionEnabled; }
    public void setPredictionEnabled(boolean v) { this.predictionEnabled = v; }
    public boolean isShowNames() { return showNames; }
    public void setShowNames(boolean v) { this.showNames = v; }
    public long getLatency() { return latency; }
    public String getCurrentRoomId() { return currentRoomId; }
    public void setCurrentRoomId(String id) { this.currentRoomId = id; }
    public String getMyId() { return myId; }
    public void setMyId(String id) { this.myId = id; }
    public double getTimeBonusInterval() { return timeBonusInterval; }
    public void setTimeBonusInterval(double v) { this.timeBonusInterval = v > 0 ? v : 5.0; }
    public int getTimeBonusPoints() { return timeBonusPoints; }
    public void setTimeBonusPoints(int v) { this.timeBonusPoints = v >= 0 ? v : 10; }
    public String getFillColor() { return fillColor; }
    public void setFillColor(String v) { this.fillColor = v != null ? v : ""; }
    public String getStrokeColor() { return strokeColor; }
    public void setStrokeColor(String v) { this.strokeColor = v != null ? v : ""; }

    public double getStrokeWidth() { return strokeWidth; }
    public void setStrokeWidth(double v) { this.strokeWidth = v > 0 ? v : 0.6; }

    public String getServerAddress() { return serverAddress; }
    public void setServerAddress(String v) { this.serverAddress = v != null && !v.isEmpty() ? v : "localhost"; }

    public int getServerPort() { return serverPort; }
    public void setServerPort(int v) { this.serverPort = v > 0 && v < 65536 ? v : 8080; }

    /**
     * 获取完整的 WebSocket 连接 URL
     */
    public String getServerUrl() {
        return "ws://" + serverAddress + ":" + serverPort + "/ws/game";
    }

    private void startHeartbeat() {
        stopHeartbeat();
        heartbeatTimer = new java.util.Timer("ws-heartbeat", true);
        heartbeatTimer.scheduleAtFixedRate(new java.util.TimerTask() {
            @Override
            public void run() {
                if (connected && webSocket != null) {
                    try {
                        Map<String, Object> ping = Map.of("type", "ping", "clientId", clientId, "timestamp", System.currentTimeMillis());
                        webSocket.sendText(mapper.writeValueAsString(ping), true);
                    } catch (Exception e) {
                        System.err.println("[WS Client] Heartbeat failed: " + e.getMessage());
                    }
                }
            }
        }, 5000, 5000);
    }

    private void stopHeartbeat() {
        if (heartbeatTimer != null) {
            heartbeatTimer.cancel();
            heartbeatTimer = null;
        }
    }

    private class WsListener implements WebSocket.Listener {
        private final StringBuilder textBuffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            textBuffer.append(data);
            if (last) {
                String fullMessage = textBuffer.toString();
                textBuffer.setLength(0);
                handleMessage(fullMessage);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            System.err.println("[WS Client] WebSocket Error: " + error.getMessage());
            connected = false;
            if (onConnectionError != null) {
                onConnectionError.accept("连接错误: " + error.getMessage());
            }
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            System.out.println("[WS Client] Connection closed: " + statusCode + " " + reason);
            connected = false;
            if (onConnectionError != null && statusCode != WebSocket.NORMAL_CLOSURE) {
                onConnectionError.accept("连接已断开 (" + statusCode + "): " + reason);
            }
            return null;
        }

        private void handleMessage(String fullMessage) {
            try {
                Map<String, Object> msg = mapper.readValue(fullMessage, new TypeReference<>() {});
                String type = (String) msg.get("type");

                if ("room_created".equals(type)) {
                    String roomId = (String) msg.get("roomId");
                    if (roomId != null) {
                        currentRoomId = roomId;
                        myId = clientId;
                    }
                } else if ("joined_room".equals(type)) {
                    String roomId = (String) msg.get("roomId");
                    String playerId = (String) msg.get("playerId");
                    if (roomId != null) currentRoomId = roomId;
                    if (playerId != null) myId = playerId;
                    else myId = clientId;
                } else if ("mode_confirmed".equals(type)) {
                    String playerId = (String) msg.get("playerId");
                    if (playerId != null) myId = playerId;
                    else myId = clientId;
                }

                if ("ping".equals(type)) {
                    // 收到服务器 ping，立即回复 pong
                    sendPong();
                } else if ("state".equals(type) && onStateReceived != null) {
                    Object payload = msg.get("payload");
                    String payloadJson;
                    if (payload instanceof String) {
                        payloadJson = (String) payload;
                    } else {
                        payloadJson = mapper.writeValueAsString(payload);
                    }
                    GameState state = mapper.readValue(payloadJson, GameState.class);
                    onStateReceived.accept(state);
                } else if (onMessage != null) {
                    onMessage.accept(msg);
                } else {
                    if (!"state".equals(type)) {
                        pendingMessages.add(msg);
                        if (pendingMessages.size() > 50) {
                            pendingMessages.remove(0);
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[WS Client] Parse error: " + e.getMessage());
                System.err.println("[WS Client] Raw: " + fullMessage.substring(0, Math.min(200, fullMessage.length())));
            }
        }
    }
}

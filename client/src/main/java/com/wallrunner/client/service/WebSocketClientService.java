package com.wallrunner.client.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallrunner.shared.entity.GameState;
import com.wallrunner.shared.entity.Player;

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
 * 【模块】client / service
 * 【代号】Z
 * 【职责】WebSocket 客户端。与原始网页版 websocket.js 严格对齐。
 * 【协议】支持原版消息类型：mode_select, mode_confirmed, room_created, joined_room,
 *        player_joined, player_left, room_closed, error, state, input。
 * 【原则】单例模式，网络 I/O 与业务逻辑解耦，通过回调通知上层。
 * 【修复】2026-05-10:
 *       1. WebSocket 文本消息分片处理：HttpClient.WebSocket 对大 JSON 会拆分为多帧，
 *          使用 StringBuilder 累积分片，等 last=true 时再完整解析。
 *       2. clientId 改为纯内存 UUID（移除 Preferences 持久化），
 *          同一台机器开多个窗口时各自拥有独立 ID，避免控制同一角色。
 *       3. ObjectMapper 配置 FAIL_ON_UNKNOWN_PROPERTIES = false，增强兼容性。
 *       4. state 消息 payload 兼容 String（Relay）和 Map（Dedicated）两种格式。
 */
public class WebSocketClientService {

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
    // 【新增】自定义角色颜色
    private String fillColor = "";
    private String strokeColor = "";

    // 【修复】纯内存 clientId，每个进程独立，多窗口不冲突
    private final String clientId = UUID.randomUUID().toString();

    // 回调
    private Consumer<GameState> onStateReceived;
    private Consumer<Map<String, Object>> onMessage;

    // 消息缓存：当 onMessage 尚未设置时，缓存非 state 消息
    private final List<Map<String, Object>> pendingMessages = new ArrayList<>();

    private WebSocketClientService() {}

    public String getClientId() { return clientId; }

    public boolean connect(String uri) {
        try {
            webSocket = httpClient.newWebSocketBuilder()
                    .buildAsync(URI.create(uri), new WsListener())
                    .join();
            connected = true;
            System.out.println("[WS Client] Connected, clientId=" + clientId);
            return true;
        } catch (Exception e) {
            System.err.println("[WS Client] Connect failed: " + e.getMessage());
            connected = false;
            return false;
        }
    }

    public void disconnect() {
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

    public boolean isConnected() { return connected; }

    // 协议：mode_select
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

    private class WsListener implements WebSocket.Listener {
        // 【关键修复】累积分片消息，处理大 JSON 被拆分为多帧的情况
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
                textBuffer.setLength(0); // 清空缓冲区
                handleMessage(fullMessage);
            }
            webSocket.request(1);
            return null;
        }

        private void handleMessage(String fullMessage) {
            try {
                Map<String, Object> msg = mapper.readValue(fullMessage, Map.class);
                String type = (String) msg.get("type");

                // 先提取关键状态，即使回调尚未设置也不丢失
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

                if ("state".equals(type) && onStateReceived != null) {
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
                // 【调试】打印前 200 字符帮助定位问题
                System.err.println("[WS Client] Raw: " + fullMessage.substring(0, Math.min(200, fullMessage.length())));
            }
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            System.err.println("[WS Client] Error: " + error.getMessage());
            connected = false;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            connected = false;
            return null;
        }
    }
}

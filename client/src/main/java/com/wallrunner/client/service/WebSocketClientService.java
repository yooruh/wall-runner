package com.wallrunner.client.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallrunner.shared.entity.GameState;
import com.wallrunner.shared.entity.Player;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/**
 * 【模块】client / service
 * 【代号】Z
 * 【职责】WebSocket 客户端。与原始网页版 websocket.js 严格对齐。
 * 【协议】支持原版消息类型：mode_select, mode_confirmed, room_created, joined_room,
 *        player_joined, player_left, room_closed, error, state, input。
 * 【原则】单例模式，网络 I/O 与业务逻辑解耦，通过回调通知上层。
 * 【修复】2026-05-08:
 *       1. connect() 返回 boolean，调用方可感知连接成败。
 *       2. 增加消息缓存队列，避免 GameController 尚未设置回调时丢失房间消息。
 *       3. WsListener 直接提取并保存 roomId / playerId 等关键状态。
 */
public class WebSocketClientService {

    private static final WebSocketClientService INSTANCE = new WebSocketClientService();
    public static WebSocketClientService getInstance() { return INSTANCE; }

    private final ObjectMapper mapper = new ObjectMapper();
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

    // 回调
    private Consumer<GameState> onStateReceived;
    private Consumer<Map<String, Object>> onMessage;

    // 消息缓存：当 onMessage 尚未设置时，缓存非 state 消息
    private final List<Map<String, Object>> pendingMessages = new ArrayList<>();

    private WebSocketClientService() {}

    public boolean connect(String uri) {
        try {
            webSocket = httpClient.newWebSocketBuilder()
                    .buildAsync(URI.create(uri), new WsListener())
                    .join();
            connected = true;
            System.out.println("[WS Client] Connected");
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

    // 原版协议：mode_select
    public void joinDedicated(String name) {
        send(Map.of("type", "mode_select", "mode", "dedicated", "name", name));
    }

    public void createRoom(String name) {
        send(Map.of("type", "mode_select", "mode", "relay", "role", "create", "name", name));
    }

    public void joinRoom(String roomId, String name) {
        send(Map.of("type", "mode_select", "mode", "relay", "role", "join", "roomId", roomId, "name", name));
    }

    public void sendInput(String action) {
        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("type", "input");
        msg.put("action", action);
        if (myId != null) msg.put("playerId", myId);
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
        // 消费缓存的消息
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

    private class WsListener implements WebSocket.Listener {
        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            try {
                Map<String, Object> msg = mapper.readValue(data.toString(), Map.class);
                String type = (String) msg.get("type");

                // 先提取关键状态，即使回调尚未设置也不丢失
                if ("room_created".equals(type)) {
                    String roomId = (String) msg.get("roomId");
                    if (roomId != null) {
                        currentRoomId = roomId;
                        myId = "local";
                    }
                } else if ("joined_room".equals(type)) {
                    String roomId = (String) msg.get("roomId");
                    String playerId = (String) msg.get("playerId");
                    if (roomId != null) currentRoomId = roomId;
                    if (playerId != null) myId = playerId;
                } else if ("mode_confirmed".equals(type)) {
                    String playerId = (String) msg.get("playerId");
                    if (playerId != null) myId = playerId;
                } else if ("error".equals(type)) {
                    // 错误消息也保留，让上层处理
                }

                if ("state".equals(type) && onStateReceived != null) {
                    String payloadJson = mapper.writeValueAsString(msg.get("payload"));
                    GameState state = mapper.readValue(payloadJson, GameState.class);
                    onStateReceived.accept(state);
                } else if (onMessage != null) {
                    onMessage.accept(msg);
                } else {
                    // 回调尚未设置，缓存非 state 消息
                    if (!"state".equals(type)) {
                        pendingMessages.add(msg);
                        // 防止缓存无限增长
                        if (pendingMessages.size() > 50) {
                            pendingMessages.remove(0);
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[WS Client] Parse error: " + e.getMessage());
            }
            webSocket.request(1);
            return null;
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

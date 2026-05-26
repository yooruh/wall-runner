package com.wallrunner.server.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallrunner.shared.entity.GameState;
import com.wallrunner.shared.entity.Player;
import com.wallrunner.shared.physics.GamePhysics;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 公共服务器模式（Dedicated）：服务端运行权威物理，向所有客户端广播 STATE。
 *
 * 原则：物理计算委托给 GamePhysics（Y层），本类仅做调度与网络 I/O（X层）。
 */
@Service
public class DedicatedService implements IDedicatedService {

    private final RoomManager roomManager;
    private final SessionManager sessionManager;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final Map<String, Boolean> activeDedicated = new ConcurrentHashMap<>();
    private static final String MAIN_ROOM_ID = "DEDICATED-MAIN";

    public DedicatedService(RoomManager roomManager, SessionManager sessionManager) {
        this.roomManager = roomManager;
        this.sessionManager = sessionManager;
    }

    public synchronized String getOrCreateRoom() {
        if (!roomManager.isRoomExists(MAIN_ROOM_ID)) {
            roomManager.createRoom(MAIN_ROOM_ID, "SERVER");
            GameState state = roomManager.getRoom(MAIN_ROOM_ID);
            if (state != null) {
                state.setPhase("menu");
            }
            activeDedicated.put(MAIN_ROOM_ID, false);
        }
        return MAIN_ROOM_ID;
    }

    public boolean isRoomActive(String roomId) {
        return Boolean.TRUE.equals(activeDedicated.get(roomId));
    }

    public void join(String roomId, Player player, WebSocketSession session) {
        GameState state = roomManager.getRoom(roomId);
        boolean isLateJoin = isRoomActive(roomId) && state != null && "playing".equals(state.getPhase());
        roomManager.joinRoom(roomId, player);
        sessionManager.bindRoom(session.getId(), roomId);
        if (isLateJoin && state != null) {
            GamePhysics.initJoiningPlayer(state, player);
        }
        if (state != null && state.getPlayers().size() >= 1 && !Boolean.TRUE.equals(activeDedicated.get(roomId))) {
            startGame(roomId);
        }
    }

    public void startGame(String roomId) {
        GameState state = roomManager.getRoom(roomId);
        if (state != null) {
            GamePhysics.startGame(state);
            activeDedicated.put(roomId, true);
        }
    }

    public void handleInput(String roomId, String playerId, String action) {
        GameState state = roomManager.getRoom(roomId);
        if (state == null) return;
        Player p = state.getPlayers().get(playerId);
        if (p == null) return;
        if ("start".equals(action)) {
            if ("menu".equals(state.getPhase()) || "gameover".equals(state.getPhase())) {
                GamePhysics.startGame(state);
                activeDedicated.put(roomId, true);
            }
        } else {
            GamePhysics.handleInput(p, action);
        }
    }

    private final Map<String, Integer> broadcastCounters = new ConcurrentHashMap<>();

    @Scheduled(fixedRate = 8)
    public void tick() {
        for (Map.Entry<String, Boolean> entry : activeDedicated.entrySet()) {
            if (!Boolean.TRUE.equals(entry.getValue())) continue;
            String roomId = entry.getKey();
            GameState state = roomManager.getRoom(roomId);
            if (state == null) continue;
            GamePhysics.update(state);

            // 掉线检测：双重确认机制
            long now = System.currentTimeMillis();
            for (Player p : state.getPlayers().values()) {
                if (p.isDisconnected()) {
                    // 已离线玩家：检查是否超过1分钟，超过则隐藏分数
                    continue;
                }
                if (p.getLastPingTime() <= 0) {
                    p.setLastPingTime(now);
                    continue;
                }
                long elapsed = now - p.getLastPingTime();
                if (elapsed > 8000 && p.isPingAcknowledged()) {
                    // 超过8秒未收到心跳，发送ping请求
                    p.setPingAcknowledged(false);
                    sendPing(roomId, p.getId());
                } else if (elapsed > 15000 && !p.isPingAcknowledged()) {
                    // 超过15秒仍未收到回应，标记为离线
                    p.setDisconnected(true);
                    p.setOfflineTime(now);
                    p.setPaused(true); // 离线玩家视为暂停状态（无碰撞）
                    System.out.println("[Dedicated] Player " + p.getName() + " marked offline");
                }
            }

            int counter = broadcastCounters.getOrDefault(roomId, 0) + 1;
            broadcastCounters.put(roomId, counter);
            if (counter >= 2) {
                broadcastCounters.put(roomId, 0);
                broadcastState(roomId, state);
            }
            if ("gameover".equals(state.getPhase())) {
                activeDedicated.put(roomId, false);
            }
        }
    }

    private void sendPing(String roomId, String playerId) {
        // 找到玩家的 WebSocketSession 并发送 ping
        for (org.springframework.web.socket.WebSocketSession s : sessionManager.getAllSessions().values()) {
            if (roomId.equals(sessionManager.getRoomId(s.getId())) && s.isOpen()) {
                try {
                    java.util.Map<String, Object> ping = new java.util.HashMap<>();
                    ping.put("type", "ping");
                    ping.put("roomId", roomId);
                    ping.put("playerId", playerId);
                    ping.put("timestamp", System.currentTimeMillis());
                    s.sendMessage(new org.springframework.web.socket.TextMessage(objectMapper.writeValueAsString(ping)));
                } catch (Exception e) {
                    System.err.println("[Dedicated] Ping send failed: " + e.getMessage());
                }
                break;
            }
        }
    }

    public GameState getGameState() {
        return roomManager.getRoom(MAIN_ROOM_ID);
    }

    private void broadcastState(String roomId, GameState state) {
        try {
            String payloadJson = objectMapper.writeValueAsString(state);
            Map<String, Object> msg = Map.of("type", "state", "payload", payloadJson);
            String json = objectMapper.writeValueAsString(msg);
            TextMessage tm = new TextMessage(json);
            for (WebSocketSession s : sessionManager.getAllSessions().values()) {
                if (roomId.equals(sessionManager.getRoomId(s.getId())) && s.isOpen()) {
                    try {
                        s.sendMessage(tm);
                    } catch (Exception e) {
                        System.err.println("[DedicatedService] Send to " + s.getId() + " failed: " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[DedicatedService] broadcast failed: " + e.getMessage());
        }
    }
}

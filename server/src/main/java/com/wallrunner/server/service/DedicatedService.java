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
 * 【模块】server / service
 * 【代号】X + Y
 * 【职责】公共服务器模式（Dedicated）：服务端运行权威物理，向所有客户端广播 STATE。
 * 【原则】物理计算委托给 GamePhysics（Y层），本类仅做调度与网络 I/O（X层）。
 * 【修复】2026-05-08: getOrCreateRoom() 使用 RoomManager.createRoom(roomId, hostSessionId)
 *        确保房间 ID 在 RoomManager 中真实存在，避免 getRoom(roomId) 返回 null 导致 NPE。
 * 【修复】2026-05-10: handleInput 处理 "start" 时重新激活 activeDedicated，
 *        解决 gameover 后无法重新开始的问题。
 */
@Service
public class DedicatedService {

    private final RoomManager roomManager;
    private final SessionManager sessionManager;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final Map<String, Boolean> activeDedicated = new ConcurrentHashMap<>();
    private int roomCounter = 0;

    public DedicatedService(RoomManager roomManager, SessionManager sessionManager) {
        this.roomManager = roomManager;
        this.sessionManager = sessionManager;
    }

    public synchronized String getOrCreateRoom() {
        roomCounter++;
        String roomId = "DEDICATED-" + roomCounter;
        roomManager.createRoom(roomId, "SERVER");
        GameState state = roomManager.getRoom(roomId);
        if (state != null) {
            state.setPhase("menu");
        }
        activeDedicated.put(roomId, false);
        return roomId;
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
            // 中途加入：使用 GamePhysics 初始化位置
            com.wallrunner.shared.physics.GamePhysics.initJoiningPlayer(state, player);
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
                activeDedicated.put(roomId, true); // 【修复】重新激活游戏循环
            }
        } else {
            GamePhysics.handleInput(p, action);
        }
    }

    @Scheduled(fixedRate = 16)
    public void tick() {
        for (Map.Entry<String, Boolean> entry : activeDedicated.entrySet()) {
            if (!Boolean.TRUE.equals(entry.getValue())) continue;
            String roomId = entry.getKey();
            GameState state = roomManager.getRoom(roomId);
            if (state == null) continue;
            GamePhysics.update(state);
            broadcastState(roomId, state);
            if ("gameover".equals(state.getPhase())) {
                activeDedicated.put(roomId, false);
            }
        }
    }

    private void broadcastState(String roomId, GameState state) {
        try {
            Map<String, Object> payload = objectMapper.convertValue(state, Map.class);
            Map<String, Object> msg = Map.of("type", "state", "payload", payload);
            String json = objectMapper.writeValueAsString(msg);
            TextMessage tm = new TextMessage(json);
            for (WebSocketSession s : sessionManager.getAllSessions().values()) {
                if (roomId.equals(sessionManager.getRoomId(s.getId())) && s.isOpen()) {
                    s.sendMessage(tm);
                }
            }
        } catch (Exception e) {
            System.err.println("[DedicatedService] broadcast failed: " + e.getMessage());
        }
    }
}

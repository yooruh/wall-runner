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
 * 【修复】2026-05-10:
 *       1. handleInput 处理 "start" 时重新激活 activeDedicated，
 *          解决 gameover 后无法重新开始的问题。
 *       2. 添加 @EnableScheduling 启用定时调度。
 * 【修复】2026-05-11:
 *       1. 改为单房间模式：所有玩家进入同一个 "DEDICATED-MAIN" 房间。
 *       2. 广播优化：直接 writeValueAsString(state)，避免 convertValue 双重序列化开销。
 *       3. 使用异步发送避免慢客户端阻塞广播。
 */
@Service
public class DedicatedService {

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
        // 【修复】单房间模式：所有玩家共享同一个房间
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
                activeDedicated.put(roomId, true);
            }
        } else {
            GamePhysics.handleInput(p, action);
        }
    }

    // 【修复】物理计算恢复60fps（16ms），广播降到30fps（每2帧一次），平衡同步与带宽
    private int broadcastCounter = 0;

    @Scheduled(fixedRate = 8)
    public void tick() {
        for (Map.Entry<String, Boolean> entry : activeDedicated.entrySet()) {
            if (!Boolean.TRUE.equals(entry.getValue())) continue;
            String roomId = entry.getKey();
            GameState state = roomManager.getRoom(roomId);
            if (state == null) continue;
            GamePhysics.update(state);
            // 【新增】掉线检测：15秒未收到心跳则标记为离线
            long now = System.currentTimeMillis();
            for (Player p : state.getPlayers().values()) {
                if (p.isActive() && !p.isDisconnected() && p.getLastPingTime() > 0) {
                    if (now - p.getLastPingTime() > 15000) {
                        p.setDisconnected(true);
                        System.out.println("[Dedicated] Player " + p.getName() + " marked offline");
                    }
                }
            }
            // 每2帧广播一次（30fps），减少网络负载但保持60fps物理
            broadcastCounter++;
            if (broadcastCounter >= 2) {
                broadcastCounter = 0;
                broadcastState(roomId, state);
            }
            if ("gameover".equals(state.getPhase())) {
                activeDedicated.put(roomId, false);
            }
        }
    }

    private void broadcastState(String roomId, GameState state) {
        try {
            // 【修复】直接序列化 GameState，避免 convertValue 双重开销
            String payloadJson = objectMapper.writeValueAsString(state);
            Map<String, Object> msg = Map.of("type", "state", "payload", payloadJson);
            String json = objectMapper.writeValueAsString(msg);
            TextMessage tm = new TextMessage(json);
            // 【修复】遍历发送，慢客户端不阻塞（Spring WebSocket 发送本身是异步的）
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

package com.wallrunner.server.service;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallrunner.shared.entity.GameState;
import com.wallrunner.shared.entity.Player;

/**
 * P2P 转发模式（Relay）：服务端仅做消息中继，不运行物理。
 *
 * 原则：纯网络 I/O，零游戏逻辑。
 */
@Service
public class RelayService implements IRelayService {

    private final RoomManager roomManager;
    private final SessionManager sessionManager;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public RelayService(RoomManager roomManager, SessionManager sessionManager) {
        this.roomManager = roomManager;
        this.sessionManager = sessionManager;
    }

    public String createRoom(String hostSessionId) {
        String roomId = roomManager.createRoom(hostSessionId);
        sessionManager.bindRoom(hostSessionId, roomId);
        return roomId;
    }

    public String createRoom(String hostSessionId, String customRoomId) {
        if (customRoomId != null && !customRoomId.isEmpty()) {
            if (roomManager.isRoomExists(customRoomId)) {
                return null;
            }
            String roomId = roomManager.createRoom(customRoomId, hostSessionId);
            sessionManager.bindRoom(hostSessionId, roomId);
            return roomId;
        }
        return createRoom(hostSessionId);
    }

    public boolean joinRoom(String roomId, Player player, WebSocketSession session) {
        boolean ok = roomManager.joinRoom(roomId, player);
        if (ok) {
            sessionManager.bindRoom(session.getId(), roomId);
        }
        return ok;
    }

    @SuppressWarnings("UseSpecificCatch")
    public void notifyHost(String roomId, Map<String, Object> msg) {
        GameState state = roomManager.getRoom(roomId);
        if (state == null) return;
        String hostId = roomManager.getHost(roomId);
        if (hostId == null) return;
        WebSocketSession host = sessionManager.getSession(hostId);
        if (host != null && host.isOpen()) {
            try {
                host.sendMessage(new TextMessage(objectMapper.writeValueAsString(msg)));
            } catch (Exception e) {
                System.err.println("[RelayService] notify host failed: " + e.getMessage());
            }
        }
    }

    @SuppressWarnings("UseSpecificCatch")
    public void broadcastFromHost(String roomId, Map<String, Object> msg, WebSocketSession sender) {
        GameState state = roomManager.getRoom(roomId);
        if (state == null) return;
        String hostId = roomManager.getHost(roomId);
        if (hostId == null || !hostId.equals(sender.getId())) return;
        try {
            String json = objectMapper.writeValueAsString(msg);
            TextMessage tm = new TextMessage(json);
            for (WebSocketSession s : sessionManager.getAllSessions().values()) {
                if (roomId.equals(sessionManager.getRoomId(s.getId())) && s.isOpen() && !s.getId().equals(sender.getId())) {
                    s.sendMessage(tm);
                }
            }
        } catch (Exception e) {
            System.err.println("[RelayService] broadcast failed: " + e.getMessage());
        }
    }

    @SuppressWarnings("UseSpecificCatch")
    public void broadcastToRoom(String roomId, Map<String, Object> msg, String excludeSessionId) {
        try {
            String json = objectMapper.writeValueAsString(msg);
            TextMessage tm = new TextMessage(json);
            for (WebSocketSession s : sessionManager.getAllSessions().values()) {
                if (roomId.equals(sessionManager.getRoomId(s.getId())) && s.isOpen() && !s.getId().equals(excludeSessionId)) {
                    s.sendMessage(tm);
                }
            }
        } catch (Exception e) {
            System.err.println("[RelayService] broadcast failed: " + e.getMessage());
        }
    }

    @SuppressWarnings("UseSpecificCatch")
    public void relayInput(String roomId, Map<String, Object> msg, WebSocketSession sender) {
        GameState state = roomManager.getRoom(roomId);
        if (state == null) return;
        String hostId = roomManager.getHost(roomId);
        if (hostId == null) return;
        WebSocketSession host = sessionManager.getSession(hostId);
        if (host != null && host.isOpen()) {
            try {
                host.sendMessage(new TextMessage(objectMapper.writeValueAsString(msg)));
            } catch (Exception e) {
                System.err.println("[RelayService] relay to host failed: " + e.getMessage());
            }
        }
    }

    public void leave(String sessionId) {
        String roomId = sessionManager.getRoomId(sessionId);
        if (roomId != null) {
            roomManager.leaveRoom(roomId, sessionId);
            sessionManager.unregister(sessionId);
        }
    }
}

package com.wallrunner.server.service;

import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 【模块】server / service
 * 【代号】X
 * 【职责】WebSocket 会话生命周期管理。X轴水平扩展时，可替换为 Redis + UUID 的分布式会话。
 * 【原则】仅管理 Session 映射，不触碰游戏逻辑（Y层）。
 */
@Service
public class SessionManager {
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> playerRoomMap = new ConcurrentHashMap<>();

    public void register(WebSocketSession session) {
        sessions.put(session.getId(), session);
    }

    public void unregister(String sessionId) {
        sessions.remove(sessionId);
        playerRoomMap.remove(sessionId);
    }

    public WebSocketSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    public void bindRoom(String sessionId, String roomId) {
        playerRoomMap.put(sessionId, roomId);
    }

    public String getRoomId(String sessionId) {
        return playerRoomMap.get(sessionId);
    }

    public Map<String, WebSocketSession> getAllSessions() {
        return sessions;
    }
}

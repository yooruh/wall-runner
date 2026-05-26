package com.wallrunner.server.service;

import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 会话注册表。
 *
 * 职责：管理 sessionId → WebSocketSession 与 roomId 绑定关系。
 * 原则：纯注册表，零业务逻辑。
 */
@Service
public class SessionManager implements ISessionManager {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToRoom = new ConcurrentHashMap<>();

    public void register(WebSocketSession session) {
        sessions.put(session.getId(), session);
    }

    public void unregister(String sessionId) {
        sessions.remove(sessionId);
        sessionToRoom.remove(sessionId);
    }

    public void bindRoom(String sessionId, String roomId) {
        sessionToRoom.put(sessionId, roomId);
    }

    public String getRoomId(String sessionId) {
        return sessionToRoom.get(sessionId);
    }

    public WebSocketSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    public Map<String, WebSocketSession> getAllSessions() {
        return sessions;
    }
}

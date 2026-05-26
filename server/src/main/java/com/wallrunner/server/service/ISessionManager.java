package com.wallrunner.server.service;

import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

/**
 * 会话管理器接口。
 */
public interface ISessionManager {
    void register(WebSocketSession session);
    void unregister(String sessionId);
    void bindRoom(String sessionId, String roomId);
    String getRoomId(String sessionId);
    WebSocketSession getSession(String sessionId);
    Map<String, WebSocketSession> getAllSessions();
    boolean hasSession(String sessionId);
}

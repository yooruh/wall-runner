package com.wallrunner.server.service;

import org.springframework.web.socket.WebSocketSession;

import java.util.Collection;

/**
 * 会话管理器接口。
 */
public interface ISessionManager {
    void register(String sessionId, WebSocketSession session, String roomId);
    void unregister(String sessionId);
    void bindRoom(String sessionId, String roomId);
    String getRoomId(String sessionId);
    WebSocketSession getSession(String sessionId);
    Collection<WebSocketSession> getAllSessions();
    boolean hasSession(String sessionId);
}

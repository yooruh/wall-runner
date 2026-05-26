package com.wallrunner.server.service;

import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

/**
 * P2P 转发服务接口。
 */
public interface IRelayService {
    void createRoom(WebSocketSession session, String clientId, String name, String roomId, String fillColor, String strokeColor);
    void joinRoom(WebSocketSession session, String clientId, String name, String roomId, String fillColor, String strokeColor);
    void broadcastFromHost(WebSocketSession session, Map<String, Object> stateMsg);
    void relayInput(WebSocketSession session, Map<String, Object> inputMsg);
    void leave(WebSocketSession session);
}

package com.wallrunner.server.service;

import com.wallrunner.shared.entity.GameState;
import org.springframework.web.socket.WebSocketSession;

/**
 * 公共服务器服务接口。
 */
public interface IDedicatedService {
    void join(WebSocketSession session, String clientId, String name, String fillColor, String strokeColor);
    void handleInput(WebSocketSession session, String action);
    void tick();
    void startGame(WebSocketSession session);
    boolean isRoomActive(String roomId);
    String getOrCreateRoom(String clientId);
    GameState getGameState();
}

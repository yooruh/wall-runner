package com.wallrunner.server.service;

import com.wallrunner.shared.entity.GameState;
import com.wallrunner.shared.entity.Player;
import org.springframework.web.socket.WebSocketSession;

/**
 * 公共服务器服务接口。
 */
public interface IDedicatedService {
    void join(String roomId, Player player, WebSocketSession session);
    void handleInput(String roomId, String playerId, String action);
    void tick();
    void startGame(String roomId);
    boolean isRoomActive(String roomId);
    String getOrCreateRoom();
    GameState getGameState();
}

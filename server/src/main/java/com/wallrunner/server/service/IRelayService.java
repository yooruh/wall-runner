package com.wallrunner.server.service;

import com.wallrunner.shared.entity.Player;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

/**
 * P2P 转发服务接口。
 */
public interface IRelayService {
    String createRoom(String hostSessionId);
    String createRoom(String hostSessionId, String customRoomId);
    boolean joinRoom(String roomId, Player player, WebSocketSession session);
    void broadcastFromHost(String roomId, Map<String, Object> stateMsg, WebSocketSession sender);
    void relayInput(String roomId, Map<String, Object> inputMsg, WebSocketSession sender);
    void leave(String sessionId);
}

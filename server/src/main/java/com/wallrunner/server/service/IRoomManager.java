package com.wallrunner.server.service;

import com.wallrunner.shared.entity.GameState;
import com.wallrunner.shared.entity.Player;

import java.util.Set;

/**
 * 房间管理器接口。
 */
public interface IRoomManager {
    String createRoom(String hostId);
    String createRoom(String hostId, String customRoomId);
    boolean joinRoom(String roomId, Player player);
    void leaveRoom(String roomId, String playerId);
    void removeRoom(String roomId);
    boolean isRoomExists(String roomId);
    GameState getRoom(String roomId);
    String getHost(String roomId);
    Set<String> getRoomMembers(String roomId);
    GameState getRoomState(String roomId);
    void setRoomState(String roomId, GameState state);
}

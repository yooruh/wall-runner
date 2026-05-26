package com.wallrunner.server.service;

import com.wallrunner.shared.entity.GameState;

import java.util.Set;

/**
 * 房间管理器接口。
 */
public interface IRoomManager {
    String createRoom(String hostId);
    String createRoom(String hostId, String customRoomId);
    boolean joinRoom(String roomId, String guestId);
    void leaveRoom(String playerId);
    void removeRoom(String roomId);
    boolean isRoomExists(String roomId);
    String getRoom(String playerId);
    String getHost(String roomId);
    Set<String> getRoomMembers(String roomId);
    GameState getRoomState(String roomId);
    void setRoomState(String roomId, GameState state);
}

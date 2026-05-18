package com.wallrunner.server.service;

import com.wallrunner.shared.constants.GameConstants;
import com.wallrunner.shared.entity.GameState;
import com.wallrunner.shared.entity.Player;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 房间（房间）生命周期管理。
 *
 * 职责：
 * - 创建/销毁房间，维护房间与房主映射。
 * - 每个房间持有独立 GameState（线程安全由 ConcurrentHashMap 保证）。
 * - 掉线玩家标记为 disconnected，不立即移除，便于断线重连。
 */
@Service
public class RoomManager {

    private final Map<String, GameState> rooms = new ConcurrentHashMap<>();
    private final Map<String, String> roomHosts = new ConcurrentHashMap<>();

    public String createRoom(String hostSessionId) {
        String roomId = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return createRoom(roomId, hostSessionId);
    }

    public String createRoom(String roomId, String hostSessionId) {
        if (rooms.containsKey(roomId)) return null;
        rooms.put(roomId, new GameState());
        roomHosts.put(roomId, hostSessionId);
        return roomId;
    }

    public boolean isRoomExists(String roomId) {
        return rooms.containsKey(roomId);
    }

    public GameState getRoom(String roomId) {
        return rooms.get(roomId);
    }

    public String getHost(String roomId) {
        return roomHosts.get(roomId);
    }

    public boolean joinRoom(String roomId, Player player) {
        GameState state = rooms.get(roomId);
        if (state == null) return false;
        if (player.getId() == null) return false;
        Player existing = state.getPlayers().get(player.getId());
        if (existing != null) {
            // 掉线重连：保留分数、生命值和状态，恢复在线
            existing.setDisconnected(false);
            existing.setOfflineTime(0);
            existing.setLastPingTime(System.currentTimeMillis());
            existing.setPingAcknowledged(true);
            existing.setPaused(false); // 取消暂停状态
            existing.setName(player.getName());
            existing.setFillColor(player.getFillColor());
            existing.setStrokeColor(player.getStrokeColor());
            // 如果未死亡，将其位置改为最落后玩家后方30m
            if (existing.isActive()) {
                double fallbackY = 0;
                for (Player p : state.getPlayers().values()) {
                    if (p.isActive() && p.getY() < fallbackY) {
                        fallbackY = p.getY();
                    }
                }
                double spawnY = fallbackY + 300;
                existing.setY(spawnY);
                existing.setJoinOffsetY(spawnY);
                existing.setCameraY(spawnY - GameConstants.CANVAS_HEIGHT * GameConstants.CAMERA_OFFSET_RATIO);
                existing.setCameraTargetY(spawnY - GameConstants.CANVAS_HEIGHT * GameConstants.CAMERA_OFFSET_RATIO);
            }
            return true;
        }
        state.getPlayers().put(player.getId(), player);
        return true;
    }

    public void leaveRoom(String roomId, String playerId) {
        GameState state = rooms.get(roomId);
        if (state == null) return;
        Player p = state.getPlayers().get(playerId);
        if (p != null) {
            long now = System.currentTimeMillis();
            p.setDisconnected(true);
            p.setOfflineTime(now);
            p.setPaused(true); // 离线玩家视为暂停状态（无碰撞）
        }
        String hostId = roomHosts.get(roomId);
        if (hostId != null && hostId.equals(playerId)) {
            roomHosts.remove(roomId);
            rooms.remove(roomId);
        }
    }

    public void removeRoom(String roomId) {
        rooms.remove(roomId);
        roomHosts.remove(roomId);
    }

    public Map<String, GameState> getAllRooms() {
        return rooms;
    }
}

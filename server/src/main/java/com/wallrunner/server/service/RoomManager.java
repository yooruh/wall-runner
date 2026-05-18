package com.wallrunner.server.service;

import com.wallrunner.shared.entity.GameState;
import com.wallrunner.shared.entity.Player;
import com.wallrunner.shared.physics.GamePhysics;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 【模块】server / service
 * 【代号】X + Y
 * 【职责】房间生命周期管理。每个房间持有独立 GameState（Y层）。
 * 【原则】Z轴数据分区：roomId 为分片键，房间之间零共享状态。
 * 【修复】2026-05-08: 新增 createRoom(String roomId, String hostSessionId)，
 *        支持公共服务器使用自定义房间 ID，避免 getOrCreateRoom() 中 roomId 不匹配导致 NPE。
 * 【修复】2026-05-10:
 *       1. createRoom 初始化 phase 为 "menu"，与房主客户端保持一致。
 * 【修复】2026-05-11:
 *       1. 随机房间号生成改为大写字母+数字组合（6位），避免纯数字房间号。
 */
@Service
public class RoomManager {
    private final Map<String, GameState> rooms = new ConcurrentHashMap<>();
    private final Map<String, String> roomHostMap = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Integer>> leftPlayerScores = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private static final String ROOM_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // 去除易混淆字符 I1O0

    public String createRoom(String hostSessionId) {
        String roomId = generateRoomId();
        return createRoom(roomId, hostSessionId);
    }

    private String generateRoomId() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(ROOM_CHARS.charAt(random.nextInt(ROOM_CHARS.length())));
        }
        return sb.toString();
    }

    public String createRoom(String roomId, String hostSessionId) {
        GameState state = new GameState();
        state.setPhase("menu");
        rooms.put(roomId, state);
        roomHostMap.put(roomId, hostSessionId);
        return roomId;
    }

    public boolean isRoomExists(String roomId) {
        return rooms.containsKey(roomId);
    }

    public GameState getRoom(String roomId) {
        return rooms.get(roomId);
    }

    public void removeRoom(String roomId) {
        rooms.remove(roomId);
        roomHostMap.remove(roomId);
        leftPlayerScores.remove(roomId);
    }

    public boolean joinRoom(String roomId, Player player) {
        GameState state = rooms.get(roomId);
        if (state == null) return false;
        Map<String, Integer> scores = leftPlayerScores.get(roomId);
        if (scores != null && scores.containsKey(player.getId())) {
            int savedScore = scores.get(player.getId());
            player.setScore(savedScore);
            player.setTimeBonusScore(savedScore);
        }
        player.setDisconnected(false);
        state.getPlayers().put(player.getId(), player);
        return true;
    }

    public void leaveRoom(String roomId, String playerId) {
        GameState state = rooms.get(roomId);
        if (state != null) {
            Player p = state.getPlayers().get(playerId);
            if (p != null) {
                p.setDisconnected(true);
                leftPlayerScores.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>()).put(playerId, p.getScore());
            }
            state.getPlayers().remove(playerId);
            if (state.getPlayers().isEmpty()) {
                removeRoom(roomId);
            }
        }
    }

    public String getHost(String roomId) {
        return roomHostMap.get(roomId);
    }

    public void startGame(String roomId) {
        GameState state = rooms.get(roomId);
        if (state != null) {
            GamePhysics.initState(state);
            state.setPhase("playing");
        }
    }

    public Map<String, GameState> getAllRooms() {
        return rooms;
    }
}

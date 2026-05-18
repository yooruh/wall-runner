package com.wallrunner.server.dto;

import java.util.Map;

/**
 * 客户端与服务端之间的统一 JSON 消息契约。
 *
 * 原则：纯 POJO，无业务逻辑，用于 Jackson 序列化。
 */
public class NetworkMessage {
    public enum Type {
        JOIN, CREATE, LEAVE, INPUT, STATE, ERROR, ROOM_INFO
    }

    private Type type;
    private String roomId;
    private String playerId;
    private String playerName;
    private Map<String, Object> payload;
    private long timestamp;

    public NetworkMessage() {
        this.timestamp = System.currentTimeMillis();
    }

    public static NetworkMessage of(Type type, String roomId, Map<String, Object> payload) {
        NetworkMessage msg = new NetworkMessage();
        msg.setType(type);
        msg.setRoomId(roomId);
        msg.setPayload(payload);
        return msg;
    }

    public static NetworkMessage state(String roomId, Map<String, Object> statePayload) {
        return of(Type.STATE, roomId, statePayload);
    }

    public static NetworkMessage error(String message) {
        NetworkMessage msg = new NetworkMessage();
        msg.setType(Type.ERROR);
        msg.setPayload(Map.of("message", message));
        return msg;
    }

    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }
    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }
    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }
    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}

package com.wallrunner.shared.event;

import java.time.Instant;

/**
 * 游戏事件抽象基类。
 * 
 * UML 建模意义：Observer 模式的核心，所有具体事件继承此类。
 * 设计原则：开闭原则（OCP）— 新增事件类型无需修改现有代码。
 */
public abstract class GameEvent {
    private final Instant timestamp;
    private final String sourceId;
    private final EventType type;

    public enum EventType {
        PLAYER_JUMP, PLAYER_DEATH, PLAYER_SPAWN, PLAYER_KNOCKBACK,
        COLLISION_OBSTACLE, COLLISION_PLAYER, COLLECTIBLE_PICKUP,
        PHASE_CHANGE, SCORE_CHANGE, DIFFICULTY_UP, ROOM_JOIN, ROOM_LEAVE,
        NETWORK_STATE, NETWORK_INPUT, NETWORK_PING, NETWORK_ERROR
    }

    protected GameEvent(EventType type, String sourceId) {
        this.type = type;
        this.sourceId = sourceId;
        this.timestamp = Instant.now();
    }

    public EventType getType() { return type; }
    public String getSourceId() { return sourceId; }
    public Instant getTimestamp() { return timestamp; }
}

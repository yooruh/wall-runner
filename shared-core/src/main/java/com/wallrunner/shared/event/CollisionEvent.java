package com.wallrunner.shared.event;

/**
 * 碰撞事件。
 */
public class CollisionEvent extends GameEvent {
    private final String entityAId;
    private final String entityBId;
    private final CollisionType collisionType;

    public enum CollisionType {
        PLAYER_OBSTACLE, PLAYER_PLAYER, PLAYER_COLLECTIBLE, PLAYER_WALL
    }

    public CollisionEvent(String sourceId, String entityAId, String entityBId, CollisionType type) {
        super(EventType.COLLISION_OBSTACLE, sourceId);
        this.entityAId = entityAId;
        this.entityBId = entityBId;
        this.collisionType = type;
    }

    public String getEntityAId() { return entityAId; }
    public String getEntityBId() { return entityBId; }
    public CollisionType getCollisionType() { return collisionType; }
}

package com.wallrunner.shared.event;

/**
 * 玩家跳跃事件。
 * 
 * UML 建模意义：具体事件类，展示继承关系。
 */
public class PlayerJumpEvent extends GameEvent {
    private final double x;
    private final double y;
    private final String side;

    public PlayerJumpEvent(String playerId, double x, double y, String side) {
        super(EventType.PLAYER_JUMP, playerId);
        this.x = x;
        this.y = y;
        this.side = side;
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public String getSide() { return side; }
}

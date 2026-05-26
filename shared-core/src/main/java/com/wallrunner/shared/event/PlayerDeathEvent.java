package com.wallrunner.shared.event;

/**
 * 玩家死亡事件。
 */
public class PlayerDeathEvent extends GameEvent {
    private final int remainingLives;
    private final int finalScore;

    public PlayerDeathEvent(String playerId, int remainingLives, int finalScore) {
        super(EventType.PLAYER_DEATH, playerId);
        this.remainingLives = remainingLives;
        this.finalScore = finalScore;
    }

    public int getRemainingLives() { return remainingLives; }
    public int getFinalScore() { return finalScore; }
}

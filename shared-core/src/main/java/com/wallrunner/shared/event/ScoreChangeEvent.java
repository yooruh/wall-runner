package com.wallrunner.shared.event;

/**
 * 分数变更事件。
 */
public class ScoreChangeEvent extends GameEvent {
    private final int newScore;
    private final int delta;

    public ScoreChangeEvent(String playerId, int newScore, int delta) {
        super(EventType.SCORE_CHANGE, playerId);
        this.newScore = newScore;
        this.delta = delta;
    }

    public int getNewScore() { return newScore; }
    public int getDelta() { return delta; }
}

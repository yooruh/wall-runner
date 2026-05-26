package com.wallrunner.shared.event;

/**
 * 游戏阶段变更事件。
 */
public class PhaseChangeEvent extends GameEvent {
    private final String oldPhase;
    private final String newPhase;

    public PhaseChangeEvent(String sourceId, String oldPhase, String newPhase) {
        super(EventType.PHASE_CHANGE, sourceId);
        this.oldPhase = oldPhase;
        this.newPhase = newPhase;
    }

    public String getOldPhase() { return oldPhase; }
    public String getNewPhase() { return newPhase; }
}

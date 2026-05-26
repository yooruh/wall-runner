package com.wallrunner.shared.physics.subsystem;

import com.wallrunner.shared.entity.GameState;
import com.wallrunner.shared.entity.Player;

import java.util.List;

/**
 * 计分系统实现。
 */
public class ScoreSystem implements IScoreCalculator {

    @Override
    public void recalculateScores(List<Player> activePlayers) {
        for (Player p : activePlayers) {
            int heightScore = (int) ((p.getJoinOffsetY() - p.getY()) / 10.0);
            int total = Math.max(0, heightScore + p.getTimeBonusScore() + p.getCoinsCollected());
            p.setScore(total);
        }
    }

    @Override
    public void applyTimeBonus(GameState state, List<Player> activePlayers) {
        double interval = state.getTimeBonusInterval();
        if (interval <= 0) return;
        state.setTimeBonusAccumulator(state.getTimeBonusAccumulator() + 0.016);
        if (state.getTimeBonusAccumulator() >= interval) {
            int points = state.getTimeBonusPoints();
            for (Player p : activePlayers) {
                if (!p.isPaused()) {
                    p.setTimeBonusScore(p.getTimeBonusScore() + points);
                }
            }
            state.setTimeBonusAccumulator(state.getTimeBonusAccumulator() - interval);
        }
    }
}

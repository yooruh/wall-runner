package com.wallrunner.shared.physics.subsystem;

import com.wallrunner.shared.entity.GameState;
import com.wallrunner.shared.entity.Player;

import java.util.List;

import static com.wallrunner.shared.constants.GameConstants.DIFFICULTY_MAX_LEVEL;

/**
 * 难度管理系统实现。
 */
public class DifficultyManager implements IDifficultyManager {

    @Override
    public void updateDifficulty(GameState state, List<Player> activePlayers) {
        double leadY = activePlayers.stream()
                .mapToDouble(Player::getY)
                .min()
                .orElse(0);
        double heightProgress = Math.max(0, 300 - leadY);
        double nextThreshold = state.getDifficultyLevel() * 500.0;
        if (heightProgress > nextThreshold && state.getDifficultyLevel() < DIFFICULTY_MAX_LEVEL) {
            state.setDifficultyLevel(state.getDifficultyLevel() + 1);
        }
    }
}

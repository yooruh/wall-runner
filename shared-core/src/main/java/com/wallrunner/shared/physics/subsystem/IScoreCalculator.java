package com.wallrunner.shared.physics.subsystem;

import com.wallrunner.shared.entity.Player;
import com.wallrunner.shared.entity.GameState;
import java.util.List;

/**
 * 计分系统接口。
 * 
 * UML 建模意义：展示分数计算/时间奖励/高度奖励的抽象。
 */
public interface IScoreCalculator {
    void recalculateScores(List<Player> activePlayers);
    void applyTimeBonus(GameState state, List<Player> activePlayers);
}

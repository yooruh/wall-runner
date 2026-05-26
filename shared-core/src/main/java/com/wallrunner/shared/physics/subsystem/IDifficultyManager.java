package com.wallrunner.shared.physics.subsystem;

import com.wallrunner.shared.entity.GameState;
import com.wallrunner.shared.entity.Player;
import java.util.List;

/**
 * 难度管理系统接口。
 * 
 * UML 建模意义：展示难度递增/阈值判定/参数调整的抽象。
 */
public interface IDifficultyManager {
    void updateDifficulty(GameState state, List<Player> activePlayers);
}

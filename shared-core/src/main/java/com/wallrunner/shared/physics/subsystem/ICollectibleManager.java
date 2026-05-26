package com.wallrunner.shared.physics.subsystem;

import com.wallrunner.shared.entity.GameState;
import com.wallrunner.shared.entity.Player;
import java.util.List;

/**
 * 收集物管理系统接口。
 * 
 * UML 建模意义：展示收集物生成/碰撞/效果触发的抽象。
 */
public interface ICollectibleManager {
    void updateCollectibles(GameState state, List<Player> activePlayers);
}

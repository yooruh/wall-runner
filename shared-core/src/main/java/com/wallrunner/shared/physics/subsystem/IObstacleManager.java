package com.wallrunner.shared.physics.subsystem;

import com.wallrunner.shared.entity.GameState;
import com.wallrunner.shared.entity.Player;
import java.util.List;

/**
 * 障碍物管理系统接口。
 * 
 * UML 建模意义：展示障碍物生成/回收/移动的抽象。
 */
public interface IObstacleManager {
    void updateObstacles(GameState state, List<Player> activePlayers);
    void checkSpawn(GameState state);
}

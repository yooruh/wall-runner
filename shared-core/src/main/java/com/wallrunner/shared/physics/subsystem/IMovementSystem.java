package com.wallrunner.shared.physics.subsystem;

import com.wallrunner.shared.entity.Player;
import com.wallrunner.shared.entity.Obstacle;
import java.util.List;

/**
 * 移动系统接口。
 * 
 * UML 建模意义：展示玩家移动职责的抽象。
 */
public interface IMovementSystem {
    boolean updatePlayerMovement(Player player, List<Obstacle> obstacles, List<Player> activePlayers);
}

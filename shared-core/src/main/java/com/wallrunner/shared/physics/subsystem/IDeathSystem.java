package com.wallrunner.shared.physics.subsystem;

import com.wallrunner.shared.entity.GameState;
import com.wallrunner.shared.entity.Player;

/**
 * 死亡系统接口。
 * 
 * UML 建模意义：展示死亡判定/重生/生命管理的抽象。
 */
public interface IDeathSystem {
    void checkDeathLine(GameState state);
    void applyDeath(GameState state, Player player);
    void checkAllDead(GameState state);
}

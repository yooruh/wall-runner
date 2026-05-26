package com.wallrunner.shared.physics;

import com.wallrunner.shared.entity.GameState;
import com.wallrunner.shared.entity.Player;

/**
 * 物理引擎接口。
 * 
 * UML 建模意义：策略模式的核心接口，便于展示不同物理实现的可替换性。
 * 设计原则：依赖倒置（DIP）、接口隔离（ISP）。
 */
public interface IPhysicsEngine {
    void initState(GameState state);
    void update(GameState state);
    void handleInput(Player player, String inputType);
    void startGame(GameState state);
    void initJoiningPlayer(GameState state, Player player);
}

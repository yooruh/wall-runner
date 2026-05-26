package com.wallrunner.client.engine;

import com.wallrunner.shared.entity.GameState;
import com.wallrunner.shared.entity.Player;

/**
 * 本地物理引擎接口。
 * 
 * UML 建模意义：展示客户端本地物理计算的抽象。
 */
public interface ILocalPhysicsEngine {
    void tick(GameState state);
    void handleInput(GameState state, String playerId, String inputType);
    void handleInput(Player player, String inputType);
    void startGame(GameState state);
    void initState(GameState state);
}

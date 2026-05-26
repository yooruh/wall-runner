package com.wallrunner.client.engine;

import com.wallrunner.shared.entity.GameState;
import com.wallrunner.shared.entity.Player;
import com.wallrunner.shared.physics.GamePhysics;

/**
 * 本地权威物理引擎封装。
 *
 * 职责：委托给共享核心物理引擎，保持客户端与服务端逻辑一致。
 * 
 * UML 建模意义：ILocalPhysicsEngine 的具体实现，展示委托模式。
 */
public class LocalPhysicsEngine implements ILocalPhysicsEngine {

    @Override
    public void tick(GameState state) {
        GamePhysics.update(state);
    }

    @Override
    public void handleInput(GameState state, String playerId, String inputType) {
        if (state.getPlayers().get(playerId) != null) {
            GamePhysics.handleInput(state.getPlayers().get(playerId), inputType);
        }
    }

    @Override
    public void handleInput(Player player, String inputType) {
        GamePhysics.handleInput(player, inputType);
    }

    @Override
    public void startGame(GameState state) {
        GamePhysics.startGame(state);
    }

    @Override
    public void initState(GameState state) {
        GamePhysics.initState(state);
    }
}

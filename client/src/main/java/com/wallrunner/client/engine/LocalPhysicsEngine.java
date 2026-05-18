package com.wallrunner.client.engine;

import com.wallrunner.shared.entity.GameState;
import com.wallrunner.shared.physics.GamePhysics;

/**
 * 【模块】client / engine
 * 【代号】Y
 * 【职责】本地权威物理引擎封装。
 */
public class LocalPhysicsEngine {

    public void tick(GameState state) {
        GamePhysics.update(state);
    }

    public void handleInput(GameState state, String playerId, String inputType) {
        if (state.getPlayers().get(playerId) != null) {
            GamePhysics.handleInput(state.getPlayers().get(playerId), inputType);
        }
    }
}
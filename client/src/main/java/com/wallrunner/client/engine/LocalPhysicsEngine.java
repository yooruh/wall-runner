package com.wallrunner.client.engine;

import com.wallrunner.shared.entity.GameState;
import com.wallrunner.shared.physics.GamePhysics;

/**
 * 本地权威物理引擎封装。
 *
 * 职责：委托给共享核心物理引擎，保持客户端与服务端逻辑一致。
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

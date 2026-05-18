package com.wallrunner.client.engine;

import com.wallrunner.shared.entity.GameState;
import com.wallrunner.shared.entity.Player;
import com.wallrunner.shared.physics.GamePhysics;

import static com.wallrunner.shared.constants.GameConstants.GRAVITY;

/**
 * 本地预测器：在 Dedicated 模式下，提前模拟玩家输入以降低延迟感。
 *
 * 原则：预测逻辑与权威物理同源（均调用 GamePhysics），确保一致性。
 * 注意：预测器仅操作玩家实体位置与速度，不操作摄像机。
 */
public class Predictor {

    public void predict(GameState state, String playerId, String inputType) {
        Player p = state.getPlayers().get(playerId);
        if (p == null) return;
        GamePhysics.handleInput(p, inputType);
        p.setVy(p.getVy() + GRAVITY);
        p.setY(p.getY() + p.getVy());
    }
}

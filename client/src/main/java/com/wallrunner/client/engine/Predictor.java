package com.wallrunner.client.engine;

import com.wallrunner.shared.entity.GameState;
import com.wallrunner.shared.entity.Player;
import com.wallrunner.shared.physics.GamePhysics;

/**
 * 【模块】client / engine
 * 【代号】Y
 * 【职责】本地预测器：在 Dedicated 模式下，提前模拟玩家输入以降低延迟感。
 * 【原则】预测逻辑与权威物理同源（均调用 GamePhysics），确保一致性。
 * 【注意】预测器仅操作玩家实体位置与速度，不操作摄像机（摄像机由权威物理统一驱动）。
 */
public class Predictor {

    public void predict(GameState state, String playerId, String inputType) {
        Player p = state.getPlayers().get(playerId);
        if (p == null) return;
        // 立即应用输入到本地状态
        GamePhysics.handleInput(p, inputType);
        // 额外推进一帧物理，模拟服务器下一 tick
        // 注意：不调用 full update 以避免重复处理障碍物/摄像机
        p.setVy(p.getVy() + com.wallrunner.shared.constants.GameConstants.GRAVITY);
        p.setY(p.getY() + p.getVy());
    }
}

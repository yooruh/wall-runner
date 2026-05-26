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
 * 
 * UML 建模意义：IPredictor 的具体实现。
 */
public class Predictor implements IPredictor {

    @Override
    public void predict(GameState state, String playerId, String inputType) {
        Player p = state.getPlayers().get(playerId);
        if (p == null) return;
        GamePhysics.handleInput(p, inputType);
        p.setVy(p.getVy() + GRAVITY);
        p.setY(p.getY() + p.getVy());
    }

    @Override
    public GameState predict(GameState current, Player localPlayer, String inputType) {
        GameState predicted = deepClone(current);
        Player p = predicted.getPlayers().get(localPlayer.getId());
        if (p == null) return predicted;
        GamePhysics.handleInput(p, inputType);
        p.setVy(p.getVy() + GRAVITY);
        p.setY(p.getY() + p.getVy());
        return predicted;
    }

    @Override
    public void reconcile(GameState predicted, GameState authoritative) {
        // 预留：客户端预测与权威状态的调和算法
        // 当前版本：直接覆盖为权威状态（保守策略）
    }

    private GameState deepClone(GameState original) {
        // 简化深拷贝：使用序列化或手动复制
        // 实际项目中应使用更高效的克隆方式
        GameState clone = new GameState();
        clone.setPlayers(new java.util.HashMap<>(original.getPlayers()));
        clone.setObstacles(new java.util.ArrayList<>(original.getObstacles()));
        clone.setPhase(original.getPhase());
        clone.setFrames(original.getFrames());
        clone.setCameraY(original.getCameraY());
        clone.setCameraTargetY(original.getCameraTargetY());
        return clone;
    }
}

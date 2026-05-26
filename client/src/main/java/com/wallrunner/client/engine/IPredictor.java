package com.wallrunner.client.engine;

import com.wallrunner.shared.entity.GameState;
import com.wallrunner.shared.entity.Player;

/**
 * 预测器接口。
 * 
 * UML 建模意义：展示客户端预测算法的抽象。
 */
public interface IPredictor {
    void predict(GameState state, String playerId, String inputType);
    GameState predict(GameState current, Player localPlayer, String inputType);
    void reconcile(GameState predicted, GameState authoritative);
}

package com.wallrunner.shared.physics;

import com.wallrunner.shared.entity.GameState;
import com.wallrunner.shared.entity.Player;
import com.wallrunner.shared.event.GameEventBus;

/**
 * 物理引擎兼容门面（Facade 模式）。
 * 
 * UML 建模意义：展示外观模式，为旧调用方提供统一入口。
 * 设计原则：本类不做计算，全部委托给 PhysicsEngine，确保功能零损失。
 * 
 * @deprecated 请直接使用 {@link PhysicsEngine} 以获得完整接口隔离优势。
 */
public class GamePhysics {
    private GamePhysics() {}

    private static final IPhysicsEngine ENGINE = PhysicsEngine.createDefault(GameEventBus.getInstance());

    public static void initState(GameState state) {
        ENGINE.initState(state);
    }

    public static void update(GameState state) {
        ENGINE.update(state);
    }

    public static void handleInput(Player player, String inputType) {
        ENGINE.handleInput(player, inputType);
    }

    public static void startGame(GameState state) {
        ENGINE.startGame(state);
    }

    public static void initJoiningPlayer(GameState state, Player player) {
        ENGINE.initJoiningPlayer(state, player);
    }
}

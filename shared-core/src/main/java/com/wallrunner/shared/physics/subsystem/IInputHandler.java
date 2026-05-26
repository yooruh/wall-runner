package com.wallrunner.shared.physics.subsystem;

import com.wallrunner.shared.entity.Player;

/**
 * 输入处理器接口。
 * 
 * UML 建模意义：展示输入映射到游戏行为的抽象。
 */
public interface IInputHandler {
    void handleInput(Player player, String inputType);
}

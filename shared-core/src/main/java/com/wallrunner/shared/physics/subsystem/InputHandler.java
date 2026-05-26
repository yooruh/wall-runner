package com.wallrunner.shared.physics.subsystem;

import com.wallrunner.shared.entity.Player;

import static com.wallrunner.shared.constants.GameConstants.JUMP_VY;

/**
 * 输入处理器实现。
 * 
 * UML 建模意义：IInputHandler 的具体实现，展示命令模式雏形。
 */
public class InputHandler implements IInputHandler {

    @Override
    public void handleInput(Player player, String inputType) {
        if (!player.isActive() || player.isPaused()) return;
        if ("jump".equals(inputType) && !player.isJumping()) {
            player.setVy(JUMP_VY);
            player.setJumping(true);
        }
    }
}

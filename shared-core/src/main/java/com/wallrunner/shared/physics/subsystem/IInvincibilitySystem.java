package com.wallrunner.shared.physics.subsystem;

import com.wallrunner.shared.entity.Player;

/**
 * 无敌系统接口。
 * 
 * UML 建模意义：展示无敌计时/特效管理的抽象。
 */
public interface IInvincibilitySystem {
    void processInvincibility(Player player);
}

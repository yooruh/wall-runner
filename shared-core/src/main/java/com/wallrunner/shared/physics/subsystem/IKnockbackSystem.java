package com.wallrunner.shared.physics.subsystem;

import com.wallrunner.shared.entity.Player;

/**
 * 击退系统接口。
 * 
 * UML 建模意义：展示击退动画/回归/旋转的抽象。
 */
public interface IKnockbackSystem {
    void processKnockback(Player player);
    void applyKnockback(Player victim, String attackerSide);
}

package com.wallrunner.shared.physics.subsystem;

import com.wallrunner.shared.entity.Player;

/**
 * 玩家间碰撞解析器接口。
 * 
 * UML 建模意义：展示玩家碰撞策略（击退/弹开/穿过）的抽象。
 */
public interface IPlayerCollisionResolver {
    boolean checkPlayerCollision(Player a, Player b);
    void resolvePlayerCollision(Player a, Player b);
}

package com.wallrunner.shared.physics.subsystem;

import com.wallrunner.shared.entity.Player;
import com.wallrunner.shared.entity.Obstacle;
import java.util.List;

/**
 * 碰撞检测系统接口。
 * 
 * UML 建模意义：展示碰撞检测职责的抽象，可与不同碰撞算法实现绑定。
 */
public interface ICollisionDetector {
    boolean checkPlayerObstacleCollision(Player player, List<Obstacle> obstacles);
    boolean checkPlayerPlayerCollision(Player a, Player b);
    boolean rectIntersect(double x1, double y1, double w1, double h1,
                          double x2, double y2, double w2, double h2);
}

package com.wallrunner.shared.physics.subsystem;

import com.wallrunner.shared.entity.Obstacle;
import com.wallrunner.shared.entity.Player;

import java.util.List;

/**
 * AABB 碰撞检测系统实现。
 * 
 * UML 建模意义：ICollisionDetector 的具体实现，展示实现关系。
 * 设计原则：单一职责（SRP）— 仅负责碰撞检测，无响应逻辑。
 */
public class CollisionSystem implements ICollisionDetector {

    @Override
    public boolean checkPlayerObstacleCollision(Player player, List<Obstacle> obstacles) {
        for (Obstacle obs : obstacles) {
            if (rectIntersect(player.getX(), player.getY(), player.getWidth(), player.getHeight(),
                    obs.getX(), obs.getY(), obs.getWidth(), obs.getHeight())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean checkPlayerPlayerCollision(Player a, Player b) {
        return rectIntersect(a.getX(), a.getY(), a.getWidth(), a.getHeight(),
                b.getX(), b.getY(), b.getWidth(), b.getHeight());
    }

    @Override
    public boolean rectIntersect(double x1, double y1, double w1, double h1,
                                 double x2, double y2, double w2, double h2) {
        return x1 < x2 + w2 && x1 + w1 > x2 && y1 < y2 + h2 && y1 + h1 > y2;
    }
}

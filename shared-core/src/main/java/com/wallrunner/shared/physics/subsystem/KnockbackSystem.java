package com.wallrunner.shared.physics.subsystem;

import com.wallrunner.shared.entity.Player;

import static com.wallrunner.shared.constants.GameConstants.*;

/**
 * 击退系统实现。
 * 
 * UML 建模意义：IKnockbackSystem 的具体实现，展示状态机行为。
 */
public class KnockbackSystem implements IKnockbackSystem {

    @Override
    public void processKnockback(Player p) {
        if (!p.isKnockedBack()) return;
        p.setKnockbackTimer(p.getKnockbackTimer() - 0.016);

        if (p.getKnockbackTimer() > KNOCKBACK_RETURN_DELAY) {
            p.setVy(p.getVy() + KNOCKBACK_GRAVITY);
            p.setY(p.getY() + p.getVy());
            p.setReturningToWall(false);
        } else if (p.getKnockbackTimer() > 0) {
            p.setReturningToWall(true);
            p.setVy(p.getVy() + KNOCKBACK_GRAVITY);
            p.setY(p.getY() + p.getVy());
            boolean onLeft = "left".equals(p.getSide());
            double targetX = onLeft ? WALL_WIDTH + 5 : CANVAS_WIDTH - WALL_WIDTH - PLAYER_SIZE - 5;
            double dx = targetX - p.getX();
            double returnSpeed = Math.min(Math.abs(dx) * 0.08, KNOCKBACK_RETURN_SPEED);
            if (Math.abs(dx) > 2.0) {
                p.setX(p.getX() + Math.signum(dx) * returnSpeed);
            } else {
                p.setX(targetX);
            }
        }

        double currentRot = p.getRotationAngle();
        double targetRot = p.getTargetRotation();
        double diff = targetRot - currentRot;
        if (Math.abs(diff) > 0.5) {
            p.setRotationAngle(currentRot + Math.signum(diff) * KNOCKBACK_ROTATION_SPEED);
        }
        if (p.isReturningToWall() && Math.abs(p.getRotationAngle()) > 0.5) {
            p.setRotationAngle(p.getRotationAngle() * 0.92);
        }

        boolean backToWall = ("left".equals(p.getSide()) && p.getX() <= WALL_WIDTH + 8)
                || ("right".equals(p.getSide()) && p.getX() >= CANVAS_WIDTH - WALL_WIDTH - PLAYER_SIZE - 8);
        if ((backToWall && p.isReturningToWall()) || p.getKnockbackTimer() <= 0) {
            endKnockback(p);
        }
    }

    private void endKnockback(Player p) {
        p.setKnockedBack(false);
        p.setReturningToWall(false);
        p.setKnockbackTimer(0);
        p.setRotationAngle(0);
        p.setTargetRotation(0);
        p.setVy(0);
        boolean onLeft = "left".equals(p.getSide());
        p.setX(onLeft ? WALL_WIDTH + 5 : CANVAS_WIDTH - WALL_WIDTH - PLAYER_SIZE - 5);
    }

    @Override
    public void applyKnockback(Player victim, String attackerSide) {
        victim.setInvincible(true);
        victim.setInvincibleTimer(2.5);
        victim.setKnockedBack(true);
        victim.setReturningToWall(false);
        victim.setKnockbackTimer(KNOCKBACK_DURATION);

        boolean pushToLeft = "right".equals(attackerSide);
        double pushDir = pushToLeft ? -1.0 : 1.0;
        double wallEdge = "left".equals(victim.getSide()) ? WALL_WIDTH : CANVAS_WIDTH - WALL_WIDTH - victim.getWidth();
        double targetX = wallEdge + pushDir * KNOCKBACK_PUSH_X;
        targetX = Math.max(5, Math.min(targetX, CANVAS_WIDTH - victim.getWidth() - 5));
        victim.setX(targetX);
        victim.setVy(KNOCKBACK_VY);
        victim.setTargetRotation(pushToLeft ? -KNOCKBACK_ROTATION : KNOCKBACK_ROTATION);
        victim.setRotationAngle(0);
        victim.setJumping(false);
    }
}

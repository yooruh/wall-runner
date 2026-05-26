package com.wallrunner.shared.physics.subsystem;

import com.wallrunner.shared.entity.Player;

import static com.wallrunner.shared.constants.GameConstants.*;

/**
 * 玩家间碰撞解析器实现。
 * 
 * UML 建模意义：IPlayerCollisionResolver 的具体实现，展示策略分支。
 */
public class PlayerCollisionResolver implements IPlayerCollisionResolver {

    private final IKnockbackSystem knockbackSystem;

    public PlayerCollisionResolver(IKnockbackSystem knockbackSystem) {
        this.knockbackSystem = knockbackSystem;
    }

    @Override
    public boolean checkPlayerCollision(Player a, Player b) {
        return a.getX() < b.getX() + b.getWidth() && a.getX() + a.getWidth() > b.getX()
                && a.getY() < b.getY() + b.getHeight() && a.getY() + a.getHeight() > b.getY();
    }

    @Override
    public void resolvePlayerCollision(Player a, Player b) {
        boolean aInvincible = a.isInvincible();
        boolean bInvincible = b.isInvincible();

        if (aInvincible && bInvincible) return;

        if (aInvincible || bInvincible) {
            resolveInvincibleCollision(aInvincible ? a : b, aInvincible ? b : a);
            return;
        }

        resolveNormalCollision(a, b);
    }

    private void resolveInvincibleCollision(Player invincible, Player normal) {
        boolean invincibleJumping = invincible.isJumping();
        boolean normalJumping = normal.isJumping();

        if (invincibleJumping) {
            knockbackSystem.applyKnockback(normal, invincible.getSide());
        } else if (normalJumping) {
            knockbackSystem.applyKnockback(normal, normal.getSide());
        }
    }

    private void resolveNormalCollision(Player a, Player b) {
        double dx = (a.getX() + a.getWidth() / 2) - (b.getX() + b.getWidth() / 2);
        double dy = (a.getY() + a.getHeight() / 2) - (b.getY() + b.getHeight() / 2);
        double overlapX = (a.getWidth() + b.getWidth()) / 2 - Math.abs(dx);
        double overlapY = (a.getHeight() + b.getHeight()) / 2 - Math.abs(dy);

        boolean aJumping = a.isJumping();
        boolean bJumping = b.isJumping();

        if (aJumping && bJumping) {
            if (dx > 0) {
                knockbackSystem.applyKnockback(a, "left");
                knockbackSystem.applyKnockback(b, "right");
            } else {
                knockbackSystem.applyKnockback(a, "right");
                knockbackSystem.applyKnockback(b, "left");
            }
            return;
        } else if (aJumping && !bJumping) {
            knockbackSystem.applyKnockback(b, a.getSide());
            double pushX = "left".equals(a.getSide()) ? JUMP_SPEED * 0.8 : -JUMP_SPEED * 0.8;
            a.setX(a.getX() + pushX);
        } else if (bJumping && !aJumping) {
            knockbackSystem.applyKnockback(a, b.getSide());
            double pushX = "left".equals(b.getSide()) ? JUMP_SPEED * 0.8 : -JUMP_SPEED * 0.8;
            b.setX(b.getX() + pushX);
        }

        if (overlapX < overlapY) {
            double shift = overlapX / 2;
            a.setX(a.getX() + (dx > 0 ? shift : -shift));
            b.setX(b.getX() + (dx > 0 ? -shift : shift));
        } else {
            double shift = overlapY / 2;
            a.setY(a.getY() + (dy > 0 ? shift : -shift));
            b.setY(b.getY() + (dy > 0 ? -shift : shift));
        }
    }
}

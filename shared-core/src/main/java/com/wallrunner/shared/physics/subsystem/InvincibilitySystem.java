package com.wallrunner.shared.physics.subsystem;

import com.wallrunner.shared.entity.Player;

import static com.wallrunner.shared.constants.GameConstants.*;

/**
 * 无敌系统实现。
 */
public class InvincibilitySystem implements IInvincibilitySystem {

    @Override
    public void processInvincibility(Player p) {
        if (!p.isInvincible()) return;
        p.setInvincibleTimer(p.getInvincibleTimer() - 0.016);

        if (COLLECTIBLE_A.equals(p.getActivePowerUp())) {
            p.setPowerUpTimer(p.getPowerUpTimer() - 0.016);
            if (p.getPowerUpTimer() <= 0) {
                clearPowerUp(p);
            }
        }

        if (p.getInvincibleTimer() <= 0) {
            p.setInvincible(false);
            p.setInvincibleTimer(0);
            if (COLLECTIBLE_A.equals(p.getActivePowerUp())) {
                clearPowerUp(p);
            }
        }
    }

    private void clearPowerUp(Player p) {
        p.setActivePowerUp("");
        p.setPowerUpTimer(0);
        p.getEffects().remove("rainbow_sparkle");
    }
}

package com.wallrunner.shared.physics.subsystem;

import com.wallrunner.shared.constants.GameConstants;
import com.wallrunner.shared.entity.Collectible;
import com.wallrunner.shared.entity.GameState;
import com.wallrunner.shared.entity.Player;

import java.util.List;
import java.util.Random;

import static com.wallrunner.shared.constants.GameConstants.*;

/**
 * 收集物管理系统实现。
 * 
 * UML 建模意义：ICollectibleManager 的具体实现。
 */
public class CollectibleManager implements ICollectibleManager {
    private final Random random = new Random();
    private final ICollisionDetector collisionDetector;

    public CollectibleManager(ICollisionDetector collisionDetector) {
        this.collisionDetector = collisionDetector;
    }

    @Override
    public void updateCollectibles(GameState state, List<Player> activePlayers) {
        spawnCollectibles(state);
        recycleCollectibles(state, activePlayers);
        detectCollisions(state, activePlayers);
        processSpeedBoosts(activePlayers);
    }

    private void spawnCollectibles(GameState state) {
        double displayCamY = state.getCameraY();
        while (state.getNextCollectibleSpawnY() > displayCamY - CANVAS_HEIGHT) {
            String[] types = {COLLECTIBLE_A, COLLECTIBLE_B, COLLECTIBLE_C};
            String type = types[random.nextInt(types.length)];
            double x = SAFE_LEFT + random.nextDouble() * (SAFE_RIGHT - SAFE_LEFT - COLLECTIBLE_SIZE);
            double y = state.getNextCollectibleSpawnY();

            Collectible c = new Collectible();
            c.setX(x); c.setY(y); c.setWidth(COLLECTIBLE_SIZE); c.setHeight(COLLECTIBLE_SIZE);
            c.setType(type); c.setCollected(false);
            c.setOscillationPhase(random.nextDouble() * Math.PI * 2);
            c.setGlowIntensity(0.5 + random.nextDouble() * 0.5);

            switch (type) {
                case COLLECTIBLE_A -> { c.setEffectOnCollect("rainbow_sparkle"); c.setValue(15); }
                case COLLECTIBLE_B -> { c.setEffectOnCollect("wind_particle"); c.setValue(15); }
                case COLLECTIBLE_C -> { c.setEffectOnCollect("life_up"); c.setValue(20); }
            }
            state.getCollectibles().add(c);
            state.setNextCollectibleSpawnY(y - COLLECTIBLE_SPAWN_INTERVAL - random.nextDouble() * 100);
        }
    }

    private void recycleCollectibles(GameState state, List<Player> activePlayers) {
        double lastCamY = activePlayers.stream()
                .mapToDouble(Player::getCameraY)
                .max()
                .orElse(state.getCameraY());
        double recycleLine = lastCamY + CANVAS_HEIGHT + RECYCLE_LINE_OFFSET;
        state.getCollectibles().removeIf(c -> c.getY() > recycleLine);
    }

    private void detectCollisions(GameState state, List<Player> activePlayers) {
        for (Player p : activePlayers) {
            if (p.isPaused() || p.isKnockedBack()) continue;
            for (Collectible c : state.getCollectibles()) {
                if (c.isCollected()) continue;
                if (collisionDetector.rectIntersect(p.getX(), p.getY(), p.getWidth(), p.getHeight(),
                        c.getX(), c.getY(), c.getWidth(), c.getHeight())) {
                    c.setCollected(true);
                    handleCollectiblePickup(p, c.getType());
                }
            }
        }
    }

    private void handleCollectiblePickup(Player p, String type) {
        if (!type.equals(p.getCollectibleType())) {
            p.setCollectibleType(type);
            p.setCollectibleCount(1);
        } else {
            p.setCollectibleCount(p.getCollectibleCount() + 1);
        }
        if (p.getCollectibleCount() >= COLLECTIBLE_MATCH_COUNT) {
            activateCollectibleSkill(p, type);
            p.setCollectibleType("");
            p.setCollectibleCount(0);
        }
    }

    private void activateCollectibleSkill(Player p, String type) {
        switch (type) {
            case COLLECTIBLE_A -> {
                p.setInvincible(true);
                p.setInvincibleTimer(COLLECTIBLE_INVINCIBLE_DURATION);
                p.setActivePowerUp(COLLECTIBLE_A);
                p.setPowerUpTimer(COLLECTIBLE_INVINCIBLE_DURATION);
                p.getEffects().add("rainbow_sparkle");
            }
            case COLLECTIBLE_B -> {
                p.setActivePowerUp(COLLECTIBLE_B);
                p.setPowerUpTimer(COLLECTIBLE_SPEED_DURATION);
                p.getEffects().add("wind_particle");
            }
            case COLLECTIBLE_C -> {
                if (p.getLives() < MAX_LIVES) {
                    p.setLives(p.getLives() + 1);
                }
                p.getEffects().add("life_up_flash");
            }
        }
    }

    private void processSpeedBoosts(List<Player> activePlayers) {
        for (Player p : activePlayers) {
            if (!COLLECTIBLE_B.equals(p.getActivePowerUp())) continue;
            p.setPowerUpTimer(p.getPowerUpTimer() - 0.016);
            if (p.getPowerUpTimer() <= 0) {
                p.setActivePowerUp("");
                p.setPowerUpTimer(0);
                p.getEffects().remove("wind_particle");
            }
        }
    }
}

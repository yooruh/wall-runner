package com.wallrunner.shared.physics.subsystem;

import static com.wallrunner.shared.constants.GameConstants.CAMERA_OFFSET_RATIO;
import static com.wallrunner.shared.constants.GameConstants.CANVAS_HEIGHT;
import static com.wallrunner.shared.constants.GameConstants.CANVAS_WIDTH;
import static com.wallrunner.shared.constants.GameConstants.DEATH_LINE_OFFSET;
import static com.wallrunner.shared.constants.GameConstants.WALL_WIDTH;
import com.wallrunner.shared.entity.GameState;
import com.wallrunner.shared.entity.Player;

/**
 * 死亡与重生系统实现。
 * 
 * UML 建模意义：IDeathSystem 的具体实现，展示生命状态机。
 */
public class DeathSystem implements IDeathSystem {

    @Override
    public void checkDeathLine(GameState state) {
        for (Player player : state.getPlayers().values()) {
            if (!player.isActive() || player.isPaused()) continue;
            double deathLine = player.getCameraY() + CANVAS_HEIGHT + DEATH_LINE_OFFSET;
            if (player.getY() > deathLine) {
                applyDeath(state, player);
            }
        }
    }

    @Override
    public void applyDeath(GameState state, Player player) {
        player.setLives(player.getLives() - 1);
        if (player.getLives() <= 0) {
            if (player.getScore() > player.getHighScore()) {
                player.setHighScore(player.getScore());
            }
            player.setActive(false);
            player.setSpectator(true);
        } else {
            respawnPlayer(state, player);
        }
    }

    private void respawnPlayer(GameState state, Player player) {
        double fallbackY = 0;
        for (Player p : state.getPlayers().values()) {
            if (p.isActive() && p.getY() < fallbackY) {
                fallbackY = p.getY();
            }
        }
        double spawnY = fallbackY + 300;
        player.setJumping(false);
        player.setVy(0);
        player.setSide("left".equals(player.getSide()) ? "right" : "left");
        player.setX("left".equals(player.getSide()) ? WALL_WIDTH : CANVAS_WIDTH - WALL_WIDTH - player.getWidth());
        player.setY(spawnY);
        player.setJoinOffsetY(spawnY);
        player.setBlocked(false);
        player.setPaused(false);
        player.setKnockedBack(false);
        player.setInvincible(true);
        player.setInvincibleTimer(2.0);
        player.setSpectator(false);
        double spawnCamY = spawnY - CANVAS_HEIGHT * CAMERA_OFFSET_RATIO;
        player.setCameraY(spawnCamY);
        player.setCameraTargetY(spawnCamY);
    }

    @Override
    public void checkAllDead(GameState state) {
        boolean allDead = state.getPlayers().values().stream().noneMatch(Player::isActive);
        if (allDead && !state.getPlayers().isEmpty()) {
            state.setPhase("gameover");
        }
    }
}

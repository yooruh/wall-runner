package com.wallrunner.shared.physics.subsystem;

import com.wallrunner.shared.entity.GameState;
import com.wallrunner.shared.entity.Obstacle;
import com.wallrunner.shared.entity.Player;

import java.util.List;
import java.util.Random;

import static com.wallrunner.shared.constants.GameConstants.*;

/**
 * 障碍物管理系统实现。
 * 
 * UML 建模意义：IObstacleManager 的具体实现，展示组合关系。
 */
public class ObstacleManager implements IObstacleManager {
    private final Random random = new Random();
    private final ICollisionDetector collisionDetector;

    public ObstacleManager(ICollisionDetector collisionDetector) {
        this.collisionDetector = collisionDetector;
    }

    @Override
    public void updateObstacles(GameState state, List<Player> activePlayers) {
        double lastCamY = activePlayers.stream()
                .mapToDouble(Player::getCameraY)
                .max()
                .orElse(state.getCameraY());
        double recycleLine = lastCamY + CANVAS_HEIGHT + RECYCLE_LINE_OFFSET;

        state.getObstacles().removeIf(obs -> {
            if (!"wall_spike".equals(obs.getType())) {
                obs.setY(obs.getY() + OBSTACLE_SPEED);
            }
            return obs.getY() > recycleLine;
        });
    }

    @Override
    public void checkSpawn(GameState state) {
        double displayCamY = state.getCameraY();
        double dist = state.getNextSpawnCameraY() - displayCamY;
        if (dist > 0) return;
        spawnObstacle(state, displayCamY);
        state.setNextSpawnCameraY(displayCamY - random(SPAWN_MIN_GAP, SPAWN_MAX_GAP));
    }

    private void spawnObstacle(GameState state, double cameraY) {
        if (!state.getObstacles().isEmpty()) {
            Obstacle last = state.getObstacles().get(state.getObstacles().size() - 1);
            if (last.getY() - (cameraY - SPAWN_AHEAD) < 60) return;
        }
        boolean isWallSpike = Math.random() < 0.5;
        double spawnY = cameraY - SPAWN_AHEAD;
        if (isWallSpike) {
            spawnWallSpike(state, spawnY);
        } else {
            spawnFloatingObstacle(state, spawnY);
        }
    }

    private void spawnWallSpike(GameState state, double spawnY) {
        String side = Math.random() < 0.5 ? "left" : "right";
        double w = SPIKE_WIDTH;
        double h = SPIKE_HEIGHT;
        double x = "left".equals(side) ? WALL_WIDTH : CANVAS_WIDTH - WALL_WIDTH - w;
        Obstacle obs = new Obstacle();
        obs.setX(x); obs.setY(spawnY); obs.setWidth(w); obs.setHeight(h);
        obs.setType("wall_spike"); obs.setSide(side);
        obs.setDifficulty(state.getDifficultyLevel());
        state.getObstacles().add(obs);
    }

    private void spawnFloatingObstacle(GameState state, double spawnY) {
        int type = random(0, 2);
        double obsH = random(40, 80);
        double safeWidth = SAFE_RIGHT - SAFE_LEFT;
        double obsWmax = Math.min(random(50, 100), safeWidth - 20);
        double obsX;
        if (type == 0) {
            double maxX = SAFE_LEFT + Math.floor((safeWidth - obsWmax) / 3);
            obsX = random((int) SAFE_LEFT, (int) maxX);
        } else if (type == 1) {
            double minX = SAFE_RIGHT - Math.floor((safeWidth - obsWmax) / 3) - obsWmax;
            obsX = random((int) Math.max(SAFE_LEFT, minX), (int) (SAFE_RIGHT - obsWmax));
        } else {
            double third = Math.floor((safeWidth - obsWmax) / 3);
            obsX = random((int) (SAFE_LEFT + third), (int) (SAFE_LEFT + third * 2));
        }
        Obstacle obs = new Obstacle();
        obs.setX(obsX); obs.setY(spawnY); obs.setWidth(obsWmax); obs.setHeight(obsH);
        obs.setType("floating");
        obs.setDifficulty(state.getDifficultyLevel());
        state.getObstacles().add(obs);
    }

    private int random(int min, int max) {
        return random.nextInt(max - min + 1) + min;
    }
}

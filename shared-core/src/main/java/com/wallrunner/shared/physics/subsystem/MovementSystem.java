package com.wallrunner.shared.physics.subsystem;

import com.wallrunner.shared.constants.GameConstants;
import com.wallrunner.shared.entity.Obstacle;
import com.wallrunner.shared.entity.Player;

import java.util.List;

import static com.wallrunner.shared.constants.GameConstants.*;

/**
 * 玩家移动系统实现。
 * 
 * UML 建模意义：IMovementSystem 的具体实现。
 * 设计原则：单一职责（SRP）— 仅处理玩家位置更新与攀爬/跳跃逻辑。
 */
public class MovementSystem implements IMovementSystem {

    private final ICollisionDetector collisionDetector;

    public MovementSystem(ICollisionDetector collisionDetector) {
        this.collisionDetector = collisionDetector;
    }

    @Override
    public boolean updatePlayerMovement(Player player, List<Obstacle> obstacles, List<Player> activePlayers) {
        if (player.isPaused()) return false;
        if (player.isKnockedBack()) return false;

        boolean isBlocked = false;

        if (!player.isJumping()) {
            isBlocked = processClimbing(player, obstacles, activePlayers);
        } else {
            processJumping(player);
        }

        resolveObstacleCollisions(player, obstacles);
        resolveWallCollisions(player);

        return isBlocked;
    }

    private boolean processClimbing(Player player, List<Obstacle> obstacles, List<Player> activePlayers) {
        double climbSpeed = COLLECTIBLE_B.equals(player.getActivePowerUp())
                ? CLIMB_SPEED * COLLECTIBLE_SPEED_MULTIPLIER : CLIMB_SPEED;
        double testY = player.getY() - climbSpeed;
        boolean blocked = false;

        if (!player.isInvincible()) {
            for (Obstacle obs : obstacles) {
                if (collisionDetector.rectIntersect(player.getX(), testY, player.getWidth(), player.getHeight(),
                        obs.getX(), obs.getY(), obs.getWidth(), obs.getHeight())) {
                    blocked = true; break;
                }
            }
            if (!blocked) {
                for (Player other : activePlayers) {
                    if (other == player || other.isPaused() || other.isInvincible()) continue;
                    if (collisionDetector.rectIntersect(player.getX(), testY, player.getWidth(), player.getHeight(),
                            other.getX(), other.getY(), other.getWidth(), other.getHeight())) {
                        blocked = true; break;
                    }
                }
            }
        }

        if (!blocked) {
            player.setY(testY);
        }
        return blocked;
    }

    private void processJumping(Player player) {
        if ("left".equals(player.getSide())) {
            player.setX(player.getX() + JUMP_SPEED);
        } else {
            player.setX(player.getX() - JUMP_SPEED);
        }
        player.setVy(player.getVy() + GRAVITY);
        player.setY(player.getY() + player.getVy());
    }

    private void resolveObstacleCollisions(Player player, List<Obstacle> obstacles) {
        if (player.isInvincible()) return;

        for (Obstacle obs : obstacles) {
            if (!collisionDetector.rectIntersect(player.getX(), player.getY(), player.getWidth(), player.getHeight(),
                    obs.getX(), obs.getY(), obs.getWidth(), obs.getHeight())) continue;

            double overlapTop = Math.max(0, (player.getY() + player.getHeight()) - obs.getY());
            double overlapBottom = Math.max(0, (obs.getY() + obs.getHeight()) - player.getY());
            double overlapLeft = Math.max(0, (player.getX() + player.getWidth()) - obs.getX());
            double overlapRight = Math.max(0, (obs.getX() + obs.getWidth()) - player.getX());
            double minOverlap = Math.min(Math.min(overlapTop, overlapBottom), Math.min(overlapLeft, overlapRight));

            if ("wall_spike".equals(obs.getType())) {
                resolveSpikeCollision(player, obs, minOverlap, overlapLeft, overlapRight, overlapBottom);
            } else {
                resolveFloatingObstacleCollision(player, minOverlap, overlapTop, overlapBottom, overlapLeft, overlapRight);
            }
        }
    }

    private void resolveSpikeCollision(Player player, Obstacle obs, double minOverlap,
                                       double overlapLeft, double overlapRight, double overlapBottom) {
        boolean isFrontal = player.isJumping() && !player.getSide().equals(obs.getSide()) &&
                (minOverlap == overlapLeft || minOverlap == overlapRight);
        if (isFrontal) {
            if (minOverlap == overlapLeft) player.setX(obs.getX() - player.getWidth() - 2);
            else player.setX(obs.getX() + obs.getWidth() + 2);
            player.setSide("left".equals(player.getSide()) ? "right" : "left");
            player.setVy(4);
        } else {
            if (minOverlap == overlapBottom) {
                player.setY(obs.getY() + obs.getHeight() + 1);
            } else if (minOverlap == overlapTop) {
                player.setY(obs.getY() - player.getHeight() - 1);
            }
        }
    }

    private void resolveFloatingObstacleCollision(Player player, double minOverlap,
                                                  double overlapTop, double overlapBottom,
                                                  double overlapLeft, double overlapRight) {
        if (minOverlap == overlapBottom) {
            player.setY(obs.getY() + obs.getHeight() + 1);
            if (player.getVy() < 0) player.setVy(0);
        } else if (minOverlap == overlapTop) {
            player.setY(obs.getY() - player.getHeight() - 1);
            if (player.getVy() > 0) player.setVy(0);
        } else if (minOverlap == overlapLeft) {
            player.setX(obs.getX() - player.getWidth() - 1);
        } else if (minOverlap == overlapRight) {
            player.setX(obs.getX() + obs.getWidth() + 1);
        }
    }

    private void resolveWallCollisions(Player player) {
        if (!player.isJumping()) return;
        if ("left".equals(player.getSide()) && player.getX() >= CANVAS_WIDTH - WALL_WIDTH - player.getWidth()) {
            player.setX(CANVAS_WIDTH - WALL_WIDTH - player.getWidth());
            player.setSide("right");
            player.setJumping(false);
            player.setVy(0);
        } else if ("right".equals(player.getSide()) && player.getX() <= WALL_WIDTH) {
            player.setX(WALL_WIDTH);
            player.setSide("left");
            player.setJumping(false);
            player.setVy(0);
        }
    }
}

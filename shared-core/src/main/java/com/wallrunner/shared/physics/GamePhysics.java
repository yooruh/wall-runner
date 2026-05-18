package com.wallrunner.shared.physics;

import com.wallrunner.shared.entity.GameState;
import com.wallrunner.shared.entity.Obstacle;
import com.wallrunner.shared.entity.Player;

import java.util.*;

import static com.wallrunner.shared.constants.GameConstants.*;

/**
 * 【模块】shared-core / physics
 * 【代号】Y
 * 【职责】游戏核心物理引擎。
 * 【重构】2026-05-08:
 *       1. 空格=跳跃，ESC=暂停。
 *       2. 彻底移除 front 角色依赖：
 *          - 死亡判断用玩家自身 cameraY。
 *          - 障碍物回收等到最后一名玩家经过。
 *       3. 每个玩家独立摄像机（橡皮筋系统）：
 *          - 正常：cameraTargetY 绑定玩家 Y，cameraY 平滑跟随。
 *          - 被阻挡：cameraTargetY 解绑并持续上移，cameraY 平滑跟随。
 *          - 解除阻挡：cameraTargetY 瞬间绑定回玩家，cameraY 橡皮筋归位。
 *       4. 显示摄像机 state.cameraY 仅用于渲染与障碍物生成，不参与生死判定。
 */
public class GamePhysics {
    private GamePhysics() {}

    private static final Random RANDOM = new Random();

    public static void initState(GameState state) {
        state.getObstacles().clear();
        state.setFrames(0);
        state.setPhase("menu");
        int i = 0;
        double initCameraY = 300 - CANVAS_HEIGHT * CAMERA_OFFSET_RATIO;
        for (Player p : state.getPlayers().values()) {
            p.setActive(true);
            p.setSide((i % 2 == 0) ? "left" : "right");
            p.setX("left".equals(p.getSide()) ? WALL_WIDTH : CANVAS_WIDTH - WALL_WIDTH - PLAYER_SIZE);
            p.setY(300);
            p.setJumping(false);
            p.setVy(0);
            p.setScore(0);
            p.setLives(MAX_LIVES);
            p.setPaused(false);
            p.setBlocked(false);
            p.setCameraY(initCameraY);
            p.setCameraTargetY(initCameraY);
            i++;
        }
        state.setCameraY(initCameraY);
        state.setCameraTargetY(initCameraY);
        state.setNextSpawnCameraY(initCameraY - random(40, 80));
    }

    public static void update(GameState state) {
        if (!"playing".equals(state.getPhase())) return;
        state.setFrames(state.getFrames() + 1);

        List<Player> activePlayers = getActivePlayers(state);
        if (activePlayers.isEmpty()) {
            checkAllDead(state);
            return;
        }

        // 1. 障碍物移动与回收（基于最后一名玩家的摄像机）
        updateObstacles(state, activePlayers);

        // 2. 玩家移动与碰撞
        Map<String, Boolean> blockedMap = new HashMap<>();
        List<Player> collidable = new ArrayList<>();
        for (Player player : activePlayers) {
            if (!player.isPaused()) {
                player.setScore(player.getScore() + 1);
                boolean blocked = updatePlayerMovement(player, state.getObstacles(), activePlayers);
                blockedMap.put(player.getId(), blocked);
                collidable.add(player);
            } else {
                blockedMap.put(player.getId(), false);
            }
        }

        // 3. 玩家间碰撞
        for (int i = 0; i < collidable.size(); i++) {
            for (int j = i + 1; j < collidable.size(); j++) {
                Player a = collidable.get(i);
                Player b = collidable.get(j);
                if (a.isPaused() || b.isPaused()) continue;
                if (checkPlayerCollision(a, b)) {
                    resolvePlayerCollision(a, b);
                }
            }
        }

        // 4. 每个玩家独立摄像机（橡皮筋系统）
        updatePlayerCameras(activePlayers, blockedMap);

        // 5. 显示摄像机（仅渲染/生成用，跟随最领先玩家）
        updateDisplayCamera(state, activePlayers);

        // 6. 同步 blocked 标记
        for (Player p : activePlayers) {
            p.setBlocked(blockedMap.getOrDefault(p.getId(), false));
        }

        // 7. 死亡判断（各自独立摄像机）
        checkDeathLine(state);

        // 8. 检查全灭
        checkAllDead(state);

        // 9. 生成障碍物（基于显示摄像机）
        checkSpawn(state);
    }

    private static List<Player> getActivePlayers(GameState state) {
        List<Player> active = new ArrayList<>();
        for (Player p : state.getPlayers().values()) {
            if (p.isActive()) active.add(p);
        }
        return active;
    }

    /**
     * 障碍物回收：必须等到最后一名玩家（cameraY 最大 = 最靠下）经过后才回收。
     */
    private static void updateObstacles(GameState state, List<Player> activePlayers) {
        double lastCamY = activePlayers.stream()
                .mapToDouble(Player::getCameraY)
                .max()
                .orElse(state.getCameraY());
        double recycleLine = lastCamY + CANVAS_HEIGHT + DEATH_LINE_OFFSET;

        state.getObstacles().removeIf(obs -> {
            if (!"wall_spike".equals(obs.getType())) {
                obs.setY(obs.getY() + OBSTACLE_SPEED);
            }
            return obs.getY() > recycleLine;
        });
    }

    private static void checkSpawn(GameState state) {
        double displayCamY = state.getCameraY();
        if (displayCamY <= state.getNextSpawnCameraY()) {
            spawnObstacle(state, displayCamY);
            state.setNextSpawnCameraY(displayCamY - random((int) SPAWN_MIN_GAP, (int) SPAWN_MAX_GAP));
        }
    }

    private static void spawnObstacle(GameState state, double cameraY) {
        if (!state.getObstacles().isEmpty()) {
            Obstacle last = state.getObstacles().get(state.getObstacles().size() - 1);
            if (last.getY() - (cameraY - SPAWN_AHEAD) < 60) return;
        }
        boolean isWallSpike = Math.random() < 0.5;
        double spawnY = cameraY - SPAWN_AHEAD;
        if (isWallSpike) {
            String side = Math.random() < 0.5 ? "left" : "right";
            double w = SPIKE_WIDTH;
            double h = SPIKE_HEIGHT;
            double x = "left".equals(side) ? WALL_WIDTH : CANVAS_WIDTH - WALL_WIDTH - w;
            Obstacle obs = new Obstacle();
            obs.setX(x); obs.setY(spawnY); obs.setWidth(w); obs.setHeight(h);
            obs.setType("wall_spike"); obs.setSide(side);
            state.getObstacles().add(obs);
        } else {
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
            state.getObstacles().add(obs);
        }
    }

    private static boolean updatePlayerMovement(Player player, List<Obstacle> obstacles, List<Player> activePlayers) {
        boolean isBlocked = false;

        if (!player.isJumping()) {
            // 攀爬阶段
            double testY = player.getY() - CLIMB_SPEED;
            boolean blocked = false;
            for (Obstacle obs : obstacles) {
                if (rectIntersect(player.getX(), testY, player.getWidth(), player.getHeight(),
                        obs.getX(), obs.getY(), obs.getWidth(), obs.getHeight())) {
                    blocked = true; break;
                }
            }
            if (!blocked) {
                for (Player other : activePlayers) {
                    if (other == player || other.isPaused()) continue;
                    if (rectIntersect(player.getX(), testY, player.getWidth(), player.getHeight(),
                            other.getX(), other.getY(), other.getWidth(), other.getHeight())) {
                        blocked = true; break;
                    }
                }
            }
            if (!blocked) {
                player.setY(testY);
            } else {
                // 【关键修复】攀爬阶段被障碍物/玩家挡住 = 被阻挡
                isBlocked = true;
            }
        } else {
            // 跳跃阶段：向对面墙壁移动
            if ("left".equals(player.getSide())) player.setX(player.getX() + JUMP_SPEED);
            else player.setX(player.getX() - JUMP_SPEED);
            player.setVy(player.getVy() + GRAVITY);
            player.setY(player.getY() + player.getVy());
        }

        // 障碍物碰撞
        for (Obstacle obs : obstacles) {
            if (!rectIntersect(player.getX(), player.getY(), player.getWidth(), player.getHeight(),
                    obs.getX(), obs.getY(), obs.getWidth(), obs.getHeight())) continue;

            double overlapTop = Math.max(0, (player.getY() + player.getHeight()) - obs.getY());
            double overlapBottom = Math.max(0, (obs.getY() + obs.getHeight()) - player.getY());
            double overlapLeft = Math.max(0, (player.getX() + player.getWidth()) - obs.getX());
            double overlapRight = Math.max(0, (obs.getX() + obs.getWidth()) - player.getX());
            double minOverlap = Math.min(Math.min(overlapTop, overlapBottom), Math.min(overlapLeft, overlapRight));

            if ("wall_spike".equals(obs.getType())) {
                boolean isFrontal = player.isJumping() && !player.getSide().equals(obs.getSide()) &&
                        (minOverlap == overlapLeft || minOverlap == overlapRight);
                if (isFrontal) {
                    if (minOverlap == overlapLeft) player.setX(obs.getX() - player.getWidth() - 2);
                    else player.setX(obs.getX() + obs.getWidth() + 2);
                    player.setSide("left".equals(player.getSide()) ? "right" : "left");
                    player.setVy(4);
                    isBlocked = true;
                } else {
                    if (minOverlap == overlapBottom) {
                        player.setY(obs.getY() + obs.getHeight() + 1);
                        isBlocked = true;
                    } else if (minOverlap == overlapTop) {
                        player.setY(obs.getY() - player.getHeight() - 1);
                    }
                }
            } else {
                if (minOverlap == overlapBottom) {
                    player.setY(obs.getY() + obs.getHeight() + 1);
                    if (player.getVy() < 0) player.setVy(0);
                    isBlocked = true;
                } else if (minOverlap == overlapTop) {
                    player.setY(obs.getY() - player.getHeight() - 1);
                    if (player.getVy() > 0) player.setVy(0);
                    isBlocked = true;
                } else if (minOverlap == overlapLeft) {
                    player.setX(obs.getX() - player.getWidth() - 1);
                    isBlocked = true;
                } else if (minOverlap == overlapRight) {
                    player.setX(obs.getX() + obs.getWidth() + 1);
                    isBlocked = true;
                }
            }
        }

        // 墙壁碰撞（跳跃到达对面）
        if (player.isJumping()) {
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
        return isBlocked;
    }

    /**
     * 显示摄像机：仅用于渲染与障碍物生成，跟随最领先玩家（最小 cameraY）。
     * 绝不参与任何玩家生死判定。
     */
    private static void updateDisplayCamera(GameState state, List<Player> activePlayers) {
        if (activePlayers.isEmpty()) return;
        double leadCamY = activePlayers.stream()
                .mapToDouble(Player::getCameraY)
                .min()
                .orElse(state.getCameraY());
        state.setCameraTargetY(leadCamY);
        state.setCameraY(state.getCameraY() + (state.getCameraTargetY() - state.getCameraY()) * CAMERA_SMOOTH);
    }

    /**
     * 每个玩家独立摄像机（橡皮筋系统）。
     * 被阻挡时解绑并持续上移；解除后瞬间绑定并橡皮筋归位。
     */
    private static void updatePlayerCameras(List<Player> activePlayers, Map<String, Boolean> blockedMap) {
        for (Player player : activePlayers) {
            if (player.isPaused()) continue;
            boolean isBlocked = Boolean.TRUE.equals(blockedMap.get(player.getId()));
            if (!isBlocked) {
                // 未阻挡：目标瞬间绑定玩家位置，cameraY 平滑橡皮筋归位
                player.setCameraTargetY(player.getY() - CANVAS_HEIGHT * CAMERA_OFFSET_RATIO);
            } else {
                // 被阻挡：解绑，摄像机无情上移
                player.setCameraTargetY(player.getCameraTargetY() - CLIMB_SPEED);
            }
            if (player.getCameraY() == 0) {
                player.setCameraY(player.getCameraTargetY());
            }
            player.setCameraY(player.getCameraY() + (player.getCameraTargetY() - player.getCameraY()) * CAMERA_SMOOTH);
        }
    }

    /**
     * 死亡判断：严格使用玩家自身的 cameraY。
     */
    private static void checkDeathLine(GameState state) {
        for (Player player : state.getPlayers().values()) {
            if (!player.isActive() || player.isPaused()) continue;
            double deathLine = player.getCameraY() + CANVAS_HEIGHT + DEATH_LINE_OFFSET;
            if (player.getY() > deathLine) {
                applyDeath(state, player);
            }
        }
    }

    /**
     * 重生位置使用玩家自己的摄像机。
     */
    private static void applyDeath(GameState state, Player player) {
        player.setLives(player.getLives() - 1);
        if (player.getLives() <= 0) {
            player.setActive(false);
        } else {
            player.setJumping(false);
            player.setVy(0);
            player.setSide("left".equals(player.getSide()) ? "right" : "left");
            player.setX("left".equals(player.getSide()) ? WALL_WIDTH : CANVAS_WIDTH - WALL_WIDTH - player.getWidth());
            player.setY(player.getCameraY() + CANVAS_HEIGHT * 0.5);
        }
    }

    private static void checkAllDead(GameState state) {
        boolean allDead = state.getPlayers().values().stream().noneMatch(Player::isActive);
        if (allDead && !state.getPlayers().isEmpty()) {
            state.setPhase("gameover");
        }
    }

    private static boolean checkPlayerCollision(Player a, Player b) {
        return rectIntersect(a.getX(), a.getY(), a.getWidth(), a.getHeight(),
                b.getX(), b.getY(), b.getWidth(), b.getHeight());
    }

    private static void resolvePlayerCollision(Player a, Player b) {
        double dx = (a.getX() + a.getWidth() / 2) - (b.getX() + b.getWidth() / 2);
        double dy = (a.getY() + a.getHeight() / 2) - (b.getY() + b.getHeight() / 2);
        double overlapX = (a.getWidth() + b.getWidth()) / 2 - Math.abs(dx);
        double overlapY = (a.getHeight() + b.getHeight()) / 2 - Math.abs(dy);
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

    private static boolean rectIntersect(double x1, double y1, double w1, double h1,
                                         double x2, double y2, double w2, double h2) {
        return x1 < x2 + w2 && x1 + w1 > x2 && y1 < y2 + h2 && y1 + h1 > y2;
    }

    private static int random(int min, int max) {
        return RANDOM.nextInt(max - min + 1) + min;
    }

    public static void handleInput(Player player, String inputType) {
        if (!player.isActive() || player.isPaused()) return;
        if ("jump".equals(inputType) && !player.isJumping()) {
            player.setVy(JUMP_VY);
            player.setJumping(true);
        }
    }

    public static void startGame(GameState state) {
        if ("menu".equals(state.getPhase()) || "gameover".equals(state.getPhase())) {
            initState(state);
            state.setPhase("playing");
        }
    }
}
package com.wallrunner.shared.physics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static com.wallrunner.shared.constants.GameConstants.CAMERA_OFFSET_RATIO;
import static com.wallrunner.shared.constants.GameConstants.CAMERA_SMOOTH;
import static com.wallrunner.shared.constants.GameConstants.CANVAS_HEIGHT;
import static com.wallrunner.shared.constants.GameConstants.CANVAS_WIDTH;
import static com.wallrunner.shared.constants.GameConstants.CLIMB_SPEED;
import static com.wallrunner.shared.constants.GameConstants.COLLECTIBLE_A;
import static com.wallrunner.shared.constants.GameConstants.COLLECTIBLE_B;
import static com.wallrunner.shared.constants.GameConstants.COLLECTIBLE_C;
import static com.wallrunner.shared.constants.GameConstants.COLLECTIBLE_INVINCIBLE_DURATION;
import static com.wallrunner.shared.constants.GameConstants.COLLECTIBLE_MATCH_COUNT;
import static com.wallrunner.shared.constants.GameConstants.COLLECTIBLE_SIZE;
import static com.wallrunner.shared.constants.GameConstants.COLLECTIBLE_SPAWN_INTERVAL;
import static com.wallrunner.shared.constants.GameConstants.COLLECTIBLE_SPEED_DURATION;
import static com.wallrunner.shared.constants.GameConstants.COLLECTIBLE_SPEED_MULTIPLIER;
import static com.wallrunner.shared.constants.GameConstants.DEATH_LINE_OFFSET;
import static com.wallrunner.shared.constants.GameConstants.DIFFICULTY_MAX_LEVEL;
import static com.wallrunner.shared.constants.GameConstants.GRAVITY;
import static com.wallrunner.shared.constants.GameConstants.JUMP_SPEED;
import static com.wallrunner.shared.constants.GameConstants.JUMP_VY;
import static com.wallrunner.shared.constants.GameConstants.KNOCKBACK_DURATION;
import static com.wallrunner.shared.constants.GameConstants.KNOCKBACK_GRAVITY;
import static com.wallrunner.shared.constants.GameConstants.KNOCKBACK_PUSH_X;
import static com.wallrunner.shared.constants.GameConstants.KNOCKBACK_RETURN_DELAY;
import static com.wallrunner.shared.constants.GameConstants.KNOCKBACK_RETURN_SPEED;
import static com.wallrunner.shared.constants.GameConstants.KNOCKBACK_ROTATION;
import static com.wallrunner.shared.constants.GameConstants.KNOCKBACK_ROTATION_SPEED;
import static com.wallrunner.shared.constants.GameConstants.KNOCKBACK_VY;
import static com.wallrunner.shared.constants.GameConstants.MAX_LIVES;
import static com.wallrunner.shared.constants.GameConstants.OBSTACLE_SPEED;
import static com.wallrunner.shared.constants.GameConstants.PLAYER_SIZE;
import static com.wallrunner.shared.constants.GameConstants.SAFE_LEFT;
import static com.wallrunner.shared.constants.GameConstants.SAFE_RIGHT;
import static com.wallrunner.shared.constants.GameConstants.SPAWN_AHEAD;
import static com.wallrunner.shared.constants.GameConstants.SPAWN_MAX_GAP;
import static com.wallrunner.shared.constants.GameConstants.SPAWN_MIN_GAP;
import static com.wallrunner.shared.constants.GameConstants.SPIKE_HEIGHT;
import static com.wallrunner.shared.constants.GameConstants.SPIKE_WIDTH;
import static com.wallrunner.shared.constants.GameConstants.WALL_WIDTH;
import com.wallrunner.shared.entity.Collectible;
import com.wallrunner.shared.entity.GameState;
import com.wallrunner.shared.entity.Obstacle;
import com.wallrunner.shared.entity.Player;

/**
 * 游戏核心物理引擎。
 *
 * 职责：
 * - 处理玩家移动、碰撞、死亡判定。
 * - 管理障碍物生成与回收。
 * - 每个玩家拥有独立摄像机（橡皮筋系统）。
 * - 支持击退、无敌、旁观等状态。
 *
 * 扩展预留：
 * - 可收集物碰撞检测（已预留方法骨架）。
 * - 难度系统（随高度自动提升障碍物速度与密度）。
 * - 技能系统（冲刺、二段跳等输入处理预留）。
 */
public class GamePhysics {
    private GamePhysics() {}

    private static final Random RANDOM = new Random();

    /* ============================================================
       状态初始化
       ============================================================ */

    public static void initState(GameState state) {
        state.getObstacles().clear();
        state.getCollectibles().clear();
        state.setFrames(0);
        state.setPhase("menu");
        state.setDifficultyLevel(1);
        state.setDifficultyAccumulator(0.0);

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
            p.setJoinOffsetY(300);
            p.setTimeBonusScore(0);
            p.setSpectator(false);
            p.setKnockedBack(false);
            p.setInvincible(false);
            p.setRotationAngle(0);
            p.setReturningToWall(false);
            p.setCoinsCollected(0);
            p.setComboCount(0);
            p.setActivePowerUp("");
            p.setPowerUpTimer(0);
            p.setEffects(new ArrayList<>());
            p.setCollectibleType("");
            p.setCollectibleCount(0);
            // 不重置最高分，保留历史最高分
            i++;
        }
        state.setCameraY(initCameraY);
        state.setCameraTargetY(initCameraY);
        state.setNextSpawnCameraY(initCameraY - random(40, 80));
        state.setTimeBonusAccumulator(0.0);
        if (state.getTimeBonusInterval() <= 0) state.setTimeBonusInterval(5.0);
        if (state.getTimeBonusPoints() <= 0) state.setTimeBonusPoints(10);
        state.setNextCollectibleSpawnY(initCameraY - 200);  // 第一个收集物在起点上方200像素
    }

    /* ============================================================
       主更新循环
       ============================================================ */

    public static void update(GameState state) {
        if (!"playing".equals(state.getPhase())) return;
        state.setFrames(state.getFrames() + 1);

        List<Player> activePlayers = getActivePlayers(state);
        if (activePlayers.isEmpty()) {
            checkAllDead(state);
            return;
        }

        // 1. 障碍物移动与回收
        updateObstacles(state, activePlayers);

        // 2. 处理击退、无敌状态
        for (Player p : activePlayers) {
            processKnockback(p);
            processInvincibility(p);
        }

        // 3. 玩家移动与碰撞
        Map<String, Boolean> blockedMap = new HashMap<>();
        List<Player> collidable = new ArrayList<>();
        for (Player player : activePlayers) {
            if (!player.isPaused()) {
                boolean blocked = updatePlayerMovement(player, state.getObstacles(), activePlayers);
                blockedMap.put(player.getId(), blocked);
                collidable.add(player);
            } else {
                // 【修复】暂停玩家位置固定，不移动，不参与碰撞
                blockedMap.put(player.getId(), false);
            }
        }

        // 4. 玩家间碰撞
        for (int i = 0; i < collidable.size(); i++) {
            for (int j = i + 1; j < collidable.size(); j++) {
                Player a = collidable.get(i);
                Player b = collidable.get(j);
                // 【修复】暂停玩家无碰撞效果
                if (a.isPaused() || b.isPaused()) continue;
                if (a.isInvincible() || b.isInvincible()) continue;
                if (checkPlayerCollision(a, b)) {
                    resolvePlayerCollision(a, b);
                }
            }
        }

        // 5. 每个玩家独立摄像机
        updatePlayerCameras(activePlayers, blockedMap);

        // 6. 显示摄像机（仅渲染/生成用）
        updateDisplayCamera(state, activePlayers);

        // 7. 同步 blocked 标记
        for (Player p : activePlayers) {
            p.setBlocked(blockedMap.getOrDefault(p.getId(), false));
        }

        // 8. 死亡判断
        checkDeathLine(state);

        // 9. 检查全灭
        checkAllDead(state);

        // 10. 时间奖励
        applyTimeBonus(state, activePlayers);

        // 11. 重新计分
        recalculateScores(activePlayers);

        // 12. 生成障碍物
        checkSpawn(state);

        // 13. 难度递增（预留扩展）
        updateDifficulty(state, activePlayers);

        // 14. 可收集物系统
        updateCollectibles(state, activePlayers);
    }

    /* ============================================================
       难度系统（预留扩展）
       ============================================================ */

    private static void updateDifficulty(GameState state, List<Player> activePlayers) {
        // 难度随最领先玩家的高度递增
        double leadY = activePlayers.stream()
                .mapToDouble(Player::getY)
                .min()
                .orElse(0);
        double heightProgress = Math.max(0, 300 - leadY); // 从起点向上爬的高度
        double nextThreshold = state.getDifficultyLevel() * 500.0; // 每500像素升一级
        if (heightProgress > nextThreshold && state.getDifficultyLevel() < DIFFICULTY_MAX_LEVEL) {
            state.setDifficultyLevel(state.getDifficultyLevel() + 1);
        }
    }

    /* ============================================================
       收集物系统 A/B/C
       ============================================================ */

    private static void updateCollectibles(GameState state, List<Player> activePlayers) {
        // 1. 生成收集物
        spawnCollectibles(state);

        // 2. 收集物随摄像机向上移动（与障碍物同步）
        double lastCamY = activePlayers.stream()
                .mapToDouble(Player::getCameraY)
                .max()
                .orElse(state.getCameraY());
        double recycleLine = lastCamY + CANVAS_HEIGHT + DEATH_LINE_OFFSET;
        state.getCollectibles().removeIf(c -> c.getY() > recycleLine);

        // 3. 碰撞检测：玩家与收集物
        for (Player p : activePlayers) {
            if (p.isPaused() || p.isKnockedBack()) continue;
            for (Collectible c : state.getCollectibles()) {
                if (c.isCollected()) continue;
                if (rectIntersect(p.getX(), p.getY(), p.getWidth(), p.getHeight(),
                        c.getX(), c.getY(), c.getWidth(), c.getHeight())) {
                    c.setCollected(true);
                    handleCollectiblePickup(p, c.getType());
                }
            }
        }

        // 4. 处理加速飞行效果
        for (Player p : activePlayers) {
            processSpeedBoost(p);
        }
    }

    private static void spawnCollectibles(GameState state) {
        double displayCamY = state.getCameraY();
        while (state.getNextCollectibleSpawnY() > displayCamY - CANVAS_HEIGHT) {
            // 随机选择 A/B/C 类型
            String[] types = {COLLECTIBLE_A, COLLECTIBLE_B, COLLECTIBLE_C};
            String type = types[RANDOM.nextInt(types.length)];

            // 在两墙之间的安全区域随机放置
            double x = SAFE_LEFT + RANDOM.nextDouble() * (SAFE_RIGHT - SAFE_LEFT - COLLECTIBLE_SIZE);
            double y = state.getNextCollectibleSpawnY();

            Collectible c = new Collectible();
            c.setX(x);
            c.setY(y);
            c.setWidth(COLLECTIBLE_SIZE);
            c.setHeight(COLLECTIBLE_SIZE);
            c.setType(type);
            c.setCollected(false);
            c.setOscillationPhase(RANDOM.nextDouble() * Math.PI * 2);
            c.setGlowIntensity(0.5 + RANDOM.nextDouble() * 0.5);

            // 设置收集时触发的特效标识
            switch (type) {
                case COLLECTIBLE_A -> {
                    c.setEffectOnCollect("rainbow_sparkle");
                    c.setValue(15);
                }
                case COLLECTIBLE_B -> {
                    c.setEffectOnCollect("wind_particle");
                    c.setValue(15);
                }
                case COLLECTIBLE_C -> {
                    c.setEffectOnCollect("life_up");
                    c.setValue(20);
                }
            }

            state.getCollectibles().add(c);
            state.setNextCollectibleSpawnY(y - COLLECTIBLE_SPAWN_INTERVAL - RANDOM.nextDouble() * 100);
        }
    }

    private static void handleCollectiblePickup(Player p, String type) {
        // 如果收集到不同类型，重置之前的进度
        if (!type.equals(p.getCollectibleType())) {
            p.setCollectibleType(type);
            p.setCollectibleCount(1);
        } else {
            p.setCollectibleCount(p.getCollectibleCount() + 1);
        }

        // 集齐3个同类触发技能
        if (p.getCollectibleCount() >= COLLECTIBLE_MATCH_COUNT) {
            activateCollectibleSkill(p, type);
            p.setCollectibleType("");
            p.setCollectibleCount(0);
        }
    }

    private static void activateCollectibleSkill(Player p, String type) {
        switch (type) {
            case COLLECTIBLE_A -> {
                // 无敌10秒（彩虹闪烁特效）
                p.setInvincible(true);
                p.setInvincibleTimer(COLLECTIBLE_INVINCIBLE_DURATION);
                p.setActivePowerUp(COLLECTIBLE_A);
                p.setPowerUpTimer(COLLECTIBLE_INVINCIBLE_DURATION);
                p.getEffects().add("rainbow_sparkle");
            }
            case COLLECTIBLE_B -> {
                // 加速飞行10秒（吹风粒子特效）
                p.setActivePowerUp(COLLECTIBLE_B);
                p.setPowerUpTimer(COLLECTIBLE_SPEED_DURATION);
                p.getEffects().add("wind_particle");
            }
            case COLLECTIBLE_C -> {
                // 加一条命（满命不加）
                if (p.getLives() < MAX_LIVES) {
                    p.setLives(p.getLives() + 1);
                }
                p.getEffects().add("life_up_flash");
            }
        }
    }

    private static void processSpeedBoost(Player p) {
        if (!COLLECTIBLE_B.equals(p.getActivePowerUp())) return;
        p.setPowerUpTimer(p.getPowerUpTimer() - 0.016);
        if (p.getPowerUpTimer() <= 0) {
            p.setActivePowerUp("");
            p.setPowerUpTimer(0);
            p.getEffects().remove("wind_particle");
        }
    }

    /* ============================================================
       时间奖励
       ============================================================ */

    private static void applyTimeBonus(GameState state, List<Player> activePlayers) {
        double interval = state.getTimeBonusInterval();
        if (interval <= 0) return;
        state.setTimeBonusAccumulator(state.getTimeBonusAccumulator() + 0.016);
        if (state.getTimeBonusAccumulator() >= interval) {
            int points = state.getTimeBonusPoints();
            for (Player p : activePlayers) {
                // 【修复】暂停玩家不得分
                if (!p.isPaused()) {
                    p.setTimeBonusScore(p.getTimeBonusScore() + points);
                }
            }
            state.setTimeBonusAccumulator(state.getTimeBonusAccumulator() - interval);
        }
    }

    private static void recalculateScores(List<Player> activePlayers) {
        for (Player p : activePlayers) {
            int heightScore = (int) ((p.getJoinOffsetY() - p.getY()) / 10.0);
            int total = Math.max(0, heightScore + p.getTimeBonusScore() + p.getCoinsCollected());
            p.setScore(total);
        }
    }

    /* ============================================================
       中途加入玩家初始化
       ============================================================ */

    public static void initJoiningPlayer(GameState state, Player player) {
        List<Player> activePlayers = getActivePlayers(state);
        if (activePlayers.isEmpty()) return;
        Player last = activePlayers.stream()
                .max(Comparator.comparingDouble(Player::getY))
                .orElse(null);
        if (last == null) return;

        double joinY = last.getY();
        player.setActive(true);
        player.setSide("left".equals(last.getSide()) ? "right" : "left");
        player.setX("left".equals(player.getSide()) ? WALL_WIDTH : CANVAS_WIDTH - WALL_WIDTH - PLAYER_SIZE);
        player.setY(joinY);
        player.setJoinOffsetY(joinY);
        player.setTimeBonusScore(0);
        player.setJumping(false);
        player.setVy(0);
        player.setLives(MAX_LIVES);
        player.setPaused(false);
        player.setBlocked(false);
        player.setCameraY(last.getCameraY());
        player.setCameraTargetY(last.getCameraTargetY());
        player.setSpectator(false);
        player.setCoinsCollected(0);
        player.setComboCount(0);
        player.setCollectibleType("");
        player.setCollectibleCount(0);
    }

    /* ============================================================
       障碍物管理
       ============================================================ */

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
        double dist = state.getNextSpawnCameraY() - displayCamY;
        if (dist > 0) {
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
            // 预留：难度影响
            obs.setDifficulty(state.getDifficultyLevel());
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
            // 预留：难度影响
            obs.setDifficulty(state.getDifficultyLevel());
            state.getObstacles().add(obs);
        }
    }

    /* ============================================================
       玩家移动与碰撞
       ============================================================ */

    private static boolean updatePlayerMovement(Player player, List<Obstacle> obstacles, List<Player> activePlayers) {
        // 【修复】暂停玩家不移动
        if (player.isPaused()) return false;
        // 【修复】击退状态：跳过正常移动和碰撞，由 processKnockback 处理
        if (player.isKnockedBack()) return false;
        boolean isBlocked = false;

        if (!player.isJumping()) {
            // 攀爬阶段
            double climbSpeed = COLLECTIBLE_B.equals(player.getActivePowerUp()) 
                    ? CLIMB_SPEED * COLLECTIBLE_SPEED_MULTIPLIER : CLIMB_SPEED;
            double testY = player.getY() - climbSpeed;
            boolean blocked = false;
            for (Obstacle obs : obstacles) {
                if (rectIntersect(player.getX(), testY, player.getWidth(), player.getHeight(),
                        obs.getX(), obs.getY(), obs.getWidth(), obs.getHeight())) {
                    blocked = true; break;
                }
            }
            if (!blocked) {
                for (Player other : activePlayers) {
                    // 【修复】暂停玩家不参与碰撞检测，无敌玩家也不参与碰撞检测
                    if (other == player || other.isPaused() || other.isInvincible()) continue;
                    if (rectIntersect(player.getX(), testY, player.getWidth(), player.getHeight(),
                            other.getX(), other.getY(), other.getWidth(), other.getHeight())) {
                        blocked = true; break;
                    }
                }
            }
            if (!blocked) {
                player.setY(testY);
            } else {
                isBlocked = true;
            }
        } else {
            // 跳跃阶段
            if ("left".equals(player.getSide())) player.setX(player.getX() + JUMP_SPEED);
            else player.setX(player.getX() - JUMP_SPEED);
            player.setVy(player.getVy() + GRAVITY);
            player.setY(player.getY() + player.getVy());
        }

        // 障碍物碰撞（无敌状态跳过）
        if (!player.isInvincible()) {
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
        }

        // 墙壁碰撞
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

    /* ============================================================
       击退与无敌状态处理
       ============================================================ */

    private static void processKnockback(Player p) {
        if (!p.isKnockedBack()) return;
        p.setKnockbackTimer(p.getKnockbackTimer() - 0.016);

        // 阶段1: 弹开墙壁后自由下落（前 KNOCKBACK_RETURN_DELAY 秒）
        if (p.getKnockbackTimer() > KNOCKBACK_RETURN_DELAY) {
            p.setVy(p.getVy() + KNOCKBACK_GRAVITY);
            p.setY(p.getY() + p.getVy());
            p.setReturningToWall(false);
        }
        // 阶段2: 开始缓慢回归墙壁
        else if (p.getKnockbackTimer() > 0) {
            p.setReturningToWall(true);
            // 继续重力下落
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

        // 旋转动画：缓慢倾斜到目标角度
        double currentRot = p.getRotationAngle();
        double targetRot = p.getTargetRotation();
        double diff = targetRot - currentRot;
        if (Math.abs(diff) > 0.5) {
            p.setRotationAngle(currentRot + Math.signum(diff) * KNOCKBACK_ROTATION_SPEED);
        }
        // 当接近墙壁或击退结束时，缓慢恢复正常角度
        if (p.isReturningToWall() && Math.abs(p.getRotationAngle()) > 0.5) {
            p.setRotationAngle(p.getRotationAngle() * 0.92);
        }

        // 结束条件：回到墙壁或时间到
        boolean backToWall = ("left".equals(p.getSide()) && p.getX() <= WALL_WIDTH + 8)
                || ("right".equals(p.getSide()) && p.getX() >= CANVAS_WIDTH - WALL_WIDTH - PLAYER_SIZE - 8);
        if ((backToWall && p.isReturningToWall()) || p.getKnockbackTimer() <= 0) {
            p.setKnockedBack(false);
            p.setReturningToWall(false);
            p.setKnockbackTimer(0);
            p.setRotationAngle(0);
            p.setTargetRotation(0);
            p.setVy(0);
            // 确保回到墙壁
            boolean onLeft = "left".equals(p.getSide());
            p.setX(onLeft ? WALL_WIDTH + 5 : CANVAS_WIDTH - WALL_WIDTH - PLAYER_SIZE - 5);
        }
    }

    private static void processInvincibility(Player p) {
        if (!p.isInvincible()) return;
        p.setInvincibleTimer(p.getInvincibleTimer() - 0.016);
        // 同步A×3 powerUp计时器
        if (COLLECTIBLE_A.equals(p.getActivePowerUp())) {
            p.setPowerUpTimer(p.getPowerUpTimer() - 0.016);
            if (p.getPowerUpTimer() <= 0) {
                p.setActivePowerUp("");
                p.setPowerUpTimer(0);
                p.getEffects().remove("rainbow_sparkle");
            }
        }
        if (p.getInvincibleTimer() <= 0) {
            p.setInvincible(false);
            p.setInvincibleTimer(0);
            // 清理A×3残留
            if (COLLECTIBLE_A.equals(p.getActivePowerUp())) {
                p.setActivePowerUp("");
                p.setPowerUpTimer(0);
                p.getEffects().remove("rainbow_sparkle");
            }
        }
    }

    /* ============================================================
       摄像机系统
       ============================================================ */

    private static void updateDisplayCamera(GameState state, List<Player> activePlayers) {
        if (activePlayers.isEmpty()) return;
        double leadCamY = activePlayers.stream()
                .mapToDouble(Player::getCameraY)
                .min()
                .orElse(state.getCameraY());
        state.setCameraTargetY(leadCamY);
        state.setCameraY(state.getCameraY() + (state.getCameraTargetY() - state.getCameraY()) * CAMERA_SMOOTH);
    }

    private static void updatePlayerCameras(List<Player> activePlayers, Map<String, Boolean> blockedMap) {
        for (Player player : activePlayers) {
            // 【修复】暂停玩家摄像机冻结
            if (player.isPaused()) continue;
            boolean isBlocked = Boolean.TRUE.equals(blockedMap.get(player.getId()));
            if (!isBlocked) {
                player.setCameraTargetY(player.getY() - CANVAS_HEIGHT * CAMERA_OFFSET_RATIO);
            } else {
                player.setCameraTargetY(player.getCameraTargetY() - CLIMB_SPEED);
            }
            if (player.getCameraY() == 0) {
                player.setCameraY(player.getCameraTargetY());
            }
            player.setCameraY(player.getCameraY() + (player.getCameraTargetY() - player.getCameraY()) * CAMERA_SMOOTH);
        }
    }

    /* ============================================================
       死亡判定与重生
       ============================================================ */

    private static void checkDeathLine(GameState state) {
        for (Player player : state.getPlayers().values()) {
            // 【修复】暂停玩家不死亡
            if (!player.isActive() || player.isPaused()) continue;
            double deathLine = player.getCameraY() + CANVAS_HEIGHT + DEATH_LINE_OFFSET;
            if (player.getY() > deathLine) {
                applyDeath(state, player);
            }
        }
    }

    private static void applyDeath(GameState state, Player player) {
        player.setLives(player.getLives() - 1);
        if (player.getLives() <= 0) {
            // 爱心用完：保存最高分，进入旁观模式
            if (player.getScore() > player.getHighScore()) {
                player.setHighScore(player.getScore());
            }
            player.setActive(false);
            player.setSpectator(true);
        } else {
            // 还有生命：只是丢一颗心，重生到后方，分数不重置
            double fallbackY = 0;
            for (Player p : state.getPlayers().values()) {
                if (p.isActive() && p.getY() < fallbackY) {
                    fallbackY = p.getY();
                }
            }
            double spawnY = fallbackY + 300; // 落后30m = 300像素
            player.setJumping(false);
            player.setVy(0);
            player.setSide("left".equals(player.getSide()) ? "right" : "left");
            player.setX("left".equals(player.getSide()) ? WALL_WIDTH : CANVAS_WIDTH - WALL_WIDTH - player.getWidth());
            player.setY(spawnY);
            // 分数不重置，保留当前分数
            player.setJoinOffsetY(spawnY);
            player.setBlocked(false);
            player.setPaused(false);
            player.setKnockedBack(false);
            player.setInvincible(true);
            player.setInvincibleTimer(2.0);
            player.setSpectator(false);
            // 重置摄像机
            double spawnCamY = spawnY - CANVAS_HEIGHT * CAMERA_OFFSET_RATIO;
            player.setCameraY(spawnCamY);
            player.setCameraTargetY(spawnCamY);
        }
    }

    private static void checkAllDead(GameState state) {
        boolean allDead = state.getPlayers().values().stream().noneMatch(Player::isActive);
        if (allDead && !state.getPlayers().isEmpty()) {
            state.setPhase("gameover");
        }
    }

    /* ============================================================
       玩家间碰撞
       ============================================================ */

    private static boolean checkPlayerCollision(Player a, Player b) {
        return rectIntersect(a.getX(), a.getY(), a.getWidth(), a.getHeight(),
                b.getX(), b.getY(), b.getWidth(), b.getHeight());
    }

    private static void resolvePlayerCollision(Player a, Player b) {
        double dx = (a.getX() + a.getWidth() / 2) - (b.getX() + b.getWidth() / 2);
        double dy = (a.getY() + a.getHeight() / 2) - (b.getY() + b.getHeight() / 2);
        double overlapX = (a.getWidth() + b.getWidth()) / 2 - Math.abs(dx);
        double overlapY = (a.getHeight() + b.getHeight()) / 2 - Math.abs(dy);

        boolean aJumping = a.isJumping();
        boolean bJumping = b.isJumping();
        if (aJumping && bJumping) {
            // 【修复】两个都在跳跃途中的玩家碰撞：互相弹开，类似撞到尖刺效果
            // 根据相对位置决定弹开方向
            if (dx > 0) {
                // a 在 b 右边，a 弹向右边，b 弹向左边
                applyKnockback(a, "left");
                applyKnockback(b, "right");
            } else {
                // a 在 b 左边，a 弹向左边，b 弹向右边
                applyKnockback(a, "right");
                applyKnockback(b, "left");
            }
            // 双跳跃碰撞后，knockback已经把玩家弹到墙壁外，不再做overlap分离（避免又推回墙壁内）
            return;
        } else if (aJumping && !bJumping) {
            applyKnockback(b, a.getSide());
            double pushX = "left".equals(a.getSide()) ? JUMP_SPEED * 0.8 : -JUMP_SPEED * 0.8;
            a.setX(a.getX() + pushX);
        } else if (bJumping && !aJumping) {
            applyKnockback(a, b.getSide());
            double pushX = "left".equals(b.getSide()) ? JUMP_SPEED * 0.8 : -JUMP_SPEED * 0.8;
            b.setX(b.getX() + pushX);
        }

        // 非双跳跃碰撞时，做overlap分离
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

    private static void applyKnockback(Player victim, String attackerSide) {
        victim.setInvincible(true);
        victim.setInvincibleTimer(2.5);
        victim.setKnockedBack(true);
        victim.setReturningToWall(false);
        victim.setKnockbackTimer(KNOCKBACK_DURATION);

        // 被撞开：向攻击者反方向弹出到墙壁外
        boolean pushToLeft = "right".equals(attackerSide);
        double pushDir = pushToLeft ? -1.0 : 1.0;
        // 确保弹出到墙壁外一定距离
        double wallEdge = "left".equals(victim.getSide()) ? WALL_WIDTH : CANVAS_WIDTH - WALL_WIDTH - victim.getWidth();
        double targetX = wallEdge + pushDir * KNOCKBACK_PUSH_X;
        // 限制在画布范围内
        targetX = Math.max(5, Math.min(targetX, CANVAS_WIDTH - victim.getWidth() - 5));
        victim.setX(targetX);
        victim.setVy(KNOCKBACK_VY);

        // 设置旋转目标：向被撞方向倾斜
        victim.setTargetRotation(pushToLeft ? -KNOCKBACK_ROTATION : KNOCKBACK_ROTATION);
        victim.setRotationAngle(0);
        victim.setJumping(false);
    }

    /* ============================================================
       工具方法
       ============================================================ */

    private static boolean rectIntersect(double x1, double y1, double w1, double h1,
                                         double x2, double y2, double w2, double h2) {
        return x1 < x2 + w2 && x1 + w1 > x2 && y1 < y2 + h2 && y1 + h1 > y2;
    }

    private static int random(int min, int max) {
        return RANDOM.nextInt(max - min + 1) + min;
    }

    private static List<Player> getActivePlayers(GameState state) {
        List<Player> active = new ArrayList<>();
        for (Player p : state.getPlayers().values()) {
            if (p.isActive()) active.add(p);
        }
        return active;
    }

    /* ============================================================
       公共接口
       ============================================================ */

    public static void handleInput(Player player, String inputType) {
        // 【修复】暂停玩家不接受输入
        if (!player.isActive() || player.isPaused()) return;
        if ("jump".equals(inputType) && !player.isJumping()) {
            player.setVy(JUMP_VY);
            player.setJumping(true);
        }
        // 预留：技能输入处理
        // if ("skill_dash".equals(inputType)) { ... }
        // if ("skill_double_jump".equals(inputType)) { ... }
    }

    public static void startGame(GameState state) {
        if ("menu".equals(state.getPhase()) || "gameover".equals(state.getPhase())) {
            initState(state);
            state.setPhase("playing");
        }
    }
}

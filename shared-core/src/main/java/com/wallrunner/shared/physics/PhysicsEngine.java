package com.wallrunner.shared.physics;

import com.wallrunner.shared.entity.GameState;
import com.wallrunner.shared.entity.Player;
import com.wallrunner.shared.physics.subsystem.*;
import com.wallrunner.shared.event.GameEventBus;
import com.wallrunner.shared.event.PhaseChangeEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.wallrunner.shared.constants.GameConstants.*;

/**
 * 物理引擎主实现 —— Facade + 组合模式。
 * 
 * UML 建模意义：
 * - 实现 IPhysicsEngine 接口（实现关系）。
 * - 组合 12 个子系统（组合关系，实心菱形）。
 * - 通过 GameEventBus 发布事件（依赖关系）。
 * 
 * 设计原则：
 * - 单一职责（SRP）：本类仅做调度，不做具体物理计算。
 * - 依赖倒置（DIP）：依赖子系统接口而非实现。
 * - 开闭原则（OCP）：新增子系统只需修改构造器，不影响调用方。
 */
public class PhysicsEngine implements IPhysicsEngine {

    private final IMovementSystem movementSystem;
    private final ICollisionDetector collisionDetector;
    private final IObstacleManager obstacleManager;
    private final ICollectibleManager collectibleManager;
    private final ICameraSystem cameraSystem;
    private final IKnockbackSystem knockbackSystem;
    private final IInvincibilitySystem invincibilitySystem;
    private final IDeathSystem deathSystem;
    private final IScoreCalculator scoreCalculator;
    private final IDifficultyManager difficultyManager;
    private final IPlayerCollisionResolver playerCollisionResolver;
    private final IInputHandler inputHandler;
    private final GameEventBus eventBus;

    public PhysicsEngine(IMovementSystem movementSystem,
                         ICollisionDetector collisionDetector,
                         IObstacleManager obstacleManager,
                         ICollectibleManager collectibleManager,
                         ICameraSystem cameraSystem,
                         IKnockbackSystem knockbackSystem,
                         IInvincibilitySystem invincibilitySystem,
                         IDeathSystem deathSystem,
                         IScoreCalculator scoreCalculator,
                         IDifficultyManager difficultyManager,
                         IPlayerCollisionResolver playerCollisionResolver,
                         IInputHandler inputHandler,
                         GameEventBus eventBus) {
        this.movementSystem = movementSystem;
        this.collisionDetector = collisionDetector;
        this.obstacleManager = obstacleManager;
        this.collectibleManager = collectibleManager;
        this.cameraSystem = cameraSystem;
        this.knockbackSystem = knockbackSystem;
        this.invincibilitySystem = invincibilitySystem;
        this.deathSystem = deathSystem;
        this.scoreCalculator = scoreCalculator;
        this.difficultyManager = difficultyManager;
        this.playerCollisionResolver = playerCollisionResolver;
        this.inputHandler = inputHandler;
        this.eventBus = eventBus;
    }

    /**
     * 工厂方法：构建默认配置的物理引擎。
     * UML 建模意义：展示创建型模式（工厂方法）。
     */
    public static PhysicsEngine createDefault(GameEventBus eventBus) {
        ICollisionDetector collisionDetector = new CollisionSystem();
        IKnockbackSystem knockbackSystem = new KnockbackSystem();
        return new PhysicsEngine(
                new MovementSystem(collisionDetector),
                collisionDetector,
                new ObstacleManager(collisionDetector),
                new CollectibleManager(collisionDetector),
                new CameraSystem(),
                knockbackSystem,
                new InvincibilitySystem(),
                new DeathSystem(),
                new ScoreSystem(),
                new DifficultyManager(),
                new PlayerCollisionResolver(knockbackSystem),
                new InputHandler(),
                eventBus
        );
    }

    @Override
    public void initState(GameState state) {
        state.getObstacles().clear();
        state.getCollectibles().clear();
        state.setFrames(0);
        state.setPhase("menu");
        state.setDifficultyLevel(1);
        state.setDifficultyAccumulator(0.0);

        int i = 0;
        double initCameraY = 300 - CANVAS_HEIGHT * CAMERA_OFFSET_RATIO;
        for (Player p : state.getPlayers().values()) {
            resetPlayer(p, i, initCameraY);
            i++;
        }
        state.setCameraY(initCameraY);
        state.setCameraTargetY(initCameraY);
        state.setNextSpawnCameraY(initCameraY - random(40, 80));
        state.setTimeBonusAccumulator(0.0);
        if (state.getTimeBonusInterval() <= 0) state.setTimeBonusInterval(5.0);
        if (state.getTimeBonusPoints() <= 0) state.setTimeBonusPoints(10);
        state.setNextCollectibleSpawnY(initCameraY - 200);
    }

    private void resetPlayer(Player p, int index, double initCameraY) {
        p.setActive(true);
        p.setSide((index % 2 == 0) ? "left" : "right");
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
        p.setEffects(new java.util.ArrayList<>());
        p.setCollectibleType("");
        p.setCollectibleCount(0);
    }

    @Override
    public void update(GameState state) {
        if (!"playing".equals(state.getPhase())) return;
        state.setFrames(state.getFrames() + 1);

        List<Player> activePlayers = getActivePlayers(state);
        if (activePlayers.isEmpty()) {
            deathSystem.checkAllDead(state);
            return;
        }

        // 1. 障碍物移动与回收
        obstacleManager.updateObstacles(state, activePlayers);

        // 2. 击退、无敌状态处理
        for (Player p : activePlayers) {
            knockbackSystem.processKnockback(p);
            invincibilitySystem.processInvincibility(p);
        }

        // 3. 玩家移动与碰撞
        Map<String, Boolean> blockedMap = new HashMap<>();
        List<Player> collidable = new ArrayList<>();
        for (Player player : activePlayers) {
            if (!player.isPaused()) {
                boolean blocked = movementSystem.updatePlayerMovement(player, state.getObstacles(), activePlayers);
                blockedMap.put(player.getId(), blocked);
                collidable.add(player);
            } else {
                blockedMap.put(player.getId(), false);
            }
        }

        // 4. 玩家间碰撞
        for (int i = 0; i < collidable.size(); i++) {
            for (int j = i + 1; j < collidable.size(); j++) {
                Player a = collidable.get(i);
                Player b = collidable.get(j);
                if (a.isPaused() || b.isPaused()) continue;
                if (playerCollisionResolver.checkPlayerCollision(a, b)) {
                    playerCollisionResolver.resolvePlayerCollision(a, b);
                }
            }
        }

        // 5. 每个玩家独立摄像机
        cameraSystem.updatePlayerCameras(activePlayers, blockedMap);

        // 6. 显示摄像机
        cameraSystem.updateDisplayCamera(state, activePlayers);

        // 7. 同步 blocked 标记
        for (Player p : activePlayers) {
            p.setBlocked(blockedMap.getOrDefault(p.getId(), false));
        }

        // 8. 死亡判断
        deathSystem.checkDeathLine(state);

        // 9. 检查全灭
        deathSystem.checkAllDead(state);

        // 10. 时间奖励
        scoreCalculator.applyTimeBonus(state, activePlayers);

        // 11. 重新计分
        scoreCalculator.recalculateScores(activePlayers);

        // 12. 生成障碍物
        obstacleManager.checkSpawn(state);

        // 13. 难度递增
        difficultyManager.updateDifficulty(state, activePlayers);

        // 14. 可收集物系统
        collectibleManager.updateCollectibles(state, activePlayers);
    }

    @Override
    public void handleInput(Player player, String inputType) {
        inputHandler.handleInput(player, inputType);
    }

    @Override
    public void startGame(GameState state) {
        if ("menu".equals(state.getPhase()) || "gameover".equals(state.getPhase())) {
            String oldPhase = state.getPhase();
            initState(state);
            state.setPhase("playing");
            eventBus.publish(new PhaseChangeEvent("system", oldPhase, "playing"));
        }
    }

    @Override
    public void initJoiningPlayer(GameState state, Player player) {
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

    private List<Player> getActivePlayers(GameState state) {
        List<Player> active = new ArrayList<>();
        for (Player p : state.getPlayers().values()) {
            if (p.isActive()) active.add(p);
        }
        return active;
    }

    private static final java.util.Random RANDOM = new java.util.Random();

    private int random(int min, int max) {
        return RANDOM.nextInt(max - min + 1) + min;
    }
}

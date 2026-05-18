package com.wallrunner.shared.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 【模块】shared-core / entity
 * 【代号】Y
 * 【职责】封装单局游戏的完整运行时状态。
 * 【注意】本类非线程安全，外部调用方需自行加锁（Server用ConcurrentHashMap，Client用单线程JavaFX）。
 * 【修复】2026-05-10:
 *       1. 移除未使用的 random 字段，彻底消除 Jackson 序列化/反序列化报错。
 *       2. 移除 getActivePlayers() 方法（无任何外部调用），避免 Jackson convertValue 时
 *          将其识别为属性并序列化为 activePlayers 字段。
 */
public class GameState {
    private Map<String, Player> players = new HashMap<>();
    private List<Obstacle> obstacles = new ArrayList<>();
    private String phase = "menu";
    private int frames = 0;
    private double cameraY = 0;
    private double cameraTargetY = 0;
    private double nextSpawnCameraY = 0;
    private double timeBonusInterval = 5.0;   // 时间奖励间隔（秒）
    private int timeBonusPoints = 10;           // 时间奖励分数
    private double timeBonusAccumulator = 0.0; // 时间奖励累加器（秒）

    public GameState() {}

    public Map<String, Player> getPlayers() { return players; }
    public void setPlayers(Map<String, Player> players) { this.players = players; }
    public List<Obstacle> getObstacles() { return obstacles; }
    public void setObstacles(List<Obstacle> obstacles) { this.obstacles = obstacles; }
    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }
    public int getFrames() { return frames; }
    public void setFrames(int frames) { this.frames = frames; }
    public double getCameraY() { return cameraY; }
    public void setCameraY(double cameraY) { this.cameraY = cameraY; }
    public double getCameraTargetY() { return cameraTargetY; }
    public void setCameraTargetY(double cameraTargetY) { this.cameraTargetY = cameraTargetY; }
    public double getNextSpawnCameraY() { return nextSpawnCameraY; }
    public void setNextSpawnCameraY(double nextSpawnCameraY) { this.nextSpawnCameraY = nextSpawnCameraY; }
    public double getTimeBonusInterval() { return timeBonusInterval; }
    public void setTimeBonusInterval(double timeBonusInterval) { this.timeBonusInterval = timeBonusInterval; }
    public int getTimeBonusPoints() { return timeBonusPoints; }
    public void setTimeBonusPoints(int timeBonusPoints) { this.timeBonusPoints = timeBonusPoints; }
    public double getTimeBonusAccumulator() { return timeBonusAccumulator; }
    public void setTimeBonusAccumulator(double timeBonusAccumulator) { this.timeBonusAccumulator = timeBonusAccumulator; }
}

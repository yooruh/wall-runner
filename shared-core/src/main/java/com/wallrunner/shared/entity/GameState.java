package com.wallrunner.shared.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 【模块】shared-core / entity
 * 【代号】Y
 * 【职责】封装单局游戏的完整运行时状态。
 * 【注意】本类非线程安全，外部调用方需自行加锁（Server用ConcurrentHashMap，Client用单线程JavaFX）。
 */
public class GameState {
    private Map<String, Player> players = new HashMap<>();
    private List<Obstacle> obstacles = new ArrayList<>();
    private String phase = "menu";
    private int frames = 0;
    private double cameraY = 0;
    private double cameraTargetY = 0;
    private double nextSpawnCameraY = 0;
    private final Random random = new Random();

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
    public Random getRandom() { return random; }

    public List<Player> getActivePlayers() {
        List<Player> active = new ArrayList<>();
        for (Player p : players.values()) {
            if (p.isActive()) active.add(p);
        }
        return active;
    }
}

package com.wallrunner.client.service;

import com.wallrunner.shared.entity.GameState;
import com.wallrunner.shared.entity.Player;
import com.wallrunner.shared.physics.GamePhysics;

import java.util.HashMap;
import java.util.Map;
import java.util.prefs.Preferences;

/**
 * 【模块】client / service
 * 【代号】Y + Z
 * 【职责】客户端状态管理。所有模式（离线/房主/客机）的本地状态中心。
 * 【原则】
 *       1. 本地物理驱动画面，网络状态仅用于校正（reconcile）。
 *       2. reconcile 同步权威状态中的所有玩家（添加缺失、更新已有、移除离线）。
 *       3. 本地玩家使用平滑插值校正，避免画面跳变；其他玩家直接覆盖。
 * 【修复】2026-05-10:
 *       1. reconcile 全面同步：玩家、障碍物、摄像机、阶段、时间奖励等所有字段。
 *       2. 自动清理本地状态中已断开（disconnected）的玩家，避免幽灵玩家。
 */
public class StateManager {

    private static final StateManager INSTANCE = new StateManager();
    public static StateManager getInstance() { return INSTANCE; }

    private GameState state = new GameState();
    private final Preferences prefs = Preferences.userNodeForPackage(StateManager.class);
    private String localPlayerId = "local";

    private StateManager() {}

    public void setLocalPlayerId(String id) { this.localPlayerId = id; }
    public String getLocalPlayerId() { return localPlayerId; }

    /** 初始化离线/房主的本地权威状态 */
    public void initLocalState(String playerName) {
        state = new GameState();
        Player local = new Player(localPlayerId, playerName);
        state.getPlayers().put(localPlayerId, local);
        GamePhysics.initState(state);
        state.setPhase("menu");
    }

    public void initLocalState(String playerName, double timeBonusInterval, int timeBonusPoints) {
        initLocalState(playerName);
        state.setTimeBonusInterval(timeBonusInterval);
        state.setTimeBonusPoints(timeBonusPoints);
    }

    public GameState getState() { return state; }
    public void setState(GameState newState) { this.state = newState; }

    /**
     * 将权威状态平滑合并到本地状态。
     * 适用于所有网络模式（Dedicated / P2P），确保客机画面流畅且同步。
     */
    public void reconcile(GameState auth) {
        if (auth == null) return;

        // 1. 同步全局字段
        state.setPhase(auth.getPhase());
        state.setFrames(auth.getFrames());
        state.setObstacles(auth.getObstacles());
        state.setCameraY(auth.getCameraY());
        state.setCameraTargetY(auth.getCameraTargetY());
        state.setNextSpawnCameraY(auth.getNextSpawnCameraY());
        state.setTimeBonusInterval(auth.getTimeBonusInterval());
        state.setTimeBonusPoints(auth.getTimeBonusPoints());
        state.setTimeBonusAccumulator(auth.getTimeBonusAccumulator());

        // 2. 同步玩家：权威状态中的玩家添加到本地，本地多余的标记为断开
        Map<String, Player> localPlayers = state.getPlayers();
        Map<String, Player> authPlayers = auth.getPlayers();

        // 添加/更新权威玩家
        for (Map.Entry<String, Player> e : authPlayers.entrySet()) {
            String pid = e.getKey();
            Player authPlayer = e.getValue();
            Player localPlayer = localPlayers.get(pid);

            if (localPlayer == null) {
                // 新玩家：深拷贝加入
                localPlayers.put(pid, clonePlayer(authPlayer));
            } else if (pid.equals(localPlayerId)) {
                // 本地玩家：平滑校正（避免画面跳变）
                smoothCorrect(localPlayer, authPlayer);
            } else {
                // 其他玩家：直接覆盖（网络延迟下直接覆盖更稳定）
                copyPlayer(localPlayer, authPlayer);
            }
        }

        // 3. 清理：本地有但权威已移除的玩家标记为断开（不立即删除，避免闪烁）
        for (Player p : localPlayers.values()) {
            if (!authPlayers.containsKey(p.getId())) {
                p.setDisconnected(true);
            }
        }
    }

    /** 本地玩家平滑校正：位置差异大时直接同步，小时插值 */
    private void smoothCorrect(Player local, Player auth) {
        double dx = Math.abs(auth.getX() - local.getX());
        double dy = Math.abs(auth.getY() - local.getY());
        if (dx > 5 || dy > 5) {
            local.setX(auth.getX());
            local.setY(auth.getY());
            local.setVy(auth.getVy());
            local.setSide(auth.getSide());
            local.setJumping(auth.isJumping());
        } else {
            local.setX(local.getX() + (auth.getX() - local.getX()) * 0.3);
            local.setY(local.getY() + (auth.getY() - local.getY()) * 0.3);
        }
        local.setScore(auth.getScore());
        local.setLives(auth.getLives());
        local.setActive(auth.isActive());
        local.setPaused(auth.isPaused());
        local.setBlocked(auth.isBlocked());
        local.setCameraY(auth.getCameraY());
        local.setCameraTargetY(auth.getCameraTargetY());
        local.setJoinOffsetY(auth.getJoinOffsetY());
        local.setTimeBonusScore(auth.getTimeBonusScore());
        local.setDisconnected(auth.isDisconnected());
    }

    /** 完全复制玩家属性 */
    private void copyPlayer(Player target, Player source) {
        target.setX(source.getX());
        target.setY(source.getY());
        target.setVy(source.getVy());
        target.setSide(source.getSide());
        target.setJumping(source.isJumping());
        target.setScore(source.getScore());
        target.setLives(source.getLives());
        target.setActive(source.isActive());
        target.setPaused(source.isPaused());
        target.setBlocked(source.isBlocked());
        target.setCameraY(source.getCameraY());
        target.setCameraTargetY(source.getCameraTargetY());
        target.setJoinOffsetY(source.getJoinOffsetY());
        target.setTimeBonusScore(source.getTimeBonusScore());
        target.setDisconnected(source.isDisconnected());
    }

    /** 深拷贝玩家 */
    private Player clonePlayer(Player p) {
        Player c = new Player(p.getId(), p.getName());
        c.setColor(p.getColor());
        c.setX(p.getX()); c.setY(p.getY());
        c.setSide(p.getSide());
        c.setJumping(p.isJumping());
        c.setVy(p.getVy());
        c.setWidth(p.getWidth()); c.setHeight(p.getHeight());
        c.setActive(p.isActive());
        c.setScore(p.getScore()); c.setLives(p.getLives());
        c.setBlocked(p.isBlocked());
        c.setCameraY(p.getCameraY()); c.setCameraTargetY(p.getCameraTargetY());
        c.setPaused(p.isPaused());
        c.setJoinOffsetY(p.getJoinOffsetY());
        c.setTimeBonusScore(p.getTimeBonusScore());
        c.setDisconnected(p.isDisconnected());
        return c;
    }

    public void reset() {
        state = new GameState();
    }

    public String loadSavedName() {
        return prefs.get("wallrunner_name", "玩家");
    }

    public void saveName(String name) {
        prefs.put("wallrunner_name", name != null ? name : "玩家");
    }
}

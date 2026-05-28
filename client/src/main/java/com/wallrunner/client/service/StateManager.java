package com.wallrunner.client.service;

import com.wallrunner.shared.entity.GameState;
import com.wallrunner.shared.entity.Player;
import com.wallrunner.shared.physics.GamePhysics;

import java.util.Map;
import java.util.prefs.Preferences;

/**
 * 客户端状态管理。所有模式的本地状态中心。
 *
 * 原则：
 * - 本地物理驱动画面，网络状态仅用于校正（reconcile）。
 * - reconcile 同步权威状态中的所有玩家（添加缺失、更新已有、移除离线）。
 * - 本地玩家使用平滑插值校正，避免画面跳变；其他玩家直接覆盖。
 */
public class StateManager implements IStateManager {

    private static final StateManager INSTANCE = new StateManager();
    public static StateManager getInstance() { return INSTANCE; }

    private GameState state = new GameState();
    private final Preferences prefs = Preferences.userNodeForPackage(StateManager.class);
    private String localPlayerId = "local";

    private StateManager() {}

    public void setLocalPlayerId(String id) { this.localPlayerId = id; }
    public String getLocalPlayerId() { return localPlayerId; }

    public void initLocalState(String playerName) {
        state = new GameState();
        Player local = new Player(localPlayerId, playerName);
        // 应用保存的自定义颜色
        java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(com.wallrunner.client.controller.SettingsController.class);
        boolean autoColor = prefs.getBoolean("auto_color", true);
        if (!autoColor) {
            String fill = prefs.get("fill_color", "");
            String stroke = prefs.get("stroke_color", "");
            if (fill != null && !fill.isEmpty()) local.setFillColor(fill);
            if (stroke != null && !stroke.isEmpty()) local.setStrokeColor(stroke);
        }
        double strokeWidth = prefs.getDouble("stroke_width", 0.6);
        local.setStrokeWidth(strokeWidth);
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
     */
    public void reconcile(GameState auth) {
        if (auth == null) return;

        state.setPhase(auth.getPhase());
        state.setFrames(auth.getFrames());
        state.setObstacles(auth.getObstacles() != null ? new java.util.ArrayList<>(auth.getObstacles()) : new java.util.ArrayList<>());
        state.setCameraY(auth.getCameraY());
        state.setCameraTargetY(auth.getCameraTargetY());
        state.setNextSpawnCameraY(auth.getNextSpawnCameraY());
        state.setTimeBonusInterval(auth.getTimeBonusInterval());
        state.setTimeBonusPoints(auth.getTimeBonusPoints());
        state.setTimeBonusAccumulator(auth.getTimeBonusAccumulator());

        // 预留：同步可收集物、特效、难度
        state.setCollectibles(auth.getCollectibles() != null ? new java.util.ArrayList<>(auth.getCollectibles()) : new java.util.ArrayList<>());
        state.setActiveEffects(auth.getActiveEffects() != null ? new java.util.ArrayList<>(auth.getActiveEffects()) : new java.util.ArrayList<>());
        state.setDifficultyLevel(auth.getDifficultyLevel());
        state.setDifficultyAccumulator(auth.getDifficultyAccumulator());
        state.setNextCollectibleSpawnY(auth.getNextCollectibleSpawnY());

        Map<String, Player> localPlayers = state.getPlayers();
        Map<String, Player> authPlayers = auth.getPlayers();
        if (authPlayers == null) return;

        for (Map.Entry<String, Player> e : authPlayers.entrySet()) {
            String pid = e.getKey();
            Player authPlayer = e.getValue();
            Player localPlayer = localPlayers.get(pid);

            if (localPlayer == null) {
                localPlayers.put(pid, clonePlayer(authPlayer));
            } else if (pid.equals(localPlayerId)) {
                smoothCorrect(localPlayer, authPlayer);
            } else {
                copyPlayer(localPlayer, authPlayer);
            }
        }

        for (Player p : localPlayers.values()) {
            if (!authPlayers.containsKey(p.getId())) {
                p.setDisconnected(true);
            }
        }
    }

    private void smoothCorrect(Player local, Player auth) {
        double dx = Math.abs(auth.getX() - local.getX());
        double dy = Math.abs(auth.getY() - local.getY());
        if (dx > 5 || dy > 5) {
            local.setX(auth.getX());
            local.setY(auth.getY());
            local.setVy(auth.getVy());
            local.setSide(auth.getSide());
            local.setJumping(auth.isJumping());
        } else if (dx > 0.5 || dy > 0.5) {
            local.setX(local.getX() + (auth.getX() - local.getX()) * 0.15);
            local.setY(local.getY() + (auth.getY() - local.getY()) * 0.15);
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
        local.setInvincible(auth.isInvincible());
        local.setInvincibleTimer(auth.getInvincibleTimer());
        local.setFillColor(auth.getFillColor());
        local.setStrokeColor(auth.getStrokeColor());
        local.setRotationAngle(auth.getRotationAngle());
        local.setKnockedBack(auth.isKnockedBack());
        local.setKnockbackTimer(auth.getKnockbackTimer());
        local.setTargetRotation(auth.getTargetRotation());
        local.setHighScore(auth.getHighScore());
        local.setSpectator(auth.isSpectator());
        local.setLastPingTime(auth.getLastPingTime());
        local.setReturningToWall(auth.isReturningToWall());
        // 预留：同步扩展字段
        local.setEffects(auth.getEffects());
        local.setSkills(auth.getSkills());
        local.setActivePowerUp(auth.getActivePowerUp());
        local.setPowerUpTimer(auth.getPowerUpTimer());
        local.setCoinsCollected(auth.getCoinsCollected());
        local.setComboCount(auth.getComboCount());
        local.setCollectibleType(auth.getCollectibleType());
        local.setCollectibleCount(auth.getCollectibleCount());
    }

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
        target.setInvincible(source.isInvincible());
        target.setInvincibleTimer(source.getInvincibleTimer());
        target.setFillColor(source.getFillColor());
        target.setStrokeColor(source.getStrokeColor());
        target.setStrokeWidth(source.getStrokeWidth());
        target.setRotationAngle(source.getRotationAngle());
        target.setKnockedBack(source.isKnockedBack());
        target.setKnockbackTimer(source.getKnockbackTimer());
        target.setTargetRotation(source.getTargetRotation());
        target.setHighScore(source.getHighScore());
        target.setSpectator(source.isSpectator());
        target.setLastPingTime(source.getLastPingTime());
        target.setReturningToWall(source.isReturningToWall());
        // 预留：同步扩展字段
        target.setEffects(source.getEffects());
        target.setSkills(source.getSkills());
        target.setActivePowerUp(source.getActivePowerUp());
        target.setPowerUpTimer(source.getPowerUpTimer());
        target.setCoinsCollected(source.getCoinsCollected());
        target.setComboCount(source.getComboCount());
        target.setCollectibleType(source.getCollectibleType());
        target.setCollectibleCount(source.getCollectibleCount());
    }

    private Player clonePlayer(Player p) {
        Player c = new Player(p.getId(), p.getName());
        c.setFillColor(p.getFillColor());
        c.setStrokeColor(p.getStrokeColor());
        c.setStrokeWidth(p.getStrokeWidth());
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
        c.setInvincible(p.isInvincible());
        c.setInvincibleTimer(p.getInvincibleTimer());
        c.setRotationAngle(p.getRotationAngle());
        c.setKnockedBack(p.isKnockedBack());
        c.setKnockbackTimer(p.getKnockbackTimer());
        c.setTargetRotation(p.getTargetRotation());
        c.setHighScore(p.getHighScore());
        c.setSpectator(p.isSpectator());
        c.setLastPingTime(p.getLastPingTime());
        c.setReturningToWall(p.isReturningToWall());
        // 预留：复制扩展字段
        c.setEffects(p.getEffects());
        c.setSkills(p.getSkills());
        c.setActivePowerUp(p.getActivePowerUp());
        c.setPowerUpTimer(p.getPowerUpTimer());
        c.setCoinsCollected(p.getCoinsCollected());
        c.setComboCount(p.getComboCount());
        c.setCollectibleType(p.getCollectibleType());
        c.setCollectibleCount(p.getCollectibleCount());
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

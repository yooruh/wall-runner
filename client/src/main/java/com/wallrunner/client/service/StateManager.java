package com.wallrunner.client.service;

import com.wallrunner.shared.entity.GameState;
import com.wallrunner.shared.entity.Player;
import com.wallrunner.shared.physics.GamePhysics;

import java.util.prefs.Preferences;

/**
 * 【模块】client / service
 * 【代号】Y + Z
 * 【职责】客户端状态管理。
 * 【重构】2026-05-08: reconcile 同步每个玩家自身的 cameraY / cameraTargetY。
 */
public class StateManager {

    private static final StateManager INSTANCE = new StateManager();
    public static StateManager getInstance() { return INSTANCE; }

    private GameState state = new GameState();
    private final Preferences prefs = Preferences.userNodeForPackage(StateManager.class);

    private StateManager() {}

    public void initLocalState(String playerName) {
        state = new GameState();
        Player local = new Player("local", playerName);
        state.getPlayers().put("local", local);
        GamePhysics.initState(state);
        state.setPhase("menu");
    }

    public GameState getState() {
        return state;
    }

    public void setState(GameState newState) {
        this.state = newState;
    }

    public void reconcile(GameState authoritative) {
        Player localAuth = authoritative.getPlayers().get("local");
        Player localPred = state.getPlayers().get("local");
        if (localAuth != null && localPred != null) {
            double dx = Math.abs(localAuth.getX() - localPred.getX());
            double dy = Math.abs(localAuth.getY() - localPred.getY());
            if (dx > 5 || dy > 5) {
                localPred.setX(localAuth.getX());
                localPred.setY(localAuth.getY());
                localPred.setVy(localAuth.getVy());
                localPred.setSide(localAuth.getSide());
                localPred.setJumping(localAuth.isJumping());
            } else {
                localPred.setX(localPred.getX() + (localAuth.getX() - localPred.getX()) * 0.3);
                localPred.setY(localPred.getY() + (localAuth.getY() - localPred.getY()) * 0.3);
            }
            localPred.setScore(localAuth.getScore());
            localPred.setLives(localAuth.getLives());
            localPred.setActive(localAuth.isActive());
            localPred.setPaused(localAuth.isPaused());
            localPred.setBlocked(localAuth.isBlocked());
            localPred.setCameraY(localAuth.getCameraY());
            localPred.setCameraTargetY(localAuth.getCameraTargetY());
        }
        state.setObstacles(authoritative.getObstacles());
        state.setCameraY(authoritative.getCameraY());
        state.setCameraTargetY(authoritative.getCameraTargetY());
        state.setPhase(authoritative.getPhase());
        state.setFrames(authoritative.getFrames());
        state.setNextSpawnCameraY(authoritative.getNextSpawnCameraY());
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
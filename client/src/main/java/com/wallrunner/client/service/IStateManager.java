package com.wallrunner.client.service;

import com.wallrunner.shared.entity.GameState;

/**
 * 状态管理器接口。
 * 
 * UML 建模意义：展示状态管理职责的抽象。
 */
public interface IStateManager {
    void setLocalPlayerId(String id);
    String getLocalPlayerId();
    void initLocalState(String playerName);
    void initLocalState(String playerName, double timeBonusInterval, int timeBonusPoints);
    GameState getState();
    void setState(GameState newState);
    void reconcile(GameState auth);
    String loadSavedName();
    void saveName(String name);
}

package com.wallrunner.client.service;

import com.wallrunner.shared.entity.GameState;

import java.util.Map;
import java.util.function.Consumer;

/**
 * WebSocket 客户端接口。
 */
public interface IWebSocketClient {
    boolean connect(String uri);
    void disconnect();
    boolean isConnected();
    void send(Map<String, Object> msg);
    void joinDedicated(String name);
    void createRoom(String name);
    void createRoom(String name, String customRoomId);
    void joinRoom(String roomId, String name);
    void sendInput(String action);
    void sendState(GameState state);
    void sendPong();
    void sendDisconnect();
    String getClientId();
    String getMyId();
    void setMyId(String id);
    String getPlayerName();
    void setPlayerName(String name);
    String getCurrentRoomId();
    void setCurrentRoomId(String roomId);
    double getTimeBonusInterval();
    void setTimeBonusInterval(double interval);
    int getTimeBonusPoints();
    void setTimeBonusPoints(int points);
    void setOnStateReceived(Consumer<GameState> callback);
    void setOnMessage(Consumer<Map<String, Object>> callback);
    void setOnConnectionError(Consumer<String> callback);
    void loadSavedColors();
    String getFillColor();
    void setFillColor(String color);
    String getStrokeColor();
    void setStrokeColor(String color);
    double getStrokeWidth();
    void setStrokeWidth(double width);
    String getServerAddress();
    void setServerAddress(String address);
    int getServerPort();
    void setServerPort(int port);
    String getServerUrl();
    boolean isShowFps();
    void setShowFps(boolean show);
    boolean isSoundEnabled();
    void setSoundEnabled(boolean enabled);
    boolean isPredictionEnabled();
    void setPredictionEnabled(boolean enabled);
    boolean isShowNames();
    void setShowNames(boolean show);
    long getLatency();
}

package com.wallrunner.client.service;

import javafx.scene.Scene;
import javafx.scene.input.KeyCode;

import java.util.Set;
import java.util.function.Consumer;

/**
 * 输入处理器接口（客户端）。
 */
public interface IInputHandler {
    void attach(Scene scene);
    void setOnAction(Consumer<String> callback);
    void setOnSystem(Consumer<String> callback);
    void setSettingsOpen(boolean open);
    void setConfirmOpen(boolean open);
    void setJumpKeys(Set<KeyCode> keys);
    Set<KeyCode> getJumpKeys();
    void onCanvasClick();
}

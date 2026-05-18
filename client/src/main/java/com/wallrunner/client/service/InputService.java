package com.wallrunner.client.service;

import javafx.scene.Scene;
import javafx.scene.input.KeyCode;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 键盘输入抽象。
 *
 * 职责：
 * - 默认跳跃键 = SPACE，ESC / P = 暂停/关闭设置。
 * - 支持多键绑定同一行为。
 * - 保留触屏/鼠标点击跳跃。
 */
public class InputService {

    private final Set<KeyCode> pressed = new HashSet<>();
    private Consumer<String> onAction;
    private Consumer<String> onSystem;
    private Scene scene;
    private boolean settingsOpen = false;
    private boolean confirmOpen = false;
    private final Set<KeyCode> jumpKeys = new LinkedHashSet<>();

    public InputService() {
        jumpKeys.add(KeyCode.SPACE);
    }

    public void attach(Scene scene) {
        if (scene == null) return;
        if (this.scene != null) {
            this.scene.setOnKeyPressed(null);
            this.scene.setOnKeyReleased(null);
        }
        this.scene = scene;
        scene.setOnKeyPressed(e -> {
            if (!pressed.contains(e.getCode())) {
                pressed.add(e.getCode());
                onKeyDown(e.getCode());
            }
        });
        scene.setOnKeyReleased(e -> pressed.remove(e.getCode()));
    }

    public void setOnAction(Consumer<String> callback) {
        this.onAction = callback;
    }

    public void setOnSystem(Consumer<String> callback) {
        this.onSystem = callback;
    }

    public void setSettingsOpen(boolean open) {
        this.settingsOpen = open;
    }

    public void setConfirmOpen(boolean open) {
        this.confirmOpen = open;
    }

    public void setJumpKeys(Set<KeyCode> keys) {
        jumpKeys.clear();
        if (keys != null && !keys.isEmpty()) {
            jumpKeys.addAll(keys);
        } else {
            jumpKeys.add(KeyCode.SPACE);
        }
    }

    public Set<KeyCode> getJumpKeys() {
        return new LinkedHashSet<>(jumpKeys);
    }

    private void onKeyDown(KeyCode code) {
        if (jumpKeys.contains(code)) {
            if (settingsOpen || confirmOpen) return;
            if (onAction != null) onAction.accept("action");
        } else if (code == KeyCode.ESCAPE || code == KeyCode.P) {
            if (settingsOpen) {
                if (onSystem != null) onSystem.accept("close_settings");
            } else if (confirmOpen) {
                if (onSystem != null) onSystem.accept("dismiss_confirm");
            } else {
                if (onSystem != null) onSystem.accept("toggle_pause");
            }
        }
    }

    public void onCanvasClick() {
        if (settingsOpen || confirmOpen) return;
        if (onAction != null) onAction.accept("action");
    }
}

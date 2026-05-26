package com.wallrunner.client.service;

import com.wallrunner.shared.entity.GameState;
import javafx.scene.canvas.Canvas;

/**
 * 渲染器接口。
 * 
 * UML 建模意义：展示渲染管线的抽象，便于策略替换（如 OpenGL / Vulkan）。
 */
public interface IRenderer {
    void bindCanvas(Canvas canvas);
    void render(GameState state, String mode, double renderCameraY, boolean showFps, String localPlayerId);
    void spawnJumpParticles(double x, double y, String side, double width, double height);
}

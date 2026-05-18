package com.wallrunner.client.service;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

import java.util.function.Consumer;

/**
 * 基于 JavaFX Timeline 的游戏主循环，固定 120 FPS 时间步。
 *
 * 原则：仅做调度器，不触碰游戏状态。
 */
public class GameLoopService {

    private final Timeline timeline;
    private Consumer<Double> onTick;
    private long lastNano = 0;

    public GameLoopService() {
        KeyFrame kf = new KeyFrame(Duration.millis(1000.0 / 120), e -> {
            long now = System.nanoTime();
            if (lastNano == 0) {
                lastNano = now;
                return;
            }
            double deltaMs = (now - lastNano) / 1_000_000.0;
            lastNano = now;
            if (onTick != null) {
                onTick.accept(deltaMs);
            }
        });
        timeline = new Timeline(kf);
        timeline.setCycleCount(Timeline.INDEFINITE);
    }

    public void setOnTick(Consumer<Double> callback) {
        this.onTick = callback;
    }

    public void start() {
        lastNano = 0;
        timeline.play();
    }

    public void stop() {
        timeline.stop();
    }
}

package com.wallrunner.client.service;

import javafx.animation.AnimationTimer;

import java.util.function.Consumer;

/**
 * 【模块】client / service
 * 【代号】Z
 * 【职责】基于 JavaFX AnimationTimer 的游戏主循环，提供固定时间步或可变时间步。
 * 【原则】仅做调度器，不触碰游戏状态。
 */
public class GameLoopService {

    private final AnimationTimer timer;
    private Consumer<Double> onTick;
    private long lastNano = 0;

    public GameLoopService() {
        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (lastNano == 0) {
                    lastNano = now;
                    return;
                }
                double deltaMs = (now - lastNano) / 1_000_000.0;
                lastNano = now;
                if (onTick != null) {
                    onTick.accept(deltaMs);
                }
            }
        };
    }

    public void setOnTick(Consumer<Double> callback) {
        this.onTick = callback;
    }

    public void start() {
        lastNano = 0;
        timer.start();
    }

    public void stop() {
        timer.stop();
    }
}

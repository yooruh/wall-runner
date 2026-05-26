package com.wallrunner.client.service;

import java.util.function.Consumer;

/**
 * 游戏循环接口。
 */
public interface IGameLoop {
    void setOnTick(Consumer<Double> callback);
    void start();
    void stop();
}

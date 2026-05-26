package com.wallrunner.shared.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 游戏事件总线 —— Observer 模式实现。
 * 
 * UML 建模意义：中央事件分发器，所有子系统通过此总线解耦通信。
 * 设计原则：依赖倒置（DIP）、单一职责（SRP）。
 */
public class GameEventBus {
    private static final GameEventBus INSTANCE = new GameEventBus();
    public static GameEventBus getInstance() { return INSTANCE; }

    private final Map<GameEvent.EventType, List<Consumer<GameEvent>>> listeners = new ConcurrentHashMap<>();

    private GameEventBus() {}

    public void subscribe(GameEvent.EventType type, Consumer<GameEvent> listener) {
        listeners.computeIfAbsent(type, k -> new ArrayList<>()).add(listener);
    }

    public void unsubscribe(GameEvent.EventType type, Consumer<GameEvent> listener) {
        List<Consumer<GameEvent>> list = listeners.get(type);
        if (list != null) list.remove(listener);
    }

    public void publish(GameEvent event) {
        List<Consumer<GameEvent>> list = listeners.get(event.getType());
        if (list != null) {
            for (Consumer<GameEvent> listener : new ArrayList<>(list)) {
                listener.accept(event);
            }
        }
    }

    public void clear() {
        listeners.clear();
    }
}

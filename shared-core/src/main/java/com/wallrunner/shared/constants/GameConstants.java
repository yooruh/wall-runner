package com.wallrunner.shared.constants;

/**
 * 【模块】shared-core / constants
 * 【代号】Y
 * 【职责】定义游戏世界的全部常量参数，作为服务端与客户端的唯一可信数值源。
 * 【原则】纯常量类，零业务逻辑，零框架依赖。
 * 【修复】2026-05-08: 将移动速度减慢至原速的约三分之一，使游戏更易操控。
 */
public final class GameConstants {
    private GameConstants() {}

    public static final int CANVAS_WIDTH = 400;
    public static final int CANVAS_HEIGHT = 600;
    public static final int WALL_WIDTH = 60;
    public static final int PLAYER_SIZE = 30;
    public static final double JUMP_SPEED = 6.0;
    public static final double GRAVITY = 0.2;
    public static final double CLIMB_SPEED = 1.2;
    public static final double OBSTACLE_SPEED = 1.5;
    public static final int BRICK_W = 40;
    public static final int BRICK_H = 22;
    public static final double SAFE_LEFT = WALL_WIDTH + PLAYER_SIZE + 5;
    public static final double SAFE_RIGHT = CANVAS_WIDTH - WALL_WIDTH - PLAYER_SIZE - 5;
    public static final double CAMERA_OFFSET_RATIO = 0.6;
    public static final double CAMERA_SMOOTH = 0.1;
    public static final int MAX_LIVES = 3;
    public static final double DEATH_LINE_OFFSET = 200.0;
    public static final double SPAWN_MIN_GAP = 90.0;
    public static final double SPAWN_MAX_GAP = 160.0;
    public static final double SPAWN_AHEAD = 100.0;
    public static final int SPIKE_WIDTH = 30;
    public static final int SPIKE_HEIGHT = 50;
    public static final double JUMP_VY = -8;
    public static final String[] PLAYER_COLORS = {
            "#4ecca3", "#3498db", "#f1c40f", "#9b59b6",
            "#e67e22", "#1abc9c", "#e74c3c", "#2ecc71"
    };
}
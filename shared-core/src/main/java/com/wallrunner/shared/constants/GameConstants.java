package com.wallrunner.shared.constants;

/**
 * 【模块】shared-core / constants
 * 【代号】Y
 * 【职责】定义游戏世界的全部常量参数，作为服务端与客户端的唯一可信数值源。
 * 【原则】纯常量类，零业务逻辑，零框架依赖。
 * 【修复】2026-05-08: FPS提升至120，物理常量调整为原值的0.75倍，
 *        使实际游戏速度为原速的1.5倍（120/60*0.75=1.5）。
 */
public final class GameConstants {
    private GameConstants() {}

    public static final int CANVAS_WIDTH = 400;
    public static final int CANVAS_HEIGHT = 600;
    public static final int WALL_WIDTH = 60;
    public static final int PLAYER_SIZE = 30;
    public static final double JUMP_SPEED = 6.4;
    public static final double GRAVITY = 0.36;
    public static final double CLIMB_SPEED = 1.8;
    public static final double OBSTACLE_SPEED = 2.25;
    public static final int BRICK_W = 40;
    public static final int BRICK_H = 22;
    public static final double SAFE_LEFT = WALL_WIDTH + PLAYER_SIZE + 5;
    public static final double SAFE_RIGHT = CANVAS_WIDTH - WALL_WIDTH - PLAYER_SIZE - 5;
    public static final double CAMERA_OFFSET_RATIO = 0.6;
    public static final double CAMERA_SMOOTH = 0.1;
    public static final int MAX_LIVES = 3;
    public static final double DEATH_LINE_OFFSET = 200.0;
    public static final double SPAWN_MIN_GAP = 136.0;
    public static final double SPAWN_MAX_GAP = 240.0;
    public static final double SPAWN_AHEAD = 150.0;
    public static final int SPIKE_WIDTH = 30;
    public static final int SPIKE_HEIGHT = 50;
    public static final double JUMP_VY = -12.0;
    public static final String[] PLAYER_COLORS = {
            "#4ecca3", "#3498db", "#f1c40f", "#9b59b6",
            "#e67e22", "#1abc9c", "#e74c3c", "#2ecc71"
    };
}

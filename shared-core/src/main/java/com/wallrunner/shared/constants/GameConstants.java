package com.wallrunner.shared.constants;

/**
 * 【模块】shared-core / constants
 * 【代号】Y
 * 【职责】定义游戏世界的全部常量参数，作为服务端与客户端的唯一可信数值源。
 * 【原则】纯常量类，零业务逻辑，零框架依赖。
 * 【修复】2026-05-08: FPS提升至120，物理常量调整为原值的0.75倍，
 *        使实际游戏速度为原速的1.5倍（120/60*0.75=1.5）。
 * 【修复】2026-05-11:
 *       1. PLAYER_COLORS 改为成对定义（填充色+描边色），支持自定义角色外观。
 *       2. 新增 KNOCKBACK 常量：被撞后的物理参数。
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

    // 【重构】颜色定义：每对为 {填充色, 描边色}，共8组，确保联机时颜色不重复
    public static final String[][] PLAYER_COLOR_PAIRS = {
            {"#4ecca3", "#3db892"},   // 青绿
            {"#3498db", "#2980b9"},   // 蓝
            {"#f1c40f", "#d4ac0d"},   // 黄
            {"#9b59b6", "#8e44ad"},   // 紫
            {"#e67e22", "#d35400"},   // 橙
            {"#1abc9c", "#16a085"},   // 青
            {"#e74c3c", "#c0392b"},   // 红
            {"#2ecc71", "#27ae60"},   // 绿
    };

    // 【兼容】保留旧数组供旧代码引用（实际使用 PLAYER_COLOR_PAIRS）
    public static final String[] PLAYER_COLORS = {
            "#4ecca3", "#3498db", "#f1c40f", "#9b59b6",
            "#e67e22", "#1abc9c", "#e74c3c", "#2ecc71"
    };

    // 【新增】被撞后物理参数
    public static final double KNOCKBACK_PUSH_X = 15.0;      // 被弹出墙壁的水平距离
    public static final double KNOCKBACK_VY = -3.0;        // 被弹出时的初始垂直速度（向上轻弹）
    public static final double KNOCKBACK_GRAVITY = 0.5;    // 击退阶段重力（比正常重力大，快速下落）
    public static final double KNOCKBACK_RETURN_SPEED = 2.0; // 回到墙壁的速度
    public static final double KNOCKBACK_ROTATION = 25.0;    // 最大旋转角度（度）
    public static final double KNOCKBACK_ROTATION_SPEED = 2.0; // 旋转速度（度/帧）
}

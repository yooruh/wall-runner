package com.wallrunner.shared.constants;

/**
 * 游戏核心常量定义。
 * 
 * 设计原则：
 * - 纯常量类，零业务逻辑，零框架依赖。
 * - 所有数值均为服务端与客户端的唯一可信源。
 * - 预留扩展区用于特效、技能、可收集物等未来功能。
 */
public final class GameConstants {
    private GameConstants() {}

    /* ============================================================
       基础物理与画布参数
       ============================================================ */
    public static final int CANVAS_WIDTH = 400;
    public static final int CANVAS_HEIGHT = 600;
    public static final int WALL_WIDTH = 60;
    public static final int PLAYER_SIZE = 30;
    public static final double JUMP_SPEED = 6;
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
    public static final double RECYCLE_LINE_OFFSET = 800.0;
    public static final double SPAWN_MIN_GAP = 136.0;
    public static final double SPAWN_MAX_GAP = 240.0;
    public static final double SPAWN_AHEAD = 150.0;
    public static final int SPIKE_WIDTH = 30;
    public static final int SPIKE_HEIGHT = 50;
    public static final double JUMP_VY = -12.0;

    /* ============================================================
       玩家外观：每对为 {填充色, 描边色}，共8组，确保联机时颜色不重复
       ============================================================ */
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

    /* ============================================================
       击退物理参数
       ============================================================ */
    public static final double KNOCKBACK_PUSH_X = 45.0;      // 被撞离墙壁的距离
    public static final double KNOCKBACK_VY = -2.5;          // 轻微向上初速度
    public static final double KNOCKBACK_GRAVITY = 0.36;     // 正常重力下落
    public static final double KNOCKBACK_RETURN_SPEED = 2.0; // 回归速度上限
    public static final double KNOCKBACK_ROTATION = 20.0;   // 倾斜角度
    public static final double KNOCKBACK_ROTATION_SPEED = 1.2; // 倾斜速度（缓慢）
    public static final double KNOCKBACK_RETURN_DELAY = 0.8; // 回归前延迟（秒）
    public static final double KNOCKBACK_DURATION = 1.8;   // 击退总时长（秒）

    /* ============================================================
       预留扩展区 —— 特效、技能、可收集物

       使用说明：
       - 新增功能时优先在此区域添加常量，保持代码整洁。
       - 下方接口类（EffectType, SkillType, CollectibleType）为预留枚举骨架，
         实际实现时取消注释并扩展。
       ============================================================ */

    // 特效持续时间（秒）
    public static final double EFFECT_DURATION_DEFAULT = 3.0;
    public static final double EFFECT_DURATION_SHORT = 1.5;
    public static final double EFFECT_DURATION_LONG = 5.0;

    // 技能冷却时间（秒）
    public static final double SKILL_COOLDOWN_DEFAULT = 5.0;

    // 可收集物类型标识（字符串常量，便于 JSON 序列化兼容）
    public static final String COLLECTIBLE_COIN = "coin";
    public static final String COLLECTIBLE_GEM = "gem";
    public static final String COLLECTIBLE_POWERUP = "powerup";
    public static final String COLLECTIBLE_SHIELD = "shield";

    // 收集物系统 A/B/C
    public static final String COLLECTIBLE_A = "A";  // 无敌10秒（彩虹闪烁）
    public static final String COLLECTIBLE_B = "B";  // 加速飞行10秒（吹风粒子）
    public static final String COLLECTIBLE_C = "C";  // 加一条命（满命不加）
    public static final int COLLECTIBLE_MATCH_COUNT = 3;  // 集齐3个同类触发技能
    public static final double COLLECTIBLE_INVINCIBLE_DURATION = 10.0;  // A×3 无敌持续秒数
    public static final double COLLECTIBLE_SPEED_DURATION = 10.0;       // B×3 加速飞行持续秒数
    public static final double COLLECTIBLE_SPEED_MULTIPLIER = 2.5;      // B×3 加速倍率
    public static final double COLLECTIBLE_SPAWN_INTERVAL = 300.0;      // 每300像素高度生成一个收集物
    public static final double COLLECTIBLE_SIZE = 22.0;                 // 收集物尺寸

    // 难度等级参数
    public static final double DIFFICULTY_SPEED_INCREMENT = 0.15;  // 每级速度增量
    public static final int DIFFICULTY_MAX_LEVEL = 10;

    /*
    // 预留枚举骨架（取消注释并扩展即可使用）：

    public enum EffectType {
        SPARKLE,    // 闪光
        TRAIL,      // 拖尾
        EXPLOSION,  // 爆炸
        GHOST,      // 幽灵（半透明）
        SHIELD      // 护盾光环
    }

    public enum SkillType {
        DASH,       // 冲刺
        DOUBLE_JUMP,// 二段跳
        SLOW_MO,    // 子弹时间
        TELEPORT    // 瞬移
    }

    public enum CollectibleType {
        COIN,       // 金币（加分）
        GEM,        // 宝石（大量加分）
        POWERUP,    // 能量（临时加速）
        SHIELD      // 护盾（临时无敌）
    }
    */
}

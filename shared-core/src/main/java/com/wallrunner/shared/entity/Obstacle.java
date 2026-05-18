package com.wallrunner.shared.entity;

/**
 * 障碍物实体数据模型 (POJO)。
 * 
 * 设计原则：
 * - 纯数据结构，无业务逻辑。
 * - 支持多种障碍物类型与动态行为预留。
 */
public class Obstacle {

    private double x;
    private double y;
    private double width;
    private double height;
    private String type;        // "wall_spike" | "floating" | 预留扩展类型
    private String side;        // "left" | "right"（墙壁尖刺用）

    /* ============================================================
       预留扩展区 —— 动态障碍物与难度系统

       difficulty: 难度等级（1-10），影响移动速度、出现频率。
       behavior: 动态行为模式（"static" | "oscillate" | "chase" | "rotate"）。
       moveSpeed: 动态障碍物自身的移动速度。
       moveRange: 动态障碍物的移动范围（像素）。
       phase: 动态障碍物当前相位（用于正弦/余弦运动）。
       ============================================================ */
    private int difficulty = 1;
    private String behavior = "static";
    private double moveSpeed = 0.0;
    private double moveRange = 0.0;
    private double phase = 0.0;

    public Obstacle() {}

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }

    public double getY() { return y; }
    public void setY(double y) { this.y = y; }

    public double getWidth() { return width; }
    public void setWidth(double width) { this.width = width; }

    public double getHeight() { return height; }
    public void setHeight(double height) { this.height = height; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }

    public int getDifficulty() { return difficulty; }
    public void setDifficulty(int difficulty) { this.difficulty = difficulty; }

    public String getBehavior() { return behavior; }
    public void setBehavior(String behavior) { this.behavior = behavior; }

    public double getMoveSpeed() { return moveSpeed; }
    public void setMoveSpeed(double moveSpeed) { this.moveSpeed = moveSpeed; }

    public double getMoveRange() { return moveRange; }
    public void setMoveRange(double moveRange) { this.moveRange = moveRange; }

    public double getPhase() { return phase; }
    public void setPhase(double phase) { this.phase = phase; }
}

package com.wallrunner.shared.entity;

/**
 * 可收集物实体数据模型 (POJO)。
 *
 * 预留扩展：金币、宝石、道具等可收集物品。
 * 当前版本仅定义结构，未在物理引擎中处理碰撞。
 */
public class Collectible {

    private double x;
    private double y;
    private double width = 20;
    private double height = 20;
    private String type;        // "coin" | "gem" | "powerup" | "shield"
    private int value = 10;     // 收集后获得的分数
    private boolean collected = false;

    /* ============================================================
       预留扩展 —— 动态行为与视觉效果
       ============================================================ */
    private double oscillationPhase = 0.0;  // 上下浮动相位
    private double glowIntensity = 0.0;     // 发光强度
    private String effectOnCollect = "";    // 收集时触发的特效标识

    public Collectible() {}

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

    public int getValue() { return value; }
    public void setValue(int value) { this.value = value; }

    public boolean isCollected() { return collected; }
    public void setCollected(boolean collected) { this.collected = collected; }

    public double getOscillationPhase() { return oscillationPhase; }
    public void setOscillationPhase(double oscillationPhase) { this.oscillationPhase = oscillationPhase; }

    public double getGlowIntensity() { return glowIntensity; }
    public void setGlowIntensity(double glowIntensity) { this.glowIntensity = glowIntensity; }

    public String getEffectOnCollect() { return effectOnCollect; }
    public void setEffectOnCollect(String effectOnCollect) { this.effectOnCollect = effectOnCollect; }
}

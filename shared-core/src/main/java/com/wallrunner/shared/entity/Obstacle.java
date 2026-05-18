package com.wallrunner.shared.entity;

/**
 * 【模块】shared-core / entity
 * 【代号】Y
 * 【职责】障碍物实体数据模型 (POJO)。
 */
public class Obstacle {
    private double x;
    private double y;
    private double width;
    private double height;
    private String type;
    private String side;

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
}

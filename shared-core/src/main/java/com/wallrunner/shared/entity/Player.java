package com.wallrunner.shared.entity;

/**
 * 【模块】shared-core / entity
 * 【代号】Y
 * 【职责】玩家实体数据模型 (POJO)。纯数据结构，无业务逻辑。
 * 【原则】仅含字段与访问器，可被 Jackson / Gson 序列化，可被 JavaFX 绑定包装。
 * 【修复】2026-05-08:
 *       1. 添加 joinOffsetY：中途加入时的高度初始值，用于公平计分。
 *       2. 添加 timeBonusScore：时间奖励累计分数，与高度分数分离。
 */
public class Player {
    private String id;
    private String name = "玩家";
    private String color = "#4ecca3";
    private double x;
    private double y;
    private String side = "left";
    private boolean jumping = false;
    private double vy = 0;
    private double width = 30;
    private double height = 30;
    private boolean active = true;
    private int score = 0;
    private int lives = 3;
    private boolean blocked = false;
    private double cameraY = 0;
    private double cameraTargetY = 0;
    private boolean paused = false;
    private double joinOffsetY = 0;      // 中途加入时的初始高度偏移
    private int timeBonusScore = 0;      // 时间奖励累计分数

    // 必须提供无参构造用于反序列化
    public Player() {}

    public Player(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public double getX() { return x; }
    public void setX(double x) { this.x = x; }
    public double getY() { return y; }
    public void setY(double y) { this.y = y; }
    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }
    public boolean isJumping() { return jumping; }
    public void setJumping(boolean jumping) { this.jumping = jumping; }
    public double getVy() { return vy; }
    public void setVy(double vy) { this.vy = vy; }
    public double getWidth() { return width; }
    public void setWidth(double width) { this.width = width; }
    public double getHeight() { return height; }
    public void setHeight(double height) { this.height = height; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
    public int getLives() { return lives; }
    public void setLives(int lives) { this.lives = lives; }
    public boolean isBlocked() { return blocked; }
    public void setBlocked(boolean blocked) { this.blocked = blocked; }
    public double getCameraY() { return cameraY; }
    public void setCameraY(double cameraY) { this.cameraY = cameraY; }
    public double getCameraTargetY() { return cameraTargetY; }
    public void setCameraTargetY(double cameraTargetY) { this.cameraTargetY = cameraTargetY; }
    public boolean isPaused() { return paused; }
    public void setPaused(boolean paused) { this.paused = paused; }
    public double getJoinOffsetY() { return joinOffsetY; }
    public void setJoinOffsetY(double joinOffsetY) { this.joinOffsetY = joinOffsetY; }
    public int getTimeBonusScore() { return timeBonusScore; }
    public void setTimeBonusScore(int timeBonusScore) { this.timeBonusScore = timeBonusScore; }
}

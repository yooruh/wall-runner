package com.wallrunner.shared.entity;

/**
 * 【模块】shared-core / entity
 * 【代号】Y
 * 【职责】玩家实体数据模型 (POJO)。纯数据结构，无业务逻辑。
 * 【原则】仅含字段与访问器，可被 Jackson / Gson 序列化，可被 JavaFX 绑定包装。
 * 【修复】2026-05-08:
 *       1. 添加 joinOffsetY：中途加入时的高度初始值，用于公平计分。
 *       2. 添加 timeBonusScore：时间奖励累计分数，与高度分数分离。
 * 【修复】2026-05-11:
 *       1. 添加 invincible / invincibleTimer：玩家碰撞后的闪烁无敌状态。
 *       2. 添加 fillColor / strokeColor：自定义角色颜色（填充+描边）。
 *       3. 添加 rotationAngle / knockedBack / knockbackTimer：被撞后旋转动画与击退状态。
 */
public class Player {
    private String id;
    private String name = "玩家";
    // 【重构】color 字段保留用于向后兼容，实际使用 fillColor + strokeColor
    private String color = "#4ecca3";
    private String fillColor = "#4ecca3";
    private String strokeColor = "#3db892";
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
    private double joinOffsetY = 0;
    private int timeBonusScore = 0;
    private boolean disconnected = false;
    // 闪烁无敌状态：被撞击后 3 秒内免碰撞
    private boolean invincible = false;
    private double invincibleTimer = 0.0;
    // 【新增】被撞后旋转动画与击退状态
    private double rotationAngle = 0.0;      // 当前旋转角度（度）
    private boolean knockedBack = false;     // 是否在被击退中
    private double knockbackTimer = 0.0;     // 击退倒计时
    private double targetRotation = 0.0;     // 目标旋转角度

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
    public String getFillColor() { return fillColor; }
    public void setFillColor(String fillColor) { this.fillColor = fillColor; }
    public String getStrokeColor() { return strokeColor; }
    public void setStrokeColor(String strokeColor) { this.strokeColor = strokeColor; }
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
    public boolean isDisconnected() { return disconnected; }
    public void setDisconnected(boolean disconnected) { this.disconnected = disconnected; }
    public boolean isInvincible() { return invincible; }
    public void setInvincible(boolean invincible) { this.invincible = invincible; }
    public double getInvincibleTimer() { return invincibleTimer; }
    public void setInvincibleTimer(double invincibleTimer) { this.invincibleTimer = invincibleTimer; }
    public double getRotationAngle() { return rotationAngle; }
    public void setRotationAngle(double rotationAngle) { this.rotationAngle = rotationAngle; }
    public boolean isKnockedBack() { return knockedBack; }
    public void setKnockedBack(boolean knockedBack) { this.knockedBack = knockedBack; }
    public double getKnockbackTimer() { return knockbackTimer; }
    public void setKnockbackTimer(double knockbackTimer) { this.knockbackTimer = knockbackTimer; }
    public double getTargetRotation() { return targetRotation; }
    public void setTargetRotation(double targetRotation) { this.targetRotation = targetRotation; }
}

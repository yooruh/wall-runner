package com.wallrunner.shared.entity;

import java.util.ArrayList;
import java.util.List;

/**
 * 玩家实体数据模型 (POJO)。
 * 
 * 设计原则：
 * - 纯数据结构，无业务逻辑。
 * - 可被 Jackson / Gson 序列化。
 * - 预留特效、技能、可收集物字段，便于未来扩展。
 */
public class Player {

    /* ============================================================
       身份与外观
       ============================================================ */
    private String id;
    private String name = "玩家";
    private String fillColor = "#4ecca3";
    private String strokeColor = "#3db892";
    private double strokeWidth = 0.6;

    /* ============================================================
       位置与运动状态
       ============================================================ */
    private double x;
    private double y;
    private String side = "left";
    private boolean jumping = false;
    private double vy = 0;
    private double width = 30;
    private double height = 30;

    /* ============================================================
       游戏状态
       ============================================================ */
    private boolean active = true;
    private int score = 0;
    private int lives = 3;
    private boolean blocked = false;
    private boolean paused = false;
    private double joinOffsetY = 0;
    private int timeBonusScore = 0;
    private int highScore = 0;

    /* ============================================================
       摄像机（每个玩家独立）
       ============================================================ */
    private double cameraY = 0;
    private double cameraTargetY = 0;

    /* ============================================================
       无敌与击退状态
       ============================================================ */
    private boolean invincible = false;
    private double invincibleTimer = 0.0;
    private double rotationAngle = 0.0;
    private boolean knockedBack = false;
    private double knockbackTimer = 0.0;
    private double targetRotation = 0.0;
    private boolean returningToWall = false;

    /* ============================================================
       网络与旁观
       ============================================================ */
    private boolean disconnected = false;
    private long lastPingTime = 0;
    private boolean pingAcknowledged = true;  // 是否收到上次ping的回应
    private long offlineTime = 0;  // 标记为离线的时间戳（0表示未离线）
    private boolean spectator = false;

    /* ============================================================
       预留扩展区 —— 特效、技能、可收集物

       设计说明：
       - effects: 当前生效的特效列表（如闪光、拖尾、护盾光环）。
       - skills: 已解锁的技能列表（如冲刺、二段跳）。
       - skillCooldowns: 技能冷却倒计时（秒）。
       - activePowerUp: 当前激活的道具类型（如加速、护盾）。
       - powerUpTimer: 道具剩余持续时间（秒）。
       - coinsCollected: 本局收集的金币数（独立计分维度）。
       - comboCount: 连续跳跃/攀爬计数（用于连击加分系统）。
       ============================================================ */
    private List<String> effects = new ArrayList<>();      // 当前特效标识列表
    private List<String> skills = new ArrayList<>();       // 已解锁技能标识列表
    private String activePowerUp = "";                   // 当前激活道具类型
    private double powerUpTimer = 0.0;                   // 道具剩余时间
    private int coinsCollected = 0;                      // 收集金币数
    private int comboCount = 0;                          // 连击计数

    /* ============================================================
       收集物系统 A/B/C
       - collectibleType: 当前连续收集的收集物类型（"A"/"B"/"C"/""）
       - collectibleCount: 当前连续收集的同类型数量（0-3）
       - 收集到不同类型时重置：type变更，count=1
       - 集齐3个同类触发技能后重置：type=""，count=0
       ============================================================ */
    private String collectibleType = "";                 // 当前连续收集的收集物类型
    private int collectibleCount = 0;                    // 当前连续收集的同类型数量

    public Player() {}

    public Player(String id, String name) {
        this.id = id;
        this.name = name;
    }

    /* ============================================================
       Getters & Setters
       ============================================================ */

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getFillColor() { return fillColor; }
    public void setFillColor(String fillColor) { this.fillColor = fillColor; }

    public String getStrokeColor() { return strokeColor; }
    public void setStrokeColor(String strokeColor) { this.strokeColor = strokeColor; }

    public double getStrokeWidth() { return strokeWidth; }
    public void setStrokeWidth(double strokeWidth) { this.strokeWidth = strokeWidth; }

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

    public boolean isPaused() { return paused; }
    public void setPaused(boolean paused) { this.paused = paused; }

    public double getJoinOffsetY() { return joinOffsetY; }
    public void setJoinOffsetY(double joinOffsetY) { this.joinOffsetY = joinOffsetY; }

    public int getTimeBonusScore() { return timeBonusScore; }
    public void setTimeBonusScore(int timeBonusScore) { this.timeBonusScore = timeBonusScore; }

    public int getHighScore() { return highScore; }
    public void setHighScore(int highScore) { this.highScore = highScore; }

    public double getCameraY() { return cameraY; }
    public void setCameraY(double cameraY) { this.cameraY = cameraY; }

    public double getCameraTargetY() { return cameraTargetY; }
    public void setCameraTargetY(double cameraTargetY) { this.cameraTargetY = cameraTargetY; }

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

    public boolean isReturningToWall() { return returningToWall; }
    public void setReturningToWall(boolean returningToWall) { this.returningToWall = returningToWall; }

    public boolean isDisconnected() { return disconnected; }
    public void setDisconnected(boolean disconnected) { this.disconnected = disconnected; }

    public long getLastPingTime() { return lastPingTime; }
    public void setLastPingTime(long lastPingTime) { this.lastPingTime = lastPingTime; }

    public boolean isSpectator() { return spectator; }
    public void setSpectator(boolean spectator) { this.spectator = spectator; }

    public boolean isPingAcknowledged() { return pingAcknowledged; }
    public void setPingAcknowledged(boolean pingAcknowledged) { this.pingAcknowledged = pingAcknowledged; }

    public long getOfflineTime() { return offlineTime; }
    public void setOfflineTime(long offlineTime) { this.offlineTime = offlineTime; }

    /* ============================================================
       扩展字段 Getters & Setters
       ============================================================ */

    public List<String> getEffects() { return effects; }
    public void setEffects(List<String> effects) { this.effects = effects != null ? effects : new ArrayList<>(); }

    public List<String> getSkills() { return skills; }
    public void setSkills(List<String> skills) { this.skills = skills != null ? skills : new ArrayList<>(); }

    public String getActivePowerUp() { return activePowerUp; }
    public void setActivePowerUp(String activePowerUp) { this.activePowerUp = activePowerUp; }

    public double getPowerUpTimer() { return powerUpTimer; }
    public void setPowerUpTimer(double powerUpTimer) { this.powerUpTimer = powerUpTimer; }

    public int getCoinsCollected() { return coinsCollected; }
    public void setCoinsCollected(int coinsCollected) { this.coinsCollected = coinsCollected; }

    public int getComboCount() { return comboCount; }
    public void setComboCount(int comboCount) { this.comboCount = comboCount; }

    public String getCollectibleType() { return collectibleType; }
    public void setCollectibleType(String collectibleType) { this.collectibleType = collectibleType != null ? collectibleType : ""; }

    public int getCollectibleCount() { return collectibleCount; }
    public void setCollectibleCount(int collectibleCount) { this.collectibleCount = collectibleCount; }
}

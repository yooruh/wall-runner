package com.wallrunner.client.service;

import com.wallrunner.shared.constants.GameConstants;
import com.wallrunner.shared.entity.Collectible;
import com.wallrunner.shared.entity.GameState;
import com.wallrunner.shared.entity.Obstacle;
import com.wallrunner.shared.entity.Player;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Canvas 渲染管线。
 *
 * 职责：
 * - 绘制背景、墙壁、障碍物、玩家、HUD、覆盖层。
 * - 支持粒子特效（跳跃尾迹）。
 * - 预留：可收集物渲染、特效渲染、技能可视化。
 */
public class Renderer implements IRenderer {

    private Canvas canvas;
    private GraphicsContext gc;
    private long lastFpsTime = 0;
    private int frameCount = 0;
    private int currentFps = 0;
    private final List<Particle> particles = new ArrayList<>();

    public void bindCanvas(Canvas canvas) {
        this.canvas = canvas;
        this.gc = canvas.getGraphicsContext2D();
    }

    public void render(GameState state, String mode, double renderCameraY, boolean showFps, String localPlayerId) {
        if (gc == null || state == null) return;

        double w = canvas.getWidth();
        double h = canvas.getHeight();
        double camY = renderCameraY;

        gc.setFill(Color.web("#16213e"));
        gc.fillRect(0, 0, w, h);

        drawWalls(camY);
        drawObstacles(state, camY);
        drawCollectibles(state, camY);  // 预留：可收集物渲染
        drawParticles(camY);
        drawPlayers(state, camY, localPlayerId);
        drawOffScreenIndicators(state, camY, localPlayerId);

        Player me = state.getPlayers().get(localPlayerId);
        drawHUD(me);
        if (showFps) {
            drawFPS();
        }

        if ("menu".equals(state.getPhase())) {
            drawMenuOverlay(state, mode);
        } else if ("gameover".equals(state.getPhase())) {
            boolean iAmDead = me != null && !me.isActive();
            drawGameOverOverlay(state, iAmDead, mode);
        }
    }

    public void spawnJumpParticles(double x, double y, String side, double width, double height) {
        int count = 8;
        double baseVx = "left".equals(side) ? -2.5 : 2.5;
        for (int i = 0; i < count; i++) {
            Particle p = new Particle();
            // 从玩家底部中心附近生成，避免错位
            p.x = x + width / 2 + (Math.random() - 0.5) * 10;
            p.y = y + height;
            p.vx = baseVx * (Math.random() * 1.5 + 0.5);
            p.vy = Math.random() * -3 - 0.5;
            p.life = 1.0;
            p.decay = 0.02 + Math.random() * 0.03;
            p.size = 2 + Math.random() * 3;
            p.color = "left".equals(side) ? "#e94560" : "#3498db";
            particles.add(p);
        }
    }

    private void drawParticles(double cameraY) {
        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            Particle p = it.next();
            p.x += p.vx;
            p.y += p.vy;
            p.vy += 0.15;
            p.life -= p.decay;
            if (p.life <= 0) { it.remove(); continue; }
            double sy = p.y - cameraY;
            gc.setGlobalAlpha(p.life);
            gc.setFill(Color.web(p.color));
            gc.fillOval(p.x - p.size, sy - p.size, p.size * 2, p.size * 2);
        }
        gc.setGlobalAlpha(1.0);
    }

    private void drawWalls(double cameraY) {
        double wallTop = cameraY - 200;
        double wallHeight = GameConstants.CANVAS_HEIGHT + 400;

        gc.setFill(Color.web("#0f3460"));
        gc.fillRect(0, wallTop - cameraY, GameConstants.WALL_WIDTH, wallHeight);
        gc.fillRect(GameConstants.CANVAS_WIDTH - GameConstants.WALL_WIDTH, wallTop - cameraY,
                GameConstants.WALL_WIDTH, wallHeight);

        int startRow = (int) Math.floor((cameraY - 100) / GameConstants.BRICK_H);
        int endRow = (int) Math.floor((cameraY + GameConstants.CANVAS_HEIGHT + 100) / GameConstants.BRICK_H);
        for (int row = startRow; row <= endRow; row++) {
            double worldY = row * GameConstants.BRICK_H;
            double screenY = worldY - cameraY;
            double offsetX = (row % 2 == 0) ? 0 : GameConstants.BRICK_W / 2;
            int cols = (int) Math.ceil(GameConstants.WALL_WIDTH / GameConstants.BRICK_W) + 1;
            for (int col = -1; col < cols; col++) {
                double x = col * GameConstants.BRICK_W + offsetX;
                if (x >= 0 && x + GameConstants.BRICK_W <= GameConstants.WALL_WIDTH) {
                    String color = ((row + col) % 2 == 0) ? "#1a5276" : "#2471a3";
                    gc.setFill(Color.web(color));
                    gc.fillRect(x, screenY, GameConstants.BRICK_W - 2, GameConstants.BRICK_H - 2);
                    gc.setStroke(Color.web("#0a2a4a"));
                    gc.setLineWidth(1);
                    gc.strokeRect(x, screenY, GameConstants.BRICK_W - 2, GameConstants.BRICK_H - 2);
                }
                double rx = GameConstants.CANVAS_WIDTH - GameConstants.WALL_WIDTH + x;
                if (rx >= GameConstants.CANVAS_WIDTH - GameConstants.WALL_WIDTH && rx + GameConstants.BRICK_W <= GameConstants.CANVAS_WIDTH) {
                    String color = ((row + col) % 2 == 0) ? "#1a5276" : "#2471a3";
                    gc.setFill(Color.web(color));
                    gc.fillRect(rx, screenY, GameConstants.BRICK_W - 2, GameConstants.BRICK_H - 2);
                    gc.setStroke(Color.web("#0a2a4a"));
                    gc.setLineWidth(1);
                    gc.strokeRect(rx, screenY, GameConstants.BRICK_W - 2, GameConstants.BRICK_H - 2);
                }
            }
        }

        gc.setStroke(Color.web("#e94560"));
        gc.setLineWidth(2);
        gc.strokeLine(GameConstants.WALL_WIDTH, 0, GameConstants.WALL_WIDTH, GameConstants.CANVAS_HEIGHT);
        gc.strokeLine(GameConstants.CANVAS_WIDTH - GameConstants.WALL_WIDTH, 0,
                GameConstants.CANVAS_WIDTH - GameConstants.WALL_WIDTH, GameConstants.CANVAS_HEIGHT);
    }

    private void drawObstacles(GameState state, double cameraY) {
        for (Obstacle obs : state.getObstacles()) {
            double sy = obs.getY() - cameraY;
            if (sy < -200 || sy > 800) continue;
            if ("wall_spike".equals(obs.getType())) {
                gc.setFill(Color.web("#c0392b"));
                if ("left".equals(obs.getSide())) {
                    gc.fillPolygon(
                            new double[]{obs.getX(), obs.getX() + obs.getWidth(), obs.getX()},
                            new double[]{sy, sy + obs.getHeight() / 2, sy + obs.getHeight()}, 3);
                } else {
                    gc.fillPolygon(
                            new double[]{obs.getX() + obs.getWidth(), obs.getX(), obs.getX() + obs.getWidth()},
                            new double[]{sy, sy + obs.getHeight() / 2, sy + obs.getHeight()}, 3);
                }
                gc.setStroke(Color.web("#922b21"));
                gc.setLineWidth(2);
                if ("left".equals(obs.getSide())) {
                    gc.strokePolygon(
                            new double[]{obs.getX(), obs.getX() + obs.getWidth(), obs.getX()},
                            new double[]{sy, sy + obs.getHeight() / 2, sy + obs.getHeight()}, 3);
                } else {
                    gc.strokePolygon(
                            new double[]{obs.getX() + obs.getWidth(), obs.getX(), obs.getX() + obs.getWidth()},
                            new double[]{sy, sy + obs.getHeight() / 2, sy + obs.getHeight()}, 3);
                }
            } else {
                gc.setFill(Color.web("#ff6b6b"));
                gc.fillRect(obs.getX(), sy, obs.getWidth(), obs.getHeight());
                gc.setStroke(Color.web("#c0392b"));
                gc.setLineWidth(2);
                gc.strokeRect(obs.getX(), sy, obs.getWidth(), obs.getHeight());
            }
        }
    }

    // 预留：可收集物渲染
    private void drawCollectibles(GameState state, double cameraY) {
        long time = System.currentTimeMillis();
        for (Collectible c : state.getCollectibles()) {
            if (c.isCollected()) continue;
            double sy = c.getY() - cameraY;
            if (sy < -50 || sy > 650) continue;

            // 浮动效果
            double floatY = sy + Math.sin(time / 300.0 + c.getOscillationPhase()) * 3;
            double cx = c.getX() + c.getWidth() / 2;
            double cy = floatY + c.getHeight() / 2;

            switch (c.getType()) {
                case "A" -> {
                    // A收集物：金色星形，彩虹发光
                    double glowPulse = 0.4 + 0.3 * Math.sin(time / 200.0 + c.getOscillationPhase());
                    // 外发光
                    gc.setGlobalAlpha(glowPulse);
                    gc.setFill(Color.web("#ff6b6b"));
                    gc.fillOval(c.getX() - 6, floatY - 6, c.getWidth() + 12, c.getHeight() + 12);
                    gc.setGlobalAlpha(1.0);
                    // 星形主体
                    gc.setFill(Color.web("#f1c40f"));
                    drawStar(cx, cy, c.getWidth() / 2, c.getWidth() / 4, 5);
                    gc.setStroke(Color.web("#e67e22"));
                    gc.setLineWidth(1.5);
                    drawStarStroke(cx, cy, c.getWidth() / 2, c.getWidth() / 4, 5);
                    // 标签
                    gc.setFill(Color.web("#fff"));
                    gc.setFont(Font.font("Segoe UI Emoji", 10));
                    gc.setTextAlign(TextAlignment.CENTER);
                    gc.fillText("A", cx, cy + 4);
                }
                case "B" -> {
                    // B收集物：蓝色菱形，风圈效果
                    double glowPulse = 0.4 + 0.3 * Math.sin(time / 250.0 + c.getOscillationPhase());
                    // 外发光
                    gc.setGlobalAlpha(glowPulse);
                    gc.setFill(Color.web("#3498db"));
                    gc.fillOval(c.getX() - 6, floatY - 6, c.getWidth() + 12, c.getHeight() + 12);
                    gc.setGlobalAlpha(1.0);
                    // 菱形主体
                    gc.setFill(Color.web("#3498db"));
                    gc.fillPolygon(
                            new double[]{cx, cx + c.getWidth() / 2, cx, cx - c.getWidth() / 2},
                            new double[]{floatY, cy, floatY + c.getHeight(), cy}, 4);
                    gc.setStroke(Color.web("#2980b9"));
                    gc.setLineWidth(1.5);
                    gc.strokePolygon(
                            new double[]{cx, cx + c.getWidth() / 2, cx, cx - c.getWidth() / 2},
                            new double[]{floatY, cy, floatY + c.getHeight(), cy}, 4);
                    // 标签
                    gc.setFill(Color.web("#fff"));
                    gc.setFont(Font.font("Segoe UI Emoji", 10));
                    gc.setTextAlign(TextAlignment.CENTER);
                    gc.fillText("B", cx, cy + 4);
                }
                case "C" -> {
                    // C收集物：红色心形，生命光环
                    double glowPulse = 0.4 + 0.3 * Math.sin(time / 180.0 + c.getOscillationPhase());
                    // 外发光
                    gc.setGlobalAlpha(glowPulse);
                    gc.setFill(Color.web("#e74c3c"));
                    gc.fillOval(c.getX() - 6, floatY - 6, c.getWidth() + 12, c.getHeight() + 12);
                    gc.setGlobalAlpha(1.0);
                    // 圆形主体
                    gc.setFill(Color.web("#e74c3c"));
                    gc.fillOval(c.getX(), floatY, c.getWidth(), c.getHeight());
                    gc.setStroke(Color.web("#c0392b"));
                    gc.setLineWidth(1.5);
                    gc.strokeOval(c.getX(), floatY, c.getWidth(), c.getHeight());
                    // 标签
                    gc.setFill(Color.web("#fff"));
                    gc.setFont(Font.font("Segoe UI Emoji", 10));
                    gc.setTextAlign(TextAlignment.CENTER);
                    gc.fillText("C", cx, cy + 4);
                }
                case "coin" -> {
                    gc.setFill(Color.web("#f1c40f"));
                    gc.fillOval(c.getX(), floatY, c.getWidth(), c.getHeight());
                    gc.setStroke(Color.web("#d4ac0d"));
                    gc.setLineWidth(1);
                    gc.strokeOval(c.getX(), floatY, c.getWidth(), c.getHeight());
                }
                case "gem" -> {
                    gc.setFill(Color.web("#9b59b6"));
                    gc.fillPolygon(
                            new double[]{cx, cx + c.getWidth() / 2, cx, cx - c.getWidth() / 2},
                            new double[]{floatY, cy, floatY + c.getHeight(), cy}, 4);
                }
                case "powerup", "shield" -> {
                    gc.setFill(Color.web("#4ecca3", 0.6));
                    gc.fillOval(c.getX() - 2, floatY - 2, c.getWidth() + 4, c.getHeight() + 4);
                    gc.setFill(Color.web("#fff"));
                    gc.fillOval(c.getX(), floatY, c.getWidth(), c.getHeight());
                }
                default -> {
                    gc.setFill(Color.web("#aaa"));
                    gc.fillRect(c.getX(), floatY, c.getWidth(), c.getHeight());
                }
            }
        }
    }

    private void drawStar(double cx, double cy, double outerR, double innerR, int points) {
        double[] xPoints = new double[points * 2];
        double[] yPoints = new double[points * 2];
        for (int i = 0; i < points * 2; i++) {
            double angle = Math.PI / 2 + i * Math.PI / points;
            double r = (i % 2 == 0) ? outerR : innerR;
            xPoints[i] = cx + r * Math.cos(angle);
            yPoints[i] = cy - r * Math.sin(angle);
        }
        gc.fillPolygon(xPoints, yPoints, points * 2);
    }

    private void drawStarStroke(double cx, double cy, double outerR, double innerR, int points) {
        double[] xPoints = new double[points * 2];
        double[] yPoints = new double[points * 2];
        for (int i = 0; i < points * 2; i++) {
            double angle = Math.PI / 2 + i * Math.PI / points;
            double r = (i % 2 == 0) ? outerR : innerR;
            xPoints[i] = cx + r * Math.cos(angle);
            yPoints[i] = cy - r * Math.sin(angle);
        }
        gc.strokePolygon(xPoints, yPoints, points * 2);
    }

    private void drawPlayers(GameState state, double cameraY, String localPlayerId) {
        for (Player p : state.getPlayers().values()) {
            if (!p.isActive()) continue;
            double sy = p.getY() - cameraY;
            if (sy < -100 || sy > 700) continue;

            String fillColor = p.getFillColor() != null && !p.getFillColor().isEmpty()
                    ? p.getFillColor() : "#4ecca3";
            String strokeColor = p.getStrokeColor() != null && !p.getStrokeColor().isEmpty()
                    ? p.getStrokeColor() : fillColor;

            boolean isMe = p.getId().equals(localPlayerId);
            boolean isPaused = p.isPaused();
            boolean invincible = p.isInvincible();
            boolean knockedBack = p.isKnockedBack();
            double rotation = p.getRotationAngle();

            gc.save();
            // 【修复】暂停玩家半透明（他人客机可见）
            if (isPaused) gc.setGlobalAlpha(0.4);

            if (p.isJumping() && !knockedBack) {
                gc.setFill(Color.web(fillColor, 0.4));
                double tailX = "left".equals(p.getSide()) ? -12 : 12;
                gc.fillRect(p.getX() + tailX, sy + 15, p.getWidth(), p.getHeight() - 25);
                gc.setFill(Color.web(fillColor, 0.15));
                gc.fillRect(p.getX() + tailX * 1.8, sy + 25, p.getWidth(), p.getHeight() - 30);
            }

            // 无敌特效：彩色闪烁（彩虹色循环），所有无敌状态统一特效
            if (invincible && !isPaused) {
                double hue = (System.currentTimeMillis() / 40.0) % 360;
                // 彩虹填充光晕
                gc.setFill(Color.hsb(hue, 0.85, 1.0, 0.5));
                gc.fillRect(p.getX() - 5, sy - 5, p.getWidth() + 10, p.getHeight() + 10);
                // 彩虹描边光环
                gc.setStroke(Color.hsb((hue + 180) % 360, 0.9, 1.0, 0.7));
                gc.setLineWidth(2.5);
                gc.strokeOval(p.getX() - 8, sy - 8, p.getWidth() + 16, p.getHeight() + 16);
                // 玩家本体半透明闪烁
                double alpha = (System.currentTimeMillis() % 200 < 100) ? 0.4 : 0.85;
                gc.setGlobalAlpha(alpha);
            }

            // B×3 加速飞行：吹风粒子效果
            if ("B".equals(p.getActivePowerUp()) && !isPaused) {
                // 速度光环
                double windPhase = System.currentTimeMillis() / 100.0;
                gc.setStroke(Color.web("rgba(135,206,250,0.6)"));
                gc.setLineWidth(2);
                gc.strokeOval(p.getX() - 3, sy - 3, p.getWidth() + 6, p.getHeight() + 6);
                // 风粒子拖尾 —— 修复方向和位置：拖尾在玩家后方（下方）
                for (int wi = 0; wi < 4; wi++) {
                    double windX = "left".equals(p.getSide()) 
                            ? p.getX() - 10 - wi * 8 
                            : p.getX() + p.getWidth() + 5 + wi * 8;
                    double windY = sy + p.getHeight() + 8 + wi * 6 + (Math.sin(windPhase + wi * 1.5) * 6);
                    double windAlpha = 0.7 - wi * 0.15;
                    gc.setGlobalAlpha(windAlpha);
                    gc.setFill(Color.web("#87CEFA"));
                    gc.fillOval(windX, windY, 5, 4);
                }
                gc.setGlobalAlpha(1.0);
            }

            // 击退旋转
            double centerX = p.getX() + p.getWidth() / 2;
            double centerY = sy + p.getHeight() / 2;
            if (knockedBack && Math.abs(rotation) > 0.1) {
                gc.translate(centerX, centerY);
                gc.rotate(rotation);
                gc.translate(-centerX, -centerY);
            }

            // 绘制玩家主体
            gc.setFill(Color.web(fillColor));
            gc.fillRect(p.getX(), sy, p.getWidth(), p.getHeight());

            // 其他玩家：普通可变颜色描边（跟随旋转）
            if (!isMe) {
                gc.setStroke(Color.web(strokeColor));
                gc.setLineWidth(p.getStrokeWidth());
                gc.strokeRect(p.getX(), sy, p.getWidth(), p.getHeight());
            }

            // 恢复变换
            if (knockedBack && Math.abs(rotation) > 0.1) {
                gc.translate(centerX, centerY);
                gc.rotate(-rotation);
                gc.translate(-centerX, -centerY);
            }

            // 本地玩家：可变颜色发光描边（不跟随旋转，位置与原白色描边一致）
            if (isMe) {
                double sw = p.getStrokeWidth();
                // 外层光晕
                gc.setStroke(Color.web(strokeColor, 0.25));
                gc.setLineWidth(sw * 3);
                gc.strokeRect(p.getX() - 2, sy - 2, p.getWidth() + 4, p.getHeight() + 4);
                // 中层光晕
                gc.setStroke(Color.web(strokeColor, 0.55));
                gc.setLineWidth(sw * 2);
                gc.strokeRect(p.getX() - 2, sy - 2, p.getWidth() + 4, p.getHeight() + 4);
                // 内层实线描边
                gc.setStroke(Color.web(strokeColor, 0.9));
                gc.setLineWidth(sw);
                gc.strokeRect(p.getX() - 2, sy - 2, p.getWidth() + 4, p.getHeight() + 4);
            }

            if (invincible && !isPaused) {
                gc.setGlobalAlpha(1.0);
            }

            // 眼睛
            gc.setFill(Color.web("#1a1a2e"));
            if ("left".equals(p.getSide())) {
                gc.fillRect(p.getX() + 5, sy + 8, 6, 6);
                gc.fillRect(p.getX() + 5, sy + 18, 6, 6);
            } else {
                gc.fillRect(p.getX() + 19, sy + 8, 6, 6);
                gc.fillRect(p.getX() + 19, sy + 18, 6, 6);
            }

            // 名字
            gc.setFill(Color.web(isPaused ? "rgba(255,255,255,0.6)" : "white"));
            gc.setFont(Font.font("Segoe UI Emoji", 11));
            String nameText = p.getName() != null ? p.getName() : "玩家";
            if (!p.isActive()) nameText += " [死亡]";
            if (p.isDisconnected()) nameText += " [离线]";
            gc.fillText(nameText, p.getX() + p.getWidth() / 2, sy - 8);

            // 【修复】暂停玩家头顶显示 "暂停" 标签
            if (isPaused) {
                gc.setFill(Color.web("rgba(255,255,255,0.8)"));
                gc.setFont(Font.font("Segoe UI Emoji", 10));
                gc.fillText("暂停", p.getX() + p.getWidth() / 2, sy - 20);
            }

            // 分数
            gc.setFont(Font.font("Segoe UI Emoji", 10));
            gc.setFill(Color.web("#aaa"));
            // 【修复】暂停玩家分数位置上移，避免与"暂停"标签重叠
            gc.fillText(p.getScore() + "分", p.getX() + p.getWidth() / 2, sy - (isPaused ? 32 : 18));

            // 激活技能光环（仅在非无敌状态下显示，避免与无敌彩虹特效重叠）
            if (p.getActivePowerUp() != null && !p.getActivePowerUp().isEmpty() && !invincible) {
                gc.setStroke(Color.web("#4ecca3", 0.5));
                gc.setLineWidth(2);
                gc.strokeOval(p.getX() - 4, sy - 4, p.getWidth() + 8, p.getHeight() + 8);
            }

            gc.restore();
        }
    }



    private void drawOffScreenIndicators(GameState state, double cameraY, String localPlayerId) {
        Player me = state.getPlayers().get(localPlayerId);
        if (me == null || !me.isActive()) return;

        double myY = me.getY();
        // screen boundaries: cameraY ~ cameraY + CANVAS_HEIGHT

        // 收集屏幕外的玩家
        List<Player> above = new ArrayList<>();
        List<Player> below = new ArrayList<>();

        for (Player p : state.getPlayers().values()) {
            if (!p.isActive() || p.getId().equals(localPlayerId)) continue;
            if (p.isPaused()) continue; // 暂停玩家不显示指示器
            double sy = p.getY() - cameraY;
            if (sy < -20) {
                above.add(p);
            } else if (sy > GameConstants.CANVAS_HEIGHT + 20) {
                below.add(p);
            }
        }

        // 按距离排序
        above.sort((a, b) -> Double.compare(Math.abs(a.getY() - myY), Math.abs(b.getY() - myY)));
        below.sort((a, b) -> Double.compare(Math.abs(a.getY() - myY), Math.abs(b.getY() - myY)));

        // 绘制上方指示器（领先玩家）
        if (!above.isEmpty()) {
            drawIndicatorBubble(above, myY, true);
        }
        // 绘制下方指示器（落后玩家）
        if (!below.isEmpty()) {
            drawIndicatorBubble(below, myY, false);
        }
    }

    private void drawIndicatorBubble(List<Player> players, double myY, boolean isAbove) {
        gc.save();
        gc.setFont(Font.font("Segoe UI Emoji", 10));

        // 构建气泡文本
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(players.size(), 3); i++) {
            Player p = players.get(i);
            if (i > 0) sb.append("  ");
            sb.append(p.getName());
            int dist = (int) (Math.abs(p.getY() - myY) / 10.0);
            sb.append(" ").append(dist).append("m");
        }
        if (players.size() > 3) {
            sb.append(" +").append(players.size() - 3);
        }
        String text = sb.toString();

        // 测量文本宽度
        javafx.scene.text.Text measure = new javafx.scene.text.Text(text);
        measure.setFont(Font.font("Segoe UI Emoji", 10));
        double textWidth = measure.getLayoutBounds().getWidth();
        double paddingX = 12;
        double bubbleW = textWidth + paddingX * 2;
        double bubbleH = 22;
        double bubbleX = (GameConstants.CANVAS_WIDTH - bubbleW) / 2;
        double bubbleY = isAbove ? 20 : GameConstants.CANVAS_HEIGHT - bubbleH - 20;

        // 绘制气泡背景
        gc.setFill(Color.web("rgba(15,52,96,0.85)"));
        gc.fillRoundRect(bubbleX, bubbleY, bubbleW, bubbleH, 8, 8);
        gc.setStroke(Color.web("rgba(78,204,163,0.5)"));
        gc.setLineWidth(1);
        gc.strokeRoundRect(bubbleX, bubbleY, bubbleW, bubbleH, 8, 8);

        // 绘制箭头
        double arrowSize = 6;
        double arrowX = GameConstants.CANVAS_WIDTH / 2;
        if (isAbove) {
            // 上方气泡：箭头向上指（指向屏幕外的领先玩家）
            gc.setFill(Color.web("#4ecca3"));
            gc.fillPolygon(
                new double[]{arrowX - arrowSize, arrowX + arrowSize, arrowX},
                new double[]{bubbleY - 2, bubbleY - 2, bubbleY - 2 - arrowSize}, 3);
        } else {
            // 下方气泡：箭头向下指（指向屏幕外的落后玩家）
            gc.setFill(Color.web("#e94560"));
            gc.fillPolygon(
                new double[]{arrowX - arrowSize, arrowX + arrowSize, arrowX},
                new double[]{bubbleY + bubbleH + 2, bubbleY + bubbleH + 2, bubbleY + bubbleH + 2 + arrowSize}, 3);
        }

        // 绘制文本
        gc.setFill(Color.web("white"));
        gc.fillText(text, bubbleX + paddingX, bubbleY + bubbleH - 6);

        gc.restore();
    }
    private void drawHUD(Player me) {
        if (me == null) return;
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFill(Color.web("white"));
        gc.setFont(Font.font("Segoe UI Emoji", 18));
        String scoreText = "分数: " + me.getScore();
        if (me.getHighScore() > 0) {
            scoreText += " (最高: " + me.getHighScore() + ")";
        }
        gc.fillText(scoreText, 15, 28);

        int heightVal = (int) (-me.getY() / 10);
        String heightText = "高度: " + heightVal;
        if (me.getJoinOffsetY() != 0 && me.getJoinOffsetY() != 300) {
            int offsetVal = (int) (me.getJoinOffsetY() / 10);
            heightText += " (初始 " + offsetVal + ")";
        }
        gc.fillText(heightText, 15, 50);

        for (int i = 0; i < GameConstants.MAX_LIVES; i++) {
            gc.setFill(Color.web(me.getLives() > i ? "#e94560" : "#444444"));
            drawHeart(14 + i * 32, 68, 24);
        }

        // 预留：金币显示
        if (me.getCoinsCollected() > 0) {
            gc.setFill(Color.web("#f1c40f"));
            gc.setFont(Font.font("Segoe UI Emoji", 14));
            gc.fillText("💰 " + me.getCoinsCollected(), 15, 100);
        }

        // 收集物进度显示 —— 形状与局内一致
        String cType = me.getCollectibleType();
        int cCount = me.getCollectibleCount();
        if (cType != null && !cType.isEmpty() && cCount > 0) {
            double hudY = me.getCoinsCollected() > 0 ? 118 : 100;
            gc.setFont(Font.font("Segoe UI Emoji", 14));
            String label;
            String color;
            switch (cType) {
                case "A" -> { label = "A"; color = "#f1c40f"; }
                case "B" -> { label = "B"; color = "#3498db"; }
                case "C" -> { label = "C"; color = "#e74c3c"; }
                default -> { label = cType; color = "#aaa"; }
            }
            // 绘制收集进度：已收集的用亮色+对应形状，未收集的用暗色轮廓
            for (int ci = 0; ci < 3; ci++) {
                double cx = 24 + ci * 24;
                double cy = hudY + 9;
                boolean collected = ci < cCount;
                gc.setFill(Color.web(collected ? color : "#444444"));
                gc.setStroke(Color.web(collected ? "#fff" : "#666"));
                gc.setLineWidth(1);
                switch (cType) {
                    case "A" -> {
                        // 星形（与局内一致）
                        drawStar(cx, cy, 9, 4, 5);
                        if (!collected) drawStarStroke(cx, cy, 9, 4, 5);
                    }
                    case "B" -> {
                        // 菱形（与局内一致）
                        gc.fillPolygon(
                            new double[]{cx, cx + 9, cx, cx - 9},
                            new double[]{cy - 9, cy, cy + 9, cy}, 4);
                        if (!collected) gc.strokePolygon(
                            new double[]{cx, cx + 9, cx, cx - 9},
                            new double[]{cy - 9, cy, cy + 9, cy}, 4);
                    }
                    case "C" -> {
                        // 圆形（与局内心形不同，HUD用圆形更紧凑）
                        gc.fillOval(cx - 9, cy - 9, 18, 18);
                        if (!collected) gc.strokeOval(cx - 9, cy - 9, 18, 18);
                    }
                    default -> {
                        gc.fillOval(cx - 9, cy - 9, 18, 18);
                        if (!collected) gc.strokeOval(cx - 9, cy - 9, 18, 18);
                    }
                }
                gc.setFill(Color.web(collected ? "#fff" : "#666"));
                gc.setFont(Font.font("Segoe UI Emoji", 10));
                gc.fillText(label, cx - 3, cy + 4);
            }
        }

        // 激活技能倒计时
        String activePower = me.getActivePowerUp();
        if (activePower != null && !activePower.isEmpty()) {
            double skillY = me.getCoinsCollected() > 0 ? 140 : 122;
            if (cType != null && !cType.isEmpty() && cCount > 0) skillY += 22;
            gc.setFont(Font.font("Segoe UI Emoji", 13));
            double remaining = me.getPowerUpTimer();
            switch (activePower) {
                case "A" -> {
                    gc.setFill(Color.web("#FF6B6B"));
                    gc.fillText("🛡️ 无敌 " + String.format("%.1f", remaining) + "s", 15, skillY);
                }
                case "B" -> {
                    gc.setFill(Color.web("#4ECDC4"));
                    gc.fillText("💨 加速 " + String.format("%.1f", remaining) + "s", 15, skillY);
                }
            }
        }

        if (me.isSpectator()) {
            gc.setFill(Color.web("rgba(255,255,255,0.8)"));
            gc.setFont(Font.font("Segoe UI Emoji", 14));
            gc.fillText("[旁观模式] 按跳跃切换视角", GameConstants.CANVAS_WIDTH / 2, GameConstants.CANVAS_HEIGHT - 20);
        }
    }

    private void drawFPS() {
        frameCount++;
        long now = System.currentTimeMillis();
        if (now - lastFpsTime >= 1000) {
            currentFps = frameCount;
            frameCount = 0;
            lastFpsTime = now;
        }
        String fpsText = "FPS: " + currentFps;
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFill(Color.web("rgba(0,0,0,0.6)"));
        gc.fillRect(320, 4, 76, 20);
        gc.setFill(Color.web("#4ecca3"));
        gc.setFont(Font.font("Segoe UI Emoji", 12));
        gc.fillText(fpsText, 325, 18);
    }

    private void drawMenuOverlay(GameState state, String mode) {
        gc.setFill(Color.web("rgba(0,0,0,0.7)"));
        gc.fillRect(0, 0, GameConstants.CANVAS_WIDTH, GameConstants.CANVAS_HEIGHT);
        gc.setFill(Color.web("#4ecca3"));
        gc.setFont(Font.font("Segoe UI Emoji", 28));
        gc.fillText("relay_host".equals(mode) ? "等待开始" : "墙间跑酷",
                GameConstants.CANVAS_WIDTH / 2, 260);
        gc.setFill(Color.web("white"));
        gc.setFont(Font.font("Segoe UI Emoji", 16));
        long count = state.getPlayers().values().stream().filter(Player::isActive).count();
        gc.fillText("在线玩家: " + count + " 人", GameConstants.CANVAS_WIDTH / 2, 300);
        gc.fillText("点击或按跳跃键开始", GameConstants.CANVAS_WIDTH / 2, 330);
        if ("relay_host".equals(mode)) {
            gc.setFont(Font.font("Segoe UI Emoji", 14));
            gc.fillText("你是房主，其他玩家加入后即可开始", GameConstants.CANVAS_WIDTH / 2, 360);
        }
    }

    private void drawGameOverOverlay(GameState state, boolean iAmDead, String mode) {
        gc.setFill(Color.web("rgba(0,0,0,0.7)"));
        gc.fillRect(0, 0, GameConstants.CANVAS_WIDTH, GameConstants.CANVAS_HEIGHT);
        gc.setFill(Color.web("#ff6b6b"));
        gc.setFont(Font.font("Segoe UI Emoji", 28));
        if (iAmDead && !"gameover".equals(state.getPhase())) {
            gc.fillText("你已死亡", GameConstants.CANVAS_WIDTH / 2, 240);
            gc.setFill(Color.web("white"));
            gc.setFont(Font.font("Segoe UI Emoji", 16));
            gc.fillText("可继续观战其他玩家", GameConstants.CANVAS_WIDTH / 2, 275);
            gc.setFont(Font.font("Segoe UI Emoji", 14));
            gc.fillText("等待房主重新开始下一局", GameConstants.CANVAS_WIDTH / 2, 305);
        } else {
            gc.fillText("relay_host".equals(mode) ? "游戏结束" : "全军覆没",
                    GameConstants.CANVAS_WIDTH / 2, 240);
            gc.setFill(Color.web("white"));
            gc.setFont(Font.font("Segoe UI Emoji", 18));
            Player best = null;
            for (Player p : state.getPlayers().values()) {
                if (best == null || p.getScore() > best.getScore()) best = p;
            }
            if (best != null) {
                gc.fillText("最高分: " + best.getName() + " (" + best.getScore() + "分)",
                        GameConstants.CANVAS_WIDTH / 2, 280);
            }
            gc.setFont(Font.font("Segoe UI Emoji", 15));
            gc.fillText("点击或按跳跃键重新开始", GameConstants.CANVAS_WIDTH / 2, 320);
        }
    }

    private void drawHeart(double x, double y, double size) {
        double s = size / 2;
        gc.beginPath();
        gc.moveTo(x + s / 2, y + s * 0.2);
        gc.bezierCurveTo(x + s / 2, y, x, y, x, y + s * 0.3);
        gc.bezierCurveTo(x, y + s * 0.6, x + s / 2, y + s * 0.8, x + s / 2, y + s);
        gc.bezierCurveTo(x + s / 2, y + s * 0.8, x + s, y + s * 0.6, x + s, y + s * 0.3);
        gc.bezierCurveTo(x + s, y, x + s / 2, y, x + s / 2, y + s * 0.2);
        gc.closePath();
        gc.fill();
    }

    private static class Particle {
        double x, y, vx, vy, life, decay, size;
        String color;
    }
}

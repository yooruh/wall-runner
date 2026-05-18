package com.wallrunner.client.service;

import com.wallrunner.shared.constants.GameConstants;
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
 * 【模块】client / service
 * 【代号】Z
 * 【职责】Canvas 渲染管线。
 * 【重构】2026-05-08: render 接收外部 cameraY，支持每个玩家独立视角。
 * 【修复】2026-05-10:
 *       1. render 方法新增 localPlayerId 参数，彻底替换硬编码 "local"。
 * 【修复】2026-05-11:
 *       1. 使用 fillColor + strokeColor 渲染玩家，支持自定义颜色。
 *       2. 渲染击退旋转动画：玩家被撞击后缓慢倾斜旋转。
 *       3. 闪烁无敌状态半透明闪烁效果。
 */
public class Renderer {

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
        drawParticles(camY);
        drawPlayers(state, camY, localPlayerId);

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

    public void spawnJumpParticles(double x, double y, String side) {
        int count = 8;
        double baseVx = "left".equals(side) ? -2.5 : 2.5;
        for (int i = 0; i < count; i++) {
            Particle p = new Particle();
            p.x = x + (Math.random() - 0.5) * 10;
            p.y = y;
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

    private void drawPlayers(GameState state, double cameraY, String localPlayerId) {
        for (Player p : state.getPlayers().values()) {
            if (!p.isActive()) continue;
            double sy = p.getY() - cameraY;
            if (sy < -100 || sy > 700) continue;

            // 【修复】使用 fillColor + strokeColor，优先于旧 color 字段
            String fillColor = p.getFillColor() != null && !p.getFillColor().isEmpty()
                    ? p.getFillColor() : (p.getColor() != null ? p.getColor() : "#4ecca3");
            String strokeColor = p.getStrokeColor() != null && !p.getStrokeColor().isEmpty()
                    ? p.getStrokeColor() : fillColor;

            boolean isMe = p.getId().equals(localPlayerId);
            boolean isPaused = p.isPaused();
            boolean invincible = p.isInvincible();
            boolean knockedBack = p.isKnockedBack();
            double rotation = p.getRotationAngle();

            gc.save();
            if (isPaused) gc.setGlobalAlpha(0.4);

            if (p.isJumping() && !knockedBack) {
                gc.setFill(Color.web(fillColor, 0.4));
                double tailX = "left".equals(p.getSide()) ? -12 : 12;
                gc.fillRect(p.getX() + tailX, sy + 15, p.getWidth(), p.getHeight() - 25);
                gc.setFill(Color.web(fillColor, 0.15));
                gc.fillRect(p.getX() + tailX * 1.8, sy + 25, p.getWidth(), p.getHeight() - 30);
            }

            // 闪烁效果
            if (invincible) {
                double alpha = (System.currentTimeMillis() % 200 < 100) ? 0.3 : 0.7;
                gc.setGlobalAlpha(alpha);
            }

            // 【关键修复】击退旋转动画：以玩家中心为原点旋转
            double centerX = p.getX() + p.getWidth() / 2;
            double centerY = sy + p.getHeight() / 2;
            if (knockedBack && Math.abs(rotation) > 0.1) {
                gc.translate(centerX, centerY);
                gc.rotate(rotation);
                gc.translate(-centerX, -centerY);
            }

            // 绘制玩家主体（填充+描边）
            gc.setFill(Color.web(fillColor));
            gc.fillRect(p.getX(), sy, p.getWidth(), p.getHeight());
            gc.setStroke(Color.web(strokeColor));
            gc.setLineWidth(2);
            gc.strokeRect(p.getX(), sy, p.getWidth(), p.getHeight());

            // 恢复变换
            if (knockedBack && Math.abs(rotation) > 0.1) {
                gc.translate(centerX, centerY);
                gc.rotate(-rotation);
                gc.translate(-centerX, -centerY);
            }

            if (isMe) {
                gc.setStroke(Color.web("rgba(255,255,255,0.9)"));
                gc.setLineWidth(2);
                gc.strokeRect(p.getX() - 2, sy - 2, p.getWidth() + 4, p.getHeight() + 4);
            }

            if (invincible) {
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

            if (isPaused) {
                gc.setFill(Color.web("rgba(255,255,255,0.8)"));
                gc.setFont(Font.font("Segoe UI Emoji", 10));
                gc.fillText("暂停", p.getX() + p.getWidth() / 2, sy - 20);
            }

            // 分数
            gc.setFont(Font.font("Segoe UI Emoji", 10));
            gc.setFill(Color.web("#aaa"));
            gc.fillText(p.getScore() + "分", p.getX() + p.getWidth() / 2, sy - (isPaused ? 32 : 18));

            gc.restore();
        }
    }

    private void drawHUD(Player me) {
        if (me == null) return;
        gc.setFill(Color.web("white"));
        gc.setFont(Font.font("Segoe UI Emoji", 18));
        // 【新增】显示最高分
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

        // 【新增】旁观模式提示
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

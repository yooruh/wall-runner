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

    // 预留：可收集物渲染
    private void drawCollectibles(GameState state, double cameraY) {
        for (Collectible c : state.getCollectibles()) {
            if (c.isCollected()) continue;
            double sy = c.getY() - cameraY;
            if (sy < -50 || sy > 650) continue;

            // 浮动效果
            double floatY = sy + Math.sin(System.currentTimeMillis() / 300.0 + c.getOscillationPhase()) * 3;

            switch (c.getType()) {
                case "coin" -> {
                    gc.setFill(Color.web("#f1c40f"));
                    gc.fillOval(c.getX(), floatY, c.getWidth(), c.getHeight());
                    gc.setStroke(Color.web("#d4ac0d"));
                    gc.setLineWidth(1);
                    gc.strokeOval(c.getX(), floatY, c.getWidth(), c.getHeight());
                }
                case "gem" -> {
                    gc.setFill(Color.web("#9b59b6"));
                    double cx = c.getX() + c.getWidth() / 2;
                    double cy = floatY + c.getHeight() / 2;
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

            // 闪烁效果（暂停玩家保持半透明，不闪烁）
            if (invincible && !isPaused) {
                double alpha = (System.currentTimeMillis() % 200 < 100) ? 0.3 : 0.7;
                gc.setGlobalAlpha(alpha);
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

            // 预留：特效可视化（如护盾光环）
            if (p.getActivePowerUp() != null && !p.getActivePowerUp().isEmpty()) {
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
        double screenTop = cameraY;
        double screenBottom = cameraY + GameConstants.CANVAS_HEIGHT;

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
            drawIndicatorBubble(above, myY, true, cameraY);
        }
        // 绘制下方指示器（落后玩家）
        if (!below.isEmpty()) {
            drawIndicatorBubble(below, myY, false, cameraY);
        }
    }

    private void drawIndicatorBubble(List<Player> players, double myY, boolean isAbove, double cameraY) {
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
        double paddingY = 6;
        double bubbleW = textWidth + paddingX * 2;
        double bubbleH = 22;
        double bubbleX = (GameConstants.CANVAS_WIDTH - bubbleW) / 2;
        double bubbleY = isAbove ? 4 : GameConstants.CANVAS_HEIGHT - bubbleH - 4;

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

package com.wallrunner.client.controller;

import com.wallrunner.client.ClientApplication;
import com.wallrunner.client.engine.LocalPhysicsEngine;
import com.wallrunner.client.engine.Predictor;
import com.wallrunner.client.service.GameLoopService;
import com.wallrunner.client.service.InputService;
import com.wallrunner.client.service.Renderer;
import com.wallrunner.client.service.StateManager;
import com.wallrunner.client.service.WebSocketClientService;
import com.wallrunner.shared.constants.GameConstants;
import com.wallrunner.shared.entity.GameState;
import com.wallrunner.shared.entity.Player;
import com.wallrunner.shared.physics.GamePhysics;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;

import java.util.Map;
import java.util.Set;
import java.util.prefs.Preferences;

/**
 * 游戏场景主控制器。
 *
 * 架构分层：
 * - 网络层 (WebSocketClientService): 收发消息
 * - 物理层 (StateManager + LocalPhysicsEngine): 本地状态与物理计算
 * - 渲染层 (Renderer): Canvas 绘制
 *
 * 核心原则：所有模式均运行本地物理，网络状态仅用于校正，确保画面流畅。
 */
public class GameController {

    public enum Mode { SINGLE, DEDICATED, RELAY_HOST, RELAY_GUEST }
    private static Mode currentMode = Mode.SINGLE;
    public static void setMode(Mode mode) { currentMode = mode; }

    @FXML private Canvas gameCanvas;
    @FXML private Button btnPause;
    @FXML private Button btnSettings;
    @FXML private Button btnHome;
    @FXML private Button btnResumeGame;
    @FXML private Button btnRespawn;
    @FXML private Label pauseTitle;
    @FXML private Label pauseHint;
    @FXML private Label toolbarInfo;
    @FXML private Label connStatus;
    @FXML private Label roomIdDisplay;
    @FXML private Label hint;
    @FXML private VBox pauseOverlay;
    @FXML private VBox gameOverOverlay;
    @FXML private Label finalScoreLabel;
    @FXML private VBox lbList;
    @FXML private HBox statusBar;
    @FXML private VBox settingsOverlay;
    @FXML private VBox leaderboard;

    private final WebSocketClientService ws = WebSocketClientService.getInstance();
    private final StateManager sm = StateManager.getInstance();
    private final Renderer renderer = new Renderer();
    private final InputService input = new InputService();
    private final GameLoopService loop = new GameLoopService();
    private final LocalPhysicsEngine physics = new LocalPhysicsEngine();
    private final Predictor predictor = new Predictor();

    private boolean paused = false;
    private boolean initialized = false;
    private boolean settingsLoaded = false;
    private String modeStr = "single";
    private String spectatorTargetId = null;

    // 动态UI元素
    private javafx.scene.control.Button respawnBtn;
    private javafx.scene.control.Label deathHintLabel;

    // ===== 身份查询 =====

    private String localId() {
        String myId = ws.getMyId();
        return (myId != null && !myId.isEmpty()) ? myId : sm.getLocalPlayerId();
    }

    private boolean isHost()     { return currentMode == Mode.RELAY_HOST; }
    private boolean isGuest()    { return currentMode == Mode.DEDICATED || currentMode == Mode.RELAY_GUEST; }
    private boolean isOffline()  { return currentMode == Mode.SINGLE; }

    // ===== 生命周期 =====

    @FXML
    private void initialize() {
        renderer.bindCanvas(gameCanvas);
        disableButtonFocus();
        focusCanvas();

        ws.setOnStateReceived(this::onState);
        ws.setOnMessage(this::onMessage);

        gameCanvas.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            if (!initialized) return;
            input.onCanvasClick();
            gameCanvas.requestFocus();
        });

        modeStr = switch (currentMode) {
            case SINGLE      -> "single";
            case DEDICATED   -> "dedicated";
            case RELAY_HOST  -> "relay_host";
            case RELAY_GUEST -> "relay_guest";
        };

        sm.setLocalPlayerId(ws.getClientId());

        if (isOffline() || isHost()) {
            sm.initLocalState(ws.getPlayerName());
            GameState s = sm.getState();
            s.setPhase("menu");
            s.setTimeBonusInterval(ws.getTimeBonusInterval());
            s.setTimeBonusPoints(ws.getTimeBonusPoints());
        }

        initConnStatus();
        initRoomDisplay();
        initLeaderboard();
        initDeathOverlay();
    }

    private void initDeathOverlay() {
        if (gameOverOverlay == null) return;

        // 使用 FXML 中定义的底部提示组件
        if (bottomRespawnBtn != null) {
            bottomRespawnBtn.setOnAction(e -> onRestart());
        }
    }

    public void onSceneReady(Scene scene) {
        if (initialized) return;
        initialized = true;
        input.attach(scene);
        input.setOnAction(this::onAction);
        input.setOnSystem(this::onSystem);
        reloadKeys();
        loop.setOnTick(this::onTick);
        loop.start();
        focusCanvas();
    }

    // ===== 每帧主循环 =====

    private void onTick(double deltaMs) {
        // 【修复】单人模式真正暂停：停止游戏运行
        if (paused && isOffline()) return;

        GameState state = sm.getState();
        if (state == null) return;

        physics.tick(state);

        if (isHost()) {
            ws.sendState(state);
        }

        Platform.runLater(() -> renderFrame(state));
    }

    private void renderFrame(GameState state) {
        Player me = state.getPlayers().get(localId());
        double camY;
        if (spectatorTargetId != null) {
            Player target = state.getPlayers().get(spectatorTargetId);
            camY = (target != null && target.getCameraY() != 0) ? target.getCameraY() : state.getCameraY();
        } else {
            camY = (me != null && me.getCameraY() != 0) ? me.getCameraY() : state.getCameraY();
        }
        renderer.render(state, modeStr, camY, ws.isShowFps(), localId());
        updateLeaderboard(state);

        if (me != null && !me.isActive() && "playing".equals(state.getPhase())) {
            showDeathUI(state);
        } else if (!"gameover".equals(state.getPhase())) {
            hideDeathUI();
        }

        if (spectatorTargetId != null) {
            Player target = state.getPlayers().get(spectatorTargetId);
            if (target != null) {
                showHint("旁观模式: 跟随 " + target.getName() + " (按跳跃切换)");
            }
        }
        if ("gameover".equals(state.getPhase())) {
            showGameOver(state);
        }
    }

    // ===== 网络状态校正 =====

    private void onState(GameState remote) {
        sm.reconcile(remote);
    }

    // ===== 用户输入 =====

    private void onAction(String action) {
        if (paused) return;
        GameState state = sm.getState();
        if (state == null) return;

        String phase = state.getPhase();
        if ("menu".equals(phase) || "gameover".equals(phase)) {
            if (isOffline() || isHost()) {
                GamePhysics.startGame(state);
            } else {
                ws.sendInput("start");
            }
            hideOverlays();
        } else if ("playing".equals(phase)) {
            Player me = state.getPlayers().get(localId());
            if (me != null && !me.isActive()) {
                // 死亡后自动进入旁观模式
                if (spectatorTargetId == null) {
                    enterSpectatorMode(state);
                } else {
                    switchSpectatorTarget(state);
                }
                return;
            }
            if (me != null && !me.isPaused()) {
                if (isOffline() || isHost()) {
                    GamePhysics.handleInput(me, "jump");
                    renderer.spawnJumpParticles(me.getX(), me.getY(), me.getSide());
                } else if (currentMode == Mode.DEDICATED) {
                    ws.sendInput("jump");
                    predictor.predict(state, localId(), "jump");
                    renderer.spawnJumpParticles(me.getX(), me.getY(), me.getSide());
                } else {
                    ws.sendInput("jump");
                }
            } else if (me == null && isGuest()) {
                ws.sendInput("jump");
            }
        }
    }

    private void onSystem(String cmd) {
        switch (cmd) {
            case "toggle_pause" -> onTogglePause();
            case "close_settings" -> onCloseSettings();
        }
    }

    // ===== 网络消息分发 =====

    private void onMessage(Map<String, Object> msg) {
        switch ((String) msg.get("type")) {
            case "mode_confirmed" -> onModeConfirmed(msg);
            case "room_created"   -> onRoomCreated(msg);
            case "joined_room"    -> onJoinedRoom(msg);
            case "player_joined"  -> onPlayerJoined(msg);
            case "player_left"    -> onPlayerLeft(msg);
            case "error"          -> onError(msg);
            case "room_closed"    -> onRoomClosed();
            case "input"          -> onRelayInput(msg);
        }
    }

    private void onModeConfirmed(Map<String, Object> msg) {
        String pid = (String) msg.get("playerId");
        String fillColor = (String) msg.get("fillColor");
        String strokeColor = (String) msg.get("strokeColor");
        ws.setMyId(pid);
        sm.setLocalPlayerId(pid);
        if (fillColor != null && !fillColor.isEmpty()) {
            ws.setFillColor(fillColor);
            ws.setStrokeColor(strokeColor);
        }
        if (isOffline() || isHost()) {
            sm.initLocalState(ws.getPlayerName());
        }
        setConnStatus("● 已连接", true);
    }

    private void onRoomCreated(Map<String, Object> msg) {
        String rid = (String) msg.get("roomId");
        String fillColor = (String) msg.get("fillColor");
        String strokeColor = (String) msg.get("strokeColor");
        ws.setMyId(ws.getClientId());
        ws.setCurrentRoomId(rid);
        if (fillColor != null && !fillColor.isEmpty()) {
            ws.setFillColor(fillColor);
            ws.setStrokeColor(strokeColor);
        }
        Platform.runLater(() -> updateRoomDisplay(rid));
        setConnStatus("● 已连接", true);
    }

    private void onJoinedRoom(Map<String, Object> msg) {
        String rid = (String) msg.get("roomId");
        String pid = (String) msg.get("playerId");
        String fillColor = (String) msg.get("fillColor");
        String strokeColor = (String) msg.get("strokeColor");
        ws.setMyId(pid);
        ws.setCurrentRoomId(rid);
        sm.setLocalPlayerId(pid);
        if (fillColor != null && !fillColor.isEmpty()) {
            ws.setFillColor(fillColor);
            ws.setStrokeColor(strokeColor);
        }
        Platform.runLater(() -> {
            if (roomIdDisplay != null) roomIdDisplay.setText("房间: " + rid);
        });
        setConnStatus("● 已连接", true);
    }

    private void onPlayerJoined(Map<String, Object> msg) {
        String name = (String) msg.get("name");
        String pid = (String) msg.get("playerId");
        String fillColor = (String) msg.get("fillColor");
        String strokeColor = (String) msg.get("strokeColor");
        showHint(name + " 加入了房间");
        if (!isHost() || pid == null) return;

        GameState state = sm.getState();
        if (state == null) return;
        if (!state.getPlayers().containsKey(pid)) {
            Player joiner = new Player(pid, name);
            if (fillColor != null && !fillColor.isEmpty()) {
                joiner.setFillColor(fillColor);
                joiner.setStrokeColor(strokeColor);
            } else {
                int colorIdx = Math.abs(pid.hashCode()) % GameConstants.PLAYER_COLOR_PAIRS.length;
                joiner.setFillColor(GameConstants.PLAYER_COLOR_PAIRS[colorIdx][0]);
                joiner.setStrokeColor(GameConstants.PLAYER_COLOR_PAIRS[colorIdx][1]);
            }
            GamePhysics.initJoiningPlayer(state, joiner);
            state.getPlayers().put(pid, joiner);
        }
        ws.sendState(state);
    }

    private void onPlayerLeft(Map<String, Object> msg) {
        String pid = (String) msg.get("playerId");
        GameState state = sm.getState();
        if (state != null && pid != null) {
            Player p = state.getPlayers().get(pid);
            if (p != null) p.setDisconnected(true);
        }
    }

    private void onError(Map<String, Object> msg) {
        showHint("错误: " + msg.get("message"));
    }

    private void onRoomClosed() {
        showHint("房间已关闭");
        setConnStatus("● 未连接", false);
    }

    private void onRelayInput(Map<String, Object> msg) {
        if (!isHost()) return;
        String action = (String) msg.get("action");
        String pid = (String) msg.getOrDefault("playerId", localId());
        GameState state = sm.getState();
        if (state == null) return;
        Player p = state.getPlayers().get(pid);
        if (p == null) return;

        boolean changed = switch (action) {
            case "start" -> {
                if ("menu".equals(state.getPhase()) || "gameover".equals(state.getPhase())) {
                    GamePhysics.startGame(state);
                    yield true;
                } else if ("playing".equals(state.getPhase())) {
                    yield true;
                }
                yield false;
            }
            case "jump"   -> { GamePhysics.handleInput(p, "jump"); yield true; }
            case "pause"  -> { p.setPaused(true); yield true; }
            case "resume" -> { p.setPaused(false); yield true; }
            default -> false;
        };

        if (changed) ws.sendState(state);
    }

    // ===== UI 与交互 =====

    @FXML private void onTogglePause() {
        if (!isPlaying()) return;
        paused = !paused;
        pauseOverlay.setVisible(paused);
        pauseOverlay.setManaged(paused);

        GameState state = sm.getState();
        Player me = state != null ? state.getPlayers().get(localId()) : null;
        boolean isDead = me != null && !me.isActive();

        // 根据是否死亡调整暂停菜单内容
        if (isDead) {
            if (pauseTitle != null) pauseTitle.setText("你已死亡");
            if (pauseHint != null) pauseHint.setText("按暂停键可以重生或退出");
            if (btnResumeGame != null) { btnResumeGame.setVisible(false); btnResumeGame.setManaged(false); }
            if (btnRespawn != null) { btnRespawn.setVisible(true); btnRespawn.setManaged(true); }
        } else {
            if (pauseTitle != null) pauseTitle.setText("游戏暂停");
            if (pauseHint != null) pauseHint.setText("按 ESC 恢复游戏");
            if (btnResumeGame != null) { btnResumeGame.setVisible(true); btnResumeGame.setManaged(true); }
            if (btnRespawn != null) { btnRespawn.setVisible(false); btnRespawn.setManaged(false); }
        }

        hint.setText(paused ? "游戏已暂停（按 ESC 恢复）" : "按跳跃键 / 点击屏幕 / 触摸 来跳跃");

        if (me != null && !isDead) me.setPaused(paused);

        // 联机模式发送暂停/恢复消息（仅非死亡状态）
        if (!isOffline() && !isDead) {
            ws.sendInput(paused ? "pause" : "resume");
        }

        // 【修复】单人模式真正暂停：停止/恢复游戏循环
        if (isOffline()) {
            if (paused) {
                loop.stop();
            } else {
                loop.start();
                focusCanvas();
            }
        } else {
            // 联机模式：暂停时停止本地循环（画面冻结），恢复时继续
            if (!paused) {
                loop.start();
                focusCanvas();
            }
        }
    }

    @FXML private void onOpenSettings() {
        // 【修复】设置本身有暂停功能：打开设置时暂停游戏
        if (isPlaying() && !paused) {
            onTogglePause();
        }
        input.setSettingsOpen(true);
        if (settingsOverlay != null && !settingsLoaded) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/wallrunner/client/view/settings.fxml"));
                Parent root = loader.load();
                SettingsController sc = loader.getController();
                sc.setOnClose(this::onCloseSettings);
                settingsOverlay.getChildren().add(root);
                settingsLoaded = true;
            } catch (Exception e) {
                e.printStackTrace();
                input.setSettingsOpen(false);
                // 如果打开设置前是暂停状态，保持暂停
                return;
            }
        }
        if (settingsOverlay != null) {
            settingsOverlay.setVisible(true);
            settingsOverlay.setManaged(true);
        }
    }

    private void onCloseSettings() {
        if (settingsOverlay != null) {
            settingsOverlay.setVisible(false);
            settingsOverlay.setManaged(false);
        }
        input.setSettingsOpen(false);
        reloadKeys();
        // 【修复】关闭设置时恢复游戏（如果当前是暂停状态）
        if (paused && isPlaying()) {
            onTogglePause(); // 恢复游戏
        }
    }

    @FXML private void onReturnToMenu() {
        // 弹出确认框
        javafx.scene.control.Alert confirm = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.CONFIRMATION);
        confirm.setTitle("确认返回");
        confirm.setHeaderText(null);
        confirm.setContentText("确定要返回主菜单吗？当前游戏进度将丢失。");
        confirm.getButtonTypes().setAll(
                javafx.scene.control.ButtonType.YES,
                javafx.scene.control.ButtonType.NO);
        confirm.showAndWait().ifPresent(response -> {
            if (response == javafx.scene.control.ButtonType.YES) {
                loop.stop();
                // 发送断开通知给服务器，让服务器正确标记为离线
                ws.sendDisconnect();
                sm.reset();
                spectatorTargetId = null;
                currentMode = Mode.SINGLE;
                ClientApplication.switchScene("/com/wallrunner/client/view/menu.fxml");
            }
        });
    }

    @FXML private void onRestart() {
        gameOverOverlay.setVisible(false);
        gameOverOverlay.setManaged(false);
        spectatorTargetId = null;
        GameState state = sm.getState();
        Player me = state != null ? state.getPlayers().get(localId()) : null;
        // 保存最高分（如果当前分数超过历史最高分）
        if (me != null && me.getScore() > me.getHighScore()) {
            me.setHighScore(me.getScore());
        }
        // 计算重生位置：最落后玩家后方30m（300像素）
        double fallbackY = 0;
        if (state != null) {
            for (Player p : state.getPlayers().values()) {
                if (p.isActive() && p.getY() < fallbackY) {
                    fallbackY = p.getY();
                }
            }
        }
        double spawnY = fallbackY + 300;
        if (me != null) {
            me.setActive(true);
            me.setLives(GameConstants.MAX_LIVES);
            me.setScore(0);
            me.setTimeBonusScore(0);
            me.setJoinOffsetY(spawnY);
            me.setY(spawnY);
            me.setX("left".equals(me.getSide()) ? GameConstants.WALL_WIDTH + 5 : GameConstants.CANVAS_WIDTH - GameConstants.WALL_WIDTH - GameConstants.PLAYER_SIZE - 5);
            me.setVy(0);
            me.setBlocked(false);
            me.setPaused(false);
            me.setInvincible(true);
            me.setInvincibleTimer(2.0);
            me.setSpectator(false);
            me.setKnockedBack(false);
            me.setRotationAngle(0);
            me.setTargetRotation(0);
            double spawnCamY = spawnY - GameConstants.CANVAS_HEIGHT * GameConstants.CAMERA_OFFSET_RATIO;
            me.setCameraY(spawnCamY);
            me.setCameraTargetY(spawnCamY);
        }
        if (state != null) {
            double spawnCamY = spawnY - GameConstants.CANVAS_HEIGHT * GameConstants.CAMERA_OFFSET_RATIO;
            state.setCameraY(spawnCamY);
            state.setCameraTargetY(spawnCamY);
        }
        hideDeathUI();
        loop.start();
    }

    // ===== 初始化与辅助 =====

    private void disableButtonFocus() {
        if (btnPause != null) btnPause.setFocusTraversable(false);
        if (btnSettings != null) btnSettings.setFocusTraversable(false);
        if (btnHome != null) btnHome.setFocusTraversable(false);
    }

    private void focusCanvas() {
        Platform.runLater(() -> {
            if (gameCanvas != null) {
                gameCanvas.setFocusTraversable(true);
                gameCanvas.requestFocus();
            }
        });
    }

    private void initConnStatus() {
        if (connStatus == null) return;
        switch (currentMode) {
            case SINGLE      -> setConnStatus("本地游戏", false);
            case DEDICATED   -> setConnStatus(ws.isConnected() ? "● 已连接" : "● 未连接", ws.isConnected());
            case RELAY_HOST, RELAY_GUEST -> setConnStatus(ws.isConnected() ? "● 已连接" : "● 连接中...", ws.isConnected());
        }
    }

    private void initRoomDisplay() {
        if (roomIdDisplay == null) return;
        String rid = ws.getCurrentRoomId();
        if (rid != null && !rid.isEmpty()) updateRoomDisplay(rid);
    }

    private void updateRoomDisplay(String rid) {
        if (roomIdDisplay == null) return;
        boolean host = isHost();
        roomIdDisplay.setText("房间: " + rid + (host ? "  (点击复制)" : ""));
        if (host) {
            roomIdDisplay.setOnMouseClicked(e -> {
                javafx.scene.input.Clipboard cb = javafx.scene.input.Clipboard.getSystemClipboard();
                javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
                cc.putString(rid);
                cb.setContent(cc);
                showHint("房间码已复制: " + rid);
            });
        }
    }

    private void initLeaderboard() {
        if (leaderboard == null) return;
        boolean visible = !isOffline();
        leaderboard.setVisible(visible);
        leaderboard.setManaged(visible);
    }

    private void reloadKeys() {
        Preferences prefs = Preferences.userNodeForPackage(SettingsController.class);
        String keysStr = prefs.get("jump_keys", "SPACE");
        Set<KeyCode> keys = new java.util.LinkedHashSet<>();
        if (keysStr != null && !keysStr.isEmpty()) {
            for (String k : keysStr.split(",")) {
                try { keys.add(KeyCode.valueOf(k.trim())); } catch (IllegalArgumentException ignored) {}
            }
        }
        if (keys.isEmpty()) keys.add(KeyCode.SPACE);
        input.setJumpKeys(keys);
    }

    private void hideOverlays() {
        Platform.runLater(() -> {
            pauseOverlay.setVisible(false); pauseOverlay.setManaged(false);
            gameOverOverlay.setVisible(false); gameOverOverlay.setManaged(false);
        });
    }

    private void updateLeaderboard(GameState state) {
        if (lbList == null) return;
        lbList.getChildren().clear();
        var allPlayers = state.getPlayers().values().stream().toList();
        if (isOffline() || allPlayers.size() <= 1) {
            if (leaderboard != null) { leaderboard.setVisible(false); leaderboard.setManaged(false); }
            return;
        }
        if (leaderboard != null) { leaderboard.setVisible(true); leaderboard.setManaged(true); }
        String myId = localId();
        long now = System.currentTimeMillis();
        allPlayers.stream().sorted((a, b) -> Integer.compare(b.getScore(), a.getScore())).forEach(p -> {
            // 离线超过1分钟的玩家：不显示在排行榜上（但保留数据）
            if (p.isDisconnected() && p.getOfflineTime() > 0 && (now - p.getOfflineTime()) > 60000) {
                return; // 跳过超过1分钟的离线玩家
            }

            StringBuilder text = new StringBuilder();
            text.append(p.getName()).append(": ").append(p.getScore());
            if (p.isPaused() && !p.isDisconnected()) text.append(" (暂停)");
            if (p.isDisconnected()) text.append(" [离线]");
            if (!p.isActive()) text.append(" [死亡]");
            Label lbl = new Label(text.toString());
            StringBuilder style = new StringBuilder("-fx-text-fill: ").append(p.getFillColor()).append("; -fx-font-size: 12px;");
            if (p.getId().equals(myId)) style.append(" -fx-font-weight: bold;");

            // 死亡玩家：删除线
            if (!p.isActive()) style.append(" -fx-strikethrough: true;");

            // 离线玩家：半透明（保留死亡玩家的删除线）
            if (p.isDisconnected()) {
                style.append(" -fx-opacity: 0.4;");
            } else if (!p.isActive()) {
                style.append(" -fx-opacity: 0.6;");
            } else if (p.isPaused()) {
                style.append(" -fx-opacity: 0.7; -fx-font-style: italic;");
            }
            lbl.setStyle(style.toString());
            lbList.getChildren().add(lbl);
        });
    }

    private void showGameOver(GameState state) {
        loop.stop();
        Player me = state.getPlayers().get(localId());
        int score = me != null ? me.getScore() : 0;
        int high = me != null ? me.getHighScore() : 0;
        String text = "最终得分: " + score;
        if (high > 0) text += " (最高分: " + high + ")";
        finalScoreLabel.setText(text);
        if (respawnBtn != null) { respawnBtn.setVisible(false); respawnBtn.setManaged(false); }
        if (deathHintLabel != null) { deathHintLabel.setVisible(false); deathHintLabel.setManaged(false); }
        gameOverOverlay.setVisible(true);
        gameOverOverlay.setManaged(true);
    }

    private void showDeathUI(GameState state) {
        Player me = state.getPlayers().get(localId());
        if (me == null) return;
        int score = me.getScore();
        int high = me.getHighScore();
        String text = "你已死亡! 得分: " + score;
        if (high > 0 && score < high) text += " (最高: " + high + ")";
        finalScoreLabel.setText(text);
        // 使用底部提示区域显示提示和重生按钮
        if (bottomDeathHint != null) {
            bottomDeathHint.setText("按暂停键可以重生或退出");
            bottomDeathHint.setVisible(true);
        }
        if (bottomRespawnBtn != null) {
            bottomRespawnBtn.setVisible(true);
            bottomRespawnBtn.setManaged(true);
        }
        if (bottomHintBox != null) {
            bottomHintBox.setVisible(true);
            bottomHintBox.setManaged(true);
        }
        // 游戏结束遮罩不显示
        gameOverOverlay.setVisible(false);
        gameOverOverlay.setManaged(false);
    }

    private void hideDeathUI() {
        if (gameOverOverlay != null && gameOverOverlay.isVisible() && !"gameover".equals(sm.getState().getPhase())) {
            gameOverOverlay.setVisible(false);
            gameOverOverlay.setManaged(false);
        }
        if (respawnBtn != null) { respawnBtn.setVisible(false); respawnBtn.setManaged(false); }
        if (deathHintLabel != null) { deathHintLabel.setVisible(false); deathHintLabel.setManaged(false); }
    }

    private void enterSpectatorMode(GameState state) {
        for (Player p : state.getPlayers().values()) {
            if (p.isActive() && !p.getId().equals(localId())) {
                spectatorTargetId = p.getId();
                showHint("进入旁观模式，跟随 " + p.getName());
                return;
            }
        }
        showHint("无其他存活玩家可旁观");
    }

    private void switchSpectatorTarget(GameState state) {
        var activePlayers = state.getPlayers().values().stream()
                .filter(p -> p.isActive() && !p.getId().equals(localId()))
                .toList();
        if (activePlayers.isEmpty()) {
            showHint("无其他存活玩家");
            return;
        }
        int currentIdx = -1;
        for (int i = 0; i < activePlayers.size(); i++) {
            if (activePlayers.get(i).getId().equals(spectatorTargetId)) {
                currentIdx = i;
                break;
            }
        }
        int nextIdx = (currentIdx + 1) % activePlayers.size();
        spectatorTargetId = activePlayers.get(nextIdx).getId();
        showHint("切换至 " + activePlayers.get(nextIdx).getName());
    }

    private boolean isPlaying() {
        GameState s = sm.getState();
        return s != null && "playing".equals(s.getPhase());
    }

    private void setConnStatus(String text, boolean ok) {
        if (connStatus == null) return;
        Platform.runLater(() -> {
            connStatus.setText(text);
            connStatus.setStyle("-fx-text-fill: " + (ok ? "#4ecca3" : "#ff6b6b") + ";");
        });
    }

    private void showHint(String text) {
        Platform.runLater(() -> { if (hint != null) hint.setText(text); });
    }
}

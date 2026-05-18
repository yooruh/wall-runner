package com.wallrunner.client.controller;

import com.wallrunner.client.ClientApplication;
import com.wallrunner.client.engine.LocalPhysicsEngine;
import com.wallrunner.client.engine.Predictor;
import com.wallrunner.client.service.GameLoopService;
import com.wallrunner.client.service.InputService;
import com.wallrunner.client.service.Renderer;
import com.wallrunner.client.service.StateManager;
import com.wallrunner.client.service.WebSocketClientService;
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
 * 【模块】client / controller
 * 【代号】Z
 * 【职责】游戏场景主控制器。
 * 【架构】三层分离：
 *       - 网络层 (WebSocketClientService): 收发消息
 *       - 物理层 (StateManager + LocalPhysicsEngine): 本地状态与物理计算
 *       - 渲染层 (Renderer): Canvas 绘制
 * 【原则】所有模式均运行本地物理，网络状态仅用于校正，确保画面流畅。
 * 【修复】2026-05-10:
 *       1. 所有模式（含Dedicated/RelayGuest）均运行本地物理，解决客机画面卡顿。
 *       2. P2P房主每3帧广播一次权威状态，确保客机同步。
 *       3. 网络状态统一走reconcile，平滑校正避免画面跳变。
 *       4. 消息处理提取为独立方法，消除巨型switch/if-else。
 *       5. 房间号显示统一初始化，房主/客机逻辑清晰分离。
 */
public class GameController {

    public enum Mode { SINGLE, DEDICATED, RELAY_HOST, RELAY_GUEST }
    private static Mode currentMode = Mode.SINGLE;
    public static void setMode(Mode mode) { currentMode = mode; }

    @FXML private Canvas gameCanvas;
    @FXML private Button btnPause;
    @FXML private Button btnSettings;
    @FXML private Button btnHome;
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
    // P2P房主每帧广播，无需计数器
    private String spectatorTargetId = null; // 旁观模式当前跟随的玩家ID

    // ===== 身份查询 =====

    private String localId() {
        String myId = ws.getMyId();
        return (myId != null && !myId.isEmpty()) ? myId : sm.getLocalPlayerId();
    }

    private boolean isHost()     { return currentMode == Mode.RELAY_HOST; }
    private boolean isGuest()    { return currentMode == Mode.DEDICATED || currentMode == Mode.RELAY_GUEST; }
    private boolean isOffline()  { return currentMode == Mode.SINGLE; }

    // ===== 生命周期 =====

    // 【新增】动态UI元素
    private javafx.scene.control.Button respawnBtn;
    private javafx.scene.control.Label deathHintLabel;

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

        // 离线或房主：立即初始化本地权威状态
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

        // 【新增】初始化死亡覆盖层UI
        initDeathOverlay();
    }

    /** 【新增】初始化死亡覆盖层：重生按钮 + 旁观提示 */
    private void initDeathOverlay() {
        if (gameOverOverlay == null) return;

        respawnBtn = new javafx.scene.control.Button("立即重生");
        respawnBtn.setStyle("-fx-background-color: #4ecca3; -fx-text-fill: white; -fx-font-size: 16px; -fx-padding: 8 24;");
        respawnBtn.setOnAction(e -> onRestart());
        respawnBtn.setVisible(false);
        respawnBtn.setManaged(false);

        deathHintLabel = new javafx.scene.control.Label("按跳跃键切换旁观视角");
        deathHintLabel.setStyle("-fx-text-fill: #aaa; -fx-font-size: 12px;");
        deathHintLabel.setVisible(false);
        deathHintLabel.setManaged(false);

        // 插入到 gameOverOverlay 中（在 finalScoreLabel 之后）
        int insertIdx = gameOverOverlay.getChildren().indexOf(finalScoreLabel) + 1;
        if (insertIdx > 0 && insertIdx <= gameOverOverlay.getChildren().size()) {
            gameOverOverlay.getChildren().add(insertIdx, respawnBtn);
            gameOverOverlay.getChildren().add(insertIdx + 1, deathHintLabel);
        } else {
            gameOverOverlay.getChildren().add(respawnBtn);
            gameOverOverlay.getChildren().add(deathHintLabel);
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
        if (paused) return;
        GameState state = sm.getState();
        if (state == null) return;

        // 【核心修复】所有模式均运行本地物理，确保画面流畅。
        // 网络状态仅用于校正（见 onState），不阻塞渲染。
        physics.tick(state);

        // P2P房主：每帧广播权威状态（与客户端同频120fps）
        if (isHost()) {
            ws.sendState(state);
        }

        // 渲染必须在JavaFX线程
        Platform.runLater(() -> renderFrame(state));
    }

    private void renderFrame(GameState state) {
        Player me = state.getPlayers().get(localId());
        // 【新增】旁观模式：使用被跟随玩家的摄像机
        double camY;
        if (spectatorTargetId != null) {
            Player target = state.getPlayers().get(spectatorTargetId);
            camY = (target != null && target.getCameraY() != 0) ? target.getCameraY() : state.getCameraY();
        } else {
            camY = (me != null && me.getCameraY() != 0) ? me.getCameraY() : state.getCameraY();
        }
        renderer.render(state, modeStr, camY, ws.isShowFps(), localId());
        updateLeaderboard(state);

        // 【新增】检测单个玩家死亡（非全局gameover），显示死亡UI
        if (me != null && !me.isActive() && "playing".equals(state.getPhase())) {
            showDeathUI(state);
        } else if (!"gameover".equals(state.getPhase())) {
            hideDeathUI();
        }

        // 【新增】旁观模式提示
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
        // 【核心修复】所有网络模式统一走reconcile，平滑校正本地状态。
        // 首次reconcile时若本地玩家缺失，StateManager会自动从权威状态复制。
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
            // 【新增】死亡后：跳跃键切换旁观视角，或重开
            if (me != null && !me.isActive()) {
                if (spectatorTargetId == null) {
                    // 首次进入旁观模式
                    enterSpectatorMode(state);
                } else {
                    // 切换到下一位存活玩家
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
                } else { // RELAY_GUEST
                    ws.sendInput("jump");
                }
            } else if (me == null && isGuest()) {
                // 玩家尚未同步，先发送输入确保不丢失
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
        // 保存服务器分配的颜色
        if (fillColor != null && !fillColor.isEmpty()) {
            ws.setFillColor(fillColor);
            ws.setStrokeColor(strokeColor);
        }
        // 客机不预创建状态，等待权威state通过reconcile同步
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
        // 客机不预创建状态，等待房主广播权威state
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
            // 使用服务器分配的颜色（如果有）
            if (fillColor != null && !fillColor.isEmpty()) {
                joiner.setFillColor(fillColor);
                joiner.setStrokeColor(strokeColor);
                joiner.setColor(fillColor);
            } else {
                joiner.setColor(com.wallrunner.shared.constants.GameConstants.PLAYER_COLORS[
                        Math.abs(pid.hashCode()) % com.wallrunner.shared.constants.GameConstants.PLAYER_COLORS.length]);
            }
            GamePhysics.initJoiningPlayer(state, joiner);
            state.getPlayers().put(pid, joiner);
        }
        // 房主立即广播权威状态，让新机同步
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
                    // 房主已在游戏中，广播当前状态让中途加入的客机同步
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
        hint.setText(paused ? "游戏已暂停（按 ESC 恢复）" : "按跳跃键 / 点击屏幕 / 触摸 来跳跃");

        GameState state = sm.getState();
        Player me = state != null ? state.getPlayers().get(localId()) : null;
        if (me != null) me.setPaused(paused);
        if (isGuest()) ws.sendInput(paused ? "pause" : "resume");

        if (!paused) {
            loop.start();
            focusCanvas();
        }
    }

    @FXML private void onOpenSettings() {
        input.setSettingsOpen(true);
        loop.stop();
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
                if (!paused) loop.start();
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
        if (!paused) {
            loop.start();
            focusCanvas();
        }
    }

    @FXML private void onReturnToMenu() {
        loop.stop();
        ws.disconnect();
        sm.reset();
        spectatorTargetId = null;
        // 【修复】重置静态模式变量，避免返回菜单后旧模式残留影响下次进入
        currentMode = Mode.SINGLE;
        ClientApplication.switchScene("/com/wallrunner/client/view/menu.fxml");
    }

    @FXML private void onRestart() {
        gameOverOverlay.setVisible(false);
        gameOverOverlay.setManaged(false);
        spectatorTargetId = null; // 退出旁观模式
        GameState state = sm.getState();
        Player me = state != null ? state.getPlayers().get(localId()) : null;
        // 【新增】保存最高分
        if (me != null && me.getScore() > me.getHighScore()) {
            me.setHighScore(me.getScore());
        }
        // 【新增】落后30m重生：找到最落后的存活玩家，在其后30m重生
        double fallbackY = 0;
        if (state != null) {
            for (Player p : state.getPlayers().values()) {
                if (p.isActive() && p.getY() < fallbackY) {
                    fallbackY = p.getY(); // y越小（越上方）越落后
                }
            }
        }
        double spawnY = fallbackY + 300; // 落后30m = 300像素
        if (me != null) {
            me.setActive(true);
            me.setLives(GameConstants.MAX_LIVES);
            me.setScore(0); // 从0开始计分
            me.setY(spawnY);
            me.setX("left".equals(me.getSide()) ? GameConstants.WALL_WIDTH + 5 : GameConstants.CANVAS_WIDTH - GameConstants.WALL_WIDTH - GameConstants.PLAYER_SIZE - 5);
            me.setVy(0);
            me.setBlocked(false);
            me.setPaused(false);
            me.setInvincible(true);
            me.setInvincibleTimer(2.0); // 重生后2秒无敌
            me.setSpectator(false);
            // 【关键修复】重置摄像机到重生位置，避免立刻再次死亡
            double spawnCamY = spawnY - GameConstants.CANVAS_HEIGHT * GameConstants.CAMERA_OFFSET_RATIO;
            me.setCameraY(spawnCamY);
            me.setCameraTargetY(spawnCamY);
        }
        // 【关键修复】同步全局摄像机到重生位置
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
        allPlayers.stream().sorted((a, b) -> Integer.compare(b.getScore(), a.getScore())).forEach(p -> {
            StringBuilder text = new StringBuilder();
            text.append(p.getName()).append(": ").append(p.getScore());
            if (p.isPaused()) text.append(" (暂停)");
            if (p.isDisconnected()) text.append(" [离线]");
            if (!p.isActive()) text.append(" [死亡]");
            Label lbl = new Label(text.toString());
            StringBuilder style = new StringBuilder("-fx-text-fill: ").append(p.getColor()).append("; -fx-font-size: 12px;");
            if (p.getId().equals(myId)) style.append(" -fx-font-weight: bold;");
            // 死亡 = 删除线
            if (!p.isActive()) style.append(" -fx-strikethrough: true;");
            // 掉线 = 半透明
            if (p.isDisconnected()) style.append(" -fx-opacity: 0.4;");
            else if (!p.isActive()) style.append(" -fx-opacity: 0.6;");
            else if (p.isPaused()) style.append(" -fx-opacity: 0.7;");
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
        // 全局gameover时隐藏重生按钮
        if (respawnBtn != null) { respawnBtn.setVisible(false); respawnBtn.setManaged(false); }
        if (deathHintLabel != null) { deathHintLabel.setVisible(false); deathHintLabel.setManaged(false); }
        gameOverOverlay.setVisible(true);
        gameOverOverlay.setManaged(true);
    }

    /** 【新增】显示单个玩家死亡UI */
    private void showDeathUI(GameState state) {
        Player me = state.getPlayers().get(localId());
        if (me == null) return;
        int score = me.getScore();
        int high = me.getHighScore();
        String text = "你已死亡! 得分: " + score;
        if (high > 0) text += " (最高: " + high + ")";
        finalScoreLabel.setText(text);
        if (respawnBtn != null) { respawnBtn.setVisible(true); respawnBtn.setManaged(true); }
        if (deathHintLabel != null) { deathHintLabel.setVisible(true); deathHintLabel.setManaged(true); }
        gameOverOverlay.setVisible(true);
        gameOverOverlay.setManaged(true);
    }

    /** 【新增】隐藏死亡UI */
    private void hideDeathUI() {
        if (gameOverOverlay != null && gameOverOverlay.isVisible() && !"gameover".equals(sm.getState().getPhase())) {
            gameOverOverlay.setVisible(false);
            gameOverOverlay.setManaged(false);
        }
        if (respawnBtn != null) { respawnBtn.setVisible(false); respawnBtn.setManaged(false); }
        if (deathHintLabel != null) { deathHintLabel.setVisible(false); deathHintLabel.setManaged(false); }
    }

    // 【新增】旁观模式方法
    private void enterSpectatorMode(GameState state) {
        // 找到第一个存活的非自己玩家
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
        // 循环切换
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
        // 【修复】WebSocket 回调在后台线程，所有 UI 操作必须走 FX 线程
        Platform.runLater(() -> {
            connStatus.setText(text);
            connStatus.setStyle("-fx-text-fill: " + (ok ? "#4ecca3" : "#ff6b6b") + ";");
        });
    }

    private void showHint(String text) {
        Platform.runLater(() -> { if (hint != null) hint.setText(text); });
    }
}

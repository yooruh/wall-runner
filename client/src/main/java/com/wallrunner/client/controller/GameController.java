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
import java.util.prefs.Preferences;

/**
 * 【模块】client / controller
 * 【代号】Z
 * 【职责】游戏场景主控制器。
 * 【重构】2026-05-08:
 *       1. 空格=跳跃/开始，ESC=暂停。
 *       2. 单人模式用玩家自身摄像机渲染，实现被阻挡碾压效果。
 *       3. 设置关闭后自动重新加载按键绑定。
 *       4. 【修复】禁用工具栏按钮焦点遍历，防止空格键触发暂停按钮。
 *       5. 【修复】根据实际连接状态初始化 connStatus，避免离线仍显示已连接。
 *       6. 【修复】初始化时读取已缓存的 roomId，解决开房后房间码不显示。
 *       7. 【修复】排行榜仅在多人在线时显示，单人模式自动隐藏。
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

    private final WebSocketClientService wsService = WebSocketClientService.getInstance();
    private final StateManager stateManager = StateManager.getInstance();
    private final Renderer renderer = new Renderer();
    private final InputService inputService = new InputService();
    private final GameLoopService gameLoop = new GameLoopService();
    private final LocalPhysicsEngine localEngine = new LocalPhysicsEngine();
    private final Predictor predictor = new Predictor();

    private boolean paused = false;
    private boolean initialized = false;
    private boolean settingsLoaded = false;
    private String currentModeStr = "single";

    @FXML
    private void initialize() {
        renderer.bindCanvas(gameCanvas);

        // 【关键修复】禁用工具栏按钮的焦点遍历，防止空格键触发按钮默认行为
        if (btnPause != null) btnPause.setFocusTraversable(false);
        if (btnSettings != null) btnSettings.setFocusTraversable(false);
        if (btnHome != null) btnHome.setFocusTraversable(false);

        // 锁定焦点到 Canvas，确保所有键盘事件都走 InputService
        Platform.runLater(() -> {
            if (gameCanvas != null) {
                gameCanvas.setFocusTraversable(true);
                gameCanvas.requestFocus();
            }
        });

        wsService.setOnStateReceived(this::onNetworkState);
        wsService.setOnMessage(this::onNetworkMessage);

        gameCanvas.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            if (!initialized) return;
            inputService.onCanvasClick();
            gameCanvas.requestFocus();
        });

        if (currentMode == Mode.SINGLE) currentModeStr = "single";
        else if (currentMode == Mode.DEDICATED) currentModeStr = "dedicated";
        else if (currentMode == Mode.RELAY_HOST) currentModeStr = "relay_host";
        else if (currentMode == Mode.RELAY_GUEST) currentModeStr = "relay_guest";

        if (currentMode == Mode.SINGLE || currentMode == Mode.RELAY_HOST) {
            stateManager.initLocalState(wsService.getPlayerName());
            stateManager.getState().setPhase("menu");
        }

        // 【修复】根据实际模式与连接状态初始化状态栏，避免离线仍显示"已连接"
        initConnectionStatus();

        // 【修复】若 WebSocketClientService 已缓存房间号，立即显示
        String cachedRoomId = wsService.getCurrentRoomId();
        if (cachedRoomId != null && !cachedRoomId.isEmpty() && roomIdDisplay != null) {
            roomIdDisplay.setText("房间: " + cachedRoomId);
        }

        // 【修复】单人模式隐藏排行榜，多人模式准备显示
        if (leaderboard != null) {
            boolean multiPlayer = currentMode != Mode.SINGLE;
            leaderboard.setVisible(multiPlayer);
            leaderboard.setManaged(multiPlayer);
        }
    }

    private void initConnectionStatus() {
        if (connStatus == null) return;
        switch (currentMode) {
            case SINGLE:
                updateConnStatus("本地游戏", false);
                break;
            case DEDICATED:
                if (wsService.isConnected()) {
                    updateConnStatus("● 已连接", true);
                } else {
                    updateConnStatus("● 未连接", false);
                }
                break;
            case RELAY_HOST:
            case RELAY_GUEST:
                if (wsService.isConnected()) {
                    updateConnStatus("● 已连接", true);
                } else {
                    updateConnStatus("● 连接中...", false);
                }
                break;
        }
    }

    public void onSceneReady(Scene scene) {
        if (initialized) return;
        initialized = true;

        inputService.attach(scene);
        inputService.setOnAction(this::handleAction);
        inputService.setOnSystem(this::handleSystem);
        reloadKeyBindings();

        gameLoop.setOnTick(this::onGameTick);
        gameLoop.start();

        Platform.runLater(() -> {
            if (gameCanvas != null) gameCanvas.requestFocus();
        });
    }

    private void reloadKeyBindings() {
        Preferences prefs = Preferences.userNodeForPackage(SettingsController.class);
        String keyName = prefs.get("jump_key", "SPACE");
        try {
            KeyCode key = KeyCode.valueOf(keyName);
            inputService.setJumpKey(key);
        } catch (IllegalArgumentException e) {
            inputService.setJumpKey(KeyCode.SPACE);
        }
    }

    private void handleAction(String action) {
        if (paused) return;
        GameState state = stateManager.getState();
        if (state == null) return;

        String phase = state.getPhase();
        if ("menu".equals(phase) || "gameover".equals(phase)) {
            if (currentMode == Mode.SINGLE || currentMode == Mode.RELAY_HOST) {
                GamePhysics.startGame(state);
            } else {
                wsService.sendInput("start");
            }
            Platform.runLater(() -> {
                pauseOverlay.setVisible(false);
                pauseOverlay.setManaged(false);
                gameOverOverlay.setVisible(false);
                gameOverOverlay.setManaged(false);
            });
        } else if ("playing".equals(phase)) {
            Player local = state.getPlayers().get("local");
            if (local != null && !local.isPaused()) {
                if (currentMode == Mode.SINGLE || currentMode == Mode.RELAY_HOST) {
                    GamePhysics.handleInput(local, "jump");
                    renderer.spawnJumpParticles(local.getX(), local.getY(), local.getSide());
                } else if (currentMode == Mode.DEDICATED) {
                    wsService.sendInput("jump");
                    predictor.predict(state, "local", "jump");
                    renderer.spawnJumpParticles(local.getX(), local.getY(), local.getSide());
                } else if (currentMode == Mode.RELAY_GUEST) {
                    wsService.sendInput("jump");
                }
            }
        }
    }

    private void handleSystem(String cmd) {
        if ("toggle_pause".equals(cmd)) {
            onTogglePause();
        } else if ("close_settings".equals(cmd)) {
            onCloseSettings();
        }
    }

    private void onGameTick(double deltaMs) {
        if (paused) return;
        GameState state = stateManager.getState();
        if (state == null) return;

        if (currentMode == Mode.SINGLE || currentMode == Mode.RELAY_HOST) {
            localEngine.tick(state);
        }

        Platform.runLater(() -> {
            double renderCamY;
            if (currentMode == Mode.SINGLE) {
                Player local = state.getPlayers().get("local");
                renderCamY = (local != null && local.getCameraY() != 0) ? local.getCameraY() : state.getCameraY();
            } else {
                renderCamY = state.getCameraY();
            }
            renderer.render(state, currentModeStr, renderCamY, wsService.isShowFps());
            updateLeaderboard(state);
            updateToolbar();

            if ("gameover".equals(state.getPhase())) {
                showGameOver(state);
            }
        });
    }

    private void onNetworkState(GameState remoteState) {
        if (currentMode == Mode.DEDICATED) {
            stateManager.reconcile(remoteState);
        } else {
            stateManager.setState(remoteState);
        }
    }

    private void onNetworkMessage(Map<String, Object> msg) {
        String type = (String) msg.get("type");
        if ("mode_confirmed".equals(type)) {
            String playerId = (String) msg.get("playerId");
            wsService.setMyId(playerId);
            Platform.runLater(() -> {
                if (connStatus != null) {
                    connStatus.setText("● 已连接");
                    connStatus.setStyle("-fx-text-fill: #4ecca3;");
                }
            });
        } else if ("room_created".equals(type)) {
            String roomId = (String) msg.get("roomId");
            wsService.setMyId("local");
            wsService.setCurrentRoomId(roomId); // 确保保存
            Platform.runLater(() -> {
                if (roomIdDisplay != null) roomIdDisplay.setText("房间: " + roomId);
                if (connStatus != null) {
                    connStatus.setText("● 已连接");
                    connStatus.setStyle("-fx-text-fill: #4ecca3;");
                }
            });
        } else if ("joined_room".equals(type)) {
            String roomId = (String) msg.get("roomId");
            String playerId = (String) msg.get("playerId");
            wsService.setMyId(playerId);
            wsService.setCurrentRoomId(roomId);
            Platform.runLater(() -> {
                if (roomIdDisplay != null) roomIdDisplay.setText("房间: " + roomId);
                if (connStatus != null) {
                    connStatus.setText("● 已连接");
                    connStatus.setStyle("-fx-text-fill: #4ecca3;");
                }
            });
        } else if ("player_joined".equals(type)) {
            String name = (String) msg.get("name");
            Platform.runLater(() -> {
                if (hint != null) hint.setText(name + " 加入了房间");
            });
        } else if ("error".equals(type)) {
            String error = (String) msg.get("message");
            Platform.runLater(() -> {
                if (hint != null) hint.setText("错误: " + error);
            });
        } else if ("room_closed".equals(type)) {
            Platform.runLater(() -> {
                if (hint != null) hint.setText("房间已关闭");
                if (connStatus != null) {
                    connStatus.setText("● 未连接");
                    connStatus.setStyle("-fx-text-fill: #ff6b6b;");
                }
            });
        }
    }

    private void updateLeaderboard(GameState state) {
        if (lbList == null) return;
        lbList.getChildren().clear();
        long activeCount = state.getPlayers().values().stream().filter(Player::isActive).count();
        // 单人模式或只有一人时不显示排行榜面板
        if (currentMode == Mode.SINGLE || activeCount <= 1) {
            if (leaderboard != null) {
                leaderboard.setVisible(false);
                leaderboard.setManaged(false);
            }
            return;
        }
        if (leaderboard != null) {
            leaderboard.setVisible(true);
            leaderboard.setManaged(true);
        }
        state.getPlayers().values().stream()
                .sorted((a, b) -> Integer.compare(b.getScore(), a.getScore()))
                .forEach(p -> {
                    Label lbl = new Label(p.getName() + ": " + p.getScore());
                    String color = p.getColor();
                    boolean isMe = "local".equals(p.getId());
                    boolean isDead = !p.isActive();
                    lbl.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 12px;" +
                            (isMe ? " -fx-font-weight: bold;" : "") +
                            (isDead ? " -fx-opacity: 0.5;" : ""));
                    lbList.getChildren().add(lbl);
                });
    }

    private void updateToolbar() {}

    private void showGameOver(GameState state) {
        gameLoop.stop();
        Player local = state.getPlayers().get("local");
        int score = local != null ? local.getScore() : 0;
        finalScoreLabel.setText("最终得分: " + score);
        gameOverOverlay.setVisible(true);
        gameOverOverlay.setManaged(true);
    }

    @FXML
    private void onTogglePause() {
        if (!isPlaying()) return;
        paused = !paused;
        pauseOverlay.setVisible(paused);
        pauseOverlay.setManaged(paused);
        hint.setText(paused ? "游戏已暂停（按 ESC 恢复）" : "按跳跃键 / 点击屏幕 / 触摸 来跳跃");

        GameState state = stateManager.getState();
        Player local = state != null ? state.getPlayers().get("local") : null;
        if (local != null) local.setPaused(paused);

        if (currentMode == Mode.DEDICATED || currentMode == Mode.RELAY_GUEST) {
            wsService.sendInput(paused ? "pause" : "resume");
        }

        if (!paused) {
            gameLoop.start();
            Platform.runLater(() -> {
                if (gameCanvas != null) gameCanvas.requestFocus();
            });
        }
    }

    @FXML
    private void onOpenSettings() {
        inputService.setSettingsOpen(true);
        gameLoop.stop();

        if (settingsOverlay != null && !settingsLoaded) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/wallrunner/client/view/settings.fxml"));
                Parent settingsRoot = loader.load();
                SettingsController sc = loader.getController();
                sc.setOnClose(this::onCloseSettings);
                settingsOverlay.getChildren().add(settingsRoot);
                settingsLoaded = true;
            } catch (Exception e) {
                e.printStackTrace();
                inputService.setSettingsOpen(false);
                if (!paused) gameLoop.start();
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
        inputService.setSettingsOpen(false);
        reloadKeyBindings();

        if (!paused) {
            gameLoop.start();
            Platform.runLater(() -> {
                if (gameCanvas != null) gameCanvas.requestFocus();
            });
        }
    }

    @FXML
    private void onReturnToMenu() {
        gameLoop.stop();
        wsService.disconnect();
        stateManager.reset();
        ClientApplication.switchScene("/com/wallrunner/client/view/menu.fxml");
    }

    @FXML
    private void onRestart() {
        gameOverOverlay.setVisible(false);
        gameOverOverlay.setManaged(false);
        stateManager.reset();
        if (currentMode == Mode.SINGLE || currentMode == Mode.RELAY_HOST) {
            stateManager.initLocalState(wsService.getPlayerName());
            stateManager.getState().setPhase("menu");
        }
        gameLoop.start();
    }

    private boolean isPlaying() {
        GameState state = stateManager.getState();
        return state != null && "playing".equals(state.getPhase());
    }

    private void updateConnStatus(String text, boolean connected) {
        if (connStatus == null) return;
        String color = connected ? "#4ecca3" : "#ff6b6b";
        connStatus.setText(text);
        connStatus.setStyle("-fx-text-fill: " + color + ";");
    }
}

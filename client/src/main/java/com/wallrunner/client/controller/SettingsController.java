package com.wallrunner.client.controller;

import com.wallrunner.client.ClientApplication;
import com.wallrunner.client.service.WebSocketClientService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.prefs.Preferences;

/**
 * 【模块】client / controller
 * 【代号】Z
 * 【职责】设置面板逻辑：读取/保存本地偏好。
 * 【重构】2026-05-08:
 *       1. 按键绑定改为"按下想绑定的按钮"方式，支持多键绑定同一行为。
 *       2. 每个已绑定按键可单独删除。
 *       3. 新增时间奖励设置（间隔秒数、每次加分）。
 *       4. 分数与高度关联，时间奖励独立计算。
 */
public class SettingsController {

    @FXML private TextField nameField;
    @FXML private CheckBox showFpsCheck;
    @FXML private CheckBox soundCheck;
    @FXML private CheckBox predictCheck;
    @FXML private VBox jumpKeysContainer;
    @FXML private Button btnAddJumpKey;
    @FXML private Label jumpKeyHint;
    @FXML private TextField timeIntervalField;
    @FXML private TextField timePointsField;

    private final WebSocketClientService wsService = WebSocketClientService.getInstance();
    private final Preferences prefs = Preferences.userNodeForPackage(SettingsController.class);
    private Runnable onClose;
    private boolean listeningForKey = false;
    private final Set<KeyCode> pendingJumpKeys = new LinkedHashSet<>();

    public void setOnClose(Runnable onClose) {
        this.onClose = onClose;
    }

    @FXML
    private void initialize() {
        nameField.setText(wsService.getPlayerName());
        showFpsCheck.setSelected(wsService.isShowFps());
        soundCheck.setSelected(wsService.isSoundEnabled());
        predictCheck.setSelected(wsService.isPredictionEnabled());

        // 加载按键绑定
        String keysStr = prefs.get("jump_keys", "SPACE");
        if (keysStr != null && !keysStr.isEmpty()) {
            for (String k : keysStr.split(",")) {
                try {
                    pendingJumpKeys.add(KeyCode.valueOf(k.trim()));
                } catch (IllegalArgumentException ignored) {}
            }
        }
        if (pendingJumpKeys.isEmpty()) pendingJumpKeys.add(KeyCode.SPACE);
        refreshJumpKeysUI();

        // 时间奖励设置
        double interval = prefs.getDouble("time_bonus_interval", 5.0);
        int points = prefs.getInt("time_bonus_points", 10);
        timeIntervalField.setText(String.valueOf(interval));
        timePointsField.setText(String.valueOf(points));

        // "按下绑定"按钮事件
        btnAddJumpKey.setOnAction(e -> startListeningForKey());
        jumpKeyHint.setText("点击上方按钮后，按下想绑定的按键");
    }

    private void startListeningForKey() {
        if (listeningForKey) return;
        listeningForKey = true;
        jumpKeyHint.setText("请按下想绑定的按键... (按 ESC 取消)");
        btnAddJumpKey.setDisable(true);

        Platform.runLater(() -> {
            if (btnAddJumpKey.getScene() != null) {
                btnAddJumpKey.getScene().addEventFilter(KeyEvent.KEY_PRESSED, this::onKeyPressedWhileListening);
            }
        });
    }

    private void onKeyPressedWhileListening(KeyEvent e) {
        if (!listeningForKey) return;
        KeyCode code = e.getCode();
        if (code == KeyCode.ESCAPE) {
            stopListening();
            return;
        }
        // 忽略修饰键和功能键
        if (code == KeyCode.UNDEFINED || code.isModifierKey() || code.isFunctionKey() || code.isNavigationKey()) {
            return;
        }
        if (!pendingJumpKeys.contains(code)) {
            pendingJumpKeys.add(code);
            refreshJumpKeysUI();
        }
        stopListening();
        e.consume();
    }

    private void stopListening() {
        listeningForKey = false;
        jumpKeyHint.setText("点击上方按钮后，按下想绑定的按键");
        btnAddJumpKey.setDisable(false);
        if (btnAddJumpKey.getScene() != null) {
            btnAddJumpKey.getScene().removeEventFilter(KeyEvent.KEY_PRESSED, this::onKeyPressedWhileListening);
        }
    }

    private void refreshJumpKeysUI() {
        jumpKeysContainer.getChildren().clear();
        for (KeyCode key : pendingJumpKeys) {
            HBox row = new HBox(8);
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            Label lbl = new Label(key.getName());
            lbl.setStyle("-fx-text-fill: #4ecca3; -fx-font-size: 14px; -fx-font-weight: bold;");
            Button del = new Button("✕");
            del.setStyle("-fx-background-color: #e94560; -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 2 8; -fx-background-radius: 4;");
            del.setOnAction(e -> {
                pendingJumpKeys.remove(key);
                refreshJumpKeysUI();
            });
            row.getChildren().addAll(lbl, del);
            jumpKeysContainer.getChildren().add(row);
        }
    }

    @FXML
    private void onSave() {
        wsService.setPlayerName(nameField.getText());
        wsService.setShowFps(showFpsCheck.isSelected());
        wsService.setSoundEnabled(soundCheck.isSelected());
        wsService.setPredictionEnabled(predictCheck.isSelected());

        // 保存按键绑定
        StringBuilder sb = new StringBuilder();
        for (KeyCode k : pendingJumpKeys) {
            if (sb.length() > 0) sb.append(",");
            sb.append(k.name());
        }
        prefs.put("jump_keys", sb.toString());

        // 保存时间奖励设置
        try {
            double interval = Double.parseDouble(timeIntervalField.getText().trim());
            wsService.setTimeBonusInterval(interval);
            prefs.putDouble("time_bonus_interval", interval);
        } catch (NumberFormatException e) {
            wsService.setTimeBonusInterval(5.0);
            prefs.putDouble("time_bonus_interval", 5.0);
        }
        try {
            int points = Integer.parseInt(timePointsField.getText().trim());
            wsService.setTimeBonusPoints(points);
            prefs.putInt("time_bonus_points", points);
        } catch (NumberFormatException e) {
            wsService.setTimeBonusPoints(10);
            prefs.putInt("time_bonus_points", 10);
        }

        close();
    }

    @FXML
    private void onCancel() {
        close();
    }

    private void close() {
        stopListening();
        if (onClose != null) {
            onClose.run();
        } else {
            backToMenu();
        }
    }

    private void backToMenu() {
        ClientApplication.switchScene("/com/wallrunner/client/view/menu.fxml");
    }
}

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
 * 设置面板逻辑：读取/保存本地偏好。
 *
 * 职责：
 * - 按键绑定（支持多键绑定同一行为）。
 * - 时间奖励设置。
 * - 预留：主题切换（深色/浅色）接口。
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

    // 角色颜色设置
    @FXML private CheckBox autoColorCheck;
    @FXML private VBox customColorBox;
    @FXML private TextField fillColorField;
    @FXML private TextField strokeColorField;
    @FXML private javafx.scene.layout.Region fillColorPreview;
    @FXML private javafx.scene.layout.Region strokeColorPreview;

    // 【跨设备联机】服务器地址配置
    @FXML private TextField serverAddressField;
    @FXML private TextField serverPortField;

    // 预留：主题选择器
    @FXML private ComboBox<String> themeSelector;

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

        // 【跨设备联机】加载服务器地址配置
        String savedAddress = prefs.get("server_address", "localhost");
        int savedPort = prefs.getInt("server_port", 8080);
        if (serverAddressField != null) serverAddressField.setText(savedAddress);
        if (serverPortField != null) serverPortField.setText(String.valueOf(savedPort));
        wsService.setServerAddress(savedAddress);
        wsService.setServerPort(savedPort);

        // 预留：主题选择器初始化
        if (themeSelector != null) {
            themeSelector.getItems().addAll("深色", "浅色");
            String savedTheme = prefs.get("theme", "深色");
            themeSelector.setValue(savedTheme);
        }

        // 角色颜色设置初始化
        boolean autoColor = prefs.getBoolean("auto_color", true);
        if (autoColorCheck != null) {
            autoColorCheck.setSelected(autoColor);
            autoColorCheck.selectedProperty().addListener((obs, old, val) -> {
                if (customColorBox != null) {
                    customColorBox.setVisible(!val);
                    customColorBox.setManaged(!val);
                }
            });
        }
        if (customColorBox != null) {
            customColorBox.setVisible(!autoColor);
            customColorBox.setManaged(!autoColor);
        }
        String savedFill = prefs.get("fill_color", "");
        String savedStroke = prefs.get("stroke_color", "");
        if (fillColorField != null) fillColorField.setText(savedFill);
        if (strokeColorField != null) strokeColorField.setText(savedStroke);
        updateColorPreviews();

        // 颜色输入监听
        if (fillColorField != null) {
            fillColorField.textProperty().addListener((obs, old, val) -> updateColorPreviews());
        }
        if (strokeColorField != null) {
            strokeColorField.textProperty().addListener((obs, old, val) -> updateColorPreviews());
        }

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

        // 【跨设备联机】保存服务器地址配置
        if (serverAddressField != null) {
            String address = serverAddressField.getText().trim();
            if (!address.isEmpty()) {
                wsService.setServerAddress(address);
                prefs.put("server_address", address);
            }
        }
        if (serverPortField != null) {
            try {
                int port = Integer.parseInt(serverPortField.getText().trim());
                wsService.setServerPort(port);
                prefs.putInt("server_port", port);
            } catch (NumberFormatException e) {
                wsService.setServerPort(8080);
                prefs.putInt("server_port", 8080);
            }
        }

        // 预留：保存主题设置
        if (themeSelector != null) {
            prefs.put("theme", themeSelector.getValue());
        }

        // 保存角色颜色设置
        if (autoColorCheck != null) {
            boolean auto = autoColorCheck.isSelected();
            prefs.putBoolean("auto_color", auto);
            if (auto) {
                wsService.setFillColor("");
                wsService.setStrokeColor("");
                prefs.put("fill_color", "");
                prefs.put("stroke_color", "");
            } else {
                String fill = fillColorField != null ? fillColorField.getText().trim() : "";
                String stroke = strokeColorField != null ? strokeColorField.getText().trim() : "";
                if (isValidHexColor(fill)) {
                    wsService.setFillColor(fill);
                    prefs.put("fill_color", fill);
                }
                if (isValidHexColor(stroke)) {
                    wsService.setStrokeColor(stroke);
                    prefs.put("stroke_color", stroke);
                }
            }
        }

        close();
    }

    @FXML
    private void onCancel() {
        close();
    }

    private void updateColorPreviews() {
        if (fillColorPreview != null) {
            String fill = fillColorField != null ? fillColorField.getText().trim() : "";
            if (isValidHexColor(fill)) {
                fillColorPreview.setStyle("-fx-background-color: " + fill + "; -fx-background-radius: 4; -fx-border-radius: 4; -fx-border-color: #444;");
            } else {
                fillColorPreview.setStyle("-fx-background-color: #333; -fx-background-radius: 4; -fx-border-radius: 4; -fx-border-color: #444;");
            }
        }
        if (strokeColorPreview != null) {
            String stroke = strokeColorField != null ? strokeColorField.getText().trim() : "";
            if (isValidHexColor(stroke)) {
                strokeColorPreview.setStyle("-fx-background-color: " + stroke + "; -fx-background-radius: 4; -fx-border-radius: 4; -fx-border-color: #444;");
            } else {
                strokeColorPreview.setStyle("-fx-background-color: #333; -fx-background-radius: 4; -fx-border-radius: 4; -fx-border-color: #444;");
            }
        }
    }

    private boolean isValidHexColor(String hex) {
        if (hex == null || hex.isEmpty()) return false;
        return hex.matches("#[0-9A-Fa-f]{6}");
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

package com.wallrunner.client.controller;

import com.wallrunner.client.ClientApplication;
import com.wallrunner.client.service.WebSocketClientService;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TextField;

import java.util.prefs.Preferences;

/**
 * 【模块】client / controller
 * 【代号】Z
 * 【职责】设置面板逻辑：读取/保存本地偏好，支持跳跃键绑定。
 * 【修复】2026-05-08: backToMenu() 使用无参 switchScene，适配统一窗口尺寸。
 */
public class SettingsController {

    @FXML private TextField nameField;
    @FXML private CheckBox showFpsCheck;
    @FXML private CheckBox soundCheck;
    @FXML private CheckBox predictCheck;
    @FXML private ChoiceBox<String> jumpKeyChoice;

    private final WebSocketClientService wsService = WebSocketClientService.getInstance();
    private final Preferences prefs = Preferences.userNodeForPackage(SettingsController.class);
    private Runnable onClose;

    public void setOnClose(Runnable onClose) {
        this.onClose = onClose;
    }

    @FXML
    private void initialize() {
        nameField.setText(wsService.getPlayerName());
        showFpsCheck.setSelected(wsService.isShowFps());
        soundCheck.setSelected(wsService.isSoundEnabled());
        predictCheck.setSelected(wsService.isPredictionEnabled());

        jumpKeyChoice.getItems().addAll("SPACE", "Z", "X", "C", "UP", "W", "SHIFT", "ENTER");
        String savedKey = prefs.get("jump_key", "SPACE");
        jumpKeyChoice.setValue(savedKey);
    }

    @FXML
    private void onSave() {
        wsService.setPlayerName(nameField.getText());
        wsService.setShowFps(showFpsCheck.isSelected());
        wsService.setSoundEnabled(soundCheck.isSelected());
        wsService.setPredictionEnabled(predictCheck.isSelected());
        prefs.put("jump_key", jumpKeyChoice.getValue());
        close();
    }

    @FXML
    private void onCancel() {
        close();
    }

    private void close() {
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

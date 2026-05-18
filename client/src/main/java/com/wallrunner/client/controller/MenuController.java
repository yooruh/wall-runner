package com.wallrunner.client.controller;

import com.wallrunner.client.ClientApplication;
import com.wallrunner.client.service.StateManager;
import com.wallrunner.client.service.WebSocketClientService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.TextField;
import javafx.scene.control.Button;

/**
 * 【模块】client / controller
 * 【代号】Z
 * 【职责】主菜单交互逻辑。与原始网页版 menu.js 严格对齐。
 * 【特性】保存玩家名字到 Preferences，加载已保存名字，按钮禁用状态。
 * 【原则】仅处理用户事件与服务调用，不操作样式。
 * 【修复】2026-05-08:
 *       1. 进入公共服务器前检查连接是否成功，失败时阻止切场景并恢复按钮。
 *       2. 创建/加入房间前同样检查连接状态。
 *       3. 统一使用 ClientApplication 的窗口尺寸切换，避免窗口忽大忽小。
 */
public class MenuController {

    @FXML private TextField playerNameField;
    @FXML private TextField joinRoomField;
    @FXML private Button btnSingle;
    @FXML private Button btnDedicated;
    @FXML private Button btnCreateRoom;
    @FXML private Button btnJoinRoom;
    @FXML private Button btnSettings;

    private final WebSocketClientService wsService = WebSocketClientService.getInstance();
    private final StateManager stateManager = StateManager.getInstance();

    @FXML
    private void initialize() {
        playerNameField.setText(stateManager.loadSavedName());
        playerNameField.textProperty().addListener((obs, old, val) -> {
            stateManager.saveName(val);
            wsService.setPlayerName(val);
        });
    }

    private String getName() {
        String name = playerNameField.getText().trim();
        return name.isEmpty() ? "玩家" : name;
    }

    private void saveName() {
        stateManager.saveName(playerNameField.getText());
        wsService.setPlayerName(getName());
    }

    private void setButtonsDisabled(boolean disabled) {
        btnSingle.setDisable(disabled);
        btnDedicated.setDisable(disabled);
        btnCreateRoom.setDisable(disabled);
        btnJoinRoom.setDisable(disabled);
    }

    private void restoreButtons() {
        Platform.runLater(() -> setButtonsDisabled(false));
    }

    @FXML
    private void onSinglePlayer() {
        saveName();
        setButtonsDisabled(true);
        ClientApplication.switchScene("/com/wallrunner/client/view/game.fxml");
        GameController.setMode(GameController.Mode.SINGLE);
    }

    @FXML
    private void onDedicated() {
        saveName();
        setButtonsDisabled(true);
        if (!wsService.isConnected()) {
            boolean ok = wsService.connect("ws://localhost:8080/ws/game");
            if (!ok) {
                showAlert("无法连接到主服务器，请检查服务器是否运行。");
                restoreButtons();
                return;
            }
        }
        wsService.joinDedicated(getName());
        ClientApplication.switchScene("/com/wallrunner/client/view/game.fxml");
        GameController.setMode(GameController.Mode.DEDICATED);
    }

    @FXML
    private void onCreateRoom() {
        saveName();
        setButtonsDisabled(true);
        if (!wsService.isConnected()) {
            boolean ok = wsService.connect("ws://localhost:8080/ws/game");
            if (!ok) {
                showAlert("无法连接到服务器，请检查网络或服务器状态。");
                restoreButtons();
                return;
            }
        }
        wsService.createRoom(getName());
        ClientApplication.switchScene("/com/wallrunner/client/view/game.fxml");
        GameController.setMode(GameController.Mode.RELAY_HOST);
    }

    @FXML
    private void onJoinRoom() {
        String roomId = joinRoomField.getText().trim().toUpperCase();
        if (roomId.isEmpty()) {
            showAlert("请输入房间号");
            return;
        }
        saveName();
        setButtonsDisabled(true);
        if (!wsService.isConnected()) {
            boolean ok = wsService.connect("ws://localhost:8080/ws/game");
            if (!ok) {
                showAlert("无法连接到服务器，请检查网络或服务器状态。");
                restoreButtons();
                return;
            }
        }
        wsService.joinRoom(roomId, getName());
        ClientApplication.switchScene("/com/wallrunner/client/view/game.fxml");
        GameController.setMode(GameController.Mode.RELAY_GUEST);
    }

    @FXML
    private void onOpenSettings() {
        ClientApplication.switchScene("/com/wallrunner/client/view/settings.fxml");
    }

    private void showAlert(String msg) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("提示");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}

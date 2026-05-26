package com.wallrunner.client.controller;

import com.wallrunner.client.ClientApplication;
import com.wallrunner.client.service.IStateManager;
import com.wallrunner.client.service.IWebSocketClient;
import com.wallrunner.client.service.StateManager;
import com.wallrunner.client.service.WebSocketClientService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.TextField;
import javafx.scene.control.Button;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

/**
 * 主菜单交互逻辑。
 *
 * 职责：
 * - 处理用户事件与服务调用。
 * - 保存玩家名字到 Preferences。
 * - 所有模式切换必须先 setMode 再 switchScene，确保 GameController 读取正确模式。
 */
public class MenuController {

    @FXML private TextField playerNameField;
    @FXML private TextField joinRoomField;
    @FXML private TextField customRoomField;
    @FXML private Button btnSingle;
    @FXML private Button btnDedicated;
    @FXML private Button btnCreateRoom;
    @FXML private Button btnJoinRoom;
    @FXML private Button btnSettings;
    @FXML private Button btnCopyRoom;
    @FXML private javafx.scene.control.Label roomCodeLabel;

    private final IWebSocketClient wsService;
    private final IStateManager stateManager;

    public MenuController() {
        this(WebSocketClientService.getInstance(), StateManager.getInstance());
    }

    public MenuController(IWebSocketClient wsService, IStateManager stateManager) {
        this.wsService = wsService;
        this.stateManager = stateManager;
    }

    @FXML
    private void initialize() {
        playerNameField.setText(stateManager.loadSavedName());
        playerNameField.textProperty().addListener((obs, old, val) -> {
            stateManager.saveName(val);
            wsService.setPlayerName(val);
        });
        // 加载保存的自定义颜色设置
        wsService.loadSavedColors();
        if (btnCopyRoom != null) {
            btnCopyRoom.setVisible(false);
            btnCopyRoom.setManaged(false);
        }
        if (roomCodeLabel != null) {
            roomCodeLabel.setVisible(false);
            roomCodeLabel.setManaged(false);
        }
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
        GameController.setMode(GameController.Mode.SINGLE);
        ClientApplication.switchScene("/com/wallrunner/client/view/game.fxml");
    }

    @FXML
    private void onDedicated() {
        saveName();
        setButtonsDisabled(true);
        wsService.setOnConnectionError(errMsg -> {
            Platform.runLater(() -> {
                showAlert(errMsg);
                restoreButtons();
            });
        });
        if (!wsService.isConnected()) {
            boolean ok = wsService.connect(wsService.getServerUrl());
            if (!ok) {
                // 错误已由回调处理
                return;
            }
        }
        wsService.joinDedicated(getName());
        GameController.setMode(GameController.Mode.DEDICATED);
        ClientApplication.switchScene("/com/wallrunner/client/view/game.fxml");
    }

    @FXML
    private void onCreateRoom() {
        saveName();
        String customId = customRoomField != null ? customRoomField.getText().trim().toUpperCase() : "";
        if (!customId.isEmpty() && !customId.matches("[A-Z0-9]+")) {
            showAlert("房间码只能包含大写字母和数字");
            return;
        }
        setButtonsDisabled(true);
        wsService.setOnConnectionError(errMsg -> {
            Platform.runLater(() -> {
                showAlert(errMsg);
                restoreButtons();
            });
        });
        if (!wsService.isConnected()) {
            boolean ok = wsService.connect(wsService.getServerUrl());
            if (!ok) {
                return;
            }
        }
        wsService.createRoom(getName(), customId.isEmpty() ? null : customId);
        GameController.setMode(GameController.Mode.RELAY_HOST);
        ClientApplication.switchScene("/com/wallrunner/client/view/game.fxml");
    }

    @FXML
    private void onJoinRoom() {
        String roomId = joinRoomField.getText().trim().toUpperCase();
        if (roomId.isEmpty()) {
            showAlert("请输入房间号");
            return;
        }
        if (!roomId.matches("[A-Z0-9]+")) {
            showAlert("房间码只能包含大写字母和数字");
            return;
        }
        saveName();
        setButtonsDisabled(true);
        wsService.setOnConnectionError(errMsg -> {
            Platform.runLater(() -> {
                showAlert(errMsg);
                restoreButtons();
            });
        });
        if (!wsService.isConnected()) {
            boolean ok = wsService.connect(wsService.getServerUrl());
            if (!ok) {
                return;
            }
        }
        wsService.joinRoom(roomId, getName());
        GameController.setMode(GameController.Mode.RELAY_GUEST);
        ClientApplication.switchScene("/com/wallrunner/client/view/game.fxml");
    }

    @FXML
    private void onCopyRoomCode() {
        String roomId = wsService.getCurrentRoomId();
        if (roomId != null && !roomId.isEmpty()) {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(roomId);
            clipboard.setContent(content);
            showAlert("房间码已复制到剪贴板: " + roomId);
        }
    }

    @FXML
    private void onOpenSettings() {
        ClientApplication.switchScene("/com/wallrunner/client/view/settings.fxml");
    }

    private void showAlert(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("提示");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}

package com.wallrunner.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;

/**
 * 【模块】client
 * 【代号】Y + Z
 * 【职责】JavaFX 桌面客户端入口。
 * 【原则】仅负责舞台初始化与场景切换，不处理业务逻辑（委托给 Controller）。
 * 【修复】2026-05-08:
 *       1. 在 switchScene() 中，FXML 加载完成后获取 Controller，
 *          并在 Scene 设置到 Stage 后调用 controller.onSceneReady(scene)，
 *          避免 GameController.initialize() 中 gameCanvas.getScene() 为 null 导致 NPE。
 *       2. 移除 setResizable(false)，允许用户调整窗口大小。
 *       3. 统一窗口初始尺寸为 640×720，切换场景时保持窗口大小不变，
 *          解决菜单/游戏/设置窗口忽大忽小问题。
 *       4. 添加 F11 全屏切换快捷键。
 */
public class ClientApplication extends Application {

    private static Stage primaryStage;
    private static final double DEFAULT_WIDTH = 640;
    private static final double DEFAULT_HEIGHT = 720;

    @Override
    public void start(Stage stage) throws Exception {
        primaryStage = stage;
        primaryStage.setTitle("墙间跑酷 — 联机版");
        primaryStage.setResizable(true);
        primaryStage.setMinWidth(480);
        primaryStage.setMinHeight(400);
        switchScene("/com/wallrunner/client/view/menu.fxml");
        primaryStage.show();
    }

    public static void switchScene(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(ClientApplication.class.getResource(fxmlPath));
            Parent root = loader.load();

            // 保持当前窗口大小；若尚未初始化则使用默认值
            double w = primaryStage.getScene() != null ? primaryStage.getWidth() : DEFAULT_WIDTH;
            double h = primaryStage.getScene() != null ? primaryStage.getHeight() : DEFAULT_HEIGHT;
            Scene scene = new Scene(root, w, h);
            scene.getStylesheets().add(ClientApplication.class.getResource(
                    "/com/wallrunner/client/css/client-theme.css").toExternalForm());

            // F11 全屏切换
            scene.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.F11) {
                    primaryStage.setFullScreen(!primaryStage.isFullScreen());
                }
            });

            primaryStage.setScene(scene);

            // 如果这是第一次设置场景，显式设置窗口大小
            if (primaryStage.getWidth() < 100 || primaryStage.getHeight() < 100) {
                primaryStage.setWidth(DEFAULT_WIDTH);
                primaryStage.setHeight(DEFAULT_HEIGHT);
            }

            // 如果 Controller 实现了场景就绪回调，则在外部调用
            Object controller = loader.getController();
            if (controller instanceof com.wallrunner.client.controller.GameController gc) {
                gc.onSceneReady(scene);
            }
        } catch (Exception e) {
            System.err.println("[Client] Scene switch failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    public static void main(String[] args) {
        launch(args);
    }
}

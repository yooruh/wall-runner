package com.wallrunner.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;

/**
 * JavaFX 桌面客户端入口。
 *
 * 职责：
 * - 舞台初始化与场景切换。
 * - F11 全屏切换快捷键。
 * - 不处理业务逻辑（委托给 Controller）。
 *
 * 扩展预留：
 * - 主题切换（深色/浅色）通过 CSS 文件动态加载实现。
 */
public class ClientApplication extends Application {

    private static Stage primaryStage;
    private static final double DEFAULT_WIDTH = 640;
    private static final double DEFAULT_HEIGHT = 780;

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

            double w, h;
            Scene oldScene = primaryStage.getScene();
            if (oldScene != null) {
                w = oldScene.getWidth();
                h = oldScene.getHeight();
            } else {
                w = DEFAULT_WIDTH;
                h = DEFAULT_HEIGHT;
            }
            Scene scene = new Scene(root, w, h);

            // 加载主题 CSS（预留：可通过设置切换不同主题文件）
            scene.getStylesheets().add(ClientApplication.class.getResource(
                    "/com/wallrunner/client/css/client-theme.css").toExternalForm());

            // F11 全屏切换
            scene.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.F11) {
                    primaryStage.setFullScreen(!primaryStage.isFullScreen());
                }
            });

            primaryStage.setScene(scene);
            if (oldScene == null) {
                primaryStage.setWidth(DEFAULT_WIDTH);
                primaryStage.setHeight(DEFAULT_HEIGHT);
            }

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

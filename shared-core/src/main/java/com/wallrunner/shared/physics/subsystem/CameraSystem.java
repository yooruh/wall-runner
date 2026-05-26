package com.wallrunner.shared.physics.subsystem;

import com.wallrunner.shared.entity.GameState;
import com.wallrunner.shared.entity.Player;

import java.util.List;
import java.util.Map;

import static com.wallrunner.shared.constants.GameConstants.*;

/**
 * 摄像机系统实现。
 * 
 * UML 建模意义：ICameraSystem 的具体实现，展示"橡皮筋"平滑跟随算法。
 */
public class CameraSystem implements ICameraSystem {

    @Override
    public void updateDisplayCamera(GameState state, List<Player> activePlayers) {
        if (activePlayers.isEmpty()) return;
        double leadCamY = activePlayers.stream()
                .mapToDouble(Player::getCameraY)
                .min()
                .orElse(state.getCameraY());
        state.setCameraTargetY(leadCamY);
        state.setCameraY(state.getCameraY() + (state.getCameraTargetY() - state.getCameraY()) * CAMERA_SMOOTH);
    }

    @Override
    public void updatePlayerCameras(List<Player> activePlayers, Map<String, Boolean> blockedMap) {
        for (Player player : activePlayers) {
            if (player.isPaused()) continue;
            boolean isBlocked = Boolean.TRUE.equals(blockedMap.get(player.getId()));
            if (!isBlocked) {
                player.setCameraTargetY(player.getY() - CANVAS_HEIGHT * CAMERA_OFFSET_RATIO);
            } else {
                player.setCameraTargetY(player.getCameraTargetY() - CLIMB_SPEED);
            }
            if (player.getCameraY() == 0) {
                player.setCameraY(player.getCameraTargetY());
            }
            player.setCameraY(player.getCameraY() + (player.getCameraTargetY() - player.getCameraY()) * CAMERA_SMOOTH);
        }
    }
}

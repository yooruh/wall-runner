package com.wallrunner.shared.physics.subsystem;

import com.wallrunner.shared.entity.GameState;
import com.wallrunner.shared.entity.Player;
import java.util.List;
import java.util.Map;

/**
 * 摄像机系统接口。
 * 
 * UML 建模意义：展示摄像机跟随/平滑/切换的抽象。
 */
public interface ICameraSystem {
    void updateDisplayCamera(GameState state, List<Player> activePlayers);
    void updatePlayerCameras(List<Player> activePlayers, Map<String, Boolean> blockedMap);
}

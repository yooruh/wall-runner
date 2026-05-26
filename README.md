# 墙间跑酷 — 联机版 (WallRunner)

**版本**: 3.0.0 | **Java**: 21 | **架构**: C/S 三层分离

---

## 项目简介

《墙间跑酷》是一款主打**竖向攀爬 + 左右横跳**的轻竞技桌面游戏。玩家仅需一个按键（空格 / 点击 / 触摸）即可完成"跳跃换墙"，在双墙之间不断向上攀爬，躲避墙刺与浮动平台，支持单人离线游玩与多人实时联机。

---

## 技术栈

| 层级 | 技术 |
|------|------|
| 共享核心 | Java 21（零框架依赖） |
| 服务端 | Spring Boot 3.1.2 + WebSocket + Jackson 2.15.2 |
| 客户端 | JavaFX 20 + FXML + CSS |
| 构建工具 | Maven 3.11.0 |

---

## 快速开始

### 1. 编译
```bash
mvn clean install
```

### 2. 启动服务端
```bash
cd server
mvn spring-boot:run
```
服务端默认监听所有网卡 `ws://0.0.0.0:8080/ws/game`，局域网内其他设备可直接访问。

### 3. 启动客户端
```bash
cd client
mvn javafx:run
```

---

## 游戏模式

| 模式 | 说明 | 物理运行方 |
|------|------|-----------|
| **单人模式** | 离线本地游戏，无需网络 | 客户端 |
| **公共服务器 (Dedicated)** | 服务端运行权威物理，8ms tick，16ms 广播 STATE | 服务端 |
| **创建房间 (Relay Host)** | 房主客户端运行物理，通过服务端转发 STATE 给访客 | 房主客户端 |
| **加入房间 (Relay Guest)** | 接收房主广播的 STATE，本地只做渲染 | 房主客户端 |

---

## 操作

| 操作 | 按键 |
|------|------|
| 跳跃 | 空格键 / 鼠标点击 / 触摸 |
| 暂停 / 恢复 | ESC 或 P |
| 全屏切换 | F11 |
| 旁观切换 | 死亡后按跳跃键 |

---

## 项目结构

```
wall-runner/
├── shared-core/    # 零框架依赖：实体 POJO + 物理引擎 + 游戏常量
│   ├── entity/     # Player, Obstacle, GameState, Collectible
│   ├── physics/    # GamePhysics（权威物理计算）
│   └── constants/  # GameConstants（唯一可信数值源）
├── server/         # Spring Boot WebSocket 服务端
│   ├── handler/    # GameWebSocketHandler（消息路由）
│   ├── service/    # DedicatedService, RelayService, RoomManager, SessionManager
│   └── config/     # WebSocketConfig
└── client/         # JavaFX 桌面客户端
    ├── controller/ # MenuController, GameController, SettingsController
    ├── service/    # Renderer, StateManager, WebSocketClientService, InputService, GameLoopService
    ├── engine/     # LocalPhysicsEngine, Predictor
    └── view/       # menu.fxml, game.fxml, settings.fxml + client-theme.css
```

---

## 核心特性

- **本地优先物理**：所有模式均运行本地物理，网络状态仅用于校正，确保 120 FPS 画面丝滑流畅。
- **橡皮筋摄像机**：每位玩家拥有独立的摄像机平滑跟随逻辑（插值系数 0.1），死亡后自动切换到最靠前存活玩家位置实现观战。
- **玩家间碰撞**：跳跃玩家撞击非跳跃玩家时，被撞者进入"击退 + 旋转 + 闪烁无敌"状态。
- **生命系统**：每名玩家初始 3 条命，掉出镜头底部死亡一次，生命归零则淘汰。
- **自定义角色外观**：支持 8 组预设颜色（填充 + 描边），服务器自动分配不重复颜色，也可客户端通过调色板自定义。
- **跨设备联机**：服务端绑定 `0.0.0.0`，客户端可在设置面板中配置服务器 IP 与端口，支持局域网 / 公网联机。

---

## 预留扩展

- **特效系统**：闪光、拖尾、爆炸、护盾光环（字段与接口已预留）
- **技能系统**：冲刺、二段跳、子弹时间、瞬移（字段与接口已预留）
- **可收集物**：金币、宝石、道具、护盾（Collectible 实体与渲染已定义）
- **主题切换**：深色 / 浅色 CSS 动态加载（接口已预留）
- **难度系统**：随高度自动递增障碍物速度与密度（GameState 字段已预留）
- **动态障碍物**：摆动、追逐、旋转（Obstacle 字段已预留）

详见 `ARCHITECTURE.md` 与 `WallRunner设计文档.md`。

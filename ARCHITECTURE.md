# WallRunner 架构文档 v2.0 — 全Java重构版

> 日期: 2026-05-08  
> 架构模式: **C/S 前后端分离**，**X+Y/Y+Z 扩展分层**，**样式与逻辑彻底分离**  
> 技术栈: Java 21, Spring Boot 3.x (Server) + JavaFX 17 (Client)

---

## 1. 总体架构

本项目基于 **AKF 扩展立方体** 的分层思想，将系统沿 **X(基础设施/水平扩展)、Y(功能/共享核心)、Z(表现/用户交互)** 三个维度拆分：

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           客户端 (Client)  —  【Y + Z】                      │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │  Z 层：表现与交互 (Presentation)                                      │  │
│  │  ├─ view/*.fxml      — 声明式UI结构，零逻辑，零样式（仅布局）          │  │
│  │  ├─ css/client-theme.css — 全部视觉样式（颜色、字体、边距、动画）      │  │
│  │  └─ controller/*     — 事件监听、视图更新、数据绑定（纯逻辑）          │  │
│  ├─────────────────────────────────────────────────────────────────────┤  │
│  │  Y 层：共享核心复用 (Shared-Core)                                     │  │
│  │  ├─ com.wallrunner.shared.entity.*   — Player, Obstacle, GameState   │  │
│  │  ├─ com.wallrunner.shared.physics.*  — GamePhysics, GameConstants      │  │
│  │  └─ client.engine.*                  — 本地预测、权威调和              │  │
│  ├─────────────────────────────────────────────────────────────────────┤  │
│  │  网络适配层                                                          │  │
│  │  └─ service/WebSocketClientService — java.net.http.WebSocket 客户端   │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
│                                    │ WebSocket (JSON over TCP)            │
└────────────────────────────────────┼──────────────────────────────────────┘
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           服务端 (Server) — 【X + Y】                        │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │  X 层：基础设施与治理 (Infrastructure)                                │  │
│  │  ├─ config/WebSocketConfig — 端点注册、握手拦截、线程策略               │  │
│  │  ├─ handler/GameWebSocketHandler — 消息路由、编解码、生命周期           │  │
│  │  └─ service/* — RoomManager(房间), SessionManager(会话)              │  │
│  ├─────────────────────────────────────────────────────────────────────┤  │
│  │  Y 层：共享核心复用 (Shared-Core)                                     │  │
│  │  ├─ com.wallrunner.shared.entity.*   — 权威状态数据结构                │  │
│  │  └─ com.wallrunner.shared.physics.*  — 权威物理计算                   │  │
│  ├─────────────────────────────────────────────────────────────────────┤  │
│  │  业务编排层                                                          │  │
│  │  ├─ service/DedicatedService — 公共服务器模式（权威物理）             │  │
│  │  └─ service/RelayService     — P2P转发模式（仅消息中继）               │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. 模块职责与代号

| 模块 | 代号 | 职责 | 技术栈 | 依赖方向 |
|------|------|------|--------|----------|
| **shared-core** | **Y** | 游戏核心：实体 POJO、物理引擎、常量。服务端与客户端的**唯一可信逻辑源**。 | 纯 Java 21（零框架依赖） | 无 |
| **server** | **X + Y** | 网络网关、房间管理、会话治理、两种服务模式。通过依赖 Y 获得权威物理能力。 | Spring Boot 3.x, WebSocket, Jackson | shared-core |
| **client** | **Y + Z** | JavaFX 桌面客户端：渲染管线、输入抽象、UI 交互、本地预测。通过依赖 Y 保证逻辑一致性。 | JavaFX 17, FXML, CSS | shared-core |

---

## 3. 核心设计原则

### 3.1 样式与逻辑彻底分离 (Z层自治)

- **FXML** 仅描述 UI 的**树形结构**与**控件ID**，禁止内联样式、禁止脚本事件。
- **CSS** 承担全部视觉表现：颜色主题、字体、边距、圆角、渐变、动画关键帧。
- **Controller** 仅处理**业务逻辑**与**数据绑定**：事件监听、服务调用、状态更新。
- 三者通过 `fx:id` 与 `styleClass` 松耦合，任何一层变更不破坏其他层。

### 3.2 X+Y/Y+Z 扩展思想

- **X 轴（水平克隆）**：Server 为无状态网关 + 有状态房间进程，可通过负载均衡水平扩展；Client 可任意多开实例。
- **Y 轴（功能拆分）**：Shared-Core 将物理、实体、常量正交拆分；Server 将 Dedicated / Relay / Session / Room 按职责隔离。
- **Z 轴（用户分区）**：RoomManager 按 `roomId` 哈希分区，不同房间的数据完全隔离，支持分片部署。

### 3.3 C/S 与前后端分离

- **前端 (Client)**：独立 JavaFX 应用，独立 JVM 进程，独立生命周期，可独立打包为 `.exe`/`.dmg`/`.jar`。
- **后端 (Server)**：独立 Spring Boot 应用，独立 JVM 进程，暴露 WebSocket 端口 8080。
- **契约**：双方仅通过 JSON 消息 DTO 交互，不共享任何 UI 类或框架类。

---

## 4. 目录结构

```
wall-runner-java/
├── docs/
│   └── ARCHITECTURE.md
├── shared-core/
│   └── src/main/java/com/wallrunner/shared/
│       ├── constants/GameConstants.java
│       ├── entity/Player.java
│       ├── entity/Obstacle.java
│       ├── entity/GameState.java
│       └── physics/GamePhysics.java
├── server/
│   └── src/main/java/com/wallrunner/server/
│       ├── WallRunnerServer.java
│       ├── config/WebSocketConfig.java
│       ├── handler/GameWebSocketHandler.java
│       ├── service/DedicatedService.java
│       ├── service/RelayService.java
│       ├── service/RoomManager.java
│       ├── service/SessionManager.java
│       └── dto/NetworkMessage.java
└── client/
    └── src/main/java/com/wallrunner/client/
        ├── ClientApplication.java
        ├── controller/MainController.java
        ├── controller/MenuController.java
        ├── controller/GameController.java
        ├── controller/SettingsController.java
        ├── view/main.fxml
        ├── view/menu.fxml
        ├── view/game.fxml
        ├── view/settings.fxml
        ├── css/client-theme.css
        ├── service/WebSocketClientService.java
        ├── service/GameLoopService.java
        ├── service/InputService.java
        ├── service/Renderer.java
        ├── service/StateManager.java
        ├── engine/LocalPhysicsEngine.java
        └── engine/Predictor.java
```

---

## 5. 通信协议

客户端与服务端通过 **JSON WebSocket** 通信，消息格式统一为 `NetworkMessage`：

```json
{
  "type": "JOIN | CREATE | STATE | INPUT | LEAVE | ERROR",
  "roomId": "string",
  "payload": { ... }
}
```

- **Client → Server**: `INPUT` (玩家输入), `JOIN` (加入房间), `CREATE` (创建房间)
- **Server → Client**: `STATE` (权威游戏状态), `ERROR` (错误通知)

---

## 6. 样式分离示例

FXML 中：
```xml
<Button fx:id="btnSingle" text="开始单人游戏" styleClass="menu-btn, primary"/>
```

CSS 中：
```css
.menu-btn {
    -fx-font-size: 14px;
    -fx-padding: 10 24;
    -fx-background-radius: 8;
}
.menu-btn.primary {
    -fx-background-color: #4ecca3;
    -fx-text-fill: #1a1a2e;
}
.menu-btn.primary:hover {
    -fx-background-color: #3db892;
}
```

Controller 中：
```java
@FXML
private Button btnSingle;

@FXML
private void initialize() {
    btnSingle.setOnAction(e -> menuService.enterSinglePlayer());
}
```

> **零内联样式，零脚本事件，纯声明式结构 + 纯样式表 + 纯逻辑控制器**

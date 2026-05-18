# WallRunner 墙间跑酷 — 全Java重构版

> 基于 **X+Y/Y+Z 扩展分层** + **C/S 前后端分离** + **样式逻辑彻底分离** 的架构大改版本。

---

## 架构概览

```
┌─────────────────────────────────────────────────────────────────────┐
│                        客户端 (Client)  — 【Y + Z】                  │
│  Z层：JavaFX FXML(结构) + CSS(样式) + Controller(逻辑)               │
│  Y层：复用 shared-core 的 entity / physics                           │
└─────────────────────────────────────────────────────────────────────┘
                                    │ WebSocket JSON
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                        服务端 (Server) — 【X + Y】                   │
│  X层：Spring Boot WebSocket 网关、房间管理、会话治理                   │
│  Y层：复用 shared-core 的 entity / physics（权威物理）                │
└─────────────────────────────────────────────────────────────────────┘
```

### 核心设计原则

1. **样式与逻辑彻底分离 (Z层自治)**
   - **FXML** 仅描述 UI 树形结构与控件 ID，禁止内联样式、禁止脚本事件。
   - **CSS** 承担全部视觉表现：颜色主题、字体、边距、圆角、渐变。
   - **Controller** 仅处理业务逻辑与数据绑定：事件监听、服务调用、状态更新。

2. **X+Y / Y+Z 扩展思想**
   - **X 轴**：Server 无状态网关 + 有状态房间，可水平扩展；Client 可任意多开。
   - **Y 轴**：Shared-Core 将物理、实体、常量正交拆分；Server/Client 按职责隔离。
   - **Z 轴**：RoomManager 按 `roomId` 哈希分区，房间数据完全隔离，支持分片部署。

3. **C/S 与前后端分离**
   - 前端：独立 JavaFX 应用，独立 JVM 进程，可打包为 `.exe`/`.dmg`/`.jar`。
   - 后端：独立 Spring Boot 应用，暴露 WebSocket 端口 8080。
   - 契约：双方仅通过 JSON DTO 交互，不共享 UI 类或框架类。

---

## 目录结构

```
wall-runner-java/
├── docs/
│   └── ARCHITECTURE.md          # 详细架构文档
├── shared-core/                 # 【Y】共享核心（零框架依赖）
│   └── src/main/java/com/wallrunner/shared/
│       ├── constants/GameConstants.java
│       ├── entity/Player.java
│       ├── entity/Obstacle.java
│       ├── entity/GameState.java
│       └── physics/GamePhysics.java
├── server/                      # 【X + Y】服务端
│   └── src/main/java/com/wallrunner/server/
│       ├── WallRunnerServer.java
│       ├── config/WebSocketConfig.java
│       ├── handler/GameWebSocketHandler.java
│       ├── service/DedicatedService.java
│       ├── service/RelayService.java
│       ├── service/RoomManager.java
│       ├── service/SessionManager.java
│       └── dto/NetworkMessage.java
│   └── src/main/resources/application.properties
└── client/                      # 【Y + Z】客户端
    └── src/main/java/com/wallrunner/client/
        ├── ClientApplication.java
        ├── controller/MenuController.java
        ├── controller/GameController.java
        ├── controller/SettingsController.java
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

## 环境要求

- **JDK 21** (LTS)
- **JavaFX 17+ SDK** (Client 模块需要)
- **IntelliJ IDEA 2023+** (推荐，无 Maven/Gradle，使用 IDEA 模块配置)
- **Spring Boot 3.2+ 依赖 JAR** (Server 模块需要)
- **Jackson 2.16+** (Server/Client 序列化)

---

## 快速开始

### 1. 编译 shared-core
```bash
cd shared-core/src/main/java
javac -d ../../../../out/shared com/wallrunner/shared/**/*.java
```

### 2. 启动服务端
```bash
cd server/src/main/java
javac -cp "../../../../lib/*:../../../../out/shared" -d ../../../../out/server com/wallrunner/server/**/*.java
java -cp "../../../../lib/*:../../../../out/server:../../../../out/shared:../resources" com.wallrunner.server.WallRunnerServer
```

### 3. 启动客户端
```bash
cd client/src/main/java
javac --module-path /path/to/javafx-sdk/lib --add-modules javafx.controls,javafx.fxml       -cp "../../../../out/shared" -d ../../../../out/client com/wallrunner/client/**/*.java
java --module-path /path/to/javafx-sdk/lib --add-modules javafx.controls,javafx.fxml      -cp "../../../../out/client:../../../../out/shared" com.wallrunner.client.ClientApplication
```

> 生产环境建议使用 **Maven/Gradle** 构建，或直接使用 IntelliJ IDEA 配置模块依赖与运行配置。

---

## 通信协议

客户端与服务端通过 **JSON WebSocket** 通信，消息格式统一为 `NetworkMessage`：

```json
{
  "type": "JOIN | CREATE | STATE | INPUT | LEAVE | ERROR",
  "roomId": "string",
  "playerId": "string",
  "playerName": "string",
  "payload": { ... },
  "timestamp": 1715155200000
}
```

---

## 游戏模式

1. **单人模式 (SINGLE)** — 本地运行完整物理引擎，不建立网络连接。
2. **公共服务器 (DEDICATED)** — 服务端运行权威物理，客户端纯渲染 + 本地预测。
3. **P2P 转发 (RELAY_HOST)** — 房主客户端运行物理，服务端仅转发状态。
4. **P2P 加入 (RELAY_GUEST)** — 接收房主广播状态，支持本地输入回传。

---

## 样式分离示例

FXML 中：
```xml
<Button fx:id="btnSingle" text="开始单人游戏" styleClass="menu-btn, primary"/>
```

CSS 中：
```css
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
private void onSinglePlayer() {
    ClientApplication.switchScene("...", 640, 720);
    GameController.setMode(GameController.Mode.SINGLE);
}
```

> **零内联样式，零脚本事件，纯声明式结构 + 纯样式表 + 纯逻辑控制器**

---

## 许可证

MIT License — 仅供学习交流使用。

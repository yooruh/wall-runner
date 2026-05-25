# 墙间跑酷（Wall Runner）联机版 — 项目详细介绍文档 v2.0

> **生成日期**：2026-05-19
> **项目类型**：WebSocket 实时联机桌面游戏
> **技术栈**：Java 21 + Spring Boot 3.1.2 + JavaFX 20 + FXML/CSS + Jackson 2.15.2

---

## 一、创意概述

《墙间跑酷》是一款主打**竖向攀爬 + 左右横跳**的轻竞技桌面游戏。核心创意可概括为：

1. **极简单键操作**：玩家仅需一个按键（空格 / 点击 / 触摸）即可完成"跳跃换墙"，上手门槛极低。
2. **双模式联机架构**：同一套前后端代码同时支持**权威服务器模式（Dedicated）**与**P2P 转发模式（Relay）**，兼顾公平竞技与好友开黑两种社交场景。
3. **橡皮筋摄像机 + 独立视口**：每位客户端拥有独立的摄像机平滑跟随逻辑，既保证本地画面丝滑，又避免了传统同步游戏中"强制统一镜头"带来的眩晕感。
4. **物理碰撞与策略深度**：玩家在攀爬过程中会遇到墙刺（反弹换边）和浮动平台（阻挡 / 借力），使得单键操作也能衍生出节奏控制与路线规划的策略性。
5. **玩家间碰撞互动**：新增跳跃玩家撞击非跳跃玩家的击退机制，被撞者进入"击退 + 旋转 + 闪烁无敌"状态，增加联机对抗趣味。
6. **自定义角色外观**：支持 8 组预设颜色（填充 + 描边），服务器自动分配不重复颜色，也可客户端通过调色板自定义。

---

## 二、系统组成与部署

### 2.1 架构概览（X + Y / Y + Z 扩展分层）

本项目基于 **AKF 扩展立方体**的分层思想，将系统沿 **X(基础设施 / 水平扩展)、Y(功能 / 共享核心)、Z(表现 / 用户交互)** 三个维度拆分：

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           客户端 (Client)  —  【Y + Z】                      │
│  ┌─────────────────────────────────────────────────────────────────────┐      │
│  │  Z 层：表现与交互 (Presentation)                                    │      │
│  │  ├─ view/*.fxml      — 声明式 UI 结构，零逻辑，零样式（仅布局）      │      │
│  │  ├─ css/client-theme.css — 全部视觉样式（颜色、字体、边距、动画）    │      │
│  │  └─ controller/*     — 事件监听、视图更新、数据绑定（纯逻辑）        │      │
│  ├─────────────────────────────────────────────────────────────────────┤      │
│  │  Y 层：共享核心复用 (Shared-Core)                                   │      │
│  │  ├─ com.wallrunner.shared.entity.*   — Player, Obstacle, GameState │      │
│  │  ├─ com.wallrunner.shared.physics.*  — GamePhysics, GameConstants    │      │
│  │  └─ client.engine.*                  — 本地预测、权威调和            │      │
│  ├─────────────────────────────────────────────────────────────────────┤      │
│  │  网络适配层                                                         │      │
│  │  └─ service/WebSocketClientService — java.net.http.WebSocket 客户端 │      │
│  └─────────────────────────────────────────────────────────────────────┘      │
│                                    │ WebSocket (JSON over TCP)                │
└────────────────────────────────────┼──────────────────────────────────────────┘
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           服务端 (Server) — 【X + Y】                          │
│  ┌─────────────────────────────────────────────────────────────────────┐      │
│  │  X 层：基础设施与治理 (Infrastructure)                              │      │
│  │  ├─ config/WebSocketConfig — 端点注册、握手拦截、线程策略              │      │
│  │  ├─ handler/GameWebSocketHandler — 消息路由、编解码、生命周期          │      │
│  │  └─ service/* — RoomManager(房间), SessionManager(会话)               │      │
│  ├─────────────────────────────────────────────────────────────────────┤      │
│  │  Y 层：共享核心复用 (Shared-Core)                                   │      │
│  │  ├─ com.wallrunner.shared.entity.*   — 权威状态数据结构              │      │
│  │  └─ com.wallrunner.shared.physics.*  — 权威物理计算                  │      │
│  ├─────────────────────────────────────────────────────────────────────┤      │
│  │  业务编排层                                                         │      │
│  │  ├─ service/DedicatedService — 公共服务器模式（权威物理）            │      │
│  │  └─ service/RelayService     — P2P 转发模式（仅消息中继）             │      │
│  └─────────────────────────────────────────────────────────────────────┘      │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 模块职责与代号

| 模块 | 代号 | 职责 | 技术栈 | 依赖方向 |
|------|------|------|--------|----------|
| **shared-core** | **Y** | 游戏核心：实体 POJO、物理引擎、常量。服务端与客户端的**唯一可信逻辑源**。 | 纯 Java 21（零框架依赖） | 无 |
| **server** | **X + Y** | 网络网关、房间管理、会话治理、两种服务模式。通过依赖 Y 获得权威物理能力。 | Spring Boot 3.1.2, WebSocket, Jackson 2.15.2 | shared-core |
| **client** | **Y + Z** | JavaFX 桌面客户端：渲染管线、输入抽象、UI 交互、本地预测。通过依赖 Y 保证逻辑一致性。 | JavaFX 20, FXML, CSS | shared-core |

### 2.3 后端模块（Java / Spring Boot）

| 文件 | 职责 |
|------|------|
| `WallRunnerServer.java` | Spring Boot 启动入口，启用 `@EnableScheduling` |
| `WebSocketConfig.java` | 注册 `/ws/game` 端点，允许跨域连接 |
| `GameWebSocketHandler.java` | 消息总线，根据 JSON `type` 字段分发到 Dedicated 或 Relay 服务；颜色分配；协议路由 |
| `DedicatedService.java` | **权威服务器模式**：维护游戏主循环（`@Scheduled(fixedRate = 8)`，~125fps tick）、物理计算、碰撞检测、状态广播（每 2 次 tick 广播一次，~60fps）；单房间模式 `DEDICATED-MAIN` |
| `RelayService.java` | **转发模式**：仅做房间管理与消息透传，不解析游戏逻辑 |
| `RoomManager.java` | 房间生命周期管理，Z 轴数据分区；掉线玩家标记为 `disconnected` 保留数据便于重连；6 位 UUID 大写房间号 |
| `SessionManager.java` | WebSocket 会话生命周期管理；sessionId → WebSocketSession / roomId 绑定 |
| `NetworkMessage.java` | 内部 DTO 枚举：JOIN / CREATE / LEAVE / INPUT / STATE / ERROR / ROOM_INFO |
| `Player.java` / `Obstacle.java` / `GameState.java` / `Collectible.java` | 实体模型，封装位置、速度、状态、生命、颜色、预留扩展字段等属性 |
| `GameConstants.java` | 全局常量：画布 400×600、墙宽 60、重力 0.36、跳跃速度 6.4 等；8 组颜色对；击退参数 |
| `GamePhysics.java` | 权威物理引擎：移动、碰撞、摄像机、死亡判定、玩家间碰撞、击退效果、难度递增、障碍物生成与回收 |

**部署方式**：
- 服务端：标准 Spring Boot 可执行 JAR，内嵌 Tomcat，WebSocket 端点 `ws://host:8080/ws/game`。
- 客户端：独立 JavaFX 桌面应用，可打包为 `.exe`/`.dmg`/`.jar`。
- **跨设备联机**：`application.properties` 中 `server.address=0.0.0.0` 表示监听所有网卡，局域网内其他设备均可访问。

### 2.4 前端模块（JavaFX / FXML / CSS）

| 区域 | 技术实现 |
|------|----------|
| 主菜单 UI | FXML + CSS（垂直居中布局，卡片式设计） |
| 游戏画面 | JavaFX Canvas 2D（400×600 固定逻辑分辨率） |
| 网络通信 | `java.net.http.WebSocket` API |
| 渲染循环 | JavaFX `Timeline` 120fps |
| 主机逻辑（Relay Host） | `LocalPhysicsEngine` 封装，逻辑与 `DedicatedService` 同源 |

---

## 三、功能描述

### 3.1 游戏核心规则

- **自动攀爬**：未跳跃时，玩家以 `CLIMB_SPEED = 1.8` 像素/帧自动向上爬升。
- **跳跃换墙**：按下动作键后，玩家水平飞向对侧墙壁；到达墙边后自动吸附并恢复攀爬。
- **障碍物系统**：
  - `wall_spike`（墙刺）：固定于左/右墙面，若玩家跳跃时正面撞击，会被反弹到对侧并赋予向下速度；若攀爬时触碰底部，则被阻挡。
  - `floating`（浮动平台）：位于墙间安全区域，随镜头下移（相对下移速度 2.25），可作为踏脚石，也可阻挡路线。
- **计分**：高度分 + 时间奖励分。高度分 = `(joinOffsetY - y) / 10`，时间奖励每 5 秒 +10 分（可设置）。
- **生命系统**：每名玩家初始 3 条命（`MAX_LIVES = 3`），掉出镜头底部死亡一次，生命归零则淘汰。
- **淘汰机制**：客户端自主判定玩家是否掉出自身镜头底部（`screenY > CANVAS_HEIGHT + DEATH_LINE_OFFSET`），并向服务端上报；服务端做宽松兜底校验。
- **玩家间碰撞**：跳跃玩家撞击非跳跃玩家时，被撞者进入"击退 + 旋转 + 闪烁无敌"状态：
  - 被弹出墙壁一小段距离（`KNOCKBACK_PUSH_X = 45.0`）
  - 向上轻弹（`KNOCKBACK_VY = -2.5`）
  - 正常重力快速下落（`KNOCKBACK_GRAVITY = 0.36`）
  - 旋转动画（最大 20°，缓慢倾斜，`KNOCKBACK_ROTATION_SPEED = 1.2`）
  - 1.8 秒击退总时长（`KNOCKBACK_DURATION = 1.8`），期间半透明闪烁

### 3.2 双模式网络架构

#### A. 公共服务器模式（Dedicated）
- 客户端仅发送输入（`jump`、`start`、`pause`、`resume`）。
- `DedicatedService` 以 `@Scheduled(fixedRate = 8)` 运行权威游戏循环（~125fps tick）：
  - 计算所有玩家坐标、速度、碰撞。
  - 生成与回收障碍物（基于服务端全局 `cameraY`）。
  - 每 2 次 tick（16ms，~60fps）广播完整 `state` JSON 到所有会话。
- **掉线检测**：8 秒未收到客户端 ping 则服务端主动发 ping 请求；15 秒仍未收到回应则标记玩家为 `disconnected` 并设为暂停状态（无碰撞）。
- **单房间模式**：所有玩家进入同一个 `DEDICATED-MAIN` 房间，简化匹配流程。
- 适合：公开匹配、公平竞技、防作弊要求高的场景。

#### B. P2P 转发模式（Relay）
- 房主（Host）客户端内运行 `LocalPhysicsEngine`，逻辑与 `DedicatedService` 完全一致。
- 服务端 `RelayService` 仅维护房间（6 位 UUID 大写房间号）与宾客列表，消息原样转发：
  - 宾客 → 房主：`relayInput`
  - 房主 → 所有宾客：`broadcastFromHost`
- 房主离开则房间解散，所有宾客收到 `player_left` 通知。
- 支持自定义房间码（大写字母 + 数字）。
- 适合：局域网 / 好友房、减轻服务端算力、降低延迟感知。

### 3.3 摄像机与同步策略

- **服务端（Dedicated）**：维护全局 `cameraY`，用于障碍物生成与兜底死亡线计算，但**不强制客户端镜头**。
- **客户端**：
  - 存活时：以自身 `y` 坐标为锚点，计算 `cameraTargetY = me.y - CANVAS_HEIGHT * CAMERA_OFFSET_RATIO`，并通过 `CAMERA_SMOOTH = 0.1` 的插值系数平滑跟随（橡皮筋效果）。
  - 被阻挡时：镜头仍缓慢上移（`CLIMB_SPEED * 1.5`），制造压迫感。
  - 死亡后：镜头平滑切换到最靠前存活玩家的位置，实现观战体验。
- **显示摄像机**：仅用于渲染与障碍物生成，跟随最领先玩家（最小 cameraY），绝不参与生死判定。
- **本地物理驱动**：所有模式（含 Dedicated / RelayGuest）均运行本地物理，网络状态仅用于 `reconcile` 平滑校正，确保画面流畅不卡顿。

---

## 四、用户界面详解

### 4.1 整体布局结构

前端采用**FXML 声明式布局**，通过 CSS `display` 控制在"主菜单"与"游戏区域"之间切换，无页面刷新。

```xml
<!-- menu.fxml: 主菜单 -->
<VBox fx:id="mainMenu" styleClass="menu-root">
    <Label text="墙间跑酷 联机版" styleClass="menu-title"/>
    <HBox>
        <Label text="玩家名称" styleClass="menu-section-label"/>
        <TextField fx:id="playerNameField" promptText="输入你的名字" styleClass="name-input"/>
    </HBox>
    <!-- 模式选择卡片... -->
</VBox>

<!-- game.fxml: 游戏区域 -->
<BorderPane fx:id="gameRoot" styleClass="game-root">
    <top>
        <HBox fx:id="statusBar" styleClass="status-bar">
            <Label fx:id="connStatus" text="本地游戏" styleClass="status-label"/>
            <Label fx:id="roomIdDisplay" styleClass="room-label"/>
            <Button fx:id="btnPause" text="暂停" styleClass="toolbar-button"/>
            <Button fx:id="btnSettings" text="设置" styleClass="toolbar-button"/>
            <Button fx:id="btnHome" text="返回" styleClass="toolbar-button"/>
        </HBox>
    </top>
    <center>
        <StackPane>
            <Canvas fx:id="gameCanvas" width="400" height="600"/>
            <VBox fx:id="pauseOverlay" styleClass="overlay-pane" visible="false"/>
            <VBox fx:id="gameOverOverlay" styleClass="overlay-pane" visible="false"/>
            <VBox fx:id="settingsOverlay" styleClass="overlay-pane" visible="false"/>
        </StackPane>
    </center>
</BorderPane>
```

### 4.2 样式与逻辑彻底分离 (Z 层自治)

- **FXML** 仅描述 UI 的**树形结构**与**控件 ID**，禁止内联样式、禁止脚本事件。
- **CSS** 承担全部视觉表现：颜色主题、字体、边距、圆角、渐变、动画关键帧。
- **Controller** 仅处理**业务逻辑**与**数据绑定**：事件监听、服务调用、状态更新。
- 三者通过 `fx:id` 与 `styleClass` 松耦合，任何一层变更不破坏其他层。

### 4.3 主菜单界面（`menu.fxml` / `MenuController`）

主菜单采用**深色科技感主题**（背景 `#1a1a2e`，强调色 `#e94560`），垂直居中布局。

#### 4.3.1 标题区
```xml
<Label fx:id="titleLabel" text="墙间跑酷 联机版" styleClass="menu-title"/>
```
- **视觉**：大字号、红色（`#e94560`）带发光阴影（`dropshadow`），强化品牌记忆。

#### 4.3.2 昵称输入区
```xml
<HBox spacing="10" alignment="CENTER">
    <Label text="玩家名称" styleClass="menu-section-label"/>
    <TextField fx:id="playerNameField" promptText="输入你的名字" styleClass="name-input"/>
</HBox>
```
- **功能**：全局共享的昵称，自动保存到 `Preferences`。
- **样式**：深色输入框（`#16213e` 背景、白色文字、圆角 8px），聚焦时红色边框高亮。

#### 4.3.3 模式选择卡片
菜单核心由四张**可点击卡片**构成：

| 卡片 | 标题 | 说明 |
|------|------|------|
| 单人模式 | 🎮 单人模式 | 离线本地游戏，无需网络连接，随时练习技巧 |
| 公共服务器 | 🌐 公共服务器 | 服务端运行权威地图，所有人公平竞技 |
| P2P 创建 | 🏠 数据转发（P2P） | 房主客户端作为主机运行游戏，服务器只转发数据 |
| P2P 加入 | 🏠 数据转发（P2P） | 输入房间码加入好友房间 |

**卡片交互细节**：
- **悬停效果**：`-fx-translate-y: -2` + 边框高亮（`border-color: #e94560`）。
- **色彩语义**：公共服务器 / P2P 标题使用绿色（`#4ecca3`），按钮主色为红色（`#e94560`），次级按钮为深蓝（`#0f3460`）。
- **房间号输入**：强制大写，与后端房间号生成规则（大写字母 + 数字）呼应。
- **自定义房间码**：支持用户输入自定义房间码，创建后显示并可复制到剪贴板。

**改进已实现**：
- ✅ 连接前检查：进入公共服务器 / 创建房间前检查 WebSocket 连接状态，失败时阻止切场景并恢复按钮。
- ✅ 统一窗口尺寸切换：使用 Scene 自身宽高，防止窗口越切越大。
- ✅ 模式切换顺序修复：先 `setMode` 再 `switchScene`，确保 `GameController.initialize()` 读取到正确模式。

### 4.4 游戏区域界面（`game.fxml` / `GameController`）

```xml
<BorderPane fx:id="gameRoot" styleClass="game-root" prefWidth="640" prefHeight="780">
    <top>
        <HBox fx:id="statusBar" spacing="12" alignment="CENTER_LEFT" styleClass="status-bar">
            <Label fx:id="connStatus" text="本地游戏" styleClass="status-label"/>
            <Region HBox.hgrow="ALWAYS"/>
            <Label fx:id="roomIdDisplay" text="" styleClass="room-label"/>
            <Region HBox.hgrow="ALWAYS"/>
            <Button fx:id="btnPause" text="暂停" styleClass="toolbar-button"/>
            <Button fx:id="btnSettings" text="设置" styleClass="toolbar-button"/>
            <Button fx:id="btnHome" text="返回" styleClass="toolbar-button"/>
        </HBox>
    </top>
    <center>
        <StackPane alignment="CENTER" styleClass="game-canvas-container">
            <Canvas fx:id="gameCanvas" width="400" height="600"/>
            <VBox fx:id="pauseOverlay" styleClass="overlay-pane" visible="false"/>
            <VBox fx:id="gameOverOverlay" styleClass="overlay-pane" visible="false"/>
            <VBox fx:id="settingsOverlay" styleClass="overlay-pane" visible="false"/>
        </StackPane>
    </center>
</BorderPane>
```

#### 4.4.1 游戏画布（`Canvas#gameCanvas`）
- **固定逻辑分辨率**：400×600，无论实际设备如何，逻辑坐标系恒定，简化物理计算。
- **实际显示尺寸**：CSS 中设置 `-fx-max-width: 400; -fx-max-height: 600`，配合 `StackPane` 居中。
- **边框效果**：`-fx-border-color: #e94560; -fx-border-width: 2; -fx-effect: dropshadow(...)`

#### 4.4.2 状态栏（`#statusBar`）
- **布局**：Flex 两端对齐，位于窗口顶部。
- **连接状态（`#connStatus`）**：
  - 本地游戏：灰色 "本地游戏"。
  - 已连接：绿色 "● 已连接"。
  - 断开 / 错误：红色 "● 未连接"。
- **房间号显示（`#roomIdDisplay`）**：P2P 模式下展示房间号（黄色高亮），房主可点击复制。

#### 4.4.3 暂停覆盖层（`#pauseOverlay`）
```xml
<VBox fx:id="pauseOverlay" visible="false" managed="false" alignment="CENTER" spacing="16"
      styleClass="overlay-pane">
    <Label fx:id="pauseTitle" text="游戏暂停" styleClass="overlay-title"/>
    <Label fx:id="pauseHint" text="按 ESC 恢复游戏" styleClass="hint-label"/>
    <Button fx:id="btnResumeGame" text="继续游戏" styleClass="overlay-button"/>
    <Button fx:id="btnRespawn" text="立即重生" styleClass="overlay-button" visible="false"/>
    <Button text="游戏设置" styleClass="overlay-button-secondary"/>
    <Button text="返回菜单" styleClass="overlay-button-secondary"/>
</VBox>
```
- **层级**：半透明黑底（`rgba(0,0,0,0.85)`），阻断 Canvas 交互。
- **ESC / P 键**：toggles 暂停 / 恢复。
- **立即重生**：死亡后暂停界面显示重生按钮。

#### 4.4.4 游戏结束覆盖层（`#gameOverOverlay`）
```xml
<VBox fx:id="gameOverOverlay" visible="false" managed="false" alignment="CENTER" spacing="16"
      styleClass="overlay-pane">
    <Label text="游戏结束" styleClass="overlay-title"/>
    <Label fx:id="finalScoreLabel" text="" styleClass="overlay-score"/>
    <Button text="重新开始" styleClass="overlay-button"/>
    <Button text="返回菜单" styleClass="overlay-button-secondary"/>
</VBox>
```
- **个人死亡但全局未结束**：显示"你已死亡" + "可继续观战" + "等待房主重新开始下一局"。
- **全局结束**：显示"全军覆没" + 最高分玩家 + "点击或按空格重新开始"。

### 4.5 设置面板（`settings.fxml` / `SettingsController`）

```xml
<VBox styleClass="settings-box" prefWidth="380" prefHeight="680">
    <Label text="游戏设置" styleClass="settings-title"/>
    <ScrollPane fitToWidth="true" hbarPolicy="NEVER" vbarPolicy="AS_NEEDED"
                styleClass="settings-scroll-pane" VBox.vgrow="ALWAYS">
        <VBox spacing="16" alignment="CENTER">
            <!-- 玩家信息 -->
            <VBox>
                <Label text="▎玩家信息" styleClass="settings-section-title"/>
                <TextField fx:id="nameField" promptText="输入你的名字" styleClass="name-input"/>
            </VBox>
            <!-- 网络设置 -->
            <VBox>
                <Label text="▎网络设置" styleClass="settings-section-title"/>
                <HBox>
                    <TextField fx:id="serverAddressField" promptText="localhost" styleClass="settings-input"/>
                    <Label text=":" styleClass="settings-unit"/>
                    <TextField fx:id="serverPortField" promptText="8080" styleClass="settings-input, number" prefWidth="60"/>
                </HBox>
                <Label text="跨设备联机时填写服务器 IP（如 192.168.137.1）" styleClass="settings-hint"/>
            </VBox>
            <!-- 游戏玩法 -->
            <VBox>
                <Label text="▎游戏玩法" styleClass="settings-section-title"/>
                <Label text="时间奖励间隔 (秒)" styleClass="settings-label"/>
                <TextField fx:id="timeIntervalField" promptText="5.0" styleClass="settings-input, number"/>
                <Label text="时间奖励分数" styleClass="settings-label"/>
                <TextField fx:id="timePointsField" promptText="10" styleClass="settings-input, number"/>
            </VBox>
            <!-- 按键绑定 -->
            <VBox>
                <Label text="▎按键绑定" styleClass="settings-section-title"/>
                <Button text="＋ 按下绑定" styleClass="menu-btn, secondary"/>
                <Label text="点击上方按钮后，按下想绑定的按键" styleClass="settings-hint"/>
                <VBox fx:id="jumpKeysContainer"/>
            </VBox>
            <!-- 角色颜色 -->
            <VBox>
                <Label text="▎角色颜色" styleClass="settings-section-title"/>
                <CheckBox fx:id="autoColorCheck" text="自动分配颜色" styleClass="settings-check"/>
                <VBox fx:id="customColorBox">
                    <ColorPicker fx:id="fillColorPicker"/>
                    <ColorPicker fx:id="strokeColorPicker"/>
                </VBox>
            </VBox>
            <!-- 其他选项 -->
            <HBox>
                <Label text="显示 FPS" styleClass="settings-label"/>
                <CheckBox fx:id="showFpsCheck" styleClass="settings-check"/>
            </HBox>
            <HBox>
                <Label text="音效" styleClass="settings-label"/>
                <CheckBox fx:id="soundCheck" styleClass="settings-check"/>
            </HBox>
            <HBox>
                <Label text="本地预测" styleClass="settings-label"/>
                <CheckBox fx:id="predictCheck" styleClass="settings-check"/>
            </HBox>
            <!-- 保存/取消 -->
            <HBox>
                <Button text="保存" styleClass="menu-btn, resume"/>
                <Button text="取消" styleClass="menu-btn, secondary"/>
            </HBox>
        </VBox>
    </ScrollPane>
</VBox>
```

**设置项说明**：
- **玩家名称**：修改后即时保存到 `Preferences`。
- **服务器地址 / 端口**：跨设备联机时填写服务器 IP（默认 `localhost:8080`）。
- **跳跃按键**：支持多键绑定（如空格 + 鼠标左键），每个按键可单独删除。绑定方式为"按下想绑定的按钮"，按 ESC 取消。
- **奖励间隔 / 每次加分**：时间奖励机制参数，默认每 5 秒 +10 分。
- **显示 FPS**：Canvas 左上角显示实时帧率。
- **音效**：预留开关（暂未实现音频播放）。
- **本地预测**：Dedicated 模式下，客户端提前模拟输入以降低延迟感。
- **角色颜色**：
  - 自动分配：服务器从 8 组预设中分配不重复颜色。
  - 自定义：通过 `ColorPicker` 选择填充色与描边色，实时预览。

### 4.6 游戏内 HUD（Canvas 绘制）

除 DOM 元素外，大量信息通过 Canvas 实时绘制：

| HUD 元素 | 绘制位置 | 说明 |
|----------|----------|------|
| 分数 | 左上角 `(15, 28)` | `分数: ${score}`，白色 18px |
| 高度 | 左上角 `(15, 50)` | `高度: ${Math.floor(-y/10)}`，反映垂直进度 |
| 生命 hearts | 左上角 `(14 + i*32, 68)` | 3 颗红心，死亡变灰 |
| 玩家昵称 | 角色头顶 `(x+w/2, sy-8)` | 居中，11px 白色粗体 |
| 玩家分数 | 昵称上方 `(x+w/2, sy-18)` | 10px 灰色 |
| 当前玩家边框 | 角色外框 | 白色 2px 描边，增强自我定位 |
| 跳跃拖尾 | 角色后方 | 半透明矩形（`rgba(bodyColor, 0.4)`），表现速度感 |
| 眼睛 | 角色内部 | 左右各两个 6×6 黑色方块，根据朝向切换左右位置 |
| 击退旋转 | 角色整体 | 以中心为原点旋转，最大 20°，缓慢倾斜 |
| 闪烁无敌 | 角色整体 | 半透明闪烁（alpha 0.3/0.7 交替） |
| FPS 显示 | 右上角 `(320, 4)` | 黑色底 + 绿色字，每秒更新 |

**菜单 / 结算画面**：
- **菜单态**：Canvas 中央显示半透明黑底遮罩 + 绿色标题 + 在线人数 + 开始提示。
- **游戏结束态**：分两种情况绘制：
  1. 个人死亡但全局未结束：显示"你已死亡" + "可继续观战" + "等待房主重新开始下一局"。
  2. 全局结束：显示"全军覆没" + 最高分玩家 + "点击或按空格重新开始"。

---

## 五、网络通信协议

### 5.1 消息格式

```json
{
  "type": "mode_select | input | state | ping | pong | disconnect | mode_confirmed | room_created | joined_room | player_joined | player_left | error",
  "roomId": "string",
  "playerId": "string",
  "playerName": "string",
  "payload": { ... },
  "timestamp": 1715155200000
}
```

### 5.2 客户端 → 服务端

| 消息类型 | 场景 | 字段 |
|----------|------|------|
| `mode_select` | 选择模式 | `mode`(dedicated/relay), `role`(create/join), `name`, `clientId`, `roomId`(可选), `fillColor`(可选), `strokeColor`(可选) |
| `input` | 玩家输入 | `action`(jump/start/pause/resume), `playerId` |
| `state` | P2P 房主广播 | `type: "state"`, `payload: GameState JSON` |
| `ping` | 心跳 | `clientId`, `roomId` |
| `pong` | 心跳回应 | 无（或携带 `clientId`） |
| `disconnect` | 主动离线 | `playerId`, `roomId` |

### 5.3 服务端 → 客户端

| 消息类型 | 场景 | 字段 |
|----------|------|------|
| `mode_confirmed` | Dedicated 确认 | `mode`, `playerId`, `fillColor`, `strokeColor` |
| `room_created` | Relay 创建成功 | `roomId`, `fillColor`, `strokeColor` |
| `joined_room` | Relay 加入成功 | `roomId`, `playerId`, `fillColor`, `strokeColor` |
| `player_joined` | 新玩家加入通知 | `playerId`, `name`, `fillColor`, `strokeColor` |
| `player_left` | 玩家离开 | `playerId` |
| `state` | 权威状态广播 | `type: "state"`, `payload: GameState JSON` |
| `ping` | 服务端主动心跳 | `roomId`, `playerId`, `timestamp` |
| `pong` | 心跳回应 | `type: "pong"` |
| `error` | 错误通知 | `message` |

### 5.4 颜色分配策略

1. 客户端提供 `fillColor` + `strokeColor` → 直接使用。
2. 客户端未提供 → 服务器从 `PLAYER_COLOR_PAIRS` 中分配第一个未使用的颜色。
3. 所有颜色都被占用 → 随机分配（`player.id.hashCode % 8`）。
- Dedicated 模式从全局房间收集已用颜色。
- Relay 模式从目标房间收集已用颜色。

---

## 六、可行性与潜在风险

### 6.1 可行性分析

| 维度 | 评估 |
|------|------|
| **技术可行性** | 高。Spring Boot + WebSocket + JavaFX 均为成熟方案；Maven 多模块构建标准。 |
| **网络可行性** | 中。Dedicated 模式对服务端带宽与算力要求随玩家数线性增长；Relay 模式依赖房主上行带宽，4–6 人房间通常可支撑。 |
| **跨平台** | 高。JavaFX 支持 Windows / macOS / Linux，可打包为原生安装包。 |
| **扩展性** | 中。后端服务模块化（Dedicated / Relay 解耦），RoomManager 支持 Z 轴分片，便于后续增加排位、观战、回放等功能。 |

### 6.2 潜在风险与应对

| 风险 | 影响 | 应对建议 |
|------|------|----------|
| **WebSocket 大消息分片** | 中。`java.net.http.WebSocket` 对大 JSON 会拆分为多帧，已使用 `StringBuilder` 累加分片处理。 | 监控消息大小，必要时压缩或分片传输。 |
| **P2P 模式作弊** | 高。Relay 模式下房主客户端拥有完整逻辑，极易篡改物理参数。 | 增加客户端代码混淆；限制 Relay 模式仅用于好友娱乐，不计入排行榜。 |
| **镜头不同步导致的争议** | 中。客户端自主判定死亡并上报，服务端仅做宽松兜底。 | Dedicated 模式下服务端应增强死亡校验逻辑；引入服务端 `cameraY` 与客户端上报值的一致性检查。 |
| **移动端性能** | 低。本项目为桌面端 JavaFX 应用，不涉及移动端渲染性能问题。 | 若未来移植到移动端，需重新评估 Canvas 渲染策略。 |
| **房间号冲突 / 遍历** | 低。6 位 UUID 大写（36^6 ≈ 21 亿组合），使用 `UUID.randomUUID()`。 | 增加数据库 / 缓存层面的房间号唯一性校验。 |
| **内存泄漏** | 中。`DedicatedService` 中 `obstacles` 持续生成但仅按屏幕范围回收，长局可能积累。 | 增加更激进的障碍物回收策略（如仅保留镜头上方 200px 至下方 200px 范围内的对象）。 |
| **无状态扩容** | 高。当前服务端为单节点内存存储（`ConcurrentHashMap`），无法水平扩展。 | 引入 Redis 存储玩家会话与房间状态，配合 Sticky Session 或分布式消息总线。 |
| **JavaFX 跨平台兼容性** | 中。不同操作系统对 JavaFX 渲染管线支持有差异。 | 使用 JavaFX 20+ LTS 版本，避免使用实验性 API；测试覆盖 Windows / macOS / Linux。 |

---

## 七、总结

《墙间跑酷》联机版 v2.0 以**极简操作 + 双模式网络架构 + 全 Java 技术栈**为核心卖点，在 400×600 的竖屏画布内实现了完整的物理攀爬与多人联机体验。其用户界面遵循"深色科技风"设计语言，从主菜单的卡片式模式选择，到游戏内的 Canvas HUD 与覆盖层，形成了统一的视觉体系。

**v2.0 主要改进**：
1. **全 Java 重构**：从 HTML5/JS 迁移到 Java 21 + JavaFX 20 + Spring Boot 3.1.2，获得更好的类型安全与性能。
2. **X + Y / Y + Z 架构**：AKF 扩展立方体分层，`shared-core` 作为唯一可信逻辑源，服务端与客户端复用同一物理引擎。
3. **样式逻辑彻底分离**：FXML 仅描述结构，CSS 承担全部视觉，Controller 仅处理逻辑。
4. **新增单人模式**：离线本地游戏，无需网络连接。
5. **新增设置面板**：支持按键绑定、时间奖励参数、FPS 显示、音效开关、本地预测开关、服务器地址配置、角色颜色调色板。
6. **新增玩家间碰撞**：击退 + 旋转 + 闪烁无敌机制，增加联机对抗趣味。
7. **新增自定义颜色**：8 组预设颜色对 + ColorPicker 自定义，服务器自动分配不重复颜色。
8. **新增生命系统**：3 条命，掉出镜头底部死亡，生命归零淘汰。
9. **帧率提升**：客户端 120fps Timeline，服务端 8ms tick（~125fps）+ 16ms 广播（~60fps）。
10. **网络优化**：WebSocket 消息分片处理、UUID 客户端 ID（多窗口不冲突）、掉线检测（8s ping / 15s 离线）、状态 reconcile 平滑校正。
11. **跨设备联机**：服务端绑定 `0.0.0.0`，客户端设置面板支持配置服务器 IP 与端口。

**后续重点优化方向**：
1. **UI 层面**：增加响应式 Canvas 缩放、虚拟按键、加载状态、结算画面按钮。
2. **体验层面**：优化摄像机平滑算法、增加音效与震动反馈、引入成就系统。
3. **架构层面**：将内存存储迁移至 Redis、增强防作弊校验、引入 Netty 提升 WebSocket 并发能力。

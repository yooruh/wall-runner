# WallRunner v3.0 架构文档（UML建模优化版）

## 1. 设计原则

1. **分层清晰**：X(网络) → Y(物理) → Z(渲染)，每层职责单一。
2. **零框架依赖**：`shared-core` 不依赖 Spring / JavaFX，可被任意平台复用。
3. **本地优先**：所有模式均运行本地物理，网络状态仅用于校正，确保画面流畅。
4. **预留扩展**：特效、技能、可收集物、主题切换等通过预留接口 / 字段实现，当前版本仅定义结构。

---

## 2. 模块职责

### shared-core（Y 层）— v3.0 接口化重构

**设计升级**：
- `GamePhysics` (38178行) → 拆分为 `IPhysicsEngine` + `PhysicsEngine` + 12个子系统接口
- 新增 `IEntity` 标记接口，所有实体实现统一抽象
- 新增 `GameEventBus` 事件总线（Observer模式），解耦各子系统

| 接口/文件 | 职责 |
|------|------|
| `GameConstants` | 所有数值常量的唯一可信源（画布尺寸、物理参数、颜色表、击退参数等） |
| `GamePhysics` | 权威物理引擎，处理移动、碰撞、死亡、摄像机、难度递增、障碍物生成与回收 |
| `Player` | 纯 POJO 实体：身份、位置、运动状态、游戏状态、摄像机、无敌 / 击退、网络 / 旁观、预留扩展字段 |
| `Obstacle` | 障碍物实体：类型、位置、动态参数（难度 / 行为 / 速度 / 范围 / 相位） |
| `GameState` | 单局完整运行时状态：玩家表、障碍物表、阶段、摄像机、时间奖励、预留扩展字段 |
| `Collectible` | 可收集物实体：类型、位置、价值、持续时间（已定义，待激活） |

### server（X + Y 层）

| 文件 | 职责 |
|------|------|
| `WallRunnerServer` | Spring Boot 启动入口，启用 `@EnableScheduling` |
| `WebSocketConfig` | 注册 `/ws/game` 端点，允许跨域连接 |
| `GameWebSocketHandler` | 消息总线（"交通警察"），根据 JSON `type` 字段分发到 Dedicated 或 Relay 服务；颜色分配；协议路由 |
| `DedicatedService` | **公共服务器模式**：8ms tick 运行权威物理，每 2 次 tick（16ms）广播完整 STATE；掉线检测（8s 发 ping / 15s 标记离线） |
| `RelayService` | **P2P 转发模式**：仅维护房间与宾客列表，消息原样转发（房主 → 服务端 → 访客） |
| `RoomManager` | 房间生命周期管理：创建 / 销毁 / 查询；掉线玩家标记为 disconnected 保留数据便于重连；6 位 UUID 大写房间号 |
| `SessionManager` | WebSocket 会话注册表：sessionId → WebSocketSession / roomId 绑定；纯注册表，零业务逻辑 |
| `NetworkMessage` | 内部 DTO：JOIN / CREATE / LEAVE / INPUT / STATE / ERROR / ROOM_INFO |

### client（Y + Z 层）

| 文件 | 职责 |
|------|------|
| `ClientApplication` | JavaFX 舞台与场景切换；F11 全屏；动态 CSS 加载 |
| `MenuController` | 主菜单交互：模式选择、昵称保存（Preferences）、房间码复制、连接前状态检查 |
| `GameController` | 游戏场景主控制器：协调网络 / 物理 / 渲染；四种模式（SINGLE / DEDICATED / RELAY_HOST / RELAY_GUEST） |
| `SettingsController` | 设置面板：按键绑定、时间奖励参数、FPS 显示、音效开关、本地预测开关、角色颜色调色板、服务器地址配置 |
| `GameLoopService` | 基于 JavaFX `Timeline` 的 120 FPS 调度器 |
| `Renderer` | Canvas 绘制管线：背景、墙壁、障碍物、玩家、HUD、粒子特效、覆盖层；预留可收集物 / 特效渲染 |
| `StateManager` | 本地状态中心：单例模式；`reconcile()` 平滑合并权威状态（本地玩家插值校正，其他玩家直接覆盖） |
| `WebSocketClientService` | WebSocket 客户端：连接管理、消息分片累加（`java.net.http.WebSocket` 大 JSON 多帧处理）、心跳（5s ping）、单例模式 |
| `InputService` | 键盘输入抽象：默认空格跳跃，支持多键绑定，ESC / P 系统事件 |
| `LocalPhysicsEngine` | 本地权威物理封装：委托给 `GamePhysics`，保持客户端与服务端逻辑一致 |
| `Predictor` | 本地预测器：Dedicated 模式下提前模拟玩家输入以降低延迟感 |

---

## 3. 网络协议

### 3.1 消息类型（JSON `type` 字段）

| 类型 | 方向 | 说明 |
|------|------|------|
| `mode_select` | C → S | 选择模式（`mode`: dedicated/relay, `role`: create/join, `name`, `clientId`, `roomId` 可选, `fillColor`/`strokeColor` 可选） |
| `input` | C → S | 玩家输入（`action`: jump/start/pause/resume, `playerId`） |
| `state` | S → C / Host → S → Guest | 完整 GameState JSON（Dedicated 每 16ms 广播；Relay 由房主客户端驱动广播频率） |
| `ping` | C ↔ S | 客户端每 5s 发送；服务端 8s 未收到则主动发 ping 请求 |
| `pong` | S → C / C → S | 心跳回应 |
| `disconnect` | C → S | 客户端主动通知离线 |
| `mode_confirmed` | S → C | Dedicated 模式确认（`playerId`, `fillColor`, `strokeColor`） |
| `room_created` | S → C | Relay 创建成功（`roomId`, `fillColor`, `strokeColor`） |
| `joined_room` | S → C | Relay 加入成功（`roomId`, `playerId`, `fillColor`, `strokeColor`） |
| `player_joined` | S → C | 新玩家加入通知（广播给房间内其他玩家） |
| `player_left` | S → C | 玩家离开通知（由 `GameWebSocketHandler.afterConnectionClosed` 触发） |
| `error` | S → C | 错误提示（房间不存在、房间码已被使用等） |

### 3.2 状态同步策略

- **Dedicated**：服务端 `@Scheduled(fixedRate = 8)` 运行物理 tick（~125fps），`broadcastCounter` 每 2 次 tick 广播一次 STATE（~60fps，16ms 间隔）。
- **Relay**：房主客户端运行本地物理（与 Dedicated 同源），通过 `handleStateForward` 将 STATE 发给服务端，服务端原样转发给所有访客。广播频率由房主客户端的 `GameLoopService` 驱动（120fps，实际发送频率受网络层节制）。
- **Client**：本地物理驱动画面（120fps），收到 STATE 后 `StateManager.reconcile()` 平滑校正：
  - 本地玩家：偏差 > 5px 直接覆盖；偏差 0.5~5px 以 0.15 插值系数平滑过渡。
  - 其他玩家：直接覆盖。

---

## 4. 扩展预留接口

### 4.1 特效系统
- `Player.effects`: `List<String>` 当前生效特效标识。
- `GameState.activeEffects`: `List<String>` 全局特效。
- `GameConstants` 预留 `EffectType` 枚举骨架（已注释）。
- `Renderer` 已预留护盾光环绘制逻辑。

### 4.2 技能系统
- `Player.skills`: `List<String>` 已解锁技能。
- `Player.skillCooldowns`: 技能冷却倒计时。
- `GamePhysics.handleInput()` 预留技能输入处理注释。
- `GameConstants` 预留 `SkillType` 枚举骨架。

### 4.3 可收集物
- `Collectible` 实体已定义（金币、宝石、道具、护盾）。
- `GameState.collectibles`: `List<Collectible>`。
- `Renderer.drawCollectibles()` 已实现渲染（浮动动画 + 发光）。
- `GamePhysics` 预留 `updateCollectibles()` 方法骨架。

### 4.4 主题切换
- `SettingsController.themeSelector`: `ComboBox` 已预留。
- `ClientApplication` 支持动态 CSS 文件加载（通过 `Scene.getStylesheets()`）。
- 新增主题时只需创建 `client-theme-light.css` 并在设置中切换加载路径。

### 4.5 难度系统
- `GameState.difficultyLevel`: 当前难度等级（1-10）。
- `GameState.difficultyAccumulator`: 难度递增累加器。
- `GamePhysics.updateDifficulty()`: 随高度自动提升难度。
- `Obstacle.difficulty/behavior/moveSpeed/moveRange/phase`: 动态障碍物参数。

---

## 5. 数据流

```
[玩家输入] → InputService → GameController
                                    ↓
[本地物理] ← LocalPhysicsEngine ← GameLoopService (120 FPS)
                                    ↓
[渲染] ← Renderer ← Canvas (JavaFX)
                                    ↓
[网络] ← WebSocketClientService → 服务端 / 房主
                                    ↓
[状态校正] ← StateManager.reconcile() ← 权威 STATE
```

---

## 6. 线程模型

- **Client**：单线程 JavaFX Application Thread。`GameLoopService` 使用 `Timeline`（JavaFX 线程）。网络 I/O（`java.net.http.WebSocket`）在独立后台线程，通过回调回到 JavaFX 线程更新 UI。
- **Server**：Spring Boot 线程池处理 WebSocket 连接。`DedicatedService.tick()` 使用 `@Scheduled(fixedRate = 8)`（独立线程）。`RoomManager` 使用 `ConcurrentHashMap` 保证线程安全。
- **shared-core**：无线程安全保证，外部调用方需自行加锁（Server 用 ConcurrentHashMap，Client 用单线程）。

---

## 7. 已知限制与建议

1. **音效**：当前未实现，`SettingsController` 中 `soundCheck` 仅作预留开关，无音频播放逻辑。
2. **P2P 作弊**：Relay 模式下房主客户端拥有完整物理逻辑，存在篡改风险。建议 Relay 模式仅用于好友娱乐，不计入排行榜。
3. **无状态扩容**：当前服务端为单节点内存存储（`ConcurrentHashMap`），无法水平扩展。如需扩容，建议引入 Redis 存储玩家会话与房间状态，配合 Sticky Session 或分布式消息总线。
4. **内存泄漏**：`DedicatedService` 中 `obstacles` 持续生成但仅按屏幕范围回收，长局可能积累。建议增加更激进的障碍物回收策略（如仅保留镜头上方 200px 至下方 200px 范围内的对象）。
5. **JavaFX 跨平台兼容性**：不同操作系统对 JavaFX 渲染管线支持有差异。建议使用 JavaFX 20+ LTS 版本，避免使用实验性 API；测试覆盖 Windows / macOS / Linux。
6. **移动端**：当前为桌面端 JavaFX 应用，不涉及移动端。若未来移植，需重新评估渲染与输入策略。
7. **WebSocket 大消息**：`java.net.http.WebSocket` 对大 JSON 会拆分为多帧，已在 `WebSocketClientService` 中使用 `StringBuilder` 累加分片处理。监控消息大小，必要时启用压缩。


---

## 4. UML 建模支持

v3.0 所有重构均围绕 **便于 UML 建模** 展开：

### 4.1 类图建模要素

| 建模要素 | 代码体现 | 示例 |
|---------|---------|------|
| 泛化（Generalization） | `implements` 接口 | `Player implements IEntity` |
| 实现（Realization） | `implements` 接口 | `PhysicsEngine implements IPhysicsEngine` |
| 组合（Composition） | 构造器注入 | `PhysicsEngine` 组合 12 个子系统 |
| 依赖（Dependency） | 方法参数/局部变量 | `GameController` 依赖 `IRenderer` |
| 关联（Association） | 字段引用 | `GameState` 关联 `Player` |
| 单例（Singleton） | `static final INSTANCE` | `GameEventBus`, `StateManager` |

### 4.2 交互图建模要素

| 建模要素 | 代码体现 | 示例 |
|---------|---------|------|
| 同步消息 | 普通方法调用 | `physics.update(state)` |
| 返回消息 | 返回值 | `GameState` 从 `update()` 返回 |
| 自调用 | 内部方法 | `StateManager.reconcile()` |
| 条件分支 | `if/else` | 碰撞类型判断（alt片段） |
| 并行处理 | `fork/join` | 击退处理与移动更新并行 |

### 4.3 状态图建模要素

| 建模要素 | 代码体现 | 示例 |
|---------|---------|------|
| 状态 | 字段组合 | `Player.active + jumping + knockedBack` |
| 转换 | 方法调用 | `applyKnockback()` 触发状态变更 |
| 复合状态 | 嵌套逻辑 | `Playing` 内含 `Normal/DifficultyUp` |
| 初始/终止 | 构造器/析构 | `initState()` / `gameover` |

### 4.4 活动图建模要素

| 建模要素 | 代码体现 | 示例 |
|---------|---------|------|
| 活动节点 | 方法体 | `updateObstacles()` |
| 判断节点 | `if` 语句 | `activePlayers.isEmpty()` |
| 分叉/汇合 | 并行循环 | `for (Player p : activePlayers)` |
| 分区 | 包边界 | `client.service` vs `shared.physics` |

---

## 5. 向后兼容性

v3.0 通过 **Facade + 默认构造器** 保证零功能损失：

1. `GamePhysics` 保留为 Facade，委托给 `PhysicsEngine`
2. `GameController` 默认构造器自动组装所有服务
3. 单例模式（`getInstance()`）保留，不影响现有调用
4. 所有 FXML/CSS/资源文件未修改
5. Maven 模块结构不变

---

## 6. 文档索引

| 文档 | 路径 | 内容 |
|------|------|------|
| UML建模设计文档 | `docs/UML建模设计文档.md` | 15个UML图的核心设计内容 |
| 用例图×3 | `docs/uml/usecase_*.puml` | PlantUML 用例图源码 |
| 类图×3 | `docs/uml/class_*.puml` | PlantUML 类图源码 |
| 交互图×3 | `docs/uml/sequence_*.puml` | PlantUML 时序图源码 |
| 状态图×3 | `docs/uml/state_*.puml` | PlantUML 状态图源码 |
| 活动图×3 | `docs/uml/activity_*.puml` | PlantUML 活动图源码 |

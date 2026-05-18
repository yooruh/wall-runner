# WallRunner 架构文档

## 1. 设计原则

1. **分层清晰**: X(网络) → Y(物理) → Z(渲染)，每层职责单一。
2. **零框架依赖**: `shared-core` 不依赖 Spring/JavaFX，可被任意平台复用。
3. **本地优先**: 所有模式均运行本地物理，网络状态仅用于校正，确保画面流畅。
4. **预留扩展**: 特效、技能、可收集物、主题切换等通过预留接口/字段实现，当前版本仅定义结构。

## 2. 模块职责

### shared-core (Y层)
- `GameConstants`: 所有数值常量的唯一可信源。
- `GamePhysics`: 权威物理引擎，处理移动、碰撞、死亡、摄像机、难度递增。
- `Player/Obstacle/GameState/Collectible`: 纯 POJO 实体。

### server (X+Y层)
- `GameWebSocketHandler`: 消息路由，不做业务逻辑。
- `DedicatedService`: 公共服务器模式，服务端运行物理，每 8ms tick。
- `RelayService`: P2P 转发模式，仅做消息中继。
- `RoomManager/SessionManager`: 房间与会话生命周期管理。

### client (Y+Z层)
- `ClientApplication`: JavaFX 舞台与场景切换。
- `GameController`: 游戏场景主控制器，协调网络/物理/渲染。
- `GameLoopService`: 120 FPS Timeline 调度器。
- `Renderer`: Canvas 绘制管线，支持粒子特效。
- `StateManager`: 本地状态中心，reconcile 权威状态。
- `WebSocketClientService`: WebSocket 连接、心跳、消息分片处理。

## 3. 网络协议

### 消息类型
| 类型 | 方向 | 说明 |
|------|------|------|
| `mode_select` | C→S | 选择模式（dedicated/relay） |
| `input` | C→S | 玩家输入（jump/start/pause/resume） |
| `state` | S→C / Host→S→Guest | 完整 GameState JSON |
| `player_joined` | S→C | 新玩家加入通知 |
| `player_left` | S→C | 玩家离开通知 |
| `room_created` | S→C | 房间创建成功 |
| `joined_room` | S→C | 加入房间成功 |
| `error` | S→C | 错误提示 |
| `ping/pong` | C↔S | 心跳检测 |

### 状态同步策略
- **Dedicated**: 服务端 8ms tick → 16ms 广播 STATE。
- **Relay**: 房主 8ms tick → 发送 STATE → 服务端转发给所有访客。
- **Client**: 本地物理驱动画面，收到 STATE 后 reconcile（本地玩家平滑插值，其他玩家直接覆盖）。

## 4. 扩展预留接口

### 4.1 特效系统
- `Player.effects`: `List<String>` 当前生效特效标识。
- `GameState.activeEffects`: `List<String>` 全局特效。
- `GameConstants` 预留 `EffectType` 枚举骨架（已注释）。
- `Renderer` 已预留护盾光环绘制逻辑。

### 4.2 技能系统
- `Player.skills`: `List<String>` 已解锁技能。
- `GamePhysics.handleInput()` 预留技能输入处理注释。
- `GameConstants` 预留 `SkillType` 枚举骨架。

### 4.3 可收集物
- `Collectible` 实体已定义（金币、宝石、道具、护盾）。
- `GameState.collectibles`: `List<Collectible>`。
- `Renderer.drawCollectibles()` 已实现渲染（浮动动画 + 发光）。
- `GamePhysics` 预留 `updateCollectibles()` 方法骨架。

### 4.4 主题切换
- `SettingsController.themeSelector`: ComboBox 已预留。
- `ClientApplication` 支持动态 CSS 文件加载（通过 Scene.getStylesheets()）。
- 新增主题时只需创建 `client-theme-light.css` 并在设置中切换加载路径。

### 4.5 难度系统
- `GameState.difficultyLevel`: 当前难度等级（1-10）。
- `GameState.difficultyAccumulator`: 难度递增累加器。
- `GamePhysics.updateDifficulty()`: 随高度自动提升难度。
- `Obstacle.difficulty/behavior/moveSpeed/moveRange/phase`: 动态障碍物参数。

## 5. 数据流

```
[玩家输入] → InputService → GameController
                                    ↓
[本地物理] ← LocalPhysicsEngine ← GameLoopService (120 FPS)
                                    ↓
[渲染] ← Renderer ← Canvas (JavaFX)
                                    ↓
[网络] ← WebSocketClientService → 服务端/房主
                                    ↓
[状态校正] ← StateManager.reconcile() ← 权威 STATE
```

## 6. 线程模型

- **Client**: 单线程 JavaFX Application Thread。GameLoopService 使用 Timeline（JavaFX 线程）。
- **Server**: Spring Boot 线程池处理 WebSocket。DedicatedService tick 使用 `@Scheduled`（独立线程）。
- **shared-core**: 无线程安全保证，外部调用方需自行加锁。

## 7. 已知限制与建议

1. **音效**: 当前未实现，预留 `WebSocketClientService.soundEnabled` 字段。
2. **断线重连**: 当前仅标记为 `disconnected`，未实现自动重连逻辑。
3. **移动端**: JavaFX 不支持移动端，未来可考虑用 libGDX 或 Compose Multiplatform 重写 Z层。
4. **存档系统**: 最高分保存在 `Preferences` 中，未来可扩展为云端存档。
5. **反作弊**: Dedicated 模式下服务端权威物理天然防作弊；Relay 模式依赖房主客户端，存在作弊风险。

# 墙间跑酷 — 联机版 (WallRunner)

**版本**: 2.0.0 | **Java**: 17 | **架构**: C/S 三层分离

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
服务端默认监听 `ws://localhost:8080/ws/game`。

### 3. 启动客户端
```bash
cd client
mvn javafx:run
```

---

## 游戏模式

| 模式 | 说明 | 物理运行方 |
|------|------|-----------|
| 单人模式 | 本地游玩 | 客户端 |
| 公共服务器 (Dedicated) | 服务端运行权威物理，每 8ms tick，每 16ms 广播 STATE | 服务端 |
| 创建房间 (Relay Host) | 房主运行物理，通过服务端转发 STATE 给访客 | 房主客户端 |
| 加入房间 (Relay Guest) | 接收房主广播的 STATE，本地只做渲染 | 房主客户端 |

---

## 操作

- **跳跃**: 空格键 / 鼠标点击 / 触摸
- **暂停**: ESC 或 P
- **全屏**: F11
- **旁观**: 死亡后按跳跃键切换视角

---

## 项目结构

```
wall-runner/
├── shared-core/    # 零框架依赖：实体 + 常量 + 物理引擎
├── server/         # Spring Boot + WebSocket
└── client/         # JavaFX + Canvas 渲染
```

---

## 预留扩展

- **特效系统**: 闪光、拖尾、爆炸、护盾光环
- **技能系统**: 冲刺、二段跳、子弹时间、瞬移
- **可收集物**: 金币、宝石、道具、护盾
- **主题切换**: 深色/浅色 CSS 动态加载
- **难度系统**: 随高度自动递增障碍物速度与密度
- **动态障碍物**: 摆动、追逐、旋转

详见 `ARCHITECTURE.md`。

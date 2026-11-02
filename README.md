# Venus Mc Ping

![Minecraft](https://img.shields.io/badge/Minecraft-26.3-62b47a?style=flat-square)
![Fabric Loader](https://img.shields.io/badge/Fabric%20Loader-0.20.1-dbb69c?style=flat-square)
![Java](https://img.shields.io/badge/Java-25-orange?style=flat-square)
![Version](https://img.shields.io/badge/version-0.1.0-blue?style=flat-square)
![License](https://img.shields.io/badge/license-MIT-success?style=flat-square)

Venus Mc Ping（VMPing）是一个 Minecraft **Fabric 纯客户端标点模组**：按住**鼠标中键**弹出轮盘，
选一个类型，松开就在你看着的地方打一个标点 —— **同服的其他玩家也能看见**，
而**服务器什么都不用装**。

```
按住中键 ──► 轮盘（注意 / 危险 / 物品 / 集合 / 求助）──► 松开 ──► 标点出现在世界里
                                                        │
                                                        └─► 经 HTTP 中继同步给同服玩家
```

> 原版（vanilla）服务端**没有**客户端之间的通信通道（详见 [`docs/可行性调研.md`](docs/可行性调研.md) §A
> 的字节码证据），所以"服务器不装 mod 也能互相看见"只有一条路：**服务器之外的轻量中继**。
> VMPing 复用了同作者项目 [VMFashion](https://github.com/venuyu045/VMFashion-mod) 已经跑通的
> 通信范式：**MC 账号认证 → Bearer token → HTTP/JSON**，中继后端挂在 VMTools 网页平台
> （[VMTools-Web](https://github.com/venuyu045/VMTools-Web)）的 `/api/ping` 路由上。

## 功能

| 能力 | 说明 |
|---|---|
| **中键轮盘** | 默认鼠标中键：按住出轮盘、移动选类型、松开标点；Esc 取消 |
| **5 种标点** | 注意（绿）/ 危险（红）/ 物品（黄）/ 集合（蓝）/ 求助（橙） |
| **服务器零安装** | 纯客户端 `environment: "client"`，不发任何自定义载荷给服务器，原版服 / Paper / Velocity 都能用 |
| **多人可见** | 同服玩家（同房间）实时收到彼此的标点，延迟典型 100–250ms |
| **世界标记** | 屏幕空间投影渲染：类型图标 + 距离 + 标点者名字（按住 Tab 强制显示）；远处自动缩小淡出 |
| **屏幕边缘指示** | 标点在屏幕外时，屏幕边缘画方向箭头 + 距离 |
| **20 秒自动消失** | 与服务端 TTL 一致，最后 3 秒淡出，不挡视线 |
| **降级可用** | 中继不可用 / 单机 / 局域网 / 离线账号 → 自动退化为**本地标点**（功能照用，只是别人看不见） |
| **登录态复用** | MC 账号 serverId 认证，token 落盘 24h，进服免重复认证；正版与皮肤站（authlib-injector）都支持 |
| **可配置** | 后端地址、音量、显示项、最远渲染距离、轮盘开关等，`config/vmping/config.json` + 游戏内设置界面 |
| **中英双语** | 界面与命令输出跟随游戏语言 |
| **可选 Mod Menu** | 装了 Mod Menu 会在模组列表里出现「配置」按钮 |

## 安装（玩家）

1. 装好 **Fabric Loader 0.20.1**（对应 Minecraft **26.3**，需要 **Java 25**）。
2. 从本仓库的 **Releases** 页面下载 `vmping-mc26.3-0.1.0.jar`，和 **Fabric API** `0.165.0+26.3` 一起放进 `.minecraft/mods/`。
3. 进游戏，按住**鼠标中键**标点；或 `/vmping ping` 直接按准星方向标一个点。

> **中键冲突**：原版中键是"选取方块"。轮盘打开期间本模组会抑制选取方块（`mixin` 注入
> `Minecraft#pickBlockOrEntity`），其它时候中键仍是原版行为。想换键位：设置 → 控制 → 按键绑定 →
> 「Venus Mc Ping → 标点」。

## 命令

| 命令 | 说明 |
|---|---|
| `/vmping status` | 中继状态 / 房间 / 维度 / 标点数 / 后端地址 |
| `/vmping ping [类型]` | 不打开轮盘，按准星方向直接标点（类型：`default` / `danger` / `item` / `go` / `help`） |
| `/vmping open` | 打开轮盘界面 |
| `/vmping config reload` | 重读 `config.json` 并重启中继 |
| `/vmping debug` | 打印诊断信息（房间键、归一化地址、时钟偏移、错误详情） |

## 配置

`config/vmping/config.json`（首次启动自动生成）：

| 字段 | 默认 | 说明 |
|---|---|---|
| `apiBaseUrl` | `https://www.venusyu045.cn/api/ping` | 中继后端地址；**留空 = 纯本地模式** |
| `enabled` | `true` | 总开关 |
| `showOwnPings` / `showOthersPings` | `true` | 显示自己 / 别人的标点 |
| `showDistance` / `showPlayerNames` | `true` / `false` | 距离 / 名字（按住 Tab 强制显示名字） |
| `playSound` / `soundVolume` | `true` / `0.7` | 提示音 |
| `maxIconDistance` | `96` | 超过这个距离只画边缘箭头 |
| `pingTtlSeconds` | `20` | 本地显示时长（不超过服务端 TTL） |
| `showEdgeIndicator` / `showStatus` | `true` / `true` | 边缘箭头 / 状态角标 |
| `authServer` | `""` | 皮肤站地址（一般不用填，会自动从 authlib-injector 探测） |
| `debugLogging` | `false` | 调试日志 |

## 为什么服务器不用装？

```
玩家 A 客户端 ──POST /api/ping/rooms/{room}/pings──► VMTools-Web 中继（公网）
                                                          │ 房间 = 服务器地址哈希
玩家 B 客户端 ◄────GET /api/ping/rooms/{room}/pings（长轮询）┘
```

- **房间键** = `sha256(归一化服务器地址)[0:16]`：同一台服的玩家自动进同一个房间，跨服不会串。
- **维度**是消息字段：同一房间不同维度共用房间，客户端按当前维度过滤渲染。
- **认证**：MC 账号 serverId 挑战（正版走 Mojang，皮肤站走 authlib-injector 的 Yggdrasil），
  中继只签发 `aud=ping` 的短 token，标点作者身份由服务端从 token 里取，客户端伪造不了。
- **隐私**：标点只活 20 秒；房间键可由服务器地址推导（知道地址且有任意有效 MC 账号的人能订阅该房间），
  v1 接受这个暴露面；单机/局域网**不同步**。

## 开发

```bash
# 构建（需要 JDK 25）
JAVA_HOME=/opt/jdk25/jdk-25.0.4.1+1 ./gradlew build

# 单元测试（纯逻辑：房间键 / 协议编解码 / TTL / 轮盘几何 / 时钟）
JAVA_HOME=/opt/jdk25/jdk-25.0.4.1+1 ./gradlew test
```

文档：

| 文档 | 内容 |
|---|---|
| [`docs/可行性调研.md`](docs/可行性调研.md) | 为什么原版服不可能转发、5 种方案对比、26.3 API 核实（带字节码证据） |
| [`docs/实现方案.md`](docs/实现方案.md) | 模块/类清单、状态机、渲染管线、测试策略、里程碑 |
| [`docs/协议-v1.md`](docs/协议-v1.md) | 客户端 ↔ 中继的完整接口契约（v1.1） |
| [`docs/后端实现.md`](docs/后端实现.md) | 后端路由/容量/限流/部署注意 |
| [`docs/上游参考.md`](docs/上游参考.md) | 与上游 Ping Wheel 的文件级对照、MIT 义务 |

## 致谢

- 交互与渲染思路参考 [LukenSkyne/Minecraft-Ping-Wheel](https://github.com/LukenSkyne/Minecraft-Ping-Wheel)（MIT），
  本项目是**独立重写**的 26.3 单加载器实现（上游是 multiloader 且依赖服务端通道）。
  详见 [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)。
- 通信/认证范式来自同作者的 [VMFashion](https://github.com/venuyu045/VMFashion-mod)。

## 许可证

MIT License

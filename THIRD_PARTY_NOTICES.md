# 第三方声明（THIRD PARTY NOTICES）

VMPing（Venus Mc Ping）本体以 MIT 许可证发布。下面是它参考/复用过的第三方作品及其许可证义务。

---

## 1. Ping Wheel（上游参考实现）

- 仓库：<https://github.com/LukenSkyne/Minecraft-Ping-Wheel>
- 作者：Luca v. d. W.（Luken，`github@luken.cc`）与贡献者
- 许可证：**MIT**
- 我们参考的版本：`ref-26.3` 分支（`v1.12.2`，HEAD `6447533`）
- 参考范围：**交互设计、射线拾取算法、世界坐标→屏幕投影、屏幕边缘指示、按键冲突处理思路**；
  本项目为 MC 26.3 的**独立重写**（单加载器、无 Lombok、无平台抽象、通信改为 HTTP 中继），
  未直接复制其源代码文件。
- 义务：MIT 要求"在副本或实质部分中保留版权声明与许可声明"。为稳妥起见，其完整许可文本附于下方。

```
The MIT License (MIT)

Copyright (c) 2023

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
```

> 上游仓库副本不随本项目分发（`.gitignore` 里排除了 `upstream/`）。
> 复现方式：`git clone https://github.com/LukenSkyne/Minecraft-Ping-Wheel.git upstream/Minecraft-Ping-Wheel`
> 然后 `git checkout ref-26.3`（或 `origin/26.3`）。

---

## 2. Venus Mc Fashion（通信与认证范式）

- 仓库：<https://github.com/venuyu045/VMFashion-mod>
- 作者：Venus（venuyu045），MIT
- 参考范围：MC 账号 serverId 认证流程（challenge → 会话服务器 join → verify）、
  Bearer token 落盘、`RetryPolicy` 退避/熔断策略、异步线程模型、配置/i18n/命令组织方式。
- 说明：`net/RetryPolicy.java` 由该项目移植并改为本模组的包名，保留原有中文注释与设计说明。

---

## 3. Minecraft / Fabric

- Minecraft 是 Mojang Studios 的商标与版权作品。
- `tools/uipreview/assets/mc/` 里的 5 张 GUI 精灵图（`widget/button*.png`、`widget/text_field*.png`）
  是从**玩家本机客户端 jar** 中提取的，仅用于无头 UI 预览工具复刻原版控件外观；
  与同作者的 VMTools / VMFashion 两个项目保持同样做法。它们不参与模组运行时，也不会被打进发布 jar。
- Fabric Loader / Fabric API 以 Apache-2.0 发布。

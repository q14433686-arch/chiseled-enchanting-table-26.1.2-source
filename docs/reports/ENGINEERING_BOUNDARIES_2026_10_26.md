# 26.3 工程质量收尾与跨分支反馈（2026-10-26）

> 历史轮次报告：其中列出的后续六项大类拆分现已全部实施。当前源码、117 项 JUnit 和真实支付/候选/SDL 证据见[六项工程拆分完成报告](ENGINEERING_SPLIT_COMPLETION_2026_10_26.md)；下文 107 项结果只对应本报告原 SHA。

范围：文档防漂移、CI 产物卫生、职责边界、配置编辑/保存与真实客户端交互验证。
版本保持 **1.1.0 / Minecraft 26.3 / Java 25 / 协议 revision 11**；不调整默认玩法、经济公式或包字段。
本次没有创建或合并 PR，也不强制改写共享 Git 历史。

## 1. 已做出的架构决定

- 保留运行时入口作为兼容门面，逐条分离解析、可变编辑状态、文件副作用、世界副作用和展示。
- 不以“抽出几个 record”代表治理完成；同时移动实际加载、归一化、文件替换、菜单路由、世界扫描/修改和绘制实现。
- 不以统一行数目标迫使代码变成相互访问私有字段的 Helper。菜单使用窄 Access 接口，绘制接收 Font/布局参数，保存状态机接收时钟值。
- 大协调器仍然存在，新增增长棘轮与依赖边界检查。棘轮不是“这些类已经很小”的证明。
- 构建日志不再回推业务分支：完整产物放 artifacts，沙箱需要的错误摘要通过 Checks API 读取。
- 不强制 LFS 所有普通图片；不擅自 force-push 清理历史。当前跟踪树禁止 `.class`、`.log`、`build-reports/` 再进入 Git。

## 2. 文档与 CI 证据边界

`scripts/check_baseline_mixins.py` 从 `fabric.mod.json` 实际加载的配置读取 common/client/server
声明，生成 CURRENT_BASELINE 的受控区块。检查比较完整类名和数量，当前为 **14** 个；相同数量下
替换一个类名也会失败。四项离线回归覆盖多配置、换类、幂等写入和标记损坏。

README 明确徽章针对 `26.3` 分支；成功只适用于具体源 SHA 与执行场景。历史 97 项 JUnit 记录
保留为历史证据，不能为本轮修改或未运行的 GUI/IME 场景背书。

工作流本体与 `docs/ci/` 参考副本保持字节一致。构建不再排除 Markdown-only 推送，避免生成清单
只改文档时绕过静态门。

## 3. 日志卫生及沙箱诊断

- 删除三个被跟踪的 CI 日志，忽略再生目录；`check_repository_hygiene.py` 禁止回归。
- compile/build 的诊断权限只需 `contents: read` 与 `checks: write`。手动发布 Release 的工作流
  仍保留其发布所必需的写权限，不能与日志回写混为一谈。
- 完整编译日志、依赖诊断、JUnit XML 和客户端/服务器日志上传为 artifacts。compile artifact 保留 14 天。
- `post_ci_diagnostics.py` 发布独立 Check run，携带源 SHA、错误上下文、JUnit 总计和末尾日志，
  按 UTF-8 字节限长，不把大日志放入命令行参数或 Git 提交。
- 已实际通过 `api.github.com` 读到 `fe9e4b8`、`aba0bcb`、`171d98b` 等源版本的失败/成功诊断，
  并据此修正编译错误及客户端探针；不是仅写了一份备用方案。
- PR（包括只读 token 的 fork）跳过补充 Check 发布，保留工作流状态与 artifacts。普通分支推送
  有 API 可读诊断；不能声称只读 fork 也获得了不可能授予的 Checks 写权限。
- 诊断发布不会吞掉真实构建退出码；发布自身失败也可见。

读法及完整工作流说明见 [CI 文档](../ci/README.md)。

## 4. 实际职责迁移

| 原协调器 | 本轮新增/独立边界 | 迁移的实际行为 |
|---|---|---|
| Data | `DataTableLoader` | 资源读取、各表 JSON 解析、经典覆盖表和内置后备；不拥有 live cache 或发布状态 |
| Data | `DataJson`、`DataMaterialRules`、独立数据模型 | 标量解析、材料合并/计数上限、残留书身份和各表领域行为 |
| Data | `RuntimeFrame` | 完整运行时快照；构造时防御复制所有集合，防止调用者反向修改已发布 frame |
| RuntimeConfig | `ConfigDraft` | 可变编辑字段、跨字段一致性、预设原子写入和 build；保留嵌套 `Draft` 兼容入口 |
| RuntimeConfig | `ConfigFileStore` | 临时文件、原子替换、平台后备重试和清理；不依赖玩法或运行时发布 |
| ConfigScreen | `ConfigEditSession`、`ConfigSaveSession` | 草稿/基线隔离、请求 ID、确认匹配、失败、迟到确认和超时 |
| ConfigScreen | `SearchInputSession`、`ConfigPanelPainter` | 输入所有权/编辑缓冲与无状态面板皮肤/字形绘制分别归属 |
| TableScreen | `ConfirmationWindow`、`ResonanceTooltip` | 破坏性操作的目标快照确认与只读共鸣/仪式事实展示 |
| ScreenHandler | `MenuQuickMove` | 目标/材料槽 shift-click 路由及库存副作用，通过窄接口接入，不放开菜单私有字段 |
| Ritual | `RitualWorldAccess` | 世界书架扫描、Infinity 母本查询与实际降级；保留原有静态入口委托 |

相对收尾前 `58c76ab` 的协调器行数（只表示迁移规模，不是质量分数）：

| 协调器 | 之前 | 当前 |
|---|---:|---:|
| Data | 2395 | 1114 |
| RuntimeConfig | 1956 | 1347 |
| Ritual | 1906 | 1679 |
| ScreenHandler | 2140 | 2024 |
| ConfigScreen | 2314 | 2170 |
| TableScreen | 2428 | 2283 |

`check_architecture_boundaries.py` 限制这些协调器继续增长，并禁止确定性输入/确认/保存状态机
反向依赖 Minecraft、网络、文件或隐式墙钟。资源解析器也不能重新持有 volatile live state
或调用发布操作。旧菜单/渲染协调器仍大，不能宣称已经“消灭所有巨类”。

### Java 源码兼容提醒

网络 payload、JSON 字段、资源 ID 和存档数据格式没有改变。运行时/仪式入口及嵌套 `Draft`
保留委托。但 Data 的 `ItemTransformation`、`ItemCostOption`、`LootInjection`、
`LootInjectionEntry`、`BookshelfBookPool`、`RuntimeFrame` 已移至 `data` 包的顶层类型。
仓内调用和测试已迁移；外部 Java 扩展若直接引用原嵌套类型，需要更新 import 并重新编译。
这不是对旧嵌套类 JVM 二进制名的兼容承诺；不要把“协议不变”误写成“所有 Java ABI 都不变”。

## 5. 同时发现并修正的保存边界

- 未建立可发送通道时不生成悬空 pending 请求；发送抛异常则明确失败并保留草稿。
- 未匹配、重复或超时后的迟到确认不能错误关闭新编辑会话。15 秒未确认后可重试，草稿不丢失。
  超时不代表能断言远端从未保存；它表示本次请求没有及时得到确认。
- 拒绝保存不重置 baseline/draft；只有匹配且接受的确认才重新载入偏好并按用户意图关闭。
- 文件替换最终失败时，不再退回 `copy` 或原地 `writeString` 覆盖正在使用的文件。旧后备路径
  在中断或半写失败时会破坏最后一份好配置。现在保留旧文件并抛错；替换能力恢复后仍可重试。
- 文件故障注入测试使用真实临时目录，覆盖原子替换不支持时的后备 rename，以及所有替换失败
  时原内容不变、临时文件清理；不通过 mock 的字符串检查冒充文件完整性验证。

## 6. 验证层级与当前证据

已验证的中间源 `171d98b7381c7393f50c13cd91d6e48d9fec7eb5`：

- compile-check `40247035767`：**104 tests / 0 failures / 0 errors / 0 skipped**。
- 完整 jar 与禁用 JEI/REI 编译通过。
- 真实 dedicated server：新世界、reload、战利品函数、仪式输入持久化/自然完成/失效引用隔离/破坏返还通过。
- 首版真实客户端探针失败，不能将该构建写成完整交互通过。已修正首帧就绪判定，并进一步查明
  探针误用了旧 GLFW 的 0/1 编号；26.3 SDL 使用 `InputConstants` 的 1/3。

最终验证源 **`178747b663bd21dca2b38fefd9cd625ef9ee8cdc`**：

- [compile-check 40247733529](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/runs/40247733529)：**107 tests / 0 failures / 0 errors / 0 skipped**。
- [build 40247733513](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/runs/40247733513)：静态、完整 jar、禁用查看器编译、真实服务器及 SDL 客户端四个 job 全部成功。
- 客户端实际写入 `CLIENT SMOKE PASS: real SDL committed CJK text + preset click + server save/ack + reopen + real menu quick-move + overlay forwarding/focus release + persistent target after close`。
- 同一源 SHA 的 `compile-diagnostics` / `server-smoke-diagnostics` 已通过 Checks API 实际读回，未新增日志回推提交。
- 本地离线门：静态 9 组/0 warning、Mixin 回归 4 项、诊断回归 2 项、同步合同 17 项、版本一致性 12 项、workflow 镜像、卫生门、边界棘轮、`git diff --check` 均通过。
  Tier-2 注册表/tag 枚举在本地缺少 MC jar，兼容检查明确有两条跳过警告；不能把 Tier-1 通过写成 Tier-2 也通过。
- Actions 仍提示现有 v4 actions 的 Node 运行时/版本弃用信息；不将本轮成功描述为全局零警告。依赖跨代升级不混入本次玩法/边界整改。

后续证据收尾提交只修改文档；上述验证对应的代码 SHA 明确固定，不用旧构建替未测试代码背书。

真实客户端探针为 `src/clientSmoke` 独立 development-only mod，只有 `-Pclient_smoke=true` 才装载，
不属于发布 jar。它复制 dedicated-server 探针的临时世界，通过真实 SDL 窗口、集成服务器与玩家：

1. 等待实际渲染产生点击区域，点击配置搜索框并检查 SDL 原生输入已激活；
2. 将已提交的 CJK 文本送入真实 SDL 事件队列，断言实际搜索缓冲接收到文本；
3. 点击预设、通过屏幕实际保存并等待服务端确认，重新打开验证偏好；
4. 打开实际附魔台菜单、shift-click 剑进入目标槽，检查源槽为空；
5. 通过父菜单打开配置覆盖层，重复原生字符转发，关闭时检查输入所有权释放；
6. 关闭实际菜单，验证目标物品仍存于 BlockEntity。

脚本拒绝复用用户世界；Gradle 成功但探针未写入成功标记时仍判失败。

### 不扩大验证结论

- Xvfb + Mesa 下的自动化客户端不等于 Windows/macOS、实体 GPU 或人工中文候选窗测试。
- SDL 已提交文本事件不等于人工操作某一种平台 IME 的组合/候选窗流程。
- 单集成服务器玩家不等于双玩家同时保存、任意网络延迟/断线时序或长期压力测试。
- 查看器开/关编译不等于 JEI/REI 每一种真实运行时覆盖层操作都已人工验证。

这些是证据适用范围，不应再被一枚 green 徽章掩盖。

## 7. 反馈给 26.1.2 / 26.2

可共享：编辑/保存/确认状态机、无截断式文件替换、故障注入测试、日志 artifacts + Checks、
生成式 Mixin 清单和边界棘轮。移植时保留各版本实际注册清单及接口，不硬拷贝 26.3 的数量或输入 ABI。

26.3 专有适配：SDL 鼠标编号、窗口原生输入所有权、当前 Minecraft GUI/菜单与资源加载 API。
尤其不要把 GLFW 的左 0/右 1 放回 SDL 测试夹具，也不要在旧版本反向套用 26.3 的 1/3 常量值。
所有版本都应使用该版本的命名常量，而不是硬编码数值。

真实 Fabric mod metadata ID 与资源 namespace 的区分、外部数据包所有权判定，以及对接受确认前
不伪造本地保存成功的原则，均应在其他分支保留并配独立回归。

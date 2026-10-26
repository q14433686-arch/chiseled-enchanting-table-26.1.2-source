# 六项工程拆分完成报告（2026-10-26）

范围：本轮明确要求的 Handler、TableScreen、ConfigScreen、列表 Widget、RuntimeConfig、Ritual 六项；不是只做前三项。
版本固定为 **1.1.0 / Minecraft 26.3 / Java 25 / wire `26_3_revision_11`**。不重新移植、不修改默认玩法、经济公式或网络字段。

## 1. 验证基线

最终源码：`3a3b0076bf56abf70ef9a29b2eb3ab173505d87f`。

**最终验证状态：该 SHA 的编译、完整构建、真实服务端/SDL 客户端及两项诊断检查全部通过。**

- [compile-check 40250562480](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/runs/40250562480)
- [build 40250562244](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/runs/40250562244)

此前本轮 `c3b89e0` 已通过 117 项 JUnit、完整构建、关闭 JEI/REI 的编译、真实服务端及 SDL 客户端；它不是后续测试收紧和只读列表适配后的最终证据。上一轮 107 项测试也不用于为新代码背书。
完整日志留在 Actions artifacts，摘要通过 Checks API 获取；没有把日志重新写入 Git。

## 2. 六项实际职责迁移

行数为物理源码行数，仅用于防回涨；验收依据是职责、状态所有权与依赖边界，而非行数本身。

| 原协调器 | 拆分前 → 当前 | 本轮实际迁出的职责 | 保留的所有权 |
|---|---:|---|---|
| `ChiseledEnchantingTableScreenHandler` | 2024 → 1941 | `MenuMaterialQuotes` 材料报价；`MenuXpQuotes` XP 报价；`MenuPaymentService` 材料匹配、库存溢出扣除、支付和 receipt；`MenuPricingAccess` 只读经济能力；`MenuPricing` 保留兼容门面与批量报价协调 | 槽位、菜单会话、同步、生命周期及世界操作路由 |
| `ChiseledEnchantingTableScreen` | 2283 → 891 | `TablePanelBackground` 与 `TablePanelLabels` 负责实际面板绘制；四个 tooltip presenter 分别负责导航/典籍、材料/XP、候选操作、洗练/仪式/启示面板说明；`TableSkin` 管理视觉和几何常量 | Screen 生命周期、overlay、输入/命中路由、确认窗口和帧输入准备 |
| `ChiseledEnchantingConfigScreen` | 2170 → 1602 | `ConfigOptionRenderer` 行/控件/来源 tooltip；`ConfigTraceRenderer` 追踪横幅/卡片；`ConfigSliderDragSession` 拖动状态及坐标归一化；`ConfigTraceActions` 规则原子切换；`ConfigSkin`/`ConfigInteractions` 视觉资源与交互注册类型 | 编辑/保存会话、viewport/scroll/modal、点击事件分发、网络和原生输入生命周期 |
| `EnchantementListWidget` | 1597 → 1358 | `InscribeOptionModel` 候选构建、可用性过滤、分组/排序；`CleanseOptionModel` 洗练候选；`EnchantmentDisplayNames`/`TomeDisplayNames` 无状态名称展示；`TableListRead`/`TableRowView` 提供只读展示能力和脱离行 Widget 的数据 | 滚动、展开/选择、命中、行实例/绘制、实际点击路由 |
| `ModRuntimeConfig` | 1347 → 840 | `RuntimeConfigJson` 配置 JSON 和迁移；`RuntimePolicyJson` 严格策略 JSON 和旧原子键扩展；`RuntimeConfigPresets` 预设内容 | 配置模型及归一化、枚举身份、client/server 运行状态和兼容入口 |
| `InscriptionRitualMechanics` | 1679 → 1579 | `RitualQueuePersistence` 7/10/11 字段格式、队列序列化、损坏拒绝、退款预算、structured/legacy 恢复协调；BlockEntity 的恢复选择/退款循环也接到该纯边界 | 等待/负载/滑移算法、公开 DTO/CODEC 身份；BlockEntity 仍拥有隔离损坏订单、物品和世界事务 |

没有为了压低行数拆开 `ChiseledEnchantingTableData` 的 reload/publish/rollback 一致性。
Handler 和 Ritual 仍是较大的协调器；本报告不把“有了增长门禁”或“迁出了 record”冒充整个类已经小型化。

## 3. 为什么不是把大类换个名字

- 支付核心接收 `MenuPaymentService.Access`，只拥有明确的玩家/材料槽/变更通知/报价能力；材料和 XP 报价核心接收 `MenuPricingAccess`，没有 Handler 实例、菜单生命周期或传包能力。旧的 `MenuPricing(Handler, …)` 入口只负责兼容转发。
- 候选模型只接收 `Catalog` 只读能力、目标/材料/解锁集合和名称函数，不接收 Widget，也不创建行、不操作滚动或选择。结果的有序分组使用 `LinkedHashMap` 再包为不可修改 Map，子列表也不可修改，不使用会丢失顺序保证的替换方式。
- 表格 presenter 不持有 Screen 或 Widget。`TableListRead` 只暴露 hover/selection 查询，`TableRowView` 不暴露行事件处理器，并复制目标 ItemStack；成本展示也读取副本。
- 配置 renderer 接收 `ConfigViewContext`：Font、draft/参考值、交互注册、变更/声音/受限导航。Screen 保有 hit/slider 集合并分发事件，renderer 不能取得 Screen、文件服务或网络对象。拖动会话独立拥有 active entry/track，且可脱离 Screen 测试。
- 帧 context 中的 Minecraft 原生对象仍按只读展示约定使用，不声称它们是 Java 类型系统强制的深度不可变对象。副作用边界由窄接口、代码审查、依赖门禁和真实行为测试共同约束。
- JSON/预设没有磁盘或运行状态发布权；队列恢复不接收世界、BlockEntity 或 Handler。错误隔离和保留库存仍由世界事务所有者处理。

## 4. 兼容性与行为保持

- 保留原 public 入口和旧拼写别名；`PaymentReceipt`、`QueuedInscription`、`PaidItemEntry`、预设枚举和公开 CODEC 类型路径不搬迁，不要求调用方仅为本轮拆分修改 Java 类型 import。
- CODEC 定义留在原公开 DTO 旁，避免引入 helper/DTO 静态字段相互初始化环。旧队列精确兼容 7、10、11 字段，编码继续使用 `Locale.ROOT` 和四位小数。
- structured 数据与旧字符串不一致时保留原来的旧字符串恢复规则；合法 structured 数据返回新列表，坏尾部和累计退款溢出仍拒绝整个恢复，不偷偷吞掉一部分订单。
- 新增 structured 恢复入口也显式拒绝超过 256 步的直接调用；持久化 CODEC 原本已有同一上限，不改变正常存档格式。
- 绘制提取保留原 tooltip 优先级与提前返回，super/overlay/carried-item 判断留在 Screen。新增 Font-only 绘制重载，旧 Minecraft 参数重载保留，绘制尺寸/缩放算法不变。
- SDL 左/右键仍使用该版本 `InputConstants`（左 1、右 3），不恢复 GLFW 的 0/1 假设。菜单内部 `ContainerInput` 编号不是 SDL 按键编号。

## 5. 新增回归与门禁

`EngineeringSplitTest` 新增 **10 项 JUnit**：所有预设/配置 JSON 往返且不发布运行状态、draft 无别名、全部直接 trace 原子双向切换、典籍耦合/派生 trace 语义、滑块边界/释放/零宽轨道、7/10 字段精确升级、Locale 与继承代数、损坏尾部/退款溢出、structured/legacy 恢复、策略旧键迁移和严格类型拒绝。

真实 SDL/integrated-server probe 在原 CJK/preset/save-ACK/reopen/quick-move/overlay/持久化测试之外增加：

- 真实库存中的重复材料合并、主槽优先和库存溢出；报价不扣材料；错误主槽不能用库存数量绕过。
- 明确断言一个不存在的 transformation 的报价确实为空，再测试 fail-closed；不是仅因槽为空而“碰巧失败”。
- 已备齐材料但 XP 不足时无扣除；资金充足时实际 XP/材料扣除与 receipt 一致，receipt 保存已支付材料副本；测试结束恢复库存/XP/能力状态。
- 用真实注册表、不创建 Widget 的候选模型测试：物品支持过滤、等级排序、不可修改子列表、拒绝同级死候选、单词条洗练可用性、候选构建不消耗材料。

新增/收紧的架构门禁：六个协调器的新行数棘轮；新 owner 也设上限；经济核心禁止 Handler 实例/传包；候选模型禁止 Widget/行/滚动；view 禁止 Screen/文件/网络；schema 禁止发布；恢复禁止世界依赖。
`test_architecture_boundaries.py` 有 **6 项**正反例；诊断脚本有 **3 项**测试并保留 `ECONOMIC BOUNDARY PASS` / `CANDIDATE BOUNDARY PASS` 标记。工作流及文档镜像同步。

## 6. 最终结果与范围边界

最终证据均对应 `3a3b0076bf56abf70ef9a29b2eb3ab173505d87f`：

- compile-check **40250562480**：Java 25 main/client 编译通过；**117 JUnit，0 failures / 0 errors / 0 skips**，包含本轮新增 10 项。
- build **40250562244**：静态检查、完整 jar 构建、JEI/REI 关闭编译、真实服务端与 SDL 客户端四个业务 job 均成功；`compile-diagnostics` 与 `server-smoke-diagnostics` 也成功。
- Checks 摘要已读取到本轮真实执行标记：`ECONOMIC BOUNDARY PASS`（含 genuinely missing-price、insufficient-XP、wrong-primary denial）、`CANDIDATE BOUNDARY PASS`，以及完整 `CLIENT SMOKE PASS`。不是只看工作流绿色状态。
- 本地 static：**9 groups / 0 warnings**；架构正反例 **6**、Mixin 文档合同 **4**、CI 诊断 **3**、26.3 同步回归 **17** 项全部通过；hygiene、架构依赖/增长棘轮通过。
- release consistency：**12 passed / 0 warned / 0 failed**；工作流与文档镜像校验通过。
- 本地兼容性检查通过，但 Tier 2 因沙箱没有 Minecraft jar 而跳过 vanilla ID/tag 存在性，仍有 **2 条明确 warning**。不将其写成全覆盖。
- GitHub runner 另有 Node action runtime 与 Ubuntu 镜像迁移的平台提示；它们不是 Java 测试失败，也不混入本地 static 的零 warning 统计。

不宣称完成：人工 Windows/macOS IME 组合/候选窗、多玩家并发压测、全 GPU/驱动组合、JEI/REI 全部人工交互。自动 SDL committed text 不等同于人工 OS 输入法候选窗测试。

## 7. 反哺 26.1.2 / 26.2

1. 可复用的是职责与状态所有权，不是直接复制 26.3 的 `GuiGraphicsExtractor`/SDL 或 loader/资源 API；每条分支按实际 Minecraft API 适配。
2. 报价、支付、生命周期分开；不要让经济 Helper 保存完整 Handler。客户端候选与行 Widget 分开，tooltip 不应重新拿回 Widget 事件处理器。
3. 配置迁移、策略 JSON、预设和 draft 编辑分别维护；trace 的典籍消费/再生耦合要有双向测试，不能只验证某个按钮的布尔值。
4. 同时测试旧字符串与 structured 队列，并保留 owner/库存的隔离恢复语义。拆分时公开 DTO/CODEC 类型身份和静态初始化方向都要审查。
5. 保留可排序结果的确定顺序，保持快照独立；不能为了“不可修改”无意改变候选类别顺序。
6. 架构检查要测试反例，并约束新 helper；不能只限制旧大类行数，更不能拿上一轮的测试计数或源码字符串检查替代当前 SHA 的 Java/native 测试。

本轮代码提交并推送到当前 Arena 分支；**没有创建 PR、没有合并、没有 force-push**。

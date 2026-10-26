# 26.3 行为兼容、运行时与同步审计

日期：2026-10-23。目标版本仍为 **1.1.1**，Minecraft 26.3 / Java 25。

本报告补充增量同步报告，不以“编译通过”替代行为验证，也不声称无头专服覆盖了 GUI 或多人联机。

## 结论和必须注意的部署变化

- 保留 26.1.2 已同步的增量功能，以及既有世界加载 schema、秘典鼠标修复；没有回退这些修复或修改默认经济参数。
- 新增明确的协议能力标识：`chiseled_enchanting_table:protocol_26_3_revision_4`。
- **客户端和服务器应同时更新到本轮构建。版本文字同为 1.1.1 不再被当作 wire 兼容的证明。** 检测到装有本模组、但没有当前协议能力的对端时，显示明确的不兼容断开提示。cost/live 数据 channel 也带当前 revision，客户端不在旧 channel 注册新 decoder，避免旧服务器在 JOIN 检查前发送旧结构而触发坏包；服务器仅查询旧 cost channel 识别旧客户端。没有模组的对端不因这个能力检查本身被误判；这不等于承诺可在原版客户端使用本模组方块。
- `/reload` 成功后关闭已打开的本模组附魔台菜单，由原版关闭流程归还输入物品；随后发送新费用、配置、转化目录。重新打开菜单获得新开屏快照，而非继续操作旧转化规则。

## 发现与修复

| 风险 | 根因 / 处理 | 自动验证与边界 |
|---|---|---|
| 主表自定义操作接受非左键 | 26.3 SDL 鼠标编号不能继续假定 GLFW 左 0、右 1；保留秘典平台常量修复，主表自定义按钮只处理平台 LEFT，其他键交还原版库存处理 | 源码回归、客户端编译；未代替实际鼠标操作 |
| 同版本不同 wire 构建互连 | 增加双向注册能力标识及 JOIN 检查；cost 发送还检查当前能力 | 能力真值表与空 marker codec 测试；没有真实双端握手测试 |
| 未知操作 token 被默认为普通附魔 | `OptionType.fromToken` 对未知 token 抛异常，避免误执行 | 所有已知操作 roundtrip + 未知 token 拒绝测试 |
| 包内集合长度可为负或极大 | menu/live/cost 集合统一先经 `PayloadLimits.readCount`，允许 0..65536，再分配/迭代 | 负数、极大值、越界、截断包测试；不是总字节数限额或全面协议 fuzz |
| 重载后开着的菜单保留旧转化快照 | 成功 reload 主动关闭表菜单再同步，原版负责回收输入 | 编译 + 专服真实 reload；玩家持物关闭/回收仍需客户端验收 |
| 书架增量更新没有目标菜单 ID | live payload 增加 `container_id`，发送使用实际 `containerId`，应用前核对目标 | 非零 ID 的 codec roundtrip、匹配/不匹配测试；不是网络延迟压力测试 |
| 查看器混用服务端费用与客户端转化目录 | cost/config payload 增加完整转化描述：ID、输入/标签、输出、等级、门槛、扣级；查看器优先使用同步目录。服务端空目录为有效数据，不回退 jar 默认；断开清缓存 | 完整描述与空目录 roundtrip、空目录/断开缓存测试；未 GUI 验证 |
| 标签输入被当作普通物品 ID | 按客户端已同步物品标签展开输入；REI display location 带具体输入物品 ID，避免同一标签多个显示互相覆盖 | 静态回归与包含查看器依赖的编译；实际 datapack 标签检索仍需 GUI 验证 |
| 同步后只清本地缓存，实际查看器注册表仍旧 | JEI 在 runtime 可用时 hide 旧快照并 add/unhide 新快照；REI 改为动态 display generator，按同步事件失效缓存，下一次查询读取新数据 | 核对当前 JEI/REI 公开 API、带查看器编译与静态回归；没有实际查看器运行测试 |
| REI 动态过滤改变原生查询语义 | 使用 REI `EntryStacks.equalsFuzzy`，保留工具伤害忽略及不同附魔书的区分，而非自行进行全部组件严格相等比较 | 对照 REI 源码及编译；GUI 行为待测 |
| 服务器 `deducted_levels: 0` 被本地同名默认规则覆盖 | 有开屏描述就直接采用该值，0 继续表示使用规范自动扣级算法，不再按“缺失”回退本地 | 0/指定扣级、免费 XP 规范转换测试和源码回归 |
| 目录有规则但同步费用缺行时静默混用本地费用 | 跳过不完整的转化显示并记录 WARN；不伪装为已同步准确价格 | 源码检查；此异常分支未故障注入 |
| 自定义 loot 函数静默忽略条件 | 两个函数由 `MapCodec.unit` 改为 `commonFields`，承接 26.3 的单 `condition` 公共字段 | 编译 + 专服资源加载及两个 always-false 条件 loot fixture，验证不删除/不附魔书本 |
| 同步缓存消费者报错被吞掉 | listener 的 RuntimeException 记录 ERROR，其他消费者仍能继续刷新 | 编译；未为每个 optional viewer 注入异常 |

### 查看器刷新限制

JEI 的公开 runtime API 提供 hide/add，不提供这里可用的彻底移除接口。因此旧快照隐藏后会留在当前 runtime 内，直到 JEI 重建 runtime；频繁 reload 的长期内存行为没有进行压力测试。REI 使用动态生成而非持续追加静态显示。

本轮的查看器修复覆盖了数据来源和刷新调用路径，但“API 编译成功”仍不等于 JEI/REI GUI 实测成功。

## 原版 26.3 差异核对

对照来源：`https://github.com/0khs/minecraft_source_26.3`。本轮另外核对了 FabricMC/fabric 的 26.3 网络 API，以及 mezz/JustEnoughItems、shedaniel/RoughlyEnoughItems 的 26.3 公开 API。

- `InputConstants` / 输入事件：SDL 左 1、右 3，不能继续使用 GLFW 编号。
- `LootPool` / `LootItemConditionalFunction`：保持 26.3 单条件 schema；不要重新把已经合法的 modifier 数组改成不支持的 condition 数组。自定义函数也必须真正解析公共条件。
- `CustomPacketPayload` / `RegistryFriendlyByteBuf`：使用明确 codec，集合以 VarInt + 元素读写；本轮新增真实编译类的 roundtrip，而不只比较源代码字符串。
- `BlockBehaviour`、`StructureTemplatePool`、`StructurePlaceSettings`、`StructurePiece`、`LevelChunk` 等：核对现有 mixin 目标方法及签名；9 个声明的 mixin 均通过源码/打包静态检查。配置 `defaultRequire: 1`，加载到的注入点不允许悄悄失配。
- `Screen` 初始化、绘制/输入入口、方块实体组件与 `CopyComponentsFunction`：核对生命周期和组件路径；染色、名字、冻结数据的真实掉落由下述专服命令验证。

**边界：** 专服 smoke 使用平坦世界、禁用结构生成。它验证启动中实际加载的 mixin 和资源，不能证明所有延迟加载的 mixin、所有结构刷书架分支、铁砧操作和客户端渲染行为均被执行。

## 自动验证

### 本地

- `python3 scripts/test_sync_26_3.py`：13 项源码回归。
- `python3 scripts/ci_static_checks.py`：mixin、11 种语言键、代码文案引用、数据单一来源、Markdown 链接。
- `python3 scripts/compat_check.py`：Tier 1 通过；本地没有 MC jar，Tier 2 原版 ID/标签存在性检查跳过并显示 WARN，不能计为通过。
- `git diff --check`。

### CI：真实 Java 与服务器

- 最终验证源码 **`f7fd95a`**：[build 39893126235](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/runs/39893126235) 四个 job 全部成功：静态检查、完整 Gradle build/JUnit、无 JEI/REI 编译、真实专服 smoke。
- [compile-check 39893126344](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/runs/39893126344) 成功。
- 本次 smoke 包括精确组件数值断言、两个 always-false 条件函数 fixture、游戏 tick 屏障和正常停服；最终游戏日志 **零 `/ERROR]`**，不是沿用早期宽松 smoke 的退出码。
- CI 日志已快进整合进当前分支；本报告的最后更新仅为文档，不更改上述已验证源码。

JUnit：11 项经济/配置测试 + 8 项网络测试，共 19 项。网络测试直接使用生产 codec，验证读取完缓冲区；包括开屏/live 的非空 ID、集合、32 位 seed，以及 cost/config 中不同字段值、非默认配置、转化描述、空目录、所有操作 token 和损坏输入。

**JUnit 限制：** headless codec fixture 使用 null registry、无非空 ItemStack；它们不覆盖 registry-aware ItemStack 组件的真实网络往返。不要将下面的掉落组件测试描述为客户端网络往返测试。

专服流程（`scripts/server_smoke.py`）：

1. 仅在隔离 checkout 创建新 `audit-world`，发现已有 world/audit-world 就拒绝运行。
2. 运行真实 Loom `runServer`，等待 Minecraft `Done`，而非仅执行 `build`。
3. 发送 `reload` 并等候实际资源重新加载。
4. 加载区块，放置附魔台，将颜色 1122867、名字 `Audit Table`、seed 123456、冻结时间 1200、周期 17 写入实际方块实体。
5. 通过游戏 `loot ... mine` 生成真实掉落，`data get entity` 检查组件。
6. 使用仅 smoke 临时注入、退出后删除的两个 loot table，验证两个自定义函数的 always-false 条件：书本不会被删除，也不会被添加效率附魔；测试资源不进入发布构建。
7. 严格匹配名字、颜色、冻结时间/seed/周期/lore，并确认两个条件 fixture 后才发送 `stop`，要求正常退出，并对任何游戏 `/ERROR]`、已知资源/Mixin 错误判失败。

已验证的服务器输出中包含：1891 advancements；模组 6 transformations / 41 loot injections；掉落的 `minecraft:custom_name`、`minecraft:dyed_color`、`minecraft:custom_data` 中 `pendingFreezeTicks:1200L`、`epiphanySeed:123456`、`frozenCycleIndex:17`，以及冻结 lore。

早期 smoke 的平坦世界设置缺失产生过 `No key layers in MapLike[{}]`。这是测试脚本配置错误，已补齐生成器设置并将所有游戏 ERROR 视为失败；不能用早期那次绿色退出冒充零错误证据。

条件探针的早期测试还遇到过测试时序问题：生成实体后同一 tick 立即用 `@e` 查询，实体可能尚未进入可查询集合。探针现使用游戏 `schedule function`，等强制加载及后续 game ticks 后再依次检查实体；也不再假设直接 `/loot loot` 的反馈必然带 table ID。这些未完成运行不计作成功证据。

日志：实际服务器日志 (historical log; current diagnostics are in Actions artifacts/Checks)。

## 尚需客户端/多人验收的明确清单

这些是未验证项，不是已经全部通过的结论：

1. 两个真实 26.3 客户端连接专服，匹配构建能加入；与旧 1.1.1 构建互连时得到明确协议提示，而非坏包/静默回退。
2. 原生、JEI、REI 三种模式下的主表和秘典：左/右键库存、页签、材料 alternatives、R/U/A 聚焦、遮挡区、绘制与 tooltip。
3. 服务端独有 datapack：新增/改写/删除转化、标签输入、修改门槛和自动扣级；核对开屏和查看器均使用服务端数据。
4. 在菜单内放入物品后 `/reload`，确认菜单关闭、物品完整归还，重新打开使用新规则；验证 JEI/REI 实际界面更新且没有可见旧行。
5. 多人共享书架、快速关/开菜单和延迟环境中的 live 状态；携带非空材料及自定义组件的 registry-aware 网络往返。
6. 非平坦旧世界、新结构、遗迹书架处理、铁砧增量规则、随机 tick 与完整刷书架/冻结交互。

因此本轮可证明的是：已修复列出的源码缺陷，增加回归和真实专服验证；不能声称“所有 GUI、多人和可选查看器行为已经实测”。

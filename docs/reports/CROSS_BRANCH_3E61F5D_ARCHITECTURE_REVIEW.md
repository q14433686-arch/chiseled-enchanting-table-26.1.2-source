# 26.1.2 → 26.3 方案 3：最新增量审计与跨分支反馈

日期：2026-10-24。用户已选 **3：先重构规则与回复层，再全量接入**，并要求纳入更新后的源内容、反馈给 26.1.2 和 26.2。

**当前是分阶段工作，不是全量移植完成声明。** 本报告区分源码定位、独立核心修复、游戏适配、实际验证。这里只修改当前 26.3 开发分支，不向另外两个分支直接推送。

## 1. 锁定版本与新增范围

| 分支/范围 | 固定提交 | 结论 |
|---|---|---|
| 当前 26.3 合并基线 | `6a8f371234a1dd524900063d4d2ece9dc845b614` | 上轮已同步至源 `3e081f3` |
| 上一份审计目标 | `78dc05173ef6dc4a61590199192e90726feeb7a2` | PR #17，不再代表最新全部需求 |
| **本次移植目标** | **`3e61f5de3ba7679ccbc92b73443dead149c96840`** | PR #18；连同 PR #17 一并审查/移植 |
| 26.2 反馈核查点 | `8b19c4807a462e20b2efec614b5ba639d36773dc` | 目前仍是 1.0.3；未发现 ModRuntimeConfig/MasterTomeWear/仪式模块，不能把源新模块缺陷说成其已存在缺陷 |

[PR #18](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/pull/18)；[PR #17 后新增比较](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/compare/78dc05173ef6dc4a61590199192e90726feeb7a2...3e61f5de3ba7679ccbc92b73443dead149c96840)。

这次在 PR #17 之后又有 19 个提交（含 CI 日志和 merge）、43 个文件变化，不再只是 38/42 温养机制：

- 表内输入改为 BlockEntity 持久库存，关闭 GUI 不再归还物品；新增长等待铭刻队列、虚拟规划物品和同步状态。
- XP 献祭跳过、强行赶工及母本降级，过载 Infinity 母本的抄错概率和新进度。
- 加权顿悟、Infinity 获取/村民交易限制。
- 残卷箱子战利品、野生书架风化及图书管理员回收/委托，委托按职业等级门槛开放。
- 秘典分页、图解和 11 语言更新。
- **源默认温养已再次改动**：基础 24,000 秒（20 个游戏日），38 架 12,000 秒（10 日），42 架 3,600 秒（3 日）。旧审计中 90/30/10 秒是 `78dc051` 的历史设置，不能作为新移植默认值。

全量同步应包含这些功能，不会仅同步早先的 PR #17。版本仍维持用户要求的 **1.1.0 / MC 26.3**；尚未接入新 wire，所以当前协议仍为 revision 5，后续新 wire 必须单独升级。

## 2. 本阶段实际落地

新增独立于 Minecraft/Fabric 的基础库：

### `config/rules/RuleBook.java`

- 区分存储偏好、权威 datapack 值和实际启动期值；不可变快照同时包含有效值、来源和可编辑性。
- 统一优先级和逐键/全部运行时锁；无数据包值的锁定字段也得到确定的默认值，而不是让直接消费者绕过锁。
- 明确启动期组件不可被世界 datapack 热替换；显式用 world-policy 锁启动期键会拒绝，而非虚假宣称安全。
- 拒绝未知键、错误类型、可变对象和非有限浮点；外部 Map/Set 不可修改已发布内容。
- `prepare` 不改状态；`commit` 检查所属实例及原始快照后再调用原子 writer，writer 成功才发布。失败、过时、跨实例、重入事务均有回归覆盖。
- **范围限制**：这是解析/发布核心，尚未替代现有 ConfigCatalog、DataTables、UI、anvil 等消费者；领域范围与跨字段约束仍须由适配器验证，文件 writer 仍须使用已有原子替换实现。

### `mechanics/resonance/RegenerationScheduler.java`

- 一窗口收齐合法观察再统一结算；同书架槽位采用最快合法速率，不按回调顺序先到先得，不叠加速度。
- 只积累连续观察的加载窗口；缺口、取出/换书、wear/速率改变会重置驻留，绝不使用物品内旧世界绝对时间。
- 用 book identity 与回写确认防止把旧观察的结果写到新物品上；满耐久条目移出调度，未观察槽位淘汰，停服/换 session 清空。
- 部分回复确认原地更新私有状态，不为每本书复制整个调度表，避免批量回复的二次复杂度。
- **范围限制**：这是主线程调度核心，不是已接入游戏的 ticker。适配器必须验证区块仍加载、槽位/物品仍匹配，收集完整覆盖集合后才能结算；Infinity/正常未损耗书不应提交观察。

新增 **13 项 JUnit 核心测试**。当前基础库没有运行时接入，因此不会提前改变已发布玩法，也不证明下面的源侧游戏缺陷已经在线修好。

## 3. 共性问题反馈给 26.1.2；26.2 移植前应规避

以下路径/符号指锁定的 **3e61f5d 源码**。除特别注明，结论来自源码路径，不是已完成的多人游戏复现。

| ID / 优先级 | 证据与问题 | 修复方向 / 当前状态 |
|---|---|---|
| R1 / 高 | `ModRuntimeConfig.shouldApplyKey` 才管锁；anvil mixin、`ModBootstrapConfig` 等直接读原始 effective/get 值 | RuleBook 核心已提供统一值/来源；**消费者接入待做**。启动组件单列，不宣称 world policy 可反向修改注册组件 |
| R2 / 高 | `SyncRuntimeConfigPayload` 发本地 DTO + policy，费用发有效表，ConfigScreen draft 却可显示被覆盖的旧值 | UI、规则和报价读取同一快照；本地待保存值另存，不把锁定的 16 显示成服务端有效的 8 |
| R3 / 高 | `MasterTomeWear.tickLibraryRegeneration` 根据物品旧 bucket 与当前世界绝对时间补算，没有驻留/世界身份 | 调度核心已去掉绝对时间输入；**实际 shelf 适配和旧 NBT 清理待做**。新默认慢了，不代表计时错误消失 |
| R4 / 中高 | 同秒 bucket 去重使共享书架先扫描的台决定速率 | 核心统一取最快合法速率；实际接入必须一窗口聚合，不可在每台 ticker 独立 settle |
| R5 / 中 | Infinity 书带 provenance 时仍反复清理/标脏；重复扫描、每秒写计时 NBT | 核心本身不写 ItemStack，满耐久淘汰；后续 adapter 仅实际回复时写，移除旧每秒写入路径；需实测后台负载 |
| R6 / 高 | `collectExternalDatapackKeys` 假定资源栈 index 0 是本模组，并合并所有外部层声明键 | 按来源及最终生效层建权威字段，覆盖单层、过滤、多层和 mod jar 测试；暂不声称所有环境都必然失败 |
| R7 / 高 | 源 `ModConfigNetworking` 仍无保存 request-ID/accepted；非 dedicated 全员可写；普通写文件失败只 log | 保留/回馈现有 26.3 原子保存、严格 reload、房主/权限校验、ACK 和失败草稿机制；不能覆盖回旧文件 |
| R8 / 高 | `readMasterCharges` / `SyncRitualStatePayload.read` 裸 VarInt 分配；后者无 container/session 路由；skip 仅一个 boolean | 新 revision + 数量/字节/值域上限；S2C/C2S 均绑定 container ID 和 ritual session，拒绝延迟旧包 |

旧报告还确认：书本互斥词条保留不是新增服务端能力；冲突退款函数对书返回 0，不应凭 UI 差异宣称存在刷 XP 漏洞。

## 4. PR #18 新增机制的审查重点

### N1 — 高：槽位 GUI 锁不是库存事务锁

源 `ChiseledEnchantingTableBlockEntity` 实现 Container，`removeItem`、`removeItemNoUpdate`、`setItem` 未按活动仪式检查目标；GUI Slot 的 mayPickup/mayPlace 则禁止仪式中取放。两条路径不一致。

自动化或其他 Container 调用方需要单独核对；不能以 GUI 已禁用证明不会换物。`completeActiveRitual` 读取完成时槽位物品，没有核对入队目标快照。若目标被替换，存在给错误物品结算或已支付队列消失的路径风险。

修复应有 escrow/目标指纹与受控写入入口，所有自动化/移除/替换都遵守同一状态机。**未运行 hopper 游戏复现，报告不把路径风险写成实测复制。**

### N2 — 高：归属字段被写入但没有构成授权边界

`enqueueInscription` 每次都覆盖 `ritualOwnerUuid`；`skipActiveRitual` 检查当前菜单/是否有母本，却不验证该 owner。非发起者可能追加或赶工消耗共享母本，归属/进度也会转移。

需要定义共享表是否允许他人接续；安全默认采用仪式 lease/session 和所有者授权，旁观者只读，管理员介入明确记录。owner 仅是领奖 recipient，不能被当成已实现鉴权。

### N3 — 高：队列“无界”与协议/存档容量矛盾

`ritualQueue` 是无界 ArrayList，`serializeQueue` 用 `;`/`|` 拼成一个持久字符串，客户端状态包也同步整队列。`enqueueInscription` 先 add 再 Math.addExact；调用方先支付经验/材料/母本扣损，后做入队。

long 计时不代表无限队列安全。应同时限制步骤数、序列化大小、等级/总额，先完成入队预检再支付；拒绝必须零副作用。应核对实际 NBT 字符串写入限额（传统 writeUTF 路径存在字节上限），不能只验证数学 long 没溢出。

### N4 — 高：完成流程先清状态，再处理可能失败的步骤

`completeActiveRitual` 先复制队列并 `clearRitualState`，之后才 lookup enchantment、修改组件、累计退款及发送效果；找不到 enchantment 直接 continue。重载移除 ID、目标变化或处理异常时，已支付订单可能被部分跳过，且没有持久化的可恢复完成阶段。

应设计 PREPARED/COMMITTING/COMPLETED（或等价状态）与结算 ID，预验证后一次发布；出错保留可诊断订单，不静默丢弃。是否退款须与入队扣费/母本预留统一，不能事后靠字符串猜历史。

### N5 — 中高：跨维度仅凭 BlockPos 同步打开菜单

`syncOpenMenusWatchingTable` 比较 pos 与 menu.tableBlockPos，没有比较 level/dimension。同坐标不同维度会误触达对方菜单的 refresh/push 路径；目前这些方法部分会自行重算各自世界，不能因此宣称直接复制物品，但路由边界确实不足。

所有 locator 统一包含世界身份 + position + session；加载/重启后更新 epoch，拒绝旧会话。

### N6 — 中：村民重写与其他模组的所有权/生命周期

`sanitizeVillagerInfinityOffers` 会处理所有 AbstractVillager 的 Infinity 输出，未区分原版、自己的委托和附属模组自定义交易。`ensureLibrarianDepletedTomeOffers` 在交互时重建候选、动态追加。

禁止 Infinity 交易是新源设计，但不能无说明声称对其他交易模组透明兼容。需要明确适用范围、持久的自有 offer 标记、升级/重载幂等、价格/折扣及职业等级回归；纯权重公式正确不代表真实 MerchantOffer 生命周期正确。

### N7 — 26.3 专属：loot/provider/API 与物品掉落状态

新增 loot 代码使用源 26.1.2 的 ConstantValue 等 builder，26.3 已有 ContextIntProviders/单 predicate holder/schema 修复必须保留。持久库存/仪式新增后，break/drop、组件复制、Creative break、重复 getDrops、chunk unload/save 都要重新测；旧“只保留颜色/顿悟”的成功探针不足以证明新库存不丢不复制。

## 5. 架构接入约束（本轮方向已选，无需重新询问）

1. **原始存储 ≠ 有效规则 ≠ 编辑草稿**。所有消费者使用同代有效快照，UI 来源与数值来自同一快照；范围和跨字段校验在 prepare 完成。
2. 领域表、policy、配置及 catalog 必须作为一个候选构建，再发布同一代。现有 RuleBook 只覆盖标量基础，不得把“新核心已测试”等同于领域表已完成原子切换。
3. 回复按世界 session、书架位置、槽位、物品身份结算。仅有效加载驻留累计；不补算背包/跨世界时间；共享书库每窗口最多一次。
4. 仪式使用有界订单和 session/lease；支付、预留、完成、取消、破坏和离线各有状态转换，游戏库存写入受同一权限与身份校验。
5. 新源默认以 **3e61f5d** 为准，不沿用已过时的 90/30/10 秒。旧文件显式值保留、迁移行为说明；旧 wear/regen 数据不能兑换未知历史时间。
6. 客户端只展示/请求；单人也走服务端授权。新协议上线才升级 revision，旧通道只探测不复用 decoder。
7. 自有村民交易/loot 与第三方规则有清晰边界；worldgen 保留现有 26.3 安全 processor，不覆盖为源版实现。

## 6. 其他两个分支如何采用

### 26.1.2

- R1–R8、N1–N6 是对当前 3e61f5d 路径的直接反馈，建议先修队列身份/容量/库存边界与保存权限，再改善 UI。
- RuleBook、RegenerationScheduler 不依赖 MC 包，可复用核心和测试；接入自身 API，不能仅拷贝类而保留所有原始消费者。
- 分别补实际 datapack 层叠、集成/LAN guest、磁盘失败、共享 shelf、hopper、跨维度同坐标、断线/保存恢复等集成用例。

### 26.2

- 固定点缺少这些新增模块，R/N 清单主要是**移植防回归门禁**，不是声称这些新缺陷都已在 26.2 触发。
- 可先引入同一独立核心，按自己的 MC/Fabric pins 写适配，不复制 26.3 的 GUI/loot/provider 接口或依赖版本。
- 接入任何新 payload 前建立独立能力 revision、数量限幅和 container/session 路由；不要先合并无界队列后依赖发行版本号辨认兼容。

没有替另两个分支开 PR、创建分支或推送；报告及独立核心保存在当前分支，便于之后按需反哺。

## 7. 验证与剩余状态

第一版基础源码 `99802e57afefd007fe062ace71145515377e9fb1`：

- [build 40004091363](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/runs/40004091363)：静态、完整 Gradle build、无 JEI/REI 编译、真实无玩家专服 smoke 四项全成功。
- [compile-check 40004091308](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/runs/40004091308)：成功，日志 **42 项 JUnit PASSED**，其中新核心 13 项。
- 最新基础源码 **`200e468b47d955d67dabaafa86f08c88e850705c`** 优化批量回写复杂度；独立 [build 40004583434](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/runs/40004583434) 四 job 全成功，[compile-check 40004583488](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/runs/40004583488) 成功，日志仍为 **42 项 JUnit PASSED**。后续仅回写日志和补文档。
- [当前 Java 日志](../../build-reports/compile-java.log)、[专服日志](../../build-reports/server-smoke.log)。专服无玩家，仍运行原有启动/reload/组件/loot 探针；不覆盖待移植的仪式或新残卷路径。
- 本地 15 项 Python、静态五组、版本一致性通过；缺 MC jar 的两项 compat Tier 2 仍跳过，不计为通过。

**未完成**：最新功能的实际合并、运行时消费者/回复 adapter、仪式状态机、wire 升级、资源栈与新交易/loot 集成测试。新 GUI、真实 LAN/双端及性能压测也未进行。当前工作树没有把隔离的三方合并候选当成已发布代码。

因此，现阶段“修复”指独立核心中已实现并测试的规则，而不是宣称 3e61f5d 的游戏路径已经全部修好。后续集成仍须通过同等 CI 和新机制专项验收。

# 26.1.2 → 26.3 方案 3：最新增量审计与跨分支反馈

> **Source-branch document, kept verbatim as porting evidence.** This report was written on the
> `26.3` branch and describes that branch's own state (`1.1.0 / MC 26.3`, wire `26_3_revision_6`,
> 52 JUnit, its commit hashes and CI run ids). It is reproduced here unchanged because §3 (R1-R8)
> and §4 (N1-N7) are the regression gates that this line's `26.2` port was checked against — see
> [`SYNC_FROM_26_3_SIDE_REPORT.md`](SYNC_FROM_26_3_SIDE_REPORT.md) §9.5 for where each gate is
> satisfied on **this** branch, and note that the version, protocol string and CI numbers below are
> **not** this line's.

日期：2026-10-24。用户已选 **3：先重构规则与回复层，再全量接入**，并要求纳入更新后的源内容、反馈给 26.1.2 和 26.2。

**全量玩法及架构已接入当前 26.3 分支。** 本报告区分实际实现、自动验证和仍需人工验收的边界；不以旧基础库的绿灯代替新玩法证据。这里只修改当前开发分支，没有向 26.1.2/26.2 推送。

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

全量同步应包含这些功能，不会仅同步早先的 PR #17。版本仍维持用户要求的 **1.1.0 / MC 26.3**；协议已独立升级为 **`26_3_revision_6`**。客户端和服务端必须一起更新；同为 1.1.0 的旧构建不等于 wire 兼容。

## 2. 全量接入结果

### 有效规则与原子发布

- `RuntimeRules` 实际连接 `ConfigCatalog` 与 `RuleBook`；范围和跨字段归一化只做一次，来源包括启动值、datapack、偏好、锁定默认和归一化结果。
- `ChiseledEnchantingTableData.RuntimeFrame` 把领域表、费用、loot、policy、有效 DTO 和规则快照放在同一不可变代中，一次 volatile 发布。`ModRuntimeConfig.effective()` 与 anvil 等直接消费者读有效值，不再绕过规则锁。
- 存储偏好、实际生效 DTO、客户端编辑草稿分开；网络分别发送 preferences/policy/effective；锁定或被 datapack 管辖的行显示有效值，重启项另提示当前实际值。ACK 成功后以服务器偏好重建基线，拒绝保留草稿。
- 保存仍先原子写盘，再提交/发布。严格解析 policy 的布尔、数组、键名；无效 policy/表不降为解锁默认。reload 失败会恢复原始表缓存，防止下一次保存把失败 reload 的半成品带入有效代。
- 资源声明与加载采用同一整文件覆盖语义：只检查最终选中的资源，依据 `sourcePackId` 区分来源；Fabric 聚合默认包用内置 JSON 内容比对兜底，不猜 index 0，不合并被遮蔽层的声明。
- 启动期 stack-size 记录实际启动值，世界 datapack 显式治理该键会拒绝。不能把世界规则描述成阻止玩家修改启动文件的绝对防篡改。

### 共享书库温养

- `WorldRegeneration` 已接入 Fabric 26.3 的 `END_SERVER_TICK`，遍历世界后按 20 tick 窗口收齐错峰观察，调用 `RegenerationScheduler` 一次结算；同槽位最快合法速率，不叠加。
- 结算核验 chunk 已加载、书架 BE、槽位 ItemStack 引用与 wear；真正回复才复制回写并确认新引用。未损耗书/Infinity 不进入调度；缺失窗口、换书、速率变化重置驻留，不读取旧绝对时间 NBT，也不累计背包/跨世界时间。
- 世界离开服务器集合/停服时清理；暂停不重复结算同窗口。后台扫描不再构建 GUI 的完整 charges map。纯调度测试覆盖次序、换书、缺口、换速率及批量回写。

### 库存、仪式与恢复

- 两槽 BE 库存持久化，关闭菜单不退回输入。活动仪式绑定 owner、随机非零 session、目标副本；Container remove/set、自动化及 GUI 均限制替换目标，完成时再核验。
- 入队在付费前检查 owner、目标 count=1、指纹、容量与退款预算。每台最多 **256 步 / 32,000 字符**，等级不超过 255；计时使用 long，但不照搬源“无限队列”。
- 所有 enchantment/适用性先验证，再对副本结算，目标提交后才清队列。缺 ID、目标变更或坏存档会隔离，不静默跳过；赶工只有成功结算后才扣 XP/执行母本降级。
- `/cet cancel-ritual` 供 owner 在打开对应表时取消，保留输入；已经支付的费用/磨损不退。坏字符串诊断最多保留 32,000 字符并明确记录 ERROR，避免加载失败丢整个 BE。
- 菜单同步限定同一世界与位置；skip/state payload 绑定 container/session，旧会话拒绝。破坏表会清订单并掉落当前存储输入，不把队列复制进掉落方块。

### 新源全部玩法/资源与 26.3 适配

已接入持久库存、等待铭刻/虚拟规划、XP 献祭、强行赶工、Infinity 抄错、加权顿悟、新进度、残卷结构箱 loot、野生书架风化、图书管理员回收/恢复/按等级委托、秘典分页图解和 11 语言资源。

- 新 loot 使用 26.3 `ContextIntProviders`，保留既有单 predicate holder、26.3 advancement/schema、结构线程安全和 SDL 等修复。
- Infinity 禁售只改附魔书结果，不碰第三方装备；就地更新结果保留原 offer 的 uses、折扣、需求和售罄状态。附魔书交易限制仍是有意的兼容行为变化，不宣称对其他交易模组完全透明。
- 自有委托结果持久标记 band，重开/升级/候选表变化不会无限追加；选择仍消费相同 RNG 序列，职业等级门槛保留。
- 新温养默认 **24,000 / 12,000 / 3,600 秒、38/42 架**，创造同损、新实例 datapack 优先；旧配置显式值不强制覆盖。旧档升级前请备份。

## 3. 共性问题反馈给 26.1.2；26.2 移植前应规避

以下路径/符号指锁定的 **3e61f5d 源码**。除特别注明，结论来自源码路径，不是已完成的多人游戏复现。

| ID / 优先级 | 证据与问题 | 修复方向 / 当前状态 |
|---|---|---|
| R1 / 高 | `ModRuntimeConfig.shouldApplyKey` 才管锁；anvil mixin、`ModBootstrapConfig` 等直接读原始 effective/get 值 | **已接入 RuntimeFrame 与直接消费者**。启动组件单列，不宣称 world policy 可反向修改注册组件 |
| R2 / 高 | `SyncRuntimeConfigPayload` 发本地 DTO + policy，费用发有效表，ConfigScreen draft 却可显示被覆盖的旧值 | **已分离 preferences/effective，锁定行显示有效值**；本地待保存值另存，不以本地偏好冒充服务器实际值 |
| R3 / 高 | `MasterTomeWear.tickLibraryRegeneration` 根据物品旧 bucket 与当前世界绝对时间补算，没有驻留/世界身份 | **已接入 WorldRegeneration，旧计时键不再参与计算**。新默认慢了，不代表计时错误消失 |
| R4 / 中高 | 同秒 bucket 去重使共享书架先扫描的台决定速率 | **已一窗口聚合再结算，最快速率**；仍需多人共享大书库性能实测 |
| R5 / 中 | Infinity 书带 provenance 时仍反复清理/标脏；重复扫描、每秒写计时 NBT | **已移除每秒物品写入，仅实际回复时回写**，满耐久/Infinity 不入队；后台负载需压测 |
| R6 / 高 | `collectExternalDatapackKeys` 假定资源栈 index 0 是本模组，并合并所有外部层声明键 | **已按最终选中资源及来源识别**；Paxi/OpenLoader/KubeJS 等环境的资源层组合仍须实机验证 |
| R7 / 高 | 源 `ModConfigNetworking` 仍无保存 request-ID/accepted；非 dedicated 全员可写；普通写文件失败只 log | **已保留并接入有效规则事务**：原子保存、严格 reload、房主/权限、ACK、失败草稿；真实 LAN 权限仍需人工验收 |
| R8 / 高 | `readMasterCharges` / `SyncRitualStatePayload.read` 裸 VarInt 分配；后者无 container/session 路由；skip 仅一个 boolean | **revision 6 已接入**；有数量/字节/值域限幅、container/session 路由及往返测试 |

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

## 7. 验证证据与剩余人工边界

全量实现 `6357979` 已通过 [build 40007212249](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/runs/40007212249) 四 job 和 [compile-check 40007212228](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/runs/40007212228)，日志 **50 JUnit PASSED**。不是旧的 42 项基础阶段结果。

最终游戏源码/探针 **`92437900b5b80128665794a43e433561844c048a`**：

- [build 40007636762](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/runs/40007636762)：**四 job 全成功**（静态、完整 Gradle build、无 JEI/REI 编译、真实专服）。
- [compile-check 40007636849](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/runs/40007636849)：**成功，52 JUnit PASSED**，含有效领域/直接消费者一致性、失败保存不发布、policy 拒绝、队列边界和新 wire 往返。
- 真实专服在新平坦世界执行启动/reload、旧命名/染色/冻结掉落及条件 loot 探针后，加载带 owner/session/target 的持久队列。日志证实等待阶段保留钻石剑与订单，自然完成后出现 sharpness/provenance 并清空队列。
- 同一真实 ticker 遇到已移除的附魔 ID 时输出预期 WARN，将 `ritualBlocked` 持久为 true，保留输入/订单；随后破坏附魔台，探针找到 count=1 的输入钻石剑。此 WARN 是故意构造的隔离路径，不是隐藏的启动错误。
- 后续仅回写 CI 日志、更新报告和修正“无限队列”说明文案；以上编译证据对应明确的游戏源码提交。没有把一次 `/save-all` 描述为已经执行关服重启。

当前 [Java 日志](../../build-reports/compile-java.log)、[专服日志](../../build-reports/server-smoke.log) 由 CI 回写；以 Java 日志首行 commit 和对应 run 为准，不以报告更新 commit 作为编译来源。

本地：15 Python 同步测试、静态五组（621 键/11 locale）、版本一致性 12 项、workflow 镜像、diff 检查通过。由于本地没有 MC jar，compat Tier 2 两项跳过，**不算已通过**。真实 Java 25 编译/运行只引用 GitHub CI。

仍需人工/专项集成验收，不冒称已测：

- GUI 全分页、SDL 拖拽、Overlay、Mod Menu、JEI/REI；LAN 房主/访客/op、旧 revision 4/5 双端拒绝、断线和延迟包。
- 多人争用表/共享书架、hopper/第三方可变引用、跨维度同坐标、满库存、Creative/爆炸/重复掉落等完整矩阵。
- 真正关闭服务器后重启、区块卸载再载入、进程硬杀时 XP/进度通知一致性。实现保证正常 tick/NBT 事务边界，不承诺跨实体磁盘崩溃时“恰好一次”经济事务。
- 多资源包/过滤包/聚合 mod jar 的来源与优先级组合；第三方 offer/折扣/职业变化和 registry 重载；自然结构生成与大规模性能。
- 缺失 owner 的坏存档没有普通 owner 取消入口，需要管理员备份后处理；超长坏队列只保留限长诊断。`Container.getItem` 可变引用无法约束恶意 Java 模组，指纹核验用于隔离，不是防护任意同进程代码的安全沙箱。

26.1.2 可反哺独立核心、RuntimeRules/Frame 方案及 R/N 清单；26.2 应把它们作为移植门禁，再适配自己的 API。本轮没有修改或合并另外两个分支，也没有自动创建/合并当前分支 PR。

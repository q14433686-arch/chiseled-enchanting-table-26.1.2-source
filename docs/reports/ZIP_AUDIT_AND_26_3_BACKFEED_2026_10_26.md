# ZIP 审计（40 条）+ 26.3 回移植处置报告 — 2026-10-26（26.1.2）

> 范围：`code-audit-and-balance-analysis.zip` 的全部 40 条发现（H/A/C/N/G/M/D/K/B），
> 以及 26.3 分支 `SYNC_9F0E13B_SEVEN_PATH_AUDIT_2026_10_26.md` §5 的回灌清单。
> 每一条都已在**当前 26.1.2 源码**中复核；26.3 文档只作线索，不作事实。
> 处置分四类：**本轮修复** / **PR #22 已修复（源码验证）** / **接受差异（附理由）** / **文档对齐**。
> 审计 ZIP 本身已按仓库 compat 闸门（transient root clutter）移出版本树；其结论以本报告为准。

## 一、26.3 回灌清单（§5）

| 条目 | 处置 | 说明 |
|---|---|---|
| RULE-01 | **本轮修复** | `DatapackKeyExtractor` 的 `library_resonance` 分支补齐 6 个 `tome_pages` 键；`ChiseledEnchantingTableData.rawRuntimeConfig()` 把同名 6 字段从 `rawLibraryResonanceConfig` 桥接进 `Draft`。此前数据包写这 6 个键会被治理静默重置。回归测试在 `EconomyAndConfigVerificationTest#datapackPriorityDefaultAndPackPolicyAntiTamperGovernance`。 |
| RULE-02 | **本轮修复** | 本方来源判定改为 `sourcePackId().equals(FABRIC_MOD_ID)`（新增 `ChiseledEnchantingTable.FABRIC_MOD_ID = "chiseled-enchanting-table"`，与 `fabric.mod.json` 一致）；仅对无法按 ID 证明归属的 `fabric`/`vanilla` 聚合源做内容比对。26.3 的 `resource.isBuiltin()` 在 26.1.2 的 `Resource` 上不存在，故未照搬。 |
| RELOAD-01 | **本轮修复（版本适配）** | `ReloadableServerRegistriesMixin` 改为 `cancellable=true, require=1`，解析失败时 `CompletableFuture.failedFuture(error)` 让整个注册表重载失败并保留旧规则。**关键差异**：26.1.2 的 `reload` 签名是 `(LayeredRegistryAccess, List, ResourceManager, Executor)`，26.3 的两参 `PreparationBarrier` 形状在此版本不存在——照搬会在服务器启动时 mixin apply 崩溃（CI server-smoke 实证，已按四参签名修正）。 |
| RELOAD-02 | **本轮修复** | 新增 `LootRegistryBuildRules(entries, mode, chestSpawns)` 暂存快照：`preloadForLootRegistries` 只解析并原子发布，不再吞异常、不再在此阶段写活 `updateChestSpawnConfig`；`LootTableEvents.MODIFY` 两处（附魔书注入 + 褪魔残卷箱子生成）统一读暂存快照；`SERVER_STARTED` / `END_DATA_PACK_RELOAD` / 完整重载成功与失败路径都会 `clearLootRegistryBuildRules()`。 |
| RELOAD-03 | **本轮修复** | Classic 三件套新增不可变基线 `BUILT_IN_CLASSIC_COSTS/UPGRADES/TRANSFORMATIONS`，`loadClassicJson` 的缺省回退改为基线常量——删掉数据包里的 classic 文件后正确回滚到内置版，而不是继续用当前生效表。 |
| NET-01 | **本轮修复** | `EnchantCostTablePayload.trimToWireBudget` 不再在「0 行成本」时短路放行超大包头：裁无可裁仍超限时抛 `IllegalArgumentException`，绝不发出超限数据包。行为测试在 `NetworkCompatibilityTest#oversizedWireHeaderFailsInsteadOfEmittingPacket`。 |
| NET-02 | **本轮修复** | `EnchantCostSync.sendTo` 失败不再 warn 后放行：清 `LAST_DISPLAYS` 并以 `message.chiseled_enchanting_table.cost_sync_failed`（11 语言）断开连接 fail-closed。成本表是权威规则数据，静默沿用旧表会造成报价与扣款不一致。 |
| LIFE-01 | **本轮修复** | `TomeParticleEffects` 注册 `SERVER_STOPPED` 清理本服务器的粒子 burst；tick 时若 burst 所属维度已被卸载也立即移除，不再跨服务器/维度泄漏。 |
| MIXIN-01 | **本轮修复** | 新增 `BookshelfCreativeExtractionMixin`：仅在原版 `ChiseledBookShelfBlock.removeBook`（创造模式玩家实际取书操作）入口处服务端解锁，自动化路径不受影响；已在 mixins.json 注册。生存模式锁定语义不变。 |

## 二、ZIP 40 条发现处置

### 硬编码 / 文档漂移（H）
- **H-01 已修复（PR #22）**：Wiki 温养 6 天 → 38 架 3 天 → 42 架 1 天（1200s/日）与 JSON `7200/3600/1200` 一致。
- **H-02 已修复（PR #22）+ 本轮文档修正**：两个滑移上限键冲突时加载即告警并以 `crossline_slip.max_slip_chance` 为准，`ritual.max_transcription_slip_chance` 仅作缺省回填；本轮修正了 `computeLoadSlipProbability` 中过期的「18%」javadoc。封顶值按 PR #22 合并基线保留 **25%**（代码/JSON/测试/Wiki 四处一致）。
- **H-03 机制已定 + 文档对齐**：超载按质量负载 `L`（ΣL）计，是用户拍板方案；Wiki §3.2 已按 ΣL 描述，不再按条数。
- **H-04 已核实**：`ConfigCatalog.ALL_OPTIONS` 恰为 80 项，与 Wiki §10.1 一致。
- **H-05 已修复（PR #22）**：扫描半径统一引用 `BookshelfProgression.DEFAULT_SCAN_HORIZONTAL_RADIUS` 且两处 clamp 一致。
- **H-06 已修复（PR #22）**：`ritual.rush_degrade_time_constant_seconds = 210.0` 已入 JSON 并同步客户端。
- **H-07 已修复（PR #22）**：村民清洗、超载候选、免磨损判定统一走 `#wear_immune` 标签（`TomeWearData.isWearImmuneEnchantment`）。
- **H-08 已修复（PR #22）**：守恒探针覆盖至 255；失败改为 zero-refund fail-safe（`verifyRuntimeConservation` 在 Data 重载路径生效），不再 fail-open 发放返还。
- **H-09 已修复（PR #22）**：`Advancement.DISENCHANT_SACRIFICE_REQUIRED` 直接引用 `BundledDefaults.SACRIFICE_REQUIRED`。

### 源码 API / 版本（A）
- **A-01 接受差异**：与 26.3 源码保持一致（`required=true`、`defaultRequire=1`）。理由：`require=0` 的静默失效比启动失败更难察觉（锁定/成本类 mixin 静默失效会直接造成规则穿透）；版本升级纪律由 `docs/MIXIN_UPGRADE_CHECKLIST.md` 承担，且 server-smoke 会在签名漂移时第一时间红灯（本轮 RELOAD 签名问题即由此抓获）。
- **A-02 已核实**：JEI 插件仅剩 `addItemStackInfo`（信息页 API，非被删的成分 API），无 `IIngredientAcceptor#addItemStack` 调用。
- **A-03 已修复（PR #22）**：`fabric.mod.json` 已声明 `"fabric-api": ">=0.151.0"`。
- **A-04 已修复（PR #22）**：村民清洗只剩 mixin 单一路径，`UseEntityCallback` 重复调用已不存在。

### 数据编解码（C）
- **C-01 本轮修复**：`BookshelfProgression` / `LibraryResonance` / `InscriptionRitualMechanics` 三个配置各自定义 `writeWire/readWire`，字段顺序住在记录定义旁；`EnchantCostTablePayload` 只负责委托，`lr0…lr24` 匿名位置变量全部消除。**字节序刻意不变**，因此无需 bump 线缆 revision（`NetworkCompatibilityTest` 全字段往返测试守护）。schema hash 未另加：包 ID 本身内嵌 `EnchantingProtocol.REVISION`，漂移即不同通道并由 NET-02 fail-closed。
- **C-02 部分修复 + 接受差异**：NET-02 后任一通道发送失败即断开，不再出现「UI 读 JSON、JEI 读二进制」的静默分叉；双通道本身保留（配置 UI 需要 JSON 形态、成本表需要二进制形态），属设计内分工。
- **C-03 接受差异**：再生进度继续用旁路 JSON（原子写 + `(维度,坐标,槽位,BookSignature)` 键 + 磨损值兜底）。搬进物品组件意味着每次结算都要重写书架内物品栈并触发方块实体同步，成本与风险不成比例；区域回滚场景靠 signature 不匹配自动失效，不会串书。

### 网络同步（N）
- **N-01 已修复（PR #22）**：旧版本探测由 `LEGACY_COST_PROBES` / `LEGACY_APPLY_PROBES` 生成，覆盖全部历史 revision。
- **N-02 已修复（PR #22）**：`Capability` 携带 `enchantedBookStackSize`（`PayloadLimits.MAX_WIRE_CAPABILITY_STACK` 上限校验），握手不一致走不兼容断开路径。
- **N-03 本轮修复**：转
...[truncated 2089 chars]
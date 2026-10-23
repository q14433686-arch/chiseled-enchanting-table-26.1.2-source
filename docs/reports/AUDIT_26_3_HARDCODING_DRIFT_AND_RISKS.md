# 审计报告：硬编码 / 版本适配 / 数值-机制合理性（26.3 分支）

- 审计对象：`arena/f9d13d90-chiseled-enchanting-table-26-1`（`gradle.properties` → Minecraft 26.3 / loader 0.19.5 / Fabric API 0.162.0+26.3，Java 25）
- 审计日期：2026-10-23
- 审计方式：全量源码通读 + 定向 grep/脚本统计 + 运行仓库自带门禁（`python3 scripts/ci_static_checks.py` → PASS，5 组检查，0 warning）
- 说明：本环境无法解析 Fabric/Mojang maven（仅 GitHub/PyPI 等白名单），因此**未本地重新编译**；所有结论均来自源码与仓库内已有 CI 产物（`build-reports/compile-java.log` = success）。标注「需实测」的条目依赖运行时行为确认。

---

## 0. 结论摘要（按严重度）

| # | 结论 | 类别 | 严重度 |
|---|---|---|---|
| 1 | 3 个 Mixin 类**根本没有注册**，导致「要塞图书馆书架替换 + 朝向启发式」整条链路失效，且相关 method 成为死代码 | Mixin 注入 | **P0** |
| 2 | `tier3_batch_size` 数据键被运行时配置无条件覆盖 → 数据包键静默失效（双源真值） | 数据/耦合 | **P1** |
| 3 | seconds↔ticks 往返导致数据包数值被静默改写（250ticks→240，<200ticks→200） | 数据编解码 | **P1** |
| 4 | 网络预算按「条目数」而非「字节数」；`MAX_TOTAL_COST_STACKS=131072` 与 2 MiB 包上限不匹配（max_level=255 场景必炸） | 网络同步 | **P1** |
| 5 | 祭坛仪式 quarantine 无自动/兜底恢复路径；owner 缺失时连 `/cet cancel-ritual` 也救不回来（永久卡死） | 机制/耦合 | **P1** |
| 6 | 大量经济/战利品/概率数值硬编码在 Java（箱子概率表、村民价格与等级阈值、XP 曲线、去魔化常量），数据包无法调整 | 数值设计 | **P1** |
| 7 | 配置覆盖层 `isPauseScreen()=true`：单人模式下暂停集成服务器 → 保存 ACK 需要服务器 tick，疑似「保存中/保存失败」自锁（需实测） | GUI 输入 | **P1（需实测）** |
| 8 | `default_enchantments.json` 的 `exclude: [infinity]` 在 Java 内置回退里**丢失**（`builtInExcludedTreasureIds()` 返回空） | 硬编码漂移 | **P1** |
| 9 | `getUpdateTag()` 回传整个 BE（含 2 格物品栏 + 最长 32 000 字符仪式队列 + owner UUID）→ 每个区块同步都发给所有客户端 | 网络同步 | **P1** |
| 10 | `calculateShelfPower()` 在开菜单时被算两次；`broadcastChanges` 每 20 tick 全量重扫一次 + 每玩家一次 `buildTransformationDisplays` | 耦合/性能 | **P2** |
| 11 | `ChiseledBookshelfTick` 每个随机 tick 做 7×5×7=245 次 `getBlockState` 找最近桌子 | 耦合/性能 | **P2** |
| 12 | 一组「隐式取服务端配置」的单参重载（`XpCostConversion.*`、`LibraryResonance.activeServerConfig()`）在客户端调用即错，且目前全是死 API | 死代码/API | **P2** |
| 13 | 2 份 `AddRandomTick` Mixin，1 份已注册、1 份死代码（且死的那份连 `@Override`/`@Inject` 都没有） | 死代码 | **P2** |
| 14 | 配置屏 `KEY_1..KEY_6` + `CategoryId.values()[i]` 硬编码分类数量 | GUI 输入 | **P2** |
| 15 | `ItemTransformation.level`、`ItemCostOption` 计数字段、`extras` 递归均无上限 → 溢出/超大栈/深层递归风险 | 数据编解码 | **P2** |
| 16 | `QueuedInscription.encode/decode` 对 `paidItems` 条数不对称（decode ≤16、encode 不限制）→ 存档后可能被判 corrupt | 数据编解码 | **P2** |
| 17 | `LootInjection.isTag` 实为「路径前缀匹配」，命名与语义不符；模组命名空间无法用前缀注入 | 数据编解码 | **P2** |
| 18 | 成就阈值（22% / 35%）硬编码，与可配置折扣脱钩 | 数值设计 | **P2** |

---

## 1. Mixin 注入

### 1.1 【P0】3 个 Mixin 类未注册 → 要塞书架链路整条失效

`src/main/resources/chiseled-enchanting-table-port.mixins.json` 只注册了 8 个类：

```json
"mixins": [
  "anvil.RemoveEnchantMerge", "anvil.RemoveXpCostScaling",
  "bookshelf.AddRandomTick", "enchanting_table.DisableBookEnchanting",
  "grindstone.GrindstoneBookDepleteMixin", "loot.ReloadableServerRegistriesMixin",
  "structure.StructurePlaceSettingsMixin", "structure.StructureTemplatePoolProjectionMixin"
]
```

但 `src/main/java/chiseled_enchanting_table/mixin/structure/` 下还有 3 个类**没被任何配置引用**：

| 类 | 作用 | 后果 |
|---|---|---|
| `LevelChunkPostProcessMixin` | 区块 post-processing HEAD 里执行 `postProcessBookshelf` / `postProcessFilledBookshelf`（服务器线程） | 永不执行 |
| `StructurePieceMarkMixin` | 要塞图书馆等**程序化放置**的 `minecraft:bookshelf` 打 post-processing 标记 | 永不执行 → 要塞/其他程序化结构的原版书架**不会被替换** |
| `StructurePieceAccessor` | `StructurePiece#getWorldPos` invoker，上面那个类依赖它 | 同上 |

连带后果：
- `BookshelfReplacerProcessor.postProcessBookshelf(...)` / `postProcessFilledBookshelf(...)`（`mechanics/structure/BookshelfReplacerProcessor.java:150,161`）**无任何调用方** → 死代码。
- `BookshelfReplacerProcessor.markForFacingPass(...)`（:193）每次 `finalizeProcessing` 都往 `ProtoChunk` 打标记，但**没有消费者**，纯开销。
- 结果是「模板路径的书架会填书、但朝向启发式永不执行；程序化路径的书架完全不转换」——与 `CHANGELOG.md:167`、`docs/EXTENDING.md:224`、`docs/patches/README.md:35` 的宣称不符。

**为什么 CI 没发现**：`scripts/ci_static_checks.py` 的 Tier A 只做 `mixins.json → 源码` 方向（配置里列出的类必须存在、必须被编译），**没有做 `源码 → mixins.json` 的反向检查**，所以「写了 Mixin 类但没注册」这种最常见的静默失效无人拦截。建议增加：

```python
# Tier A2: 反向检查 —— package 下每个 @Mixin 类都必须出现在某个已注册 config 中
for each java file under src/*/java/<package>/**: 
    if "@Mixin(" in src and rel not in declared_set: fail(...)
```
（参考实现见 §9 附录脚本。）

**修复**：把 3 个类加进 `mixins.json` 的 `mixins` 数组（并按需给它们 `require = 0`，见 1.2）。注意 `StructurePieceMarkMixin` 在 `HEAD` 里就调用 `level.getChunk(...).markPosForPostProcessing(...)`，是 worldgen 期的跨区块访问，必须与 `docs/reports/WORLDGEN_BOOKSHELF_DEADLOCK_REPORT.md` 的死锁结论一起复核后再开启。

### 1.2 【P2】`required: true` + `defaultRequire: 1` 的「全有或全无」

```json
{ "required": true, "injectors": { "defaultRequire": 1 } }
```

26.x 上游仍在频繁改 `AnvilMenu#createResult`、`EnchantmentMenu#getEnchantmentList`、`GrindstoneMenu#computeResult`、`ReloadableServerRegistries#reload` 等签名。任何一个改名都会**启动硬崩**，而不是降级。建议：仅对「不影响世界数据一致性」的注入显式写 `@Inject(..., require = 0)`（例如朝向启发式、书架粒子），核心经济类保持 `require = 1` 并在 `onInitialize` 里打一次版本自检日志。

### 1.3 【P2】死代码：第二份 `AddRandomTick`

`mixin/bookshelf/AddRandomTick.java`（已注册，`extends BaseEntityBlock` + `@Override`）与 `mixin/chiseled_bookshelf/AddRandomTick.java`（未注册）目标类完全相同，且后者：

- 没有 `@Override`、没有 `@Inject`，只是两个同名同签名方法；
- `isRandomlyTicking(state)` 无条件 `return true`（连空格书架也随机 tick），与已注册版「仅占用槽位才 tick」的性能策略相悖。

若将来有人把它加进配置，会出现同目标双重注入/语义冲突。建议直接删除 `mixin/chiseled_bookshelf/` 整个包。

### 1.4 【P2】其它注入风险

| 位置 | 问题 |
|---|---|
| `mixin/grindstone/GrindstoneBookDepleteMixin.java:88-125` | 在 `GrindstoneMenu` 构造器 `RETURN` 处替换 `slots.set(GrindstoneMenu.RESULT_SLOT, new Slot(this.resultSlots, 2, ...))`：同时硬编码了结果槽索引 2 与容器槽索引 2，对 vanilla 内部结构强耦合；`depleted_tome` 产出**没有配置开关**（即使 `tome` 经济被关掉，磨石仍产出褪魔残卷）。 |
| `mixin/anvil/RemoveEnchantMerge.java:62-72` | `createResult` HEAD 里把左槽 `setCount(1)`，RETURN 再复原。若 `createResult` 中途抛异常/被其它 mod 取消，左槽会**永久停在 count=1**（潜在的「复制/吞物品」）。建议 try/finally 或改用 `@ModifyVariable`。 |
| `mixin/anvil/*.java` | 两处都用 `ModRuntimeConfig.effective()`（可能读到 `remoteServerInstance`）。在集成服务器/单人环境里，客户端同步下来的 remote 值会参与服务端判定，语义含糊；应显式用「服务端本地值」或 `get()`。 |
| `mixin/enchanting_table/DisableBookEnchanting.java:70-83` | HEAD 里 `mode==ALL || stack.is(Items.BOOK)` → 返回空列表。`is(Items.BOOK)` 对 `ENCHANTED_BOOK` 为 false（OK），但此判定只覆盖 `minecraft:book`，若数据包/模组另有「空白书」等价物则不会被拦。 |
| `ChiseledEnchantingTableScreenHandler.java:2425-2429` | 用 `EnchantmentHelper.updateEnchantments(m -> m.set(k, 0))` 把附魔等级置 0 来「清空」。若 26.x 的 `ItemEnchantments`/codec 校验 `level >= 1`，0 级条目会残留在探针栈里（虽然是 `copyWithCount(1)` 的临时对象，但同样被 `isEnchantable()` 判定路径消费）。建议 `m.removeIf(...)`。 |

---

## 2. 数据编解码

### 2.1 【P1】seconds↔ticks 往返破坏数据包精度

`ChiseledEnchantingTableData.applyRuntimeConfig` 附近的映射：

```java
d.epiphanyCycleSeconds        = rawTreasureEpiphanyRules.cycleTicks() / 20;        // 整数除，向下取整
d.epiphanyFreezeDurationSeconds = rawTreasureEpiphanyRules.freezeDurationTicks() / 20;
d.epiphanyMaxFreezeSeconds    = rawTreasureEpiphanyRules.maxFreezeTicks() / 20;
// 回写时：
int frzTicks = Math.max(200, cfg.epiphanyFreezeDurationSeconds() * 20);   // line 830
Math.max(200, cfg.epiphanyCycleSeconds() * 20)                            // line 843
```

后果（静默，无日志、无告警）：

- `treasure_epiphany.json` 里 `cycle_ticks: 250` → 12 s → 回写 240 ticks；
- `freeze_duration_ticks: 100` → 5 s → 回写 `max(200, 100)=200` ticks（**翻倍**）；
- 任何非 20 倍数的 tick 值都被截断。

**修复**：运行时配置直接以 tick 为单位（`epiphanyCycleTicks`），或在映射时保留原始 tick 值并把 seconds 仅作为 UI 显示单位。

### 2.2 【P1】`tier3_batch_size` 被运行时配置覆盖（双源真值）

```java
// ChiseledEnchantingTableData.java:692
d.transformationMaxBatchSize = rawShelfGatesConfig.baseBatchSize();
// :747,760-761
int batchCap = cfg.transformationMaxBatchSize();
new BookshelfProgression.Config(..., batchCap, cfg.transformationMaxBatchSize(), ...);
//                                    ↑ base_batch_size   ↑ tier3_batch_size
```

`shelf_gates.json` 的 `depleted_tome_gradients.tier3_batch_size`（默认 64）**永远不会进入运行时**：两个位置都被运行时键 `transformation_max_batch_size` 填充。而且该键语义是「物品转化批量上限」（clamp 1..64），却被复用为「褪魔残卷批量」（`ChiseledEnchantingTableBlockEntity.canPlaceItem` 用 `max(baseBatchSize, tier3BatchSize)` 决定可堆叠量）。

**修复**：给 `ModRuntimeConfig` 分别加 `baseBatchSize` / `tier3BatchSize` 两个键（或明确合并为单键并删除 JSON 里的 `tier3_batch_size` + 在 `shelf_gates.json` 注释里说明），避免「键存在但无效」。

### 2.3 【P2】`ItemTransformation.level` 与成本计数字段无上限

`ChiseledEnchantingTableData.ItemTransformation.fromJson`（:1394-1447）只做 `Math.max(1, level)`，没有上界：

- `ItemTransformationApplication.apply`（:56）`int totalProduced = batchCount * Math.max(1, rule.level())` → datapack 写 `"count": 2000000000` 时 int 溢出（负数量）或生成超大 `ItemStack`；
- `ItemCostOption.fromJson`（:1821）的 `count/countPerLevel/min/max/base/maxLevelCount` 全无上下界，`expandBundles` 直接 `new ItemStack(primary, computedCount)`；
- `extras` / `additional_costs` 递归**没有深度与数量限制** → 恶意的深层 JSON 可造成 `StackOverflowError`（Gson 解析本身也会递归）。

**修复**：`level` clamp 到 `[1, 64]`（或 `[1, maxStackSize]`），成本计数 clamp 到 `[0, 4096]`，`extras.size() <= 8`、递归深度 <= 4。

### 2.4 【P2】`QueuedInscription` 编解码不对称

`InscriptionRitualMechanics`：

- `encode()`（:400-417）对 `paidItems` 数量**不设限**；
- `decode()`（:440-462）遇到 `entries.length > 16` 直接 `return null` → `deserializeQueue` 抛 `IllegalArgumentException`。

当成本包来自 `additional_costs`（无上限）且 >15 项，一次成功入队（`canQueue` 只按 `serializeQueue().length()+1024 <= 32000` 估算）的仪式在**保存并重载后会被判为 corrupt**，进入 quarantine（见 4.1）。

**修复**：`encode()` 侧与 decode 同源限制，或在 `canQueue` 里按 `paidItems.size()` 硬性拒绝；两者共用一个 `MAX_PAID_ITEM_ENTRIES` 常量。

### 2.5 【P2】`LootInjection` 的「tag」其实是路径前缀

`EnchantedBookLootInjector.matches`（:107-114）：

```java
if (injection.isTag()) {
    Identifier tag = injection.tableId();
    return tableId.getNamespace().equals(tag.getNamespace())
        && tableId.getPath().startsWith(tag.getPath());
}
```

- 名字叫 `isTag`，但语义是前缀匹配（`loot_injections.json` 里大量 `chests/` 之类的前缀）；
- 命名空间必须相等 → **任何模组命名空间都无法用前缀注入**（只能逐条精确 id）；
- `"chests/simple_dungeon"` 会同时命中 `chests/simple_dungeon_extra` 之类，前缀写短了会命中整片（例如空路径命中全部）。

**修复**：字段改名 `is_prefix`/`table_prefix`，并做向后兼容读取；或真正支持 `#tag` 语法（读取 `Registries.LOOT_TABLE` tag / `TagKey`）。同时在加载期拒绝空前缀。

### 2.6 【P2】`default_enchantments.json` 与 Java 回退漂移（P1 影响的直接证据）

| 数据 | JSON（`default_enchantments.json`） | Java 回退 |
|---|---|---|
| `values` | `[]` | `Set.of()` ✅ |
| `tags` | `#minecraft:in_enchanting_table` level 1 | 硬编码同名 ✅ |
| `exclude` | `["minecraft:infinity"]` | **`Set.of()` ❌** |
| `exclude_tags` | treasure / curse / `#chiseled_enchanting_table:epiphany_exclusive` | 硬编码同名 ✅ |

`builtInExcludedTreasureIds()`（`ChiseledEnchantingTableData.java:1286`）返回空集合。触发路径：数据包**删除** `default_enchantments.json`，或提供 `values`/`tags` 都为空的文件（`loadDefaultEnchantments` 的 `loadedValues.isEmpty() && loadedTags.isEmpty()` 会走到回退）→ **`minecraft:infinity` 变成默认解锁附魔**，违背「Infinity 只来自探索战利品/原版附魔台/秘典」的设计铁律。

**修复**：回退应直接 `readBundledJsonObject("default_enchantments.json")`（本文件里 `builtInItemTransformations()`、`TreasureEpiphanyRules.builtInFallback()` 已经是这么做的好范例），彻底删掉手写常量。

---

## 3. 网络同步

### 3.1 【P1】预算按「条目数」而不是「字节数」

`menu/PayloadLimits.java`：

```java
MAX_COLLECTION_ENTRIES = 65_536;
MAX_TOTAL_COST_STACKS  = 131_072;
```

`EnchantCostTablePayload.build` 会对 `1..min(255, maxLevel)` × 每个附魔 × 最多 4 种 OptionType 生成行（`TREASURE_EPIPHANY / ENCHANTMENT / STRIP_SINGLE / PURGE_CURSE`，≥2 级再加 `BOOK_COMBINE / BOOK_RESEARCH`）。原版 ~42 附魔、maxLevel=255 的整合包场景：

- 行数 ≈ 255 × 42 × 4 ≈ **42 000+**，每行还可能带多个 `ItemStack`（含完整 DataComponents）；
- 仅 VarInt 计数就有数万字节，加 `Identifier` 与组件后很容易越过 Netty/`PacketDecoder` 的 **2 097 152 字节** 上限 → 客户端 `Badly compressed packet` / 直接断连；
- 现有 `truncated` 分支只打一条 `LOGGER.warn`，**客户端 UI 无任何提示**（缺行 = 静默功能缺失）。

**修复**：
1. 改为字节预算：编码用 `buf.writerIndex()` 前后差做软上限（例如 ≤ 512 KiB），超限即截断并置 `truncated=true`；
2. 把 `truncated` 放进 payload 让客户端显示「部分数据被截断」；
3. 在 `NetworkCompatibilityTest` 增加一个「极端表」用例，断言 `buffer.readableBytes()` 上界（目前只做 round-trip 断言，见 `src/test/.../NetworkCompatibilityTest.java`）。

### 3.2 【P1】`getUpdateTag()` 把整个方块实体发给客户端

```java
@Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
    return this.saveCustomOnly(registries);          // BlockEntity.java:649
}
@Override public Packet<...> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
```

同步内容包含：2 格物品栏、`ritualQueue`（最长 **32 000 字符**）、`ritualOwnerUuid`、`ritualTarget` 物品栈。后果：

- 每次区块加载 / `sendBlockUpdated` 都下发全量 NBT，区块里桌子越多越可观；
- 仪式队列与 owner UUID 属于**服务端权威状态**，不需要广播给所有客户端（仅 owner 的菜单需要，且已有 `SyncRitualStatePayload`）。

**修复**：`getUpdateTag` 只写渲染/视觉字段（`color`、`epiphanySeed`、`epiphanyFreezeUntil`、`CustomName`），物品栏与仪式状态走 `saveAdditional`（磁盘）与专用 payload（网络）。

### 3.3 【P2】每 20 tick 全服重建

`EnchantCostSync.init()`：

```java
if (server.getTickCount() % 20 != 0) return;
for (ServerPlayer player : ...) {
    var displays = AvailableEnchantmentPayload.buildTransformationDisplays(player);
    if (!displays.equals(LAST_DISPLAYS.get(player))) sendTo(player);   // sendTo 里再 build 一整张 cost 表
}
```

- 每 20 tick 对所有在线玩家各做一次「transformation 列表构建 + 深比较」；
- `LAST_DISPLAYS` 是 `WeakHashMap`（**非线程安全**），写点分布在 JOIN / END_SERVER_TICK / DISCONNECT / reload 路径；
- 只有在 `transformationDisplays` 变化时才需要重发，可用 `ChiseledEnchantingTableData` 的 reload 版本号做脏标记，而不是轮询。

### 3.4 【P2】协议版本化不一致

`menu/EnchantingProtocol.java` 的 `REVISION = "26_3_revision_8"`，但：

| 通道 | 是否带 revision |
|---|---|
| `enchant_cost_table_<rev>`、`available_chiseled_enchantment_<rev>`、`live_shelf_state_<rev>`、`sync_ritual_state_<rev>`、`skip_ritual_<rev>`、`protocol_<rev>` | ✅ |
| `apply_chiseled_enchantment`、`reroll_epiphany`、`freeze_epiphany`、`reopen_table` | ❌ |

C2S 的 `ApplyEnchantmentPayload` 若将来加字段，旧客户端会以同名通道发旧布局 → 服务端解码错位（而非被 revision 隔离）。同时客户端 JOIN 的「对端是否有本模组」探针用的正是未版本化的 `APPLY_CHISELED_ENCHANTMENT_PAYLOAD_ID`。

**修复**：要么把「所有自定义 payload 通道一律带 revision」，要么在 `EnchantingProtocol` 里显式列出「稳定通道白名单」并加注释说明字段不可变。

---

## 4. 机制 / 状态机

### 4.1 【P1】仪式 quarantine 是单向门

`ChiseledEnchantingTableBlockEntity`：

- `ritualBlocked` 在两类事件后置真：目标物与 `ritualTarget` 不一致（`completeActiveRitual`）或存档解析出 corrupt 队列（`loadAdditional`）；
- `serverTick` 只在 `hasActiveRitual() && !ritualBlocked` 时推进；
- 唯一恢复路径是 `cancelRitual(actor)`，其前置 `ownsRitual(actor)`：
  ```java
  public boolean ownsRitual(UUID actor) { return !hasActiveRitual() || actor.equals(this.ritualOwnerUuid); }
  ```
  → `ritualOwnerUuid == null` 时对任何玩家都返回 false（`actor.equals(null)` = false）。

因此：**旧存档/异常丢失 owner UUID + ritualBlocked = 该桌永久卡死**，玩家无法取出物品，`/cet cancel-ritual` 也走 `table.cancelRitual(player.getUUID())` 被同一判定挡住；只能 `/setblock` 或改 NBT。

**修复**：
1. `ownsRitual` 在 owner 为 null 时允许「任何玩家」或「OP」取消（把安全性交给命令权限）；
2. 给 quarantine 加超时（例如 5 游戏日）自动解封并把残留队列丢进 `Containers.dropContents`；
3. `/cet cancel-ritual` 增加 `PermissionLevel.GAMEMASTERS` 的强制分支。

### 4.2 【P2】`batchSize` 语义混淆

`canPlaceItem(ENCHANTABLE_SLOT)` 用 `max(baseBatchSize, tier3BatchSize)` 决定褪魔残卷可堆叠数，而同一个 `transformationMaxBatchSize` 又决定转化批量上限（`resolveTransformationBatchCount`）。两个不同机制共用一个运行时键（见 2.2），任何一侧调参都会「顺带」改变另一侧。

### 4.3 【P2】`MAX_RITUAL_STEPS = 256` 与实际批量上限不匹配

`resolveTransformationBatchCount` 的 `maxCap = transformationMaxBatchSize()`（clamp 1..64），而队列允许 256 步。一次点击最多排 64 步，`canQueue` 的 256 步预算与 `MAX_RITUAL_CHARACTERS` 预算在多步叠加时才会触发——两套上限没有统一推导，容易在调参后互相打架（例如 64 步 × 每步 1024 字符 = 65 536 > 32 000，实际会在中途抛 `IllegalStateException: Ritual admission rejected`，而 UI 可能已经扣了 XP，见 4.4）。

### 4.4 【P2】扣费与入队顺序（需实测）

`EnchantmentApplication.apply` 先 `applyEnchantementConditionWithReceipt(payload)`（扣 XP/材料），再 `enqueueInscription(...)`；后者可能抛 `IllegalStateException("Ritual admission rejected")`（长度/步数/退款预算）。若 `canQueue` 的估算与实际 `encode().length()` 不一致（见 2.4），玩家**材料已扣但仪式未入队**。建议把 `canQueue` 的检查改为对「即将 encode 的真实字符串」判定，或在 `enqueueInscription` 失败时回滚 receipt。

---

## 5. GUI / 输入

### 5.1 【P1，需实测】配置覆盖层暂停导致保存 ACK 自锁

```java
// ChiseledEnchantingConfigScreen.java:165
@Override public boolean isPauseScreen() { return true; }
// ChiseledEnchantingTableScreen.java:135
@Override public boolean isPauseScreen() { return this.configOverlay != null && this.configOverlay.isPauseScreen(); }
```

在单人（集成服务器）里，`isPauseScreen()=true` 会让 **集成服务器停止 tick**。而保存流程 `saveAndApply(true)` → `UpdateRuntimeConfigPayload` → 服务端 `applyAndBroadcast` → `SyncRuntimeConfigPayload(accepted=true)` 的 ACK 依赖服务器 tick/网络处理。若暂停期间服务器不处理包，UI 只能超时显示「保存失败」（`lastSaveFailed`）。

需实测确认；若成立，建议：覆盖层 `isPauseScreen()=false`（与 `ArcaneGrimoireScreen:232` 保持一致），或仅在 `SessionMode.OFFLINE_CLIENT`（无服务器）时暂停。

### 5.2 【P2】配置屏数字键硬编码分类数量

```java
if (key >= InputConstants.KEY_1 && key <= InputConstants.KEY_6) {
    this.activeCategory = CategoryId.values()[key - InputConstants.KEY_1];   // :827-829
}
```

- `CategoryId` 一旦新增/删除分类，直接 `ArrayIndexOutOfBoundsException`；
- 无条件吞掉 1–6 数字键（该屏 `render` 里另有 `cats = CategoryId.values()` 的正确写法，见 :332）。

**修复**：`int idx = key - KEY_1; if (idx >= 0 && idx < CategoryId.values().length) ...`。

### 5.3 【P2】布局常量全是魔法像素

`ChiseledEnchantingTableScreenHandler`（:270-280）与各 Renderer 里的 `inventorySlotStartX=172 / enchantableSlotX=183 / costSlotX=183 / SLOT_SIZE=16 / ...` 直接与 `gui/chiseled_enchanting_table.png` 的像素尺寸耦合，没有任何集中常量或「贴图尺寸断言」。换贴图即错位，且无法在运行时发现。

同一处还有：

```java
public static final Identifier SLOT_TEXTURE = Identifier.withDefaultNamespace("textures/gui/sprites/container/slot.png");
```

硬编码**原版**资源路径——上游改图集即断（26.x 的 GUI sprite 迁移已发生过一次）。

### 5.4 【P2】覆盖层的鼠标分派不一致

`ChiseledEnchantingTableScreen.mouseClicked`（:354-364）在 overlay 存在时无条件 `return true` 并**丢弃 overlay 的返回值**；`mouseScrolled`/`mouseDragged`/`mouseReleased` 则转发返回值。行为不对称，容易出现「overlay 认为没处理、父屏也不处理」的按键黑洞。建议统一为 `return overlay.mouseXxx(...)` 或 `if (overlay.mouseXxx(...)) return true;`。

---

## 6. 死代码清单（实测统计）

用脚本统计「全仓出现次数 ≤ 2 的 `public static` 成员」，剔除文档提及后，确认**仅声明、无调用**的项：

| 成员 | 位置 | 备注 |
|---|---|---|
| `MAX_COLLECTION` | `menu/PayloadLimits.java` | `MAX_COLLECTION_ENTRIES` 的重复别名 |
| `MAX_XP_DISCOUNT_PERCENT` | `block/ChiseledEnchantingTableBlock.java:381` | 折扣已完全由 `LibraryResonance.Config` 驱动 |
| `POWER_PROVIDER_OFFSETS` | 同上 | 私有 `getScanOffsets` 才是真实现 |
| `TICKS_PER_GAME_DAY` / `SECONDS_PER_GAME_DAY` | `mechanics/ritual/InscriptionRitualMechanics.java:69-70` | 无引用 |
| `activeConfig()` | `LibraryResonance.java:675` | `activeServerConfig()` 的别名 |
| `additionalItemCosts` / `bookResearchPrimaryCost` / `bookResearchAdditionalCosts` / `transformationPrimaryCost` / `transformationAdditionalCosts` / `transformationEnchantmentsFor` / `treasureEpiphanyChance` / `treasureEpiphanyMaxSlots` | `ChiseledEnchantingTableData` | 全套「单参便捷重载」，均无调用 |
| `hasShelfLineOfSight` | `ChiseledEnchantingTableBlock.java` | 只是 `hasClearLineOfSight` 的转发 |
| `checkAllEnchantBook` | `utils/Advancement.java` | 转发 `checkBookMilestones` |
| `rarityMultiplier` / `remainingCharges` / `doesMatchingMasterTomeHaveInfinity` / `applyShelfWear` / `applyDepletedTomeXpTier` / `forBook` / `forItem` | 多处 | 重构后遗留 |
| `BookshelfReplacerProcessor.REPLACEMENT_ENABLED` | :72 | 有引用但**恒为 `true`** 的遗留开关，且与 `ChiseledEnchantingTableData.bookshelfReplacementEnabled()` 重复控制同一件事（两处都能关掉替换，行为叠加） |
| `ModBootstrapConfig` | 全类 | 有引用但不是死代码：`load()`/`get()` 都 `new` 一个新对象再转发到 `ModRuntimeConfig`，属纯包装（可内联） |
| `mixin/chiseled_bookshelf/AddRandomTick` | 整类 | 见 1.3 |
| `LevelChunkPostProcessMixin` / `StructurePieceMarkMixin` / `StructurePieceAccessor` | 整类 | 见 1.1（当前等价死代码） |
| `postProcessBookshelf` / `postProcessFilledBookshelf` | `BookshelfReplacerProcessor` | 见 1.1 |

建议：能删就删；暂时保留的（如 `MAX_SACRIFICE_XP_LEVELS` 等只在 `Config.DEFAULT` 里用的常量）标注「仅作默认值」，避免下次误以为是运行时真值。

---

## 7. 耦合风险

### 7.1 【P2】方块 → 菜单的反向依赖

`ChiseledEnchantingTableBlockEntity` 直接 `import` 并引用：

- `ChiseledEnchantingTableScreenHandler.ENCHANTABLE_SLOT / COST_SLOT`（:124, :159, :229…）
- `ChiseledEnchantingTableScreenHandler.findEnchantment(...)`（:314）
- `ChiseledEnchantingTableScreenHandler` 类型本身（`syncOpenMenusWatchingTable` 之外的分布）

`block` 层不应依赖 `menu` 层：把槽位常量下沉到 `block`/`registry` 侧，`findEnchantment` 移到 `utils`。

### 7.2 【P2】服务端配置的隐式入口

`LibraryResonance.activeServerConfig()` 被 `XpCostConversion` 的一组**单参重载**隐式调用：

```java
public static XpRequirement forMasterTomeEnchant(int level, int maxLevel, int rawCost, int discountPercent) {
    return forMasterTomeEnchant(LibraryResonance.activeServerConfig(), ...);   // :219
}
```

这些重载**目前在客户端/服务端都无调用**（真正的调用点都显式传 `SyncedEnchantmentCosts.*` 或 `handler.libraryResonanceConfig()`）。也就是说：这是一个**随时会被踩的坑**——任何人图省事用单参版本，在专用服务器的客户端上就会用本地 jar 的默认值算 XP，与服务端不一致。建议把单参重载全部删除或标 `@Deprecated(forRemoval = true)`，并在类注释里指向 `configForSide(boolean)`。

### 7.3 【P2】书架扫描被重复执行

`ChiseledEnchantingTableBlock.createChiseledEnchantingTableMenuProvider`（:282-296）：

```java
ShelfPower shelfPower = calculateShelfPower(level, pos);                 // 第 1 次
Set<EnchantmentWithLevel> unlockedEnchantments = this.get_unlocked_enchantements(state, level, pos);  // 内部又 calculateShelfPower(...) 第 2 次
```

`calculateShelfPower` 每次要遍历 `(2*3+1)²*(2*2+1)-1 = 244` 个偏移、`hasChunkAt` + `getBlockState` + `getBlockEntity` + 对每个雕纹书架 `EnchantmentFinder.streamAllEnchantements`，再叠加 `MasterTomeWear.snapshotLiveMasterCharges`。开菜单 = 2 遍；`broadcastChanges` 每 20 tick 再一遍（`computeLiveShelfState`）。

另外 `streamLibraryPositions(...).toList()` 每次都把 244 个 `BlockPos` 复制成 List（`getScanOffsets` 已经缓存了 `List<BlockPos>`，`.toList()` 是多余的一次分配）。

**修复**：`createChiseledEnchantingTableMenuProvider` 复用同一个 `ShelfPower` 传给 `getUnlockedEnchantmentsFromPower`；`streamLibraryPositions` 直接返回 `List` 而不是再 `.toList()`。

### 7.4 【P2】书架随机 tick 里的 245 次方块查询

`block/ChiseledBookshelfTick.findNearestChiseledTable`：对每次随机 tick，在 7×5×7 范围里逐格 `hasChunkAt` + `getBlockState`。而 `AddRandomTick.isRandomlyTicking` 只要**任意槽位有书**（不限附魔书）就返回 true。大型图书馆（数百个雕纹书架）在默认随机 tick 速率下是明显的持续开销。

**修复**：只在「槽位里确实是附魔书」时才 tick（`isRandomlyTicking` 无法读 BE，可退化为在 `randomTick` 里先判附魔书再找桌子——现在已经是先抽槽判附魔书，但仍然每 tick 抽一次）；或把「最近桌子」缓存到 `ChiseledBookShelfBlockEntity` 的临时字段（区块卸载即失效）。

### 7.5 【P2】战利品注入两条路径

- `EnchantedBookLootInjector.init()`（`loot_injections.json` 驱动）
- `DepletedTomeWorldMechanics.initChestLootInjection()`（Java 硬编码 `EXACT_CHEST_SPECS` 驱动）

两者都在 `LootTableEvents.MODIFY` 且都只对 `source.isBuiltin()` 生效。若数据包**覆盖**了某张 chest 表（很常见的整合包做法），这两条注入会同时静默消失，而 `vanilla_chest_books` 的 `STRIP_VANILLA` 语义又只在「本模组匹配到该表」时生效 —— 行为随数据包而变，且没有任何日志提示。

---

## 8. 数值 / 机制设计合理性

### 8.1 【P1】经济与战利品数值硬编码在 Java

`mechanics/loot/DepletedTomeWorldMechanics.java` 把整套「世界战利品经济」写死：

| 常量/结构 | 值 | 位置 |
|---|---|---|
| `MAX_WILD_BOOKSHELF_REPLACEMENT_PROBABILITY` | 0.30 | :125 |
| `WEATHERED_GRADE_WEIGHTS / PROBS` | {0.35,0.45,0.20} / {0.08,0.20,0.30} | :128-129 |
| `EXACT_CHEST_SPECS` | 逐箱子概率与数量（stronghold_library 0.55 等） | :176+ |
| `RECYCLE_*` | 2 残卷 → 1 绿宝石 / 2 紫水晶 | :255-257 |
| `BASE_COMMISSION_*` / `MAX_COMMISSION_*` | 4 青金石 / 2 墨囊 / 1 绿宝石，上限 24/14/18 | :260-272 |
| `VILLAGER_LEVEL_*_THRESHOLD` | 0.065 / 0.120 / 0.235 / 0.500 | :288-291 |
| `MIN_TRIPLE_PRODUCT` | 0.25×0.35×0.10 | :293 |

同类问题：

- `XpCostConversion`：门槛曲线 `8.0 + 22.0 * fraction`（3 处重复）、`remainingFraction` 下限 `0.15`、转化 fallback `vanillaTierDeduction(raw, 30)` 的 `30`、`r<=0.40 / 0.75` 的三档阶梯；
- `DisenchantmentApplication`：`baseMax = maxLevel==1 ? 34 : 48`、`pow(r, 1.65)`、`rarity = 1+0.18*ln(anvilCost)`（cap 1.45，treasure ×1.15）、`CONSERVATION_SAFETY_MARGIN_POINTS = 5`；
- `ItemCostOption.computeCount`：`span = min(1.30, 0.52 + 0.096*maxLevel)`、`maxLevel==1` 时的 `0.45`、默认指数 `1.30`；
- `InscriptionRitualMechanics.TomeCandidate.preservationScore`：`0.40 * count/5 + 0.35 * rel + 0.25 * rarity`。

这些数字有详尽注释解释「为什么是这个值」，但**玩家/整合包无法改**，且与仓库自己反复强调的「N1 单一数据源、内容进 JSON」原则相冲突（`library_resonance.json` 里连 `crossline_slip` 的细枝末节都数据化了，却没覆盖上面这些）。

**建议**：至少把「世界战利品经济」（§8.1 的箱子表 + 村民价格/等级阈值）抽到 `loot_injections.json` / 新增 `world_economy.json`；把 `8+22f`、`0.15`、`30`、`1.65`、`34/48`、`5` 收进 `library_resonance.json` 的 `economy_curve` 段并进 `EnchantCostTablePayload`（客户端 tooltip 需要一致）。

### 8.2 【P2】成就阈值与配置脱钩

```java
// ChiseledEnchantingTableBlock.java:315
if (shelfPower.xpDiscountPercent() >= 22 || shelfPower.peakEffectiveXpDiscountPercent(level) >= 35) {
    Advancement.give(player, "library_resonance");
}
```

`max_wooden_discount_percent` / `max_total_xp_discount_percent` 可调（clamp 0..90），但成就阈值写死 22/35 → 调低上限后成就不可达，调高后过早达成。建议阈值 = 「上限的固定百分比」或进配置。

### 8.3 【P2】库共振口径易误读

`library_resonance.json` 的 `tome_regen_tier2_shelves: 42` 大于 `shelf_gates.json` 的 `max_bookshelves: 38`。二者口径不同：

- 38 = `maxDirectBookshelves`（**常规书架**的直接升级门）；
- 42 = 再生用的 `totalLibraryBookshelves()`（**广义**书架 = 常规 + 空雕纹 + 有书雕纹，见 `ShelfPower.totalLibraryBookshelves`）。

数值本身可达，但 `MasterTomeWear` 的类注释（:34-46）与 `ModRuntimeConfig` 的 UI 标签都写作「38/42 shelves」，容易被理解成同一口径。建议在 UI/文档统一写「广义书架」。

### 8.4 【P2】`epiphany_exclusive` 与 `#minecraft:treasure` 重复

`tags/enchantment/epiphany_exclusive.json` 里 `frost_walker / soul_speed / swift_sneak / wind_burst / mending / silk_touch` 等本就是 `#minecraft:treasure` 成员（`default_enchantments.json` 已 `exclude_tags: ["#minecraft:treasure"]`）。重复列举的价值是「即使 treasure 标签变动也保持独占」，但代价是 vanilla 调整 treasure 成员后两处会漂移。建议保留并在文件里写明「这是防漂移的红线清单」，或者只用 `#minecraft:treasure` + `infinity`。

### 8.5 【P2】`crying_obsidian` 配方偏强

`data/.../recipe/crying_obsidian.json`：8 黑曜石 + 1 恶魂之泪 → **8** 哭泣黑曜石（无损转换），而原版哭泣黑曜石不可合成、需要猪灵交易/废弃传送门。考虑到「冻结 Epiphany」的成本选项里含 `crying_obsidian ×2`（`treasure_epiphany.json`），这个配方实际上把「冷冻」的门槛降到了「有黑曜石农场即可」。属设计取舍，但建议在 `docs/reports` 里记录为有意为之，或把产出降到 4。

### 8.6 【P2】`shelf_gates.json` 的 `regular_bookshelves` 标签是冗余

```json
// tags/block/regular_bookshelves.json
{ "replace": false, "values": ["#minecraft:enchantment_power_provider"] }
```

而 `ChiseledEnchantingTableBlock.isRegularBookshelf` 已经是：

```java
!state.is(Blocks.CHISELED_BOOKSHELF) && (state.is(REGULAR_BOOKSHELVES_TAG) || state.is(BlockTags.ENCHANTMENT_POWER_PROVIDER));
```

标签内容与代码里的第二个分支**完全等同** → 该标签目前无实际作用（除非数据包改它，但代码又无条件接受 `ENCHANTMENT_POWER_PROVIDER`）。要么让代码只认标签（这样数据包才能排除某些方块），要么删掉标签。当前形态既不能增值、又制造「改了没用」的错觉。

---

## 9. 建议的落地顺序

1. **P0**：把 3 个 structure mixin 注册（或删掉整条链路 + 同步文档/CHANGELOG，避免宣称与行为不符）。
2. **P1**：`getUpdateTag` 精简；payload 字节预算；seconds/ticks 改 tick 直存；`tier3_batch_size` 双源问题；quarantine 兜底取消；`default_enchantments` 回退改用 bundled JSON。
3. **P1（需实测）**：单人 Config 覆盖层暂停下的保存 ACK。
4. **P2**：死代码清理（§6）、单参重载删除（§7.2）、开菜单双重扫描（§7.3）、书架随机 tick 缓存（§7.4）、配置屏按键越界（§5.2）。
5. **设计**：把 §8.1 的经济数值抽表；§8.2 成就阈值跟配置联动。
6. **CI**：给 `scripts/ci_static_checks.py` 加 Tier A2（源码 → mixins.json 反向检查）与「数据包键被运行时覆盖」的白名单测试；给 `NetworkCompatibilityTest` 加极端表字节上限断言。

### 附录：Tier A2 反向检查参考实现

```python
def check_unregistered_mixins() -> None:
    """Every @Mixin class under a config's package must be listed in that config."""
    for cfg_path in registered_mixin_configs():
        data = read_json(os.path.join(REPO, "src", "main", "resources", cfg_path))
        package = data.get("package", "").replace(".", os.sep)
        declared = {
            f"{package}/{m.replace('.', os.sep)}.java".replace(os.sep, "/")
            for section in MIXIN_SECTIONS
            for m in (data.get(section) or [])
        }
        base = os.path.join(REPO, "src", "main", "java", package)
        for root, _dirs, files in os.walk(base):
            for name in files:
                if not name.endswith(".java"):
                    continue
                rel = os.path.relpath(os.path.join(root, name), os.path.join(REPO, "src", "main", "java")).replace(os.sep, "/")
                text = open(os.path.join(root, name), encoding="utf-8").read()
                if "@Mixin(" in text and rel not in declared:
                    fail(f"{rel} declares @Mixin but is not listed in {os.path.basename(cfg_path)}")
```

（按当前仓库状态跑这段会精确报出 4 个未注册的 `@Mixin` 类：`structure/LevelChunkPostProcessMixin`、`structure/StructurePieceMarkMixin`、`structure/StructurePieceAccessor`，以及死代码 `chiseled_bookshelf/AddRandomTick`。）

# 26.3 rev12 -> 26.2 全量回移植 + 七路径彻查（2026-10-27）

> 本分支为非官方社区移植。目标版本 **1.1.0 / Minecraft 26.2 / Fabric Loader 0.19.3 / Fabric API 0.155.2+26.2 / Java 25**。
> 本文记录把 26.3 rev12 (`b3cb3c9`) 最新功能批次**真实落地**到本线，并按「源码 API / 数据编解码 / 网络同步 / GUI 输入 /
> Mixin 注入 / 死代码 / 耦合风险」七条路径逐条源码级彻查，修正本线独有的 BUG 与回移植文档遗漏点。所有结论以真实源码差分 +
> 真实远程 CI 编译为准，不以文档清单代替源码差分；运行时/客户端项如实列为待人工验证。

## 0. 三条线拓扑与母本选择（实测）

| 线 | ref | MC | wire | main-java | client-java | test | lang(en_us) | mixin | 角色 |
|---|---|---|---|---|---|---|---|---|---|
| 本线 26.2 (回移植前) | `d55e824` | 26.2 | `26_2_revision_6` | 69 | 28 | 5 | 621 | 9 | 本次起点 |
| 26.1.2 线最新 | `fbcd0fc` | 26.1.2 | `26_1_2_revision_18` | 116 | 59 | 18 | 1019 | 14 | **本次母本** |
| 26.3 线（"更新"源） | `b3cb3c9` | 26.3 | `26_3_revision_12` | 116 | 59 | 17 | 1019 | 14 | 结构 / Mixin / API 参考 |

母本 `fbcd0fc` 本身就是 26.3 rev12 的 CI 全量回移植，与本线共享「复数 loot schema / recipe_id / ConstantValue /
2 参 drop / per-block codec / ValueInput-ValueOutput / 同款 GUI 输入事件。它内含的七路径修复（RULE/RELOAD/NET/
LIFE/MIXIN/DEAD）随母树一起落地。26.3 专属 API（单数 condition/modifiers、recipes[]、ContextIntProviders、
Prediction、BlockEntityTypes、4 参 reload、ItemModel/SDL/ChatFormatting 等一律不倒灌。

三条线历史各自独立（无共同祖先），故lag用「分层文件同步 + 版本边界点位适配」，不 `git merge`、不盲目 cherry-pick。
保留本线 26.2 版本坐标文件与 26.2 管属结构处理/Mixin 文件；其余业务/数据/网络/配置/资源/脚本/文档按母本同步后逐点改写。

**验证通道**：本沙箱无 JDK/Maven。真实编译由 `compile-check` / `build` 工作流在 `arena/**` push 时执行
（`compileJava compileClientJava test`，JDK 25 + 外网 Maven），并把 `build-reports/compile-java.log` 经 Contents API
回推本分支 —— 这是本线唯一的「真实编译」通道（见 `.github/workflows/compile-check.yml` 头注）。最终结果：
**compile-check 与 build 双双 success**（真实 MC 26.2：162 个 JUnit 全绿、jar 产出、真实服务端启动 + `/reload` 冒烟通过）。
证据见 `build-reports/compile-java.log` / `build-reports/server-smoke.log`。

## 1. 回移植文档的遗漏点（彻查结论）

对照 `SYNC_TO_26_2_AND_26_3_2026_10_26.md` 与 `SYNC_9F0E13B_SEVEN_PATH_AUDIT_2026_10_26.md`。
源码差分发现指南 §2「26.1.2 -> 26.2/26.3」API 矩阵**漏列**多处，若不补，本线必然编译失败或 GUI 失效：

- **D1 (源码 API) `ChatFormatting` 在 26.2 被移除**：母本 19 文件 240 处使用。26.1.2/26.3 都有该枚举，只有 26.2 没有；
  本线原本全部用 `Style#withColor(0xAARRGGBB)`。指南 §2 未列出此项。
- **D2 (源码 API) `StructureProcessor` 在 26.2 是 interface + `codec()`**：指南 §2 把 26.1.2 的
  `extends StructureProcessor` / `getType()` / `StructureProcessorType.register` / 小写 `markPosForPostprocessing` 当作
  26.2「相同」；实际 26.2 为 `implements StructureProcessor` + `codec()` + 大写 `markPosForPostProcessing` +
  `BuiltInRegistries.STRUCTURE_PROCESSOR`。母本 26.1.2 形态照搬会编译失败。
- **D3 (GUI) `setOverlayMessage` 在 26.2 位于 `Gui.hud`**（与 26.3 一致），不是 26.1.2 的 `Gui` 直接调用。
- **D4 (GUI 输入) SDL 文本输入焦点**：指南只提 `setScreen/screen -> gui.*`，未说明 26.2 的 SDL/CharacterEvent 焦点
  归属差异。26.1.2 无 SDL 层（no-op），26.3 有 SDL 层（`onTextInputFocusChange` / `capturesInput`）。本线不确定，
  故以反射自适应桥接，避免把 26.1.2 的「no SDL」假设硬编码进 26.2。
- **D5 (数据/脚本) CI 脚本线专属**：`test_sync_26_1_2.py` 断言 26.1.2 专属 API 形态（与本线冲突）；
  `compat_check.py` 整文件覆盖会丢本线 rev6 的 26.2 专属守卫；`docs/ci/README.md` 引用已删除脚本。
- **D6 (协议) 历史探针区间**：母本用 `IntStream.range(9, N)` 生成 apply 探针（26.3 线专属，N=18）。本线 rev6 只发过裸
  `apply_chiseled_enchantment` 通道，协议 bump 到 7 时 `range(9,7)` 为空 -> 探针数异常。

## 2. 七条路径 — 落地与 BUG 修复

### A. 源码 API
- **保留本线 26.2 版本坐标**：`gradle.properties`、`build.gradle`、`fabric.mod.json`（name/description/depends 仍标
  `Unofficial 26.2 Port` / `minecraft:26.2`）、`README.md`、`publish.yml`、`docs/ci/*`。`check_release_consistency.sh --strict` PASS。
- **保留本线 26.2 结构处理器**：`BookshelfReplacerProcessor`（`implements` + `codec()` + `markPosForPostProcessing`）
  + `StructureProcessorRegistry`（`BuiltInRegistries.STRUCTURE_PROCESSOR`）+ 5 个 `mixin/structure/*`。未采用母本的
  `extends`/`getType()`/`StructureProcessorType` 形态（D2）。
- **`ChatFormatting` 兼容桥（D1）**：新增 `compat/CetFormatting`，以 `Style.EMPTY.withColor(ARGB)` 暴露同名 `Style`
  常量 + `*_ARGB` int 伴随常量。确定性转换器把 240 处 `ChatFormatting.X` 改写为 `CetFormatting.X`；
  `withColor(ChatFormatting.X)`（Style lambda 内）改写为 `withColor(CetFormatting.X_ARGB)`；`ChatFormatting` 类型声明改为
  `Style`；`TextColor.fromLegacyFormat(...).equals(color)` 改为鲁棒的 RGB 低 24 位比较（`.getValue() & 0xFFFFFF`）。
  全仓 `grep ChatFormatting` 仅命中 CetFormatting 与注释。
- **方块/实体持久化**：保留本线 26.2 `ValueInput/ValueOutput`（`loadAdditional`/`saveAdditional`）。
- **BER**：保留 26.2 `poseStack.mulPose` + 10 参 submitModel；未引入 26.3 的 `rotate` / 9 参 / `submitCrumblingOverlay`。
- **玩家丢弃**：保留 26.2 两参 `drop`（无 `Prediction.PREDICTED` 三参）。
- **GUI 屏幕 API**：`Minecraft.getInstance().setScreen/screen` -> `gui.setScreen/gui.screen()`（4 文件 7 处）；
  `setOverlayMessage` -> `gui.hud.setOverlayMessage`（3 处，D3）。`InputConstants.MOUSE_BUTTON_LEFT/RIGHT` = 0/1，与本线 GLFW 裸按钮号一致。
- 全 196 个 `.java` 经 tree-sitter（真实 Java 语法解析器）0 错误；`git diff --check` 无空白错误。

### B. 数据编解码
- 全套 13 张模组 JSON 规则/成本/经典对照/战利品注入表（含 `*_classic_1_0_3.json`）、`tags/**`（9 张含 keystone/wear_immune/crossline/regular_bookshelves 等）。
- 保留 **26.2 复数 `conditions[]`/`functions[]` loot schema**（`chiseled_enchanting_table.json` 方块掉落表）；
  `recipe_crafted` 进度仍用 `recipe_id`（非 26.3 `recipes[]`）。`compat_check.check_advancements_use_26_1_2_schema`
  （26.1.2/26.2 共享 schema 族）PASS，反向守卫能抓 26.3 `recipes` 列表误拷。
- `LibraryResonance.Config` 嵌套 6 个 DFU `Codec` 子记录 + `max_wooden_discount <= max_total_xp_discount` 约束。
- `BundledDefaults` + `gen_bundled_defaults.py --check`（单一事实源）PASS。
- **修 merge 而非覆盖（D5）**：母本 `compat_check.py` 落下后，把本线 rev6 的 `check_loot_function_codecs_keep_conditions`
  （26.2 专属：`MapCodec.unit` 会丢弃 loot function 的 `conditions[]`，必须走 `commonFields(i)`）合并回去，覆盖 3 个
  `LootItemConditionalFunction` 子类，全部 PASS。

### C. 网络同步
- **wire bump 到 `26_2_revision_7`**（D6）：`CURRENT_REVISION_NUMBER = 7`，历史探针统一 `IntStream.range(1, N)`
  （cost + apply 皆然），前缀 `26_2_revision_`，`MISMATCH_MESSAGE` 标注 26.2。裸 `apply_chiseled_enchantment`
  仅作 query-only 旧探针，不在旧通道注册业务解码器冒充兼容。
- **NET-01（本线 rev6 缺陷）**：rev6 `EnchantCostTablePayload` 无 980000 字节上限、无裁剪。随母树落地
  `costTableComplete` + `Unpooled` 实编码字节裁剪 + 头超预算直接抛错（禁止空表上线），`MAX_SAFE_ENCODED_PAYLOAD_BYTES=980000`。
  集合计数走 `PayloadLimits.writeBoundedCount/readBoundedCount`（26.3 已移除 `writeCollection/readList`）。
- **NET-02（本线 rev6 缺陷）**：rev6 `EnchantCostSync.sendTo` 失败仅 warn、留下陈旧价。落地「失败即清 display cache +
  `cost_sync_failed` 断开该客户端」（11 语言新增 `message.chiseled_enchanting_table.cost_sync_failed`）。
- **LIFE（本线 rev6 缺陷）**：rev6 `LAST_DISPLAYS` 为 `WeakHashMap<ServerPlayer,...>`（存活实体强引用，跨世界残留）。
  落地为 `Map<UUID,...>` + `ConcurrentHashMap`，停机/断线按 UUID 清理。粒子 burst 静态表也按 server 清理。
- 仪式队列 <=256 步 / 32000 序列化字符 / <=255 级双向一致；`/reload` 关闭打开的容器并回传新费用/配置。

### D. GUI 输入
- `SearchTextBuffer`（码点游标/选区、剪贴板替换、过滤、长度上限）纯 Java，headless 可测。
- **D4 版本自适应 SDL 桥（本次新修的本线独有 BUG）**：母本（26.1.2）把 `SearchInputSession` 的 native-focus
  回调写成 no-op；若 26.2 存在 SDL 文本输入层，手绘搜索框仅翻私有 boolean，永不触发 `CharacterEvent`，搜索框表现为
  「打不了字」。新增 `NativeTextInputFocus`（置于 client 源集，全反射按方法名解析 `Minecraft#onTextInputFocusChange`，
  缺钩子则 no-op；单源可跨 26.x 编译）。`charTyped(CharacterEvent)` 在本线经真实 CI 编译确认可用；不静态声明
  `capturesInput()`（26.3-only Screen 方法，签名/final 性跨线不同，硬引用会破坏 26.2 编译）。
- 配置保存会话 `ConfigSaveSession` pending 超时（15s）自动解除，避免通道不可用时编辑器永久卡 pending（R7）。
- `MouseButtonEvent.button()` 26.2 GLFW 号；`KeyEvent.key()`/`CharacterEvent.codepoint()` 26.2 形态。
- **待人工验证**：IME、鼠标拖动、缩放布局、JEI/REI 切屏、真实 SDL 文本输入是否启动（取决于 26.2 运行时是否有该钩子）——
  headless CI 不覆盖，如实标注，不冒充已测。

### E. Mixin 注入
- 14 个活跃 Mixin（原 9）。`ci_static_checks` + `check_baseline_mixins` 校验「声明=已编译」+ `required=true` /
  `defaultRequire=1`（`CURRENT_BASELINE.md` 清单已 `--write` 同步，门禁 PASS）。
- **MIXIN-01（本线 rev6 缺陷）**：创造模式书架取书被存活锁定逻辑误伤 —— 新增
  `bookshelf.BookshelfCreativeExtractionMixin`（26.2 目标方法签名 `removeBook`，守卫失败 PASS 回落原版）。
- `ReloadableServerRegistriesMixin`（RELOAD-01）：`reload` HEAD 注入失败返回 `failedFuture` 拒绝本轮（26.2 4 参签名，
  真实 CI 编译 + 服务端冒烟已验证零 Mixin AP 目标/描述符告警）。
- loot `commonFields(...)`、`GrindstoneMenu` 匿名槽、`AnvilMenu#createResult`、`MerchantOffer/ItemCost` 构造器等按 26.2
  目标签名核对；`compileJava` 由真实 26.2 MC jar 验证零告警。

### F. 死代码
- **DEAD-01（本线 rev6 缺陷）**：删除 14 个已退役的上游旧文件（`modifyChestLootTable/*`、
  `mixin/structureProcessor/*`、`structureProcessor/*`、`integration/colorful_books/ColorBook.java`、
  `BlockPosStream.java`、`ChiseledBookshelfLootTable.java`、`chiseled_bookshelf/AddRandomTick.java`、
  `ModBootstrapConfig.java`）。
- 删除 `assets/chiseled_enchanting_table/icon.png` 重复副本（`fabric.mod.json` 实际引用连字符命名空间
  `assets/chiseled-enchanting-table/icon.png`，两者字节相同），保留被引用的一份。
- `tomes.btn_lock_all` / `tip_lock_all` 语言键作为兼容保留键说明在案。
- `EntityRegistry` 弃用桥保留至 2.0.0（活代码，非死代码）。

### G. 耦合风险
- `RuntimeFrame` 单次 volatile 发布；stored preferences / effective rules / UI draft 分离；
  数据层 `DataTableLoader/DataJson/DataMaterialRules` 与配置 `ConfigDraft/ConfigFileStore` 拆分。
- **RELOAD-01/02/03（本线 rev6 缺陷）**：rev6 preload 吞错且 staged 值泄漏到 live generation；落地不可变
  `LootRegistryBuildRules` 暂存（注册回调只读暂存，玩法只读已发布 `RuntimeFrame`）、`BUILT_IN_CLASSIC_COSTS`
  不可变基线（移除 Classic 数据包回退到可变活动表）、失败不发布半份候选。
- 原子写（R7）：`ConfigFileStore` 的 `MoveOperation` 可测接口，绝不以 copy/truncate 活动文件「恢复」；崩溃不丢配置。
  配置界面 pending 死锁修复（见 D）。
- 单体行数/能力边界棘轮 `check_architecture_boundaries.py` PASS；`TomeCatalog`/`TomeActionQueue` 纯模型与布局几何隔离。

## 3. 本线（26.2）独有 BUG 清单（本次修复）

| 编号 | 路径 | rev6 缺陷 | 处置 |
|---|---|---|---|
| B1 | 网络 | 成本表载荷无字节上限/裁剪，超限配置可撑爆包 | NET-01 落地（C 路径） |
| B2 | 网络 | 成本同步失败留陈旧价 | NET-02 落地（C 路径） |
| B3 | 耦合/LIFE | `LAST_DISPLAYS` 以存活 `ServerPlayer` 为 WeakHashMap 键 | UUID + 停机/断线清理（C 路径） |
| B4 | GUI 输入 | 搜索框 SDL 焦点 no-op（26.1.2 假设倒灌） | `NativeTextInputFocus` 反射自适应桥（D 路径） |
| B5 | 数据/脚本 | `compat_check` 整文件覆盖丢 26.2 专属守卫 | 合并回 `check_loot_function_codecs_keep_conditions`（B 路径） |
| B6 | 协议 | `range(9,N)` 26.3 探针区间在 N=7 为空 | 统一 `range(1,N)`（C 路径） |
| B7 | 死代码 | 14 个退役文件 + 重复 icon | 删除（F 路径） |
| B8 | 配置写 | 原子写可截断活动文件 + 编辑器 pending 死锁 | ConfigFileStore + ConfigSaveSession（G 路径）——随母树落地 |

## 4. 版本与发布
- `mod_version` 保持 `1.1.0`，版本号不变；**wire 到 `26_2_revision_7`**（协议/方块状态 schema 变化，两端必须同升）。
  不引入 26.3 工具链/JVM 参数/构建日志。CI 参考副本 `docs/ci/*` 与 `.github/workflows/*` 经
  `docs/ci/install.sh --check` 全部字节一致。

## 5. 验证（证据边界）

**本地离线门禁（全部 PASS）**：
- `python3 scripts/ci_static_checks.py`：9 组（14 mixin / 1019 键 x 11 语 / 码点覆盖 / BMP / 单一事实源 / BundledDefaults / CURRENT_BASELINE mixin 清单）。
- `python3 scripts/compat_check.py`：PASS（2 warning = 沙箱无 MC jar 跳过 Tier 2 id/tag 存在性，如实保留）。
- `python3 scripts/gen_bundled_defaults.py --check`：PASS。
- `python3 scripts/test_sync_26_2.py`（新增，替代 26.1.2 专属的 test_sync_26_1_2.py）：6 组 PASS。
- `python3 scripts/test_baseline_mixins.py` / `test_architecture_boundaries.py` / `test_ci_diagnostics.py` /
  `test_ui_readability.py`：全部 PASS。`bash scripts/check_release_consistency.sh --strict`：PASS
  （`bash docs/ci/install.sh --check`：4 副本一致）。
- tree-sitter 真实 Java 语法解析 196 文件 0 错误；`compat_check` 数据 schema 守卫；`git diff --check`。

**远程 CI（真实编译，唯一真实编译通道）**：`compile-check` + `build` 均 **success**（commit `fca48c5` 起）。真实 MC 26.2 / JDK 25：
`compileJava` + `compileClientJava` + `compileTestJava` 全绿，JUnit **162 tests / 0 failures**，`build` 产出 jar，
真实服务端启动 + `/reload` 冒烟（stored-input 掉落实测通过）。`build-reports/compile-java.log` 头固定记录
`minecraft: 26.2`；此前 checkpoint 的 2 个 JUnit 失败（26.1.2 线专属断言）已在 `fca48c5` 修正为 26.2 值后复绿。

**未人工验证（如实标注，不冒充）**：真实客户端 GUI 交互、SDL/IME 文本输入是否实际启动、鼠标拖动/缩放布局、
LAN 房主/访客/管理员权限、多人同台竞争、漏斗/自动化对运行仪式、跨维度同坐标菜单、硬重启/chunk 卸载、
第三方交易生命周期、大图书馆性能、Iris 实机/F3+T。这些需真实 26.2 客户端。

## 6. 文档同步
- `CURRENT_BASELINE.md` mixin 清单由 `check_baseline_mixins.py --write` 刷新（14 个）；版本坐标仍 26.2。
- `docs/PROTOCOL_HISTORY.md` 追加 26.2 线 `26_2_revision_7` 条目。
- `docs/ci/README.md` 脚本引用修正为 `test_sync_26_2.py`。
- `CHANGELOG.md` / `NEXT_TASK.md` 相应补记；旧历史滚轮归档保持。
- `scripts/test_sync_26_1_2.py` 删除；`test_sync_26_2.py` 新增并纳入离线门禁。
- 详见本报告 §2 与 `build-reports/*` 证据。

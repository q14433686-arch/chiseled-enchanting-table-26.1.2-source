# 26.3 设计审计批次回港报告（2026-10-24）

> 执行依据：26.3 分支 `docs/reports/SYNC_TO_26_1_2_2026_10_24.md`（源提交 `5cff469`，
> 源线 tip `1f2dd03` / PR #21）。目标分支：`arena/2d61e52c-chiseled-enchanting-table-26-1`
> （PR #20，在上一轮架构回港 `345c1b9` 之上增量执行）。

## 1. 基线修正（文档基线与目标现状的差异）

同步文档 §1 的目标基线为 `3e61f5d`（无修订体系、1 个测试文件），但目标分支在文档
撰写后已合入上一轮回港（PR #20：规则帧、`26_1_2_revision_6`、`PayloadLimits`、
52 个 JUnit、633→621 键）。因此本次执行不是从零移植，而是把源线在旧基线
`d2abe21`（26_3_revision_6 时代）之后新增的 **31 个非 CI 提交**增量回港，
文档的 L0–L5 清单按现状重新映射如下：

| 文档层 | 本次落地 | 对应提交（目标侧） |
| --- | --- | --- |
| L0 门禁 | `ci_static_checks.py` 增加 Locale.ROOT 门禁（DATA-03/04）与 `BundledDefaults` 新鲜度门禁（共 7 组）；`compat_check.py` 对齐 D1 wear/4/2 与 H-01 合同 | 随各层同批 |
| L1 架构地基 | 上一轮已落地；本轮随批次增量对齐 `RegenerationScheduler`/`WorldRegeneration` 二次漂移 | `7004348` |
| L2 协议与边界 | 目标侧修订独立递增 `_8 → _9 → _10`（命名空间 `26_1_2_revision_N`，与源 `_8/_9/_10` 一一对应但不共用通道 ID）；`_4.._9` 裸通道保留为只查询探针（DEAD-03 政策），四条业务通道后缀化（NET-02），成本表字节预算 + `costTableComplete` 尾标（R-01） | `7004348` / `e7f9321` / `2521c34` |
| L3 机制批次 | CET-001..015（`c1ec2f7` 等 10 提交）+ 模板磨损/跨线（`300f7fe`）；batch 1A（`e9775a0`）、batch 2（`8049bf0`/`ab07122`/`f6058b1`）、batch 3（`605e48f`/`4126d0b`）、COUP-04（`2034b67`）、Iris/Windows（`04896ca`）、audit2（`0d60469`/`ef31d53`） | `7004348` / `e7f9321` |
| L4 审计包 | 世代褪色、精华替罪定价、WaitCurve 数据化、`#wear_immune`、外围新装默认关、`BundledDefaults` 代码生成、`TomeWearData`/`EnchantingNetworking`/`DataIds` 有界拆分、砂轮开关全链路（目录 55→57） | `2521c34` |
| L5 文本与测试 | lang 键级合并 621→633×11（源值 + 目标独有 4 键保留）；`library_resonance.json` 36→82 叶键；新增 `crossline_slip` 进度与 `crossline_excluded`/`wear_immune` 标签；测试 52→65（新增 `AuditMechanicsTest`） | `7004348` / `2521c34` |

文档 §7 要求「不可把五个 L 压成一个大提交」——本次按层拆为 4 个目标侧提交，
每个提交信息回引源提交号，且逐层推送并等 CI 全绿后再叠下一层。

## 2. 版本必要差异（全部保留，门禁守护）

沿用 `SYNC_FROM_26_3_ARCHITECTURE_BACKPORT.md` 的差异矩阵，本轮新增/复核项：

1. 工具链坐标不变：MC `26.1.2` / loader `0.19.3` / Fabric API `0.151.0+26.1.2` /
   Loom `1.16-SNAPSHOT`；源侧 API-02（`fabric-api >=0.162.0`）**不回港**。
2. 客户端屏幕走 `Minecraft.getInstance().setScreen(...)` / `.screen`（源为 `.gui.*`）。
3. 方块 `CODEC` + `codec()` 覆盖保留（源 26.3 已删除，26.1.2 必须保留）。
4. 玩家掉落 2 参 `player.drop(stack, false)`（源为 3 参 `Prediction.PREDICTED`）。
5. 战利品 API：`ConstantValue.exactly(...)`、`LootPoolSingletonContainer.Builder<?>`、
   `List<LootItemCondition>` + `commonFields`、`getOptionalParameter(ORIGIN)`；
   新增 `ApplyWildBookWearFunction` 按此改写（源为 `Optional<Holder<...>>` 形式）。
6. JSON schema：进度 `recipe_id` 字符串形式、战利品 `conditions`/`functions` 数组形式
   （`crossline_slip.json` 为 `impossible` 触发，无需改写，原样带入并与现存进度同构）。
7. 世界生成：现役处理器为 `mechanics/structure/BookshelfReplacerProcessor`
   （`extends StructureProcessor` + `StructureProcessorType.register`，小写
   `markPosForPostprocessing`）；源侧普通类形态不回港，R-06 填充范围语义已随
   `BookshelfBookFiller`/处理器增量对齐。
8. 图标路径保留 `assets/chiseled-enchanting-table/icon.png`（源 API-01 的下划线
   迁移会同时改动 mod JSON 断言，属外观项，不回港）。

## 3. 死代码簇处置（文档 §2「同步=替换」的目标侧核验）

源 `c1ec2f7` 删除的旧架构簇在目标侧先做了引用审计，确认现役引用为零后同步删除：
`structureProcessor/*`、`mixin/structureProcessor/*`、`modifyChestLootTable/*`、
`integration/colorful_books/ColorBook`、`utils/ChiseledBookshelfLootTable`、
`utils/CustomDrawContext` 及重复的 `mixin/chiseled_bookshelf/AddRandomTick`
（保留源侧新版 `mixin/bookshelf/AddRandomTick`，已注册）。`utils/BlockPosStream`
在源侧已无引用、目标侧方块亦在同批改为新扫描写法，故同步删除；
`scripts/import_upstream_loot.py` 与 `scripts/test_sync_26_1_2.py` 为目标侧工具链，保留。
`build.gradle` 中指向已删目录的 5 条 source-set exclude 已清理。

新增 mixin 三件套按源注册：`grindstone/GrindstoneBookDepleteMixin`、
`loot/ReloadableServerRegistriesMixin`、`villager/VillagerInfinitySanitizeMixin`
（注册表共 12 条，`ci_static_checks.py` 逐条核验类文件存在）；其在 26.1.2 上的
目标方法签名由 GitHub Actions 的 Loom 编译 + 真实专用服务器冒烟裁决（全绿）。

## 4. 验收（文档 §6 四项门禁，目标分支 CI 实测）

1. 静态双脚本绿：`ci_static_checks.py` 7/7、`compat_check.py` PASS、
   `test_sync_26_1_2.py` 7/7、`check_release_consistency.sh --strict` 12/12（本地）。
2. compile-check：主+客户端+测试三源集编译，JUnit **65/65 PASSED**（CI 日志
   `build-reports/compile-java.log`）。
3. build：完整 Gradle 构建、无 JEI/REI 编译、MC 26.1.2 真实专用服务器世界
   加载/`/reload` 冒烟逐层全绿（CI 日志 `build-reports/server-smoke.log`）。
4. 专项：坐标未被误升（`check_release_consistency.sh` 守护）；旧存档升级路径——
   无 `cet_generation` 的书按世代 0、7/10 段旧队列文本解码为无继承世代、
   旧修订（`_6` 起）对端在 JOIN 时友好断开（探针通道只查询不注册解码器）。

## 5. 未回港 / 明确不做

- 5 个 `Add files via upload` 审计 ZIP 提交（`4f0dedf` 等）：仅附件归档，
  且源侧 `86d1dfa` 已删根目录重复 ZIP 以过门禁，目标侧不引入 ZIP。
- 源侧 BAL-04/BAL-05 等「明确不做/延后」项（见源 `AUDIT_2026_10_24_DESIGN_PACKAGE.md`）
  在目标侧同样维持现状。
- 真实客户端 GUI 手感、多人混合版本对端体验、结构生成目视确认仍待人工验收
  （与源侧处置报告口径一致，仅 CI 证据覆盖编译/协议/服务端行为）。

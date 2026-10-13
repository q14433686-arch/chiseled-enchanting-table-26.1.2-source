# PORTING 26.2 → 26.3 — 移植报告（1.1.0 线）

**日期：** 2026-10-13
**目标：** Minecraft `26.3`（stable）、Fabric Loader `>=0.19.5`、Fabric API `0.160.7+26.3`、
Java 25（不变）、Loom `1.17-SNAPSHOT`（不变）
**Mod 版本：** `1.1.0`（unofficial-port SemVer：新 MC 目标 = minor 升位）
**验证级别：** 🔧 仅静态核对 + CI 编译（沙箱无 JDK、maven.* 直连不通、不能进游戏）；
实机验证项见 §6。

---

## 0. 方法论：先建 CI，再动源码（借鉴其他仓库的移植手册）

本轮按用户要求**先建 {UN}TACZR（`TaCZ_Refabricated_Unofficial`）那样的 CI**，再做移植。
直接借鉴的同组织移植手册与约定：

| 借鉴来源 | 用在本仓的部分 |
|---|---|
| TaCZ_Refabricated `docs/ci/` 约定 + 三件套 workflow（compile-check v4 / build / consistency v2） | `docs/ci/` 暂存区 + 同名三件套（本仓适配版）；沙箱 App 令牌无 `workflows` 权限，workflow 由维护者手动复制上线 |
| TaCZ compile-check 的**编译日志回推闭环** | arena 分支 push → Actions 编译 → `build-reports/compile-java.log` 经 Contents API 写回分支 → 沙箱 `gh api contents` 读日志迭代修错。本沙箱实测 `results-receiver.actions.githubusercontent.com` 不可达（`gh run view --log` EOF），复证该闭环是必需品 |
| TaCZ `scripts/check_release_consistency.sh` | 版本一致性门禁（本仓四面：gradle.properties ↔ README ↔ fabric.mod.json ↔ publish.yml），分「暂存区必须同步」与「正式件待手动安装」两级 |
| TaCZ `docs/PORTING_NOTES.md`（26.2 移植经验）与 `docs/lineage/PORT_GUIDE_26_3_FOR_RENOVATED_NEOFORGE_20260921.md`（26.2→26.3 全量差异） | §2 的 API 改名清单、§3 的战利品表 schema 迁移（`LegacyLootCompat.migrateSchema` 的映射表逐条对照） |
| TaCZ `docs/investigations/PORT_26_3_PLAN_2026_09_17.md` §1 依赖矩阵 | 26.3 依赖版本（loader 0.19.5 / FAPI 0.160.7+26.3 / Loom 不动 / Java 25 不动），2026-09-30 他们逐项实拉核实 |
| TaCZ build.yml 的静态校验（mixin 配置完整性、语言键齐平） | 同款检查 + 本仓加码「孤儿 mixin 配置注册性检查」（TaCZ 1.21.11 线踩过：`fabric.mod.json` 没注册的 mixins json 永不生效） |

**先建 CI 的直接收益（本轮即兑现）：** 版本一致性门禁第一次运行就抓到存量漂移
（`publish.yml` 仍停在 26.1.2 —— `CURRENT_BASELINE.md` 记录已久的 pending manual apply）；
注册性检查抓到孤儿 `chiseled-enchanting-table.client.mixins.json` 与遗留
`chiseled-enchanting-table.mixins.json`（后者已删除）。

## 1. 依赖矩阵（26.2 → 26.3）

| 项 | 26.2 现值 | 26.3 目标值 | 依据 |
|---|---|---|---|
| Minecraft | `26.2` | `26.3` | TaCZ 依赖矩阵（meta.fabricmc.net `stable:true`） |
| Fabric Loader | `0.19.3` | `0.19.5` | 同上（loader meta 最新 stable；向下兼容） |
| Fabric API | `0.155.2+26.2` | `0.160.7+26.3` | Modrinth `hHj6EvFZ`，release，2026-09-17 |
| Loom | `1.17-SNAPSHOT` | 不动 | TaCZ 26.3 线实证 1.17-SNAPSHOT 可用；「保持 SNAPSHOT 不动，避免同时引入两个变量」 |
| Java | 25 | 25（不变） | 26.3 起最低 Java 25 |
| Gradle wrapper | 9.5.1 | 不动 | — |

`fabric.mod.json`：`depends.minecraft 26.3`、`fabricloader >=0.19.5`、
name/description/authors 封面同步 `Unofficial 26.3 Port`。

## 2. Mojang 侧 API 改动（本仓命中面）

按 PORT_GUIDE_26_3 §2 逐条对照本仓源码，命中的只有 3 处（本模组无自定义 shader /
无第一人称接管 / 无手写网络包 —— TaCZ 的大头工作量都与本仓无关）：

| 改动（26.3） | 本仓命中 | 处理 |
|---|---|---|
| `Block#codec()` 抽象方法**删除**（留着 @Override 会「覆写不存在的方法」编译失败） | `ChiseledEnchantingTableBlock.codec()` + 仅供它的 `CODEC` 字段 | 整段删除（含 `MapCodec` import） |
| `PoseStack#mulPose(Quaternionf)` → `PoseStack#rotate(Quaternionf)` | `ChiseledEnchantingTableBlockEntityRenderer.submit()` 2 处 | 改名 |
| `com.mojang.blaze3d.pipeline.*` → `com.mojang.renderpearl.api.pipeline.*` | `ChiseledEnchantingTableScreen` 的 `RenderPipeline` import | **import 实为未使用**，直接删除（比搬家更干净） |

**核对过但未命中**（PORT_GUIDE_26_3 §2.2 清单里其余各项）：`InputConstants` 改名、
`Player#swing` 三参化、`EntityRenderer#shouldRender` 增参、`AbstractSoundInstance#resolve` →
`getOrResolve`、`Util.OS#openUri` 移除、`FriendlyByteBuf#readMap/writeMap` 移除、
`PushReaction.DESTROY` → `POPPED`、`ConfirmLinkScreen` 签名、`Entity.invulnerableTime`
私有化、`AbstractPackResources` —— 本仓源码 grep 全部零命中。
`PoseStack`/`RenderSystem`/`RenderTarget` 等留在 `blaze3d` 的类（PORT_GUIDE §2.1
「确认不搬家」名单）无需任何改动。

程序化战利品 API（`LootPool.lootPool().setRolls()/add()`、`LootItem.lootTableItem().apply()`、
`SetEnchantmentsFunction.Builder`、`SetLoreFunction.setLore()`）与自定义
`LootItemConditionalFunction`/`StructureProcessor` 的 `codec()` 覆写：26.3 变更清单里
**没有**这些 builder 的改名记录（只有 JSON schema 变了，见 §3），暂时保持原样，
以 CI 编译为最终裁决。若编译报错，按日志逐个改名。

## 3. 战利品表 JSON schema（26.3 硬变更，旧格式**静默不加载**）

PORT_GUIDE_26_3 §2.8 + TaCZ `LegacyLootCompat.migrateSchema` 的映射（六份 loot json
重写实样已逐份对照）：

| 旧（≤26.2） | 26.3 |
|---|---|
| `"conditions": [ {...}, ... ]` | `"condition": {...}`（多条包 `{"type":"minecraft:all_of","terms":[...]}`） |
| `"functions": [ {...}, ... ]` | `"modifier": {...}`（对象，或对象数组） |
| 条件对象 `"condition": "<id>"` | `"type": "<id>"` |
| 函数对象 `"function": "<id>"` | `"type": "<id>"` |
| `block_state_property{block,properties}` | `match_block{blocks,state}`（本仓未用） |

**本仓改动：** `data/chiseled_enchanting_table/loot_table/blocks/chiseled_enchanting_table.json`
重写为 26.3 方言；`functions` 里的 `copy_components{include:[custom_name]}` 顺势换成
TaCZ 26.3 实样在用、语义等价的 `copy_name{source:block_entity}`（custom_name 之外的
component 一个都没拷，两版行为相同；`copy_components` 在 26.3 是否保留未经实证，不赌）。

**门禁固化：** `scripts/compat_check.py` 新增 Tier-1 `check_loot_table_schema_26_3`
（递归扫描全部 `loot_table/**`，legacy `conditions`/`functions`/`function:`/`condition:`
字面形态一律 FAIL；已做正/负两向自测）。`RemoveEnchantedBooksFunction` 的 javadoc
示例同步 26.3 写法。

**不受影响：** `loot_injections.json` 是本仓自有 schema（`EnchantedBookLootInjector`
Java 端解析重建），不走 vanilla loot JSON 方言；`advancement/` 的 `conditions` 是
trigger payload（与 loot condition 数组同名不同物），PORT_GUIDE 未列变更，保持原样。

## 4. 26.3 已知行为变化（本仓相关性评估）

| 26.3 变化 | 本仓相关性 | 处理 |
|---|---|---|
| **服务端不再向客户端发全量配方**（`ClientboundUpdateRecipesPacket` 只剩物品属性集+切石机；mod 数据包的 `crafting_shaped/shapeless` 在专服客户端的 JEI/配方书消失，单人不触发） | 低-中：`recipe/chiseled_enchanting_table.json` 在专服客户端可能查不到（配方书/JEI 不显示），但**合成本身服务端结算，不影响功能** | 记入 §6 实机清单；若需要专服可见性，后续按 Fabric 的 `RecipeSynchronization.synchronizeRecipeSerializer` 登记（PORT_GUIDE §4.9） |
| 战利品表 schema（§3） | 高 | 已处理 + 门禁 |
| 渲染 pass 归属倒置 / shaderc 方言 / Iris 26.3 | 无（本仓无自定义管线/shader） | — |
| 投影 UBO 不可读回 | 无 | — |

## 5. CI 落地状态（{UN}TACZR 模式）

| 件 | 状态 |
|---|---|
| `docs/ci/compile-check.yml` | **待维护者手动复制**到 `.github/workflows/`（App 令牌无 `workflows` 权限，2026-10-13 实测推送被拒） |
| `docs/ci/build.yml` | 同上（整体替换旧 build.yml） |
| `docs/ci/consistency.yml` | 同上（新建） |
| `docs/ci/publish.yml` | 同上（整体替换；含 1.1.0 / 26.3 版本对齐） |
| `scripts/check_release_consistency.sh` | ✅ 已入库；本地 0 错，唯一 WARN 是「publish.yml 正式件待手动安装」（预期） |
| `scripts/compat_check.py`（含 26.3 loot schema 门禁） | ✅ 已入库，PASS |
| 遗留旧 `build.yml` | 仍在正式件里生效：每次 push 跑 compat_check + `./gradlew build` + jar artifact，沙箱可看步骤级红绿（`gh run view`，API 通道），但日志 blob 域不可达，看不到错误细节 |

## 6. 待实机 / 待 CI 验证清单

1. **CI 编译**（compile-check 上线后）：`compileJava compileClientJava` 全绿；
   §2 的三处改动与 §3 的 loot json 是本轮全部源码改动面。
2. **Mixin APPLY 阶段**（编译期零校验，TaCZ 在 26.3 上踩过 6 处签名失配）：8 个活跃
   mixin 的注入点是否仍命中 —— `EnchantmentMenu.slotsChanged`、`AnvilMenu.createResult`、
   `ChiseledBookShelfBlock` random-tick 转发、`StructureTemplatePool.Projection.getProcessors`、
   `StructurePlaceSettings.getProcessors`、`StructurePiece.placeBlock`、
   `StructurePiece.getWorldPos`、`LevelChunk.postProcessGeneration`。启动日志无
   `InvalidInjectionException` 即过。
3. **掉落**：挖 chiseled enchanting table 掉**一个**、带自定义名（copy_name 生效）、
   无紫黑无名物品（§3 schema 生效的判定标准）。
4. **专服配方可见性**（§4）：开专服看客户端配方书/JEI；若不可见属已知 26.3 机制变化，
   功能不受影响。
5. 原 1.0.2 线既有的 live-test 清单（NEXT_TASK.md §A）继续有效。

## 7. 本线版本口径

- `mod_version=1.1.0` = unofficial 26.3 线首个版本；发布物命名
  `chiseled-enchanting-table-1.1.0.jar`，release 目录 `chiseled_enchanting_table_1_1_0/`。
- 封面统一 `Unofficial 26.3 Port`（版本一致性门禁 `--strict` 可查）。
- 与 26.2 线（`1.0.2`，tag `chiseled-enchanting-table-26.2`）并存互不影响；
  本线不得回并 26.2 线（TaCZ 同款纪律）。

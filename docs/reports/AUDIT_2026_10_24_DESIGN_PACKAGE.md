# 设计审计包处置报告（2026-10-24 ZIP 第二轮）

来源：仓库内 `enchanting-table-audit-and-design (1).zip`（与 `docs/` 副本字节一致）。
机制与数值决策均经维护者在会话中逐项选定后实施；本文件记录决策与落地位置。

## 维护者选定的四项决策

| 决策 | 选定方案 | 落地 |
| --- | --- | --- |
| 副本世代 | **世代褪色**：新书世代 = 母本 +1，有效上限 `max(1, maxUses − 世代)`，回复只回磨损不回世代上限 | `TomeWearData`（`cet_generation`；世代 0 不写键以保持堆叠）、队列步 `inheritedGeneration`、完成/直通/转移/合并四路盖章、客户端提示显示有效上限 |
| 替罪羊定价 | **精华定价默认开**：溢出恢复 ∝ `精华(烧掉词条) / 书内最高精华`，至少恢复 1 次；数据包 `scapegoat_pricing: essence\|flat` | `TomeWearData.computeOverflowNextWear`、`MasterTomeWear` 11 参磨损步、载荷同步 |
| 全局默认 | **仅外围新装默认关**：`anvil_flat_repair_cost`、`restrict_villager_infinity_books` 全新安装默认 OFF；核心三项（原版台禁书、同级合并禁、砂轮返枯竭典籍）保持 ON；既有配置文件读盘优先，不受影响 | `ModRuntimeConfig.Draft` 默认翻转两项；新增 `grindstone_depleted_tome`（默认 ON）全链路：记录/草稿/JSON/目录/砂轮门控/11 语言 |
| 范围 | **最大包**：平衡包 + 单一事实源代码生成 + 上帝类拆分 | 见下 |

## 最大包其余落地项

- **等待曲线数据驱动**：仪式 WAIT 六系数（0.42/0.58/0.35/0.65/1.15/0.62）+ 最小 100 tick
  由私有字面量改为 `InscriptionRitualMechanics.WaitCurve`，读 `library_resonance.json -> ritual.wait_curve`，
  经 `EnchantCostTablePayload` 同步（6 double + 1 VarInt，读写对称）。默认值不变。
- **磨损豁免标签**：新增 `#chiseled_enchanting_table:wear_immune`（默认 `minecraft:infinity`，
  `replace:false`）；`hasInfinityImmunity` 实际判定改走「标签或 ID」，标签缺失时 ID 兜底；
  `eternal_codex` 进度仍锚定原始 ID 以免进度语义漂移。
- **协议**：`TransformationDisplay` 的 level 改 VarInt；wire 一次升级
  `26_3_revision_9 → 26_3_revision_10`；rev9 降为只查询探针纳入 JOIN 握手。
- **单一事实源**：`scripts/gen_bundled_defaults.py` 从数据包 JSON 生成签入的
  `BundledDefaults.java`（根包）；`LibraryResonance` / `BookshelfProgression` /
  `InscriptionRitualMechanics`（含 `CrosslineSlipConfig`）的 `Config.DEFAULT` 与上限常量
  全部改引生成值（数值与改动前逐项一致）；`ci_static_checks.py` 增加新鲜度门禁，
  Gradle 增加非构建绑定的 `genBundledDefaults` 任务。BAL-02「35 = 15+20」语义不变，补注释说明。
- **上帝类拆分（有界）**：`MasterTomeWear` 的纯数据层抽至 `TomeWearData`（原公共 API 委托不变）；
  `ScreenHandler.init()` 的网络注册整体抽至 `menu/EnchantingNetworking`
  （含 `LAST_REOPEN_TICK` 生命周期）；`ChiseledEnchantingTableData` 仅抽表 ID 常量至
  `DataIds`（公共 record 类型不移动，避免 API 迁移面）。
- **测试**：`AuditMechanicsTest` 覆盖世代褪色、替罪定价、`PayloadLimits` 计数边界（模糊式）；
  `NetworkCompatibilityTest` 以区分值（含 `FLAT` 与非默认 `WaitCurve`）往返新字段。

## 明确不做 / 延后（维持审计默认，待维护者后续定夺）

- BAL-04：抄串书架加成继续「加」到抄串率（不改符号）。
- BAL-05：外部退款维持 25%（不加祖父条款开关）。
- 回复节奏 7200/3600/1200 秒不变；钓鱼战利品（🚧）与讲台（❓）不在本轮范围；不改 modid。
- `DisableBookEnchanting` 直读 `shelfGatesConfig()` 的行为维持（记录于此）；
  弃用 API 仅在 26.3 替代确定时迁移。

## 兼容性说明

- **客户端与服务端必须同为 revision 10**：可见版本标签不变时也需两侧更新，
  rev9 对端会在 JOIN 时被友好断开而非解码崩溃。
- 存档：旧队列步文本（7/10 段）解码为「无继承世代」；无 `cet_generation` 的旧书世代为 0。
- 已知接受的细节：刚盖上更高世代的书若磨损已超过新上限，下一次使用走溢出规则；
  图书馆回复可能为低于上限的「幻影次数」花周期（见 `TomeWearData` 类注释）。

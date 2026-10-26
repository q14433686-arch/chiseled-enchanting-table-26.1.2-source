# 26.3 → 26.1.2 回移植彻查报告（2026-10-26）

> 本分支为非官方社区移植。回移植对象为姐妹分支 `26.3`（至 `b3cb3c9`，协议 `26_3_revision_12`）。
> 方法：**不照单搬运**——对 248 个差分文件逐一分类（版本无关缺陷 / 功能 / 工程拆分 / 版本绑定 API），
> 只移植前两类并按 26.1.2 API 与数据 schema 改写；同时按七条路径独立审计 26.1.2 独有缺陷。
> 协议保持 `26_1_2_revision_17`（本次无线格式与方块状态 schema 变更）。

## 1. 已移植的 26.3 修复（提交 `6587e9f`，对应 26.3 七路径审计）

| ID | 缺陷（26.1.2 侧确认存在） | 修复 |
|---|---|---|
| RULE-01 | 数据包 6 个母本页键（`tomePageEnabled/Origin/...`）可被抽取但从未写入运行时草稿，且嵌套在 `tome_pages` 对象内时不可见 | `DatapackKeyExtractor` 允许根级或 `tome_pages` 嵌套读取；`buildRules` 将六键写入草稿 |
| RULE-02 | 本模数据包识别：Fabric 含连字符 id `chiseled-enchanting-table` 与命名空间不同，原实现用命名空间比对，且「同内容包优先」会吞掉他包覆盖 | 引入 `FABRIC_MOD_ID` 与自有源变体集；内容比对仅限 `fabric`/`vanilla` 源 |
| RELOAD-01/02 | 战利品注册预载失败被吞、且注册期读到半成品活动运行时 | 失败返回 `failedFuture` 真正拒绝重载；新增 `LootRegistryBuildRules` 暂存，注册回调只读暂存，游戏逻辑只读已发布运行时 |
| RELOAD-03 | 经典模式战利品缺省时回退依赖可变活动表 | 不可变的 `BUILT_IN_CLASSIC_*` 常量表 |
| NET-01 | 成本表载荷不可再裁剪时曾回退发送空成本（绕过校验语义） | 直接抛错，禁止空表上线 |
| NET-02 | 成本同步失败后客户端仍显示旧价 | 清除上次显示并以 `cost_sync_failed` 断开（新增 11 语言键） |
| LIFE-01 | 母本粒子突发队列在服务器停机/世界切换后残留 | 停机与过期世界时清空 |
| MIXIN-01 | 创造模式下无法从凿制书架精确抽取指定书（存活模式锁定逻辑误伤） | 新增 `bookshelf.BookshelfCreativeExtractionMixin`（第 14 个 mixin；创造模式完整书架绕过锁定，守卫失败时 PASS 回原版 `removeBook`） |

## 2. 26.1.2 独有缺陷（本轮自查发现，26.3 无对应报告条目）

| 缺陷 | 修复 |
|---|---|
| `ModRuntimeConfig.writeAtomically` 的最终回退路径会**截断正在使用的配置文件**后重写，崩溃即丢配置 | 重构为包级 `MoveOperation` 可测接口；任何路径都不截断目标文件（新增故障注入测试） |
| 配置界面在通道不可用时保存会留下悬挂的 pending 状态并死锁后续保存 | 发送前 `canSend` 检查、捕获发送异常、15s pending 超时自动解除 |

## 3. 已移植的功能（提交 `1946b53`）：母本目录 Tome Catalogue

- 主源集纯模型 `catalog/TomeCatalog`（附魔/剩余/容量/锁状态过滤 + 五种排序 + 多选保留可见集）、
  `catalog/TomeActionQueue`（>1 本需 5 秒内二次确认、每动作 3 tick 节流、发送前身份再校验）、
  `gui/TomeDirectoryLayout`（纯几何）；客户端 `TomeDirectoryControls`、`CatalogueSkin`（九宫格贴图 13 张）、
  `TomeDisplayNames`、`TomeEnchantmentDetails`（Shift 悬停分页全附魔，Shift+滚轮翻页）。
- 批量锁按钮统一作用于**显式选择集**；第 5 按钮由「暗中锁全库」改为「补满所选」。
  服务端 `LOCK_ALL_FULL` 处理保留（线兼容），客户端不再发送。
- **26.1.2 适配点**：`EnchantmentDisplayNames.findEnchantment` → 本分支既有 `EnchantementListWidget.findEnchantment`；
  `TooltipPresentation` → 本分支 6 参 `setTooltipForNextFrame`；`RowRenderSupport` 新增 `Font` 重载；
  `Screen.hasShiftDown()` 判定 Shift；未移植 26.3 的 `gui.setScreen` 等版本绑定 API。
- **有意偏离 26.3**：无显式选择时锁按钮回退作用于面板焦点书（第一本可见），保持 1.1.0 单本操作不回归；
  26.3 要求必须先点选。该回退只影响单本路径（无需二次确认）。
- 新增 `catalog.*` 32 键 × 11 语言（自 26.3 完整译文移植，BMP/槽位门禁通过）。

## 4. 明确未移植（结论与理由）

| 项 | 理由 |
|---|---|
| 工程类拆分（`178747b` 等大规模重排） | 纯内部结构、无玩家可感知收益，且会破坏本分支 ABI 与既有测试定位；只移植了其中的**语义**（暂存/防御拷贝），类保持原地 |
| Rainbow Cloth（`jeb_` 桌面彩蛋） | 涉及方块状态 schema 变更（`RAINBOW_CLOTH`）必须伴随协议升级与旧档迁移评估；本轮范围外，见 NEXT_TASK 后续项 |
| UI 可读性包（`TooltipPresentation`/`TooltipBudget`/`ui.*` 全局提示预算） | 与本分支提示截断策略不同且耦合 26.3 渲染 API；目录功能只取了必要的 Shift 详情分页，整体移植留作独立任务 |
| 26.3 渲染/输入 API 倒灌（`poseStack.rotate`、9 参 `submitModel`、`gui.setScreen` 等） | 版本不匹配，逐项核对后确认本分支直调形式正确 |

## 5. 七路径自查结论（26.1.2 侧）

- **源码 API**：无 26.3 API 泄漏（`gui.setScreen`/`ContextIntProviders`/`Prediction`/`poseStack.rotate` 全仓 grep 为空）；方块 `CODEC/codec()`、2 参 `player.drop`、BER `mulPose`+10 参 `submitModel` 等 26.1.2 写法完好。
- **数据编解码**：战利品编解码共用 `commonFields`，无 26.3 同型缺陷；复数 `conditions[]/functions[]`、`recipe_id` 与 26.1.2 schema 一致。
- **网络同步**：仪式队列上限 256 双向一致；成本载荷、断开语义见 NET-01/02；本次无任何线格式变化，历史探针（cost `1..16`、apply `9..16`）原样保留。
- **GUI 输入**：目录控件走既有 `MouseButtonEvent`/`hasShiftDown` 事务；配置界面 pending 死锁见 §2。
- **Mixin 注入**：新增创造抽取 mixin 后共 14 个，`required:true, defaultRequire:1`；CI server smoke 绿证明 `removeBook` 目标命中。
- **死代码**：含连字符路径 `icon.png` 是 `fabric.mod.json` 实际引用的唯一副本（26.3 的重复结论不适用于本分支）；`tomes.btn_lock_all`/`tip_lock_all` 语言键转为保留键（无代码引用，为语言包兼容保留）。
- **耦合风险**：`IrisGlintPreload` 保持惰性；`EntityRegistry` 弃用桥保留至 2.0.0；成本监听器异常隔离已存在，无需移植。

## 6. 验证

- 离线门禁：`ci_static_checks.py`（含 11 语言 × 1008 键等价、槽位、BMP、覆盖率、mixin/贴图存在性）、
  `compat_check.py`、`gen_bundled_defaults --check`、`test_sync_26_1_2.py`（9 项，新增七路径回归断言）。
- CI：`compile-check`（主+客户端+测试编译、JUnit）与 `build`（全量构建+服务端 smoke）均绿；
  新增测试 `Backport26_3AuditTest`（7）、`ConfigWriteAtomicallyTest`（2）、`TomeCatalogTest`/`TomeActionQueueTest`/`TomeDirectoryLayoutTest` 及成本裁剪线预算用例。
- 未验证项：实机客户端冒烟（目录交互手感、批量解锁逐本扣费的视觉反馈）仍属 NEXT_TASK A 项范畴。

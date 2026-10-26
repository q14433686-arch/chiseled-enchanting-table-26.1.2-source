# 26.3 → 26.1.2 全量回移植完成报告（2026-10-26）

> 本分支为非官方社区移植。回移植对象：姐妹分支 `26.3` @ `b3cb3c9`（协议 `26_3_revision_12`）。
> 前置轮次（P0 行为修复 + Tome 目录功能）见 `BACKPORT_FROM_26_3_2026_10_26.md`；
> 本报告记录其后的全量移植（用户明确要求「全量移植，必要时 CI 也做」）。
> 方法：源码级差分而非文档清单搬运；所有触及跨版本 API/JSON schema/渲染/输入的文件均改写为
> 26.1.2 写法；每期 CI 全绿后才进入下一期（compile-check + build，含真实服务器/客户端烟测）。

## 一、五期交付

### 第一期 彩虹布（`bb770ee`，协议 17→18）
- 附魔台物品潜行+命名牌精确 `jeb_` 切换彩虹布（不消耗、不改名）；放置/破坏/复制/战利品
  完整保持标记（`rainbowCloth` 自定义数据，染色退出保真）。
- 世界：羊毛 400 tick 同色周期（原版 `ColorLerper.Type.SHEEP`），BER 状态门控彩色盖面不遮铭文。
- 物品：手持/背包烘焙 tint 随帧动画（Fabric `ItemModelWrapper` + 自定义烘焙包装），名称徽章
  「🌈 彩虹」+说明行，27 色相染料模型原样回归。
- `rainbow_cloth` 方块状态属性 → **协议 bump 至 `26_1_2_revision_18`**（见 `docs/PROTOCOL_HISTORY.md`）。

### 第二期 UI 可读性包（`5ebd328`）
- `TooltipBudget`/`TooltipPresentation`：全部列表/面板/共鸣 HUD/配置/图鉴/目录悬浮提示统一
  主信息 + Shift 详情（有界、风险行保真）；纯文本书架 HUD、图标化锁定按钮（5 Sprite +
  `batch_full`）、🔒 图鉴书徽章、行内 ENCH/ℹ 徽章（二进制门禁）、苍白地牢锁底部对齐浮动标签。
- 26.1.2 适配：Shift 状态由按键事件/`keyShift` 判定；风险色经 `TextColor.fromLegacyFormat`。

### 第三期 CI/脚本现代化（`10bcadd`）
- 删除「CI 写回仓库」旧模型（无 Contents API push、无 `contents: write`）；产物走 artifacts，
  诊断走有界 GitHub Checks（compile/server-smoke/client-smoke 三诊断 check-run）。
- `docs/ci/` 为工作流单一事实源（`install.sh --check` 双向门禁）；新增仓库卫生门禁
  （拒绝跟踪生成日志/字节码/CI 写回）与 mixin 清单漂移门禁（14 个活动 mixin）。
- `publish.yml` 仍为 dry-run（发布流程未动）。

### 第四期 工程拆分（`0b67c1d`/`3b8cd80`/`20e1d2d`/`6738e9d`/`e58c503`）
六个单体按 26.3 同一边界拆分，行为保持由「方法体归一化等价比对 + 行为测试」双重验证：
- 数据层：`DataTableLoader`、`DataJson`、`DataMaterialRules` 及 6 个顶级记录；
- 配置：`ConfigDraft`、`ConfigFileStore`（原子写）、`RuntimeConfigJson`、`RuntimePolicyJson`、
  `RuntimeConfigPresets`；
- 菜单经济：`MenuQuickMove`、`MenuMaterialQuotes`、`MenuXpQuotes`、`MenuPaymentService`、
  `MenuPricingAccess`、`MenuEnchantmentLookup`；
- 仪式：`RitualWorldAccess`、`RitualQueuePersistence`（`recoverQueue` 结构化/旧式对账）；
- 客户端：table/config 双屏 skin + 面板上下文/呈现器 + 配置会话
  （编辑/保存/搜索/滑杆拖拽/销毁确认）；
- `check_architecture_boundaries.py` 行数与能力边界棘轮入 CI；
  移植 `ConfigInteractionTest`、`EngineeringSplitTest`、`LatestPortAuditTest`；
- mod 图标路径对齐 `assets/chiseled_enchanting_table/icon.png`。

### 第五期 clientSmoke（`af7fa7e`）
一次性真实客户端集成探针（独立可选 source set `-Pclient_smoke=true`，永不打包进发布 jar）：
8 个 PASS 标记覆盖配置搜索 CJK 输入与保存往返、列表模型、UI 呈现预算、目录锁定往返、
模态 overlay 输入隔离、彩虹世界/物品/资源重载、图鉴书世界流程；CI 以 xvfb 真实执行。

## 二、七路审计最终状态
1. **源码 API**：与 26.3 的剩余差异即版本边界集（方块编解码、BER 提交式渲染、结构处理、
   2 参数 `player.drop`、`setScreen/screen`、SDLManager 缺失、命题色常量缺失），
   全部有 CI 编译+烟测覆盖；无盲拷越界。
2. **数据编解码**：数据层拆分后双快照读放大缺陷（26.3 DATA-01）经 `DataJson` + 
   `RuntimeFrameSnapshotTest` 锁定；战利品/进度 JSON 保留 26.1.2 复数数组 schema。
3. **网络同步**：协议 `26_1_2_revision_18`，探针全保留；config-pack 校验数据包源识别
   含连字符 Fabric id（两版本曾共有缺陷，本线 `LatestPortAuditTest` 以真实
   `ResourceManager` 代理验证钉版语义）。
4. **GUI 输入**：命名常量替代原始按钮字面量；overlay 全输入隔离；搜索焦点会话化
   （26.1.2 无 SDL 文本输入管理器，焦点所有权为 no-op 并有书面留痕）。
5. **Mixin 注入**：14 个活动 mixin 清单漂移门禁；创造提取 mixin 含实体失效委托；
   `IrisGlintPreload` 在 26.1.2 为有意惰性桩（Iris 目标类不存在，非死代码）。
6. **死代码**：`dev/overlay/AuditStubs` 为 CI 互斥 overlay 的链式替身（活代码，删除即断
   无 JEI/REI 编译路径）；`EntityRegistry` 为 2.0.0 前文档化桥接；老玩家被动补给
   （SupplyDrop）三版本均未实现，文档仅余设计措辞，无运行时入口。
7. **耦合风险**：单体拆分完成且由行数棘轮冻结；数据加载单例帧 + 配置 draft/文件存储
   边界有静态门禁（架构检查）与行为测试双锁定。

## 三、刻意保留的 26.1.2 偏差（与 26.3 有意不同）
- 图鉴书面板**只读焦点回退**：无显式多选时，面板细节与锁定按钮作用于第一个可见图鉴书，
  以免 1.1.0 单本锁定流程退化；显式多选语义与 26.3 完全一致（`EnchantementListWidget`、
  table screen 与 `TablePanelBackground` 三处均有注释）。
- 工具提示细节键：26.1.2 无 GLFW 轮询替代的版本化差异（事件追踪），行为等价。
- `gradle.properties` 坐标保持 26.1.2 工具链（Loader 0.19.3 / API 0.151.0 / Loom
  1.16-SNAPSHOT / Gradle 9.4.1）；`publish.yml` 保持本线发布脚本。

## 四、验证
- 离线门禁：`ci_static_checks`（9 组）、`compat_check`、`check_architecture_boundaries`、
  `test_sync_26_1_2`、`test_ui_readability`、`check_repository_hygiene`、严格发布一致性全通。
- CI：每期 merge 后 `compile-check` + `build` 全绿（含 `gradle build` 全量 JUnit、
  真实服务器烟测；第五期起含 clientSmoke 真实客户端执行与三诊断 check-run）。

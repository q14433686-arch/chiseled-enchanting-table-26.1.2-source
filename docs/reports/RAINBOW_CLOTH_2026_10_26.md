# `jeb_` 彩虹桌布彩蛋 — 2026-10-26

当前源码：`0a43bd3a7e4284beef49a21dbffa34bdc5e513f7`；下方原始放置桌布验证对应 `871b696532df3882559057670dcb5ed0a255570e`。保持 **1.1.0 / MC 26.3**，兼容性通道升至 **26_3_revision_12**。

## 玩家行为

- 使用原版彩虹羊的准确名称 **`jeb_`**，小写、下划线在末尾；`_JEB`、`Jeb_` 不触发。
- 手持已命名的命名牌，蹲下对篆刻附魔台右键，开启循环渐变桌布。命名牌不消耗；不修改附魔台自定义名称。重复触发保持开启，不开菜单或反复开关。
- 正常使用染料会退出彩虹模式，并按原有规则与保存的底色混色、扣除一个染料；不把某一帧的随机渐变色混进永久底色。
- 打掉后通过现有 `minecraft:custom_data` 保存开关，放回恢复。原有染色、冻结倒计时/世代周期数据可以与彩蛋标志共存。追加的客户端表现使彩虹物品栏/手持图标实时渐变，普通染色物品不动画；不改变持久化格式。
- 保留旁观者/建造权限、原版 `mayInteract` 和 `mayUseItemAt` 检查。未承诺通用第三方领地保护兼容，需在相应整合包另测。

## 为什么这样实现

1. **不能只改 `useItemOn`**：原版可能在蹲下且持物时跳过方块常规交互。使用 Fabric `UseBlockCallback` 的提前回调，只处理符合名称、物品、蹲姿和目标方块的交互，其余返回 PASS。
2. **不能逐帧重烘焙区块**：静态 tint 会缓存于区块网格。增加默认关闭的 `rainbow_cloth` 方块状态，只在该状态下用不带静态桌布的模型变体，再由现有 BER 绘制五个桌布面。
3. **不重复造调色算法**：26.3 原版羊使用 `ColorLerper.Type.SHEEP`，含羊毛亮度与 sRGB 插值，本实现直接复用。每种颜色25 tick、16色循环，约20秒一轮；保留 partial tick。先取模限制时间精度，避免老世界长时间运行后 float 丢失渐变细节。
4. **不逐帧同步颜色**：服务端只切换持久化方块状态；客户端按世界时间计算颜色。不新增颜色包、随机序列或每帧区块更新。绘制提交只捕获颜色/光照数值，不在提交阶段访问可变世界数据；固定面坐标、UV和纹理标识预先缓存。
5. **普通模式不回归**：普通台仍走原方块模型与物品 Dye tint 路径；仅彩虹状态走额外动态路径。纹理仍使用现有桌布 overlay，不染书本或台座。
6. **新旧端不能静默混用**：新增注册方块状态属于客户端/服务端共同 schema。虽不改经济菜单载荷字段，仍将现有兼容性能力通道和带后缀业务通道升到 revision12；revision11 自动保留为旧探测项，不注册旧解码器。协议测试明确更新预期版本，并保留其余载荷与边界断言。[协议说明](../PROTOCOL_HISTORY.md)。

## 追加：背包辨识与实时物品图标

Verified source **`0a43bd3a7e4284beef49a21dbffa34bdc5e513f7`**; **1.1.0 / MC 26.3 / `26_3_revision_12`** unchanged. This client presentation extension adds no component, registered state, payload or Mixin.

- Rainbow items show a purple localized **〔彩虹〕 / [Rainbow]** suffix in their tooltip header and two short explanatory lines. Player custom names and stored name/lore/data components are not rewritten; hotbar hover-name text remains the original name.
- Fabric's item-model before-bake hook replaces only the table's primary dye tint. Rainbow GUI/held models use the same vanilla sheep clock as placed cloth, with an explicit animated flag to prevent GUI atlas freezing. Ordinary tables still delegate to the original Dye source. Non-cuboid/no-primary-Dye resource-pack models are preserved with a warning, not forcibly replaced.
- [compile-check 40267243846](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/runs/40267243846): **155 JUnit, zero failures/errors/skips**. [build 40267243828](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/runs/40267243828): all jobs successful, including no-viewer compilation and real server/SDL client smoke.
- Diagnostics include **RAINBOW ITEM PASS** and **RAINBOW ITEM RELOAD PASS**: actual server loot, baked GUI colour changes across live frames, animated-cache flag, held colour parity, static ordinary dye control, actual tooltip callback/custom-name safety, unchanged custom data, and actual resource reload. Existing **RAINBOW CLOTH PASS**, **CATALOGUE WORLD PASS**, **CLIENT SMOKE PASS** remain passing.
- [Installable build including item animation](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/runs/40267243828/artifacts/12626602664); [native evidence including rainbow-items-a.png and rainbow-items-b.png](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/runs/40267243828/artifacts/12626638940). These are two captured frames, not a manually reviewed animation recording or a claim of all shader/resource-pack compatibility.
- [Implementation and limits](RAINBOW_CLOTH_2026_10_26.md). No PR/merge authorized or performed.

## 原始放置桌布验证（871b696）

该阶段 CI 已确认（旧构建不含后续物品动画）：

- [compile-check 40263622160](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/runs/40263622160)：**155 JUnit，零失败、错误或跳过**，含7项新测试。
- [build 40263622260](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/runs/40263622260)：四个业务 job、compile/server-smoke diagnostics 全部 success，含关闭 JEI/REI 的编译和真实服务端/中文 SDL 客户端。
- 从最终 Check 日志读到 **RAINBOW CLOTH PASS**：精确名称和蹲姿回调、可复用命名牌、实际 loot/放置标志、染色与冻结组件共存、染色退出/消耗、重新激活均通过。**CATALOGUE WORLD PASS**、**CLIENT SMOKE PASS** 也保留。
- [可安装构建](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/runs/40263622260/artifacts/12625302256)。[原生测试日志及现有菜单截图](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/runs/40263622260/artifacts/12625970306)。未制作或人工查看专门的彩虹桌布动画录屏，不以静态截图证明整段动画视觉效果。

- 新增7项纯 Java 测试：精确名称、400 tick周期、帧内插值时间、超大世界时间精度、跨周期边界、异常 partial tick、安全负时间。
- 原生 smoke 在真实服务端世界内调用已注册 Fabric 事件：不蹲下、错误名称、无建造权限均不触发；有效命名牌开启且不消耗。
- 通过真实 loot 查询检查掉落彩蛋标记、原染色和冻结数据；走放置状态解析与 BlockEntity 组件恢复检查，验证两者共存；实际方块染色处理退出彩虹并正常扣除染料，再次触发可恢复。
- 客户端调用实际羊颜色路径比较不同时间颜色，并将相机转向新桌布。原有真实菜单/书架多选锁定、配置保存/ACK、原生 CJK 输入、持久目标等检查继续执行。
- 该交互 smoke 是服务端真实事件回调测试，不冒充真人键鼠或完整客户端到服务端右键网络包测试；放置检查也不冒充真人挖取再放置矩阵。
- 本地静态9组、架构6项、同步17项及 release consistency12项通过；最终源码对应155项及真实客户端/服务端结果如上，中间失败和取消的运行不作为通过证据。

## 跨版本与风险边界

- 触发、布尔状态、掉落组件设计相对稳定，不碰附魔定价、支付或仪式队列，不新增 Mixin。
- 主要适配风险在渲染层：26.3 的 RenderState / SubmitNodeCollector / RenderTypes 与此前版本签名有差异；26.3 已移除 `entityCutoutNoCull`，本实现使用对应的 `entityCutout`。不能直接复制到26.1.2/26.2后宣称兼容；也需检查该版本羊的颜色算法入口。
- 当前只承诺本分支26.3实现及最终列出的自动验证。未测试 Iris/全体 shader 组合、多玩家长时间观测、自定义方块几何资源包。默认桌布的五面尺寸与原模型 overlay 保持一致；替换整个方块几何的资源包可能需要相应适配。
- 新旧客户端/服务端必须一起更新，版本号同为1.1.0也不代表兼容。已有普通桌子默认为关闭；备份世界后更新，不把旧 jar 降级读取新方块状态当作受支持迁移。
- 本轮未获 PR/merge 授权，不创建或合并 PR。

验证过程发现 26.3 已将 `Items.RED_DYE` 改为 `Items.DYE.red()`（ColorCollection record）；仅修正原生测试夹具的 API 使用，未跳过染色退出/消耗断言。

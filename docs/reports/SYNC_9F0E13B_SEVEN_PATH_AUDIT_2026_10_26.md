# 26.1.2 → 26.3：9f0e13b 全量适配与七路径审计

日期：2026-10-26。版本 **1.1.0 / Minecraft 26.3 / Java 25**。

## 1. 范围、基线与结论

- 上次已移植源：`3e61f5de3ba7679ccbc92b73443dead149c96840`。
- 本次固定源：`9f0e13bd75e31f7d1d8a569302d0f57abd2ce840`。
- 26.3 对照基线：`1f2dd036ff35ea72712240b312a9c9b7e4e249bf`；先快进保留主线已合入的保护，再移植增量。
- 源增量约 209 文件，不能用移植文档清单代替源码差分。源指南 `SYNC_TO_26_2_AND_26_3_2026_10_26.md` 的 Wave A 已在目标存在；本次接入其余母本页/锁、配置目录与预设、Classic 数据、Codec/菜单拆分、标签和 loot 数据化、语言及测试更新。
- 不复制源分支依赖坐标、协议号、旧 MC API、服务器探针、历史验证结论或有误的 Mixin 清单。
- 当前协议独立升级为 **`26_3_revision_11`**。保留原始查询探针及历史 cost revision 1..10、apply revision 9..10；不注册旧业务解码器冒充兼容。

## 2. 实际发现与修复

| 编号 | 分类/影响 | 复现链路与实际修改 | 可反哺范围 |
| --- | --- | --- | --- |
| API-26 | 26.3 专属，编译失败 | 新 `BookshelfBookFiller` 用了 `BlockEntityType.CHISELED_BOOKSHELF`；26.3 常量在 **`BlockEntityTypes`**，两处注册表查键已改。 | 26.3；26.2 须核对自身 API |
| GUI-26 | 26.3 专属，客户端编译失败 | 新提示调用需要从 `Gui` 转到 `Gui.hud.setOverlayMessage`；新快捷键使用 `InputConstants.Type.KEYBOARD`，不再用源 `KEYSYM`。三处 overlay、两处 key type 已实改，并添加回归合同。 | 26.3；其他线按实际 API |
| DATA-26 | 26.3 schema，进度条件失效 | 七处 recipe 进度仍使用 `recipe_id`/`recipe`；实际转为 **`recipes` 数组**，包括 crafted/unlocked，不只更新某一个文件。 | 26.3；其他线按原版 schema |
| RULE-01 | 共性，配置静默失效 | `tome_pages` 六项已被 Codec 读入，但来源提取器没有登记，`rawRuntimeConfig` 也没复制；随后 `withRuntimeOverrides` 覆盖回默认。补齐六键来源与六字段 raw bridge，验证 raw → resolver → configuration → domain/snapshot 一致。 | 26.1.2、26.2 建议同步 |
| RULE-02 | 共性，数据包失去治理权 | 任意外部包只要 JSON 等于 bundled 就被当作 own，无法用默认值覆盖非默认本地偏好。内容比较现在仅给 `fabric`/`vanilla` 聚合内置来源兜底；明确的 own 来源保留，普通 external 始终参与来源判定。 | 两条其他线建议同步 |
| RELOAD-01 | 共性，静默吞错/代际混用 | preload catch 只 warn，外层 falsely 声称 fallback；旧 staged 值可能被继续使用。移除吞错；26.3 reload HEAD 注入返回 failed future，拒绝这轮 registry build，不用旧规则构建新表。 | 逻辑共性；Mixin 签名各线适配 |
| RELOAD-02 | 共性，准备状态泄露到玩法 | 原 preload 直接更新 live getter 和 chest spawn cache。改成单个不可变 **`LootRegistryBuildRules`**，只由两类 loot table MODIFY 回调消费；玩法仍读已发布 `RuntimeFrame`。成功/失败 reload 结束与 server started 清 stage；preload 不改 active chest cache。 | 两条其他线建议同步 |
| RELOAD-03 | 共性，移除 Classic 数据包后仍沿用旧值 | 三个 Classic loader 把当前可变表当作缺资源 fallback；包移除后旧成本/升级/转化被继续保留。独立保存三个不可变 bundled 基线，每轮缺资源回到基线，补完整 loader 添加→移除回归。 | 两条其他线建议同步 |
| NET-01 | 共性，成本包仍可超限 | 裁剪仅处理成本行；行为空/裁到零后仍可能让超限配置头或转化列表出站。现在实测仍超上限就抛错；实际 Netty 测试覆盖可裁剪与不可裁剪。**不丢弃配置字段伪造一致性。** | 两条其他线建议同步 |
| NET-02 | 共性，失败后继续显示旧价 | cost sync 并非纯装饰；捕获失败只 warn 会留下本地/旧报价。现在清 display cache，明确报错断开该客户端；管理员修复包后可重新连接。新增 11 语言报错。 | 两条其他线建议同步；属于有意 fail-closed 行为 |
| MIXIN-01 | 共性，Creative 豁免与底层守卫冲突 | UseBlockCallback 的 Creative PASS 无法绕过容器 removeItem 锁。新增 **实际 vanilla `removeBook` HEAD** 注入，仅服务端 Creative 的真实取书操作先解锁该格；普通点击/插入不解锁，漏斗不继承豁免。 | 语义共性；各线核对私有静态方法签名 |
| LIFE-01 | 共性，跨世界强引用 | 粒子 burst 静态表持有 ServerLevel，旧 server 的条目会在新 server tick 一直 continue。stop 时按 server 清理；tick 丢弃非当前 server/已卸载维度实例。 | 两条其他线建议同步 |
| IRIS-26 | 26.3 渲染回归风险 | 新增 manager identity 缓存后，F3+T 同一 TextureManager 不会重新预热。增加客户端资源 reload invalidation；取消必须进世界才预热的条件，仍在 render-thread tick 而非 render pass 加载纹理，仍只在 Iris+26.3 启用。 | 26.3；其他线不要盲搬 workaround |
| DEAD-01 | 维护/漂移 | 移除退役 `ModBootstrapConfig` 并更新测试合同；删除重复的连字符命名空间 icon，保留实际配置所用资源。测试不再错误断言源 revision_17/至少七个 apply 历史通道。 | 按各分支真实架构处理 |

六个 RULE-01 键：`locked_shelf_breakable`、`locked_book_extraction_blocked`、`early_unlock_xp_levels`、`early_unlock_grit_count`、`sacrifice_required`、`tome_page_required_shelves`。没有凭空增加不在 `tome_pages` 数据模型里的规则原子。

## 3. 七条审计路径与保留的保护

### A. 源码 API

实查原版 26.3 `Slot`、`ItemCombinerMenu`、`MouseButtonEvent/Info`、`ChiseledBookShelfBlock/Entity`、`ReloadableServerRegistries`，而非照抄源升级说明。

- `Slot` 的双 ItemStack `onQuickCraft` 为 public，带 int 的版本及 `onSwapCraft` 为 protected；`allowModification` 为 public，`getNoItemIcon` 为 nullable Identifier。
- `MouseButtonEvent.button()` 仍是带注解的 int，保留 `InputConstants.MOUSE_BUTTON_LEFT/RIGHT` 比较。
- reload 是 context、pending tags、resource manager、executor **四参数**签名。
- 保留 26.3 `StructureProcessor` 接口/codec 注册、模板位置参数、post-processing 面向修正、`rotate/rotateDegrees`、单独 crumbling overlay、`Prediction` 掉落 API。
- loot 保留 `ContextIntProviders`、`UniformContainerBase`、可选 condition holder；不搬回源旧接口。

### B. 数据编解码

接入嵌套 LibraryResonance Codec、Classic 三表、箱子生成规则与标签；核对 JSON 默认值与生成 `BundledDefaults`。真实解决的是 schema 和六项规则桥接，不是只检查 JSON 合法。保留严格 pack policy 验证、有效资源层 whole-file replacement、失败不发布当前模组的候选 frame。界限：不能承诺其他模组在整个 Minecraft reload 中失败时也会跨模组回滚其副作用。

### C. 网络同步

开菜单、live shelf、仪式、转化、配置请求/确认、母本页动作均检查当前 wire 字段与容器匹配；服务端动作仍回主线程，校验菜单有效性、距离/书架范围、解锁条件、权限与 burst 限额。成本表保留 **980,000 字节实编码上限**、`costTableComplete`、转化优先/按等级交织、明确空目录不回退本地数据。失败不再把旧报价当作可用同步。

### D. GUI 输入

移植纯 `SearchTextBuffer`：code-point 游标/选区、剪贴板替换、过滤与长度限制；保留 26.3 输入事件形状与左右键常量。检查配置恢复、菜单输入、母本动作与服务端校验链，不将客户端按钮启用状态当作授权。文本测试可 headless；IME、鼠标拖动、缩放布局和 JEI/REI 实机切屏不冒充已人工测试。

### E. Mixin 注入

14 个声明均有类，配置 `required=true` / defaultRequire=1。锁容器 `removeItem(II)` 注入真实声明方法；`removeItemNoUpdate` 在目标由 `ListBackedContainer` 默认提供，继续使用目标类 override，**不向继承的接口默认方法做虚假的 require=1 注入**。NoUpdate 保持全 slot 取出和不额外 setChanged 的语义。新 Creative 注入只接真正取书的窄入口。loot 四参 HEAD 失败返回 failed future。服务端启动探针负责发现目标注入找不到/变形错误，但不能代替每种交互分支测试。

### F. 死代码与生命周期

删除旧 bootstrap 与重复资源；保留兼容 facade/别名供仍存在的调用方使用，不能把“旧名字”自动当作死代码。玩家缓存使用 UUID，断线/停服清理；补粒子强引用清理。Iris 无模组时不注册 tick，F3+T 失效后重新预热。

### G. 耦合风险

保留 `RuleBook` 事务、单次 `RuntimeFrame` 发布、stored preferences/effective rules 区分和启动栈大小约束。分离 raw、候选 loot-build 输入和已发布玩法配置。菜单页动作与输入栈服务拆分后仍在服务端重验，不缓存旧世界权限。**startup snapshot/coherent 配置不一致候选未证实**：Draft build 不会按 Classic 强制 stack=64，初始化读取实际本地启动值；不将注释推测列作已修 bug。

## 4. 验证与证据边界

本地可执行、已通过：

- `python3 scripts/test_sync_26_3.py`：16 项。
- `python3 scripts/ci_static_checks.py`：8 组，14 Mixin、976 key × 11 语言、资源/生成默认值合同。
- `python3 scripts/compat_check.py`：PASS；沙箱没有 MC jar，因此本地 registry/tag Tier-2 两项跳过，不能声称查过。
- `git diff --check`。

新增真实 Java 回归：外部包 pin 默认值、聚合内置来源、六项 tome page 的解析→来源→resolver→frame、preload 不污染 live/chest cache、无效 loot/policy 不发布半份候选、实编码成本行裁剪与空行后 header 仍超预算拒绝。已有本轮移植测试还覆盖 Codec 往返、80 项目录/20 原子、四预设、Unicode 搜索、锁时长与持久队列。

远程最新源码的编译、JUnit、可选依赖关闭、真实服务端 smoke **正在验证，完成后在本节补证据**。较早源码 `5a1db65`、`07a4c7f` 的真实服务器 world-load/reload 与既有仪式/隔离/输入返还探针已通过，不等同于最终源码全通过。

未人工验证：Iris 实机/F3+T、Creative 与漏斗的玩家交互、GUI/IME/缩放、真实多客户端/LAN、JEI/REI 返回菜单、长时间性能与第三方组合。不得把 headless smoke 或源码断言写成这些项目通过。

CI 诊断同时修复了大日志回写：base64 不再作为单个命令行参数（会触发 Linux 参数长度上限），改用 JSON 请求文件；JUnit XML 数量与失败详情写入可取回日志。

## 5. 反哺其他两个分支

建议 26.1.2/26.2 优先吸收 RULE-01/02、RELOAD-01/02、NET-01/02、LIFE-01，并核对 Creative 取书与底层守卫冲突。最小重构单元是“来源提取 + raw bridge + resolver/frame 一致性回归”和“registry-build 候选与 live generation 分离”，不要只 cherry-pick 文案或 Codec。

26.3 的 BlockEntityTypes、四参 reload、input/render/loot schema 与 Iris workaround 是版本专属，不能照搬到其他线。本会话只修改当前 Arena 分支，没有代改或推送其他两个分支，也没有本轮 PR/合并授权。

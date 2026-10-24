# Tome page, page unlocks & tome locks — 2026-10-24

## Player-facing behavior

- The Chiseled Enchanting Table gains a third top tab, **Tomes**. It lists every
  Enchanted Book on in-range Chiseled Bookshelves using the same scan scope as
  Master Tome charging, with remaining/effective charges, generation, ETA to
  +1, ETA to full, qualifying-shelf count, total library shelves, and the gap
  to the next regeneration tier.
- **Cleanse** and **Tomes** start locked per player. Hovering either locked tab
  shows: “未解锁该功能页，请前往纂刻秘典查看详情”. Unlocks are permanent and use
  advancements as the source of truth — no new saved-data schema.
- Cleanse unlock: the amber sacrifice control beside the tabs consumes
  Depleted Tomes from in-range shelves. Twelve total sacrifices complete
  `sacrifice_depleted_tomes` and unlock the page.
- Tomes unlock: four in-range Chiseled Bookshelves each holding at least one
  Enchanted Book complete `how_much_longer` (“还要多久……”) and unlock the page.
- The Grimoire gains chapter 9, explaining both unlocks and all lock modes.

## Tome locks

Lock state lives in the book's `CUSTOM_DATA` (`cet_lock_owner`,
`cet_lock_owner_name`, `cet_lock_mode`, `cet_lock_target_wear`), owned by the
locking player's UUID. Modes are `next_charge`, `full_charge`, and
`permanent`. Locking is free; locks never stop regeneration. Timed locks
expire exactly when wear reaches their target, are cleared when observed, and
notify an online owner with an actionbar message and sound. Locked books on
shelves emit marker particles.

Early manual unlock costs exactly **1 XP level + 1 Cleansing Grit per book**
(non-Creative, owner or Creative override). Server-side guards cover menu
apply/receipt paths, candidate generation, ritual/transmutation inputs,
anvil/grindstone results, container extraction (including hoppers), manual
shelf removal paths through `ContainerHelper`, and non-Creative shelf breaks.
Batch **Lock All Full** locks every currently charging, unlocked book.

## Wire & data

- Enchanting wire revision is now `26_1_2_revision_11`; revision 10 and older
  remain query-only handshake probes.
- `LiveShelfStatePayload` carries unlock flags, sacrifice progress, qualifying
  shelves, library totals, regeneration tier/gap/interval, and up to 256
  bounded `TomeBookEntry` records (64 enchantments each). The revision-10
  8/11-argument constructors remain source-compatible.
- `TomePageActionPayload` carries `LOCK_NEXT`, `LOCK_FULL`, `LOCK_PERMANENT`,
  `UNLOCK`, `LOCK_ALL_FULL`, and `SACRIFICE_DEPLETED`; the server revalidates
  menu target, shelf scope, slot contents, ownership, and costs.

## Verification gates

- `scripts/test_sync_26_1_2.py` (now pinning revision 11 + revision-10 probe)
- JUnit: pure TomeLock target/expiry/ETA tests in `AuditMechanicsTest`;
  full live-state + tome-action codec round trips in `NetworkCompatibilityTest`
- GitHub Actions compile, JUnit, and server smoke on Minecraft 26.1.2

## Field fix (2026-10-24, integrated-server log)

A user log (Minecraft 26.1.2 integrated server, 49 repeated errors) showed
`LockedBookContainerMixin` failing to apply: the jar predated commit
`25a19ef` and still targeted `removeItem(NonNullList, int, int)`, while
26.1.2's `ContainerHelper` declares `removeItem(List, int, int)`. Because the
mixin config is `defaultRequire: 1`, the failed injection broke the
`ContainerHelper` transformation itself, cascading into block-entity
load/save failures (enchanting table, chiseled shelves, hoppers, barrels)
and suppressed packet-handling errors. Fix: the `List` descriptors (already
in `25a19ef`, verified by the CI server smoke) plus `require = 0` on both
auxiliary injections, so any future descriptor drift degrades the lock
guard instead of corrupting world I/O.

## 现场反馈第二轮（2026-10-24 23:10 截图）修复记录

玩家在实机截图中报告十项问题，逐项定位与修复如下（线协议升至 `26_1_2_revision_12`，`revision_11` 按策略保留为 query-only 探针）：

1. **UI 文字溢出/挤压**：锁定行改用短模式文案（已锁·满/永久/+1）并附剩余时间与主人，行内文字由 bounded 渲染裁剪；右侧按钮上移并改 AI 贴图底（见 9）。
2. **解锁判定延迟约 1 秒**：解锁标记与献祭进度本质是按玩家持久化的进度（advancement），现随**开屏载荷**（`AvailableEnchantmentPayload` 新增 5 字段）从服务端进度直接下发，第一帧即正确，不再等 20 tick 广播；后续仍由实时广播保鲜。
3. **献祭 0/12 不变**：同 2，开屏即显示真实进度；献祭扫描放宽为扫描体积内全部雕纹书架（不再受呼吸空间/视线门控误杀），且无可献祭时提示写明需先放入附魔台周围雕纹书架。
4. **锁定剩余时间不可见**：列表行、悬停提示均显示限时锁的剩余时长（锁至 +1 用 eta_next、锁至满用 eta_full），新增「还需 %s 后自动解除」提示行。
5. **锁定书可右键取出**：原版右键取书不走 `ContainerHelper`，改由 `UseBlockCallback` 在书架正面按原版命中槽位公式拦截，并发送动作栏提示「该书已被锁定，时长为：…」（永久/约 X 时长，按最近附魔台的实时速率估算）。
6. **献祭未记载在册**：献祭进度以 12 条成就准则为唯一记录（玩家进度文件持久化），修复后计数在开屏与献祭后即时可见、可核查。
7. **文本数值与事实脱节**：秘典第 3 章、图示 5、JEI 两条信息、WIKI 中的「每级 16 次/16 件」均为旧值——现全部由实时配置注入（捆绑默认 `max_tome_uses = 4`、`book_copy_wear_cost = 2`，见 `library_resonance.json`）；秘典第 9 章的献祭数/书架数/解锁费用改为读取代码常量；提前解锁费用抽为 `EARLY_UNLOCK_*` 常量供服务端与文案共用。
8. **易读性**：纂刻的书页移除「广义书架」，改为「书架总数」；「还差 XX 架」改为「还差 XX 架至下一阶段」；速率行加「当前速率」前缀；左侧列表悬停提示改为「+1 耐久还需 … / 满耐久则需 …」。注：用户提及的文字建议 ZIP 未随本次反馈送达工作区，以上按本条明确要求执行，如 ZIP 另有细则可再对齐。
9. **新 UI 素材**：页签（激活/未激活）与锁定按钮底图改用 AI 生成的秘术风格贴图（`tab_tome_active/inactive.png`、`tome_button.png`，148×36 / 100×24，已按原 UV 尺寸落盘）。
10. **锁定/解锁重置充能进度（BUG）**：根因是锁定写回用了 `ItemStack.copy()` 新实例，而恢复调度器按**引用同一性**认书，新实例被当作新书从零计时。现锁定/批量锁定/到期解锁全部改为**原地修改**同一实例（`TomeLockData` 本就原地编辑 CUSTOM_DATA），调度进度得以保留；`WorldRegeneration` 的到期解锁同改。

## 现场反馈第三轮（2026-10-25 00:07 截图）修复记录

线协议升至 `26_1_2_revision_13`（`revision_12` 按策略保留为 query-only 探针）：

1. **按钮视觉溢出至面板边框**：面板贴图内框实际为相对 +5..+114 / +6..+55；总览文字改 4 行紧凑排版（标题/书架/档位/速率分行），按钮两行移至 +36/+46、行高 9，完整落在内框内。
2. **贴图未铺满判定区、灰白底未扣**：AI 源图的圆角与摄影棚阴影被一并裁入。现处理管线改为裁切→缩放→**边缘拉伸填充**（背景连通区以最近的深色面像素铺满），三张贴图均为不透明全幅，绘制即判定区。
3. **「剩余时间」被省略号挤掉**：原速率与档位挤在同一行。现拆为独立两行——「第 X 档 · 还差 XX 架至下一阶段」与「当前速率 XX/次」各占一行，不再截断。
4. **左侧书名被装饰条挤占**：行牌匾内裁剪区为 dy+4..dy+21（行高 25），原标题起笔 +3、状态行至 +23 均被裁。现三行收进 +5/+11/+16 并微调字号。
5. **恢复显示必须落到具体附魔（设计）**：机制上耐久是**按次扣在玩家所用的那一种附魔上**，降级也只降那一种；耗尽且书上再无其他附魔才化作褪魔残卷。现服务端在每次耐久消耗时把所用附魔记入书本（`cet_last_used_enchant`），纂刻的书条目新增 `last_used_*` 与耗尽代价预览（降为 X 级 / 失去该附魔 / 化作褪魔残卷；一次性消耗模式另有「下次使用后…」文案），在悬停提示中与充能计时并列显示，不再是无主语的裸计时。

## 现场反馈第四轮（2026-10-25 00:55 截图）修复记录

线协议升至 `26_1_2_revision_14`（`revision_13` 保留为 query-only 探针；成本表载荷新增 6 个共鸣配置字段）：

1. **含锁定书的书架可开配置破坏 + 新设定全部可配**：`library_resonance.json` 新增 `tome_pages` 区：`locked_shelf_breakable`（默认开，含锁定母本的书架可破坏、书带锁掉落）、`locked_book_extraction_blocked`（默认开）、`early_unlock_xp_levels`（1）、`early_unlock_grit_count`（1）、`sacrifice_required`（12，按进度准则总数封顶）、`tome_page_required_shelves`（4）。六项贯通 `BundledDefaults`/`LibraryResonance.Config`/GUI 运行时配置与目录（条目 57→63）/服务端与文案读取点，提前解锁费用不再是硬编码常量。
2. **按钮底图左侧拉伸条**：弃用「单张贴图拉伸到任意按钮框」与边缘拉伸管线；5 个按钮改为按各自实际尺寸（32/32/31/48/50×9）逐尺寸 2x 绘制的面板同语言学底图（`tome_button_0..4`），页签同法 150×36，绘制即判定区、无任何方向拉伸。
3. **装饰条压字整体排查**：纂刻总览四行整体下移 1px（9/16/23/30）避开顶饰条；其余面板标题（洗净 +10、仪式 +9、启迪 +10）与书行三行（牌匾内裁剪 +5/+11/+16）已逐一核对均在内框安全区内。
4. **无限书显示无限身份**：带 `minecraft:infinity` 的书在纂刻页书行与悬停提示中按物品提示同式呈现——「无限」全名（原版灰）+ 描述（若语言包含）+ LIGHT_PURPLE 的 `master_tome_infinite` 行，永不渲染「耐久 X/Y」；仅耐久免疫而非无限的书仍显示「耐久免疫」。
5. **粒子反馈（建议采纳）**：上锁与附魔耐久完全耗尽（降至移除/化作残卷）→ 书所在格位末影人传送粒子（PORTAL，约 2.5 秒）；充能 +1/充满/手动提前解锁 → 末地烛粒子（END_ROD，约 2 秒）；全域突发数上限 48、每 tick 少量发射，性能可控（`TomeParticleEffects`）。

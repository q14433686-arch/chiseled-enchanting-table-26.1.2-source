# Enchantment Fusion (融合附魔)

![Enchantment Fusion icon](./icon.png)

**Current version:** `0.6.0`
**Target:** Minecraft `26.1.2`, Fabric Loader `>=0.19.3`, Fabric API `0.151.0+26.1.2`, Java `>=25`.

📖 **Full Game Design Document (完整玩法与系统设计定稿): [`docs/GDD.md`](docs/GDD.md)**

**Enchantment Fusion (`enchantment_fusion`)** is a Vanilla+ enchanting mod built around **order-sensitive binary enchantment fusion (`[A ▷ B] ≠ [B ▷ A]`)**, **Trajectory Path-Cache Mechanics (`路径暂缓存判定`)**, and **Cross-Domain Transmutations (`异位化形`)** without breaking vanilla enchantment level caps:

- **Universal Host Inscription (`万物皆可铭刻`) + 174 Unique Emergent Signature Resonances (`衍生语义共鸣`)** — **Any** fused pair (`[A ▷ B]` or `[B ▷ A]`) can be inscribed onto **any** weapon, spear, tool, bow/crossbow, trident, mace, armor piece, or fishing rod within `1–2` **`◇` Vessel Slots (`器魂槽`)** (Gold gear including `Golden Spear` naturally holds `2◇`). Features **174 unique directional Signature Resonances** crafted around emergent conceptual wordplay across all **43** vanilla `26.1.2` enchantments including `minecraft:lunge` (`突进`) — every reversed order `[B ▷ A]` yields a distinct mechanic (`回马金枪 / 人枪合一`, `怀璧其罪 / 顺手牵羊`, `秋风扫叶 / 雁过拔毛`, `庖丁解牛`, `余音绕梁`, `薪火相传`, `真金火炼 / 火中取栗`, `苦肉修身 / 卧薪尝胆`, `穿针引线 / 势如破竹`, `水火淬刃 / 沸泉裂石`, `镜花水月 / 吹毛断发`), with full multi-slot & multi-enchantment stacking across equipped gear.
- **Temporary Trajectory Path-Cache Engine (`路径暂缓存判定`) & Multi-Mode Spear Triggers (`矛多模态触发`)** — Launched Arrows, Tridents, and projected/thrown Swords, Axes, and Spears record sampled flight corridors with swept-capsule hit detection, delayed path afterimages (`余音回响 / 回马金枪`), elemental trail wakes, silk-tether stitching, and cross-path X-slash resonance (`双轨交错·十字斩`). Spears (`#minecraft:spears`) **preserve 100% of vanilla Hold-Right-Click couched charging (`按住右键蓄力`)** by splitting effects across hold-charge sustain/release (`贯冲枪芒`), sprint/airborne thrusts, jab strikes, and `Sneak + Right-Click` active javelin throws.
- **Cross-Domain Transmutations (`异位化形` · 140 Variants · `0◇` Vessel Slots · Exclusive to `附魔融合台`)** — Vanilla Anvils strictly obey native compatibility and can **never** perform `化形铭刻` (even in Creative Mode); inscribing a **single** non-native Enchanted Book belongs exclusively to **`附魔融合台` Tab ② (`器魂铭刻`)** at **`0` `◇` Vessel Slots** and stacks freely alongside native enchantments and binary fusions! Covers all **43** vanilla enchantments across Swords, Spears, Axes, Maces, Tridents, Bows, Crossbows, Dig Tools, Shields, Armor, **Elytra**, and **Fishing Rods** — including post-`playerDestroy` `RecipeType.SMELTING` **「自动熔炼 / 炭火熔伐」** (preserving Fortune drop counts!), direct Right-Click **3D Boomerang Flying Blades (`飞剑掷斧`)**, **`沸水炙钩 / 灵犀自钓`**, **`御雷巡翼 / 乱流升羽`**, **`沂水`**, **`锯刃`**, and **`三美金 ($3 Bill)`**:
  - **`火焰附加 / 火矢` on Tools & Axes** — **「自动熔炼」** smelts real post-Fortune ore/clay/sand/stone/debris drops via live furnace recipes, and **「炭火熔伐」** smelts felled Logs into Charcoal (`60% / 100%` by level; hold **Sneak** to pause smelting!).
  - **`水下速掘` on Weapons, Spears & Bows** — **「沂水」**: standing in water or rain extends attack reach (`+1.5` blocks) and boosts wet-combat damage.
  - **`灵魂疾行` / `荆棘` / `时运` / `突进` Cross-Domain** — **「追魂步 / 幽魂脱兔 / 引魂矢」**, **「锯刃」** (bleed + yanks **all** hit monsters including Creepers toward you!), **「三美金 ($3 Bill)」** random proc roulette, and **`26.1.2` `突进` (`寸步突刺 / 跃射步 / 疾行突步 / 掘进冲刺`)**.
  - **Special Non-Damageable Relics (`不死图腾` / `玻璃瓶` / Consumables)** — **`耐久`** grants **「裂而不碎」** (`10% / 18% / 25%` non-bypassing Totem save without shattering) & consumption-verified **「经久省用」**; **`经验修补`** grants **「凝灵成瓶」** (`Glass Bottle` → `Bottle o' Enchanting`), **「汲灵增殖」** (`+1` consumable count), & **「汲灵愈体」**.
- **2-Tab Pixel-Art Workstation (`308×214`) + Standalone & Embedded `《融合秘典》 (Fusion Grimoire)` (`360×224`)**:
  1. **① Book Fuse (`附魔融合`)** — Condense `Fused Enchanted Books` directly without needing a blank book (including atomic multi-enchant same-book `[A + B]` synthesis) and use the always-visible **Flip / Split** toolbar at any time.
  2. **② Gear Sockets (`器魂铭刻`)** — Manage `1–2` `◇/◇◇` Vessel Slots, fuse gear enchantments 2-in-1 in-place, or toggle **`[✦ Single Only / 仅单附魔]`** (`Shift+Click` shortcut) for `0◇` Cross-Domain Transmutations.
  3. **Three-Stage Discovery (`未知 → 初识 → 通晓` / `Unknown → Glimpsed → Mastered`)** — Unlock clues automatically by carrying Enchanted Books or deciphering dungeon/Librarian **`《融合残页》 (Tattered Fusion Pages)`**, and master full mechanics through synthesis, combat triggers, or Ancient Manuscripts.
- **Datapack Economy (`economy.json`), Native `纂刻附魔台` Cleanse & Curse-Purge Integration & Rich Audio-Visual Rituals** — Low-tier synthesis has **`0%` failure**; mid-tier fluctuations **never destroy books or gear**; max-level repulsive clashes can mutate into 6 double-edged Aberrant Fusions (`畸变词条`). When paired with sister mod **`纂刻附魔台` (*Chiseled Enchanting Table*)**, Aberrant Fusions natively appear in its **Curse Purge** list (purifying the side-effect while keeping the pure `[A ▷ B]` fusion!), while clean fused pairs, `融合附魔书`, and `异位化形` support native **Book Extraction, Step-Down, Targeted Strip, and Full Disenchant** with XP essence refund. Includes 4-slot 3D orbital levitation on `附魔融合台` and pixel-art rune halos, shockwave rings, and spark particles inside both UIs.

## Installation

Grab `enchantment-fusion-0.6.0.jar` from GitHub Actions build artifacts and place it in `mods/` alongside Fabric API `0.151.0+26.1.2`.

## License

MIT License — see [`LICENSE`](LICENSE).

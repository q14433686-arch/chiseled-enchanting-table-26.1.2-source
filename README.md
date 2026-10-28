# Enchantment Fusion (融合附魔)

![Enchantment Fusion icon](./icon.png)

**Current version:** `0.3.1`
**Target:** Minecraft `26.1.2`, Fabric Loader `>=0.19.3`, Fabric API `0.151.0+26.1.2`, Java `>=25`.

📖 **Full Game Design Document (完整玩法与系统设计定稿): [`docs/GDD.md`](docs/GDD.md)**

**Enchantment Fusion (`enchantment_fusion`)** is a Vanilla+ enchanting mod built around **order-sensitive binary enchantment fusion (`[A ▷ B] ≠ [B ▷ A]`)** and **Cross-Domain Single Enchantment Transmutations (`异位单附魔化形`)** without breaking vanilla enchantment level caps:

- **Universal Host Inscription (`万物皆可铭刻`) + 108 Handcrafted Signature Resonances** — **Any** fused pair (`[A ▷ B]` or `[B ▷ A]`) can be inscribed onto **any** weapon, tool, bow/crossbow, trident, mace, armor piece, or fishing rod across all equipment barriers. Features **108 handcrafted directional Signature Resonances** across all 42 vanilla enchantments (e.g., `[Fire Aspect ▷ Frost Walker]` *Steam True Burn* armor-ignoring true damage vs `[Frost Walker ▷ Fire Aspect]` *Frostfire Ice Cage* Slowness + Fire + `3×3×3` ice cage; `[Fortune ▷ Protection]` *Lucky Golden Body* damage immunity at `2x` durability cost) backed by the `8 × 14` universal resonance matrix.
- **Cross-Domain Single Enchantments (`异位单附魔化形` · 111 Variants · `0◇` Vessel Slots)** — Keep the vanilla Anvil untouched against mis-clicks while inscribing a **single** cross-domain Enchanted Book exclusively in **`附魔融合台` Tab ② (`器魂铭刻`)** at **`0` `◇` Vessel Spirit slots**! Cross-domain enchantments replace their vanilla line directly with their transmuted name (`[Shift]` reveals source & details), including:
  - **`火焰附加 / 火矢` on Tools & Axes** — **「自动熔炼」** on Pickaxes/Shovels (lower `Fire Aspect` level slows mining speed more) and **「炭火熔伐」** on Axes (Logs drop Charcoal at ~100%; Planks have a chance to burn to ash or drop Charcoal).
  - **`水下速掘` on Weapons & Bows** — **「沂水」**: standing in water or rain extends attack reach (`+1.5` blocks) and boosts wet-combat damage.
  - **`灵魂疾行` / `荆棘` / `时运` Cross-Domain** — **「追魂步 / 幽魂脱兔 / 引魂矢」**, **「锯刃」** (bleed + yanks **all** hit monsters including Creepers toward you!), and **「三美金 ($3 Bill)」** random elemental/status roulette.
  - **Special Non-Damageable Relics (`不死图腾` / `玻璃瓶` / Consumables)** — **`耐久`** grants **「裂而不碎」** (`10% / 18% / 25%` Totem survival without shattering) & **「经久省用」**; **`经验修补`** grants **「凝灵成瓶」** (empty Glass Bottle absorbs XP into `Bottle o' Enchanting`), **「汲灵增殖」** (`+1` consumable count), & **「汲灵愈体」**.
- **3-Tab + Mode C Enchantment Picker + Gem-Like Vessel Sockets (`308×214` Pixel-Art Workstation)** — Classic `16×12×16` 3D Enchanting Table slab geometry in-world and in-hand:
  1. **① Book Fuse (`附魔融合`)** — Condense `Fused Enchanted Books` directly without needing a blank book.
  2. **② Gear Sockets & Transmute (`器魂铭刻`)** — Manage `1–2` `◇/◇◇` Vessel Spirit sockets, fuse gear enchantments 2-in-1 in-place, or **inscribe `0◇` single cross-domain transmutations** (right-click `[B]` to toggle single mode).
  3. **③ Compendium (`拆解图鉴`)** — Flip or split `Fused Enchanted Books` and browse all 108 Signature Resonances.
- **6 Aberrant Mutations (`畸融异变`) & Sister-Mod Synergy** — Unstabilized max-level mutually-exclusive clashes risk mutating into 6 double-edged Aberrant Fusions cleansable **only** via *Chiseled Enchanting Table* (`雕纹附魔台` / `纂刻附魔台`).

## Installation

Grab `enchantment-fusion-0.3.1.jar` from GitHub Actions build artifacts and place it in `mods/` alongside Fabric API `0.151.0+26.1.2`.

## License

MIT License — see [`LICENSE`](LICENSE).

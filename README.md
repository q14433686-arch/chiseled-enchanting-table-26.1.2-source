# Enchantment Fusion (融合附魔)

![Enchantment Fusion icon](./icon.png)

**Current version:** `0.2.0`
**Target:** Minecraft `26.1.2`, Fabric Loader `>=0.19.3`, Fabric API `0.151.0+26.1.2`, Java `>=25`.

📖 **Full Game Design Document (完整玩法与系统设计定稿): [`docs/GDD.md`](docs/GDD.md)**

**Enchantment Fusion (`enchantment_fusion`)** is a Vanilla+ enchanting mod built around **order-sensitive binary enchantment fusion (`[A ▷ B] ≠ [B ▷ A]`)** without breaking vanilla enchantment level caps:

- **Universal Host Inscription (`万物皆可铭刻`) + 108 Handcrafted Signature Resonances** — **Any** fused pair (`[A ▷ B]` or `[B ▷ A]`) can be inscribed onto **any** weapon, tool, bow/crossbow, trident, mace, armor piece, or fishing rod across all equipment barriers. Features **108 handcrafted directional Signature Resonances** across all 42 vanilla enchantments (e.g., `[Fire Aspect ▷ Frost Walker]` *Steam True Burn* armor-ignoring true damage vs `[Frost Walker ▷ Fire Aspect]` *Frostfire Ice Cage* Slowness + Fire + `3×3×3` ice cage; `[Fortune ▷ Protection]` *Lucky Golden Body* damage immunity at `2x` durability cost vs `[Protection ▷ Fortune]` *Fortune's Grace* `1`-HP fatal save) backed by the `8 × 14` universal resonance matrix.
- **3-Tab + Mode C Enchantment Picker + Gem-Like Vessel Sockets (`308×214` Pixel-Art Workstation)** — Classic `16×12×16` 3D Enchanting Table slab geometry in-world and in-hand:
  1. **① Book Fuse (`附魔融合`)** — Insert Enchanted Books (including multi-enchant books!), left-click `[A Primary]`, right-click `[B Secondary]`, or click **`[⇄]`** to condense a `Fused Enchanted Book` directly (**no blank plain book needed**).
  2. **② Gear Sockets (`器魂铭刻`)** — Manage `1–2` `◇/◇◇` Vessel Spirit sockets like gem cards (inlay `Fused Enchanted Books`, inline `[⇄]` flip, clean `[Out]` unsocket back to book, expand capacity with `Echo Shard + Amethyst Cluster`, or **fuse existing enchantments on the gear itself 2-in-1 in-place**).
  3. **③ Compendium (`拆解图鉴`)** — Flip or split `Fused Enchanted Books` and browse all 108 Signature Resonances.
- **6 Aberrant Mutations (`畸融异变`) & Sister-Mod Synergy** — Unstabilized max-level mutually-exclusive clashes risk mutating into 6 double-edged Aberrant Fusions (`狂血反噬`, `走火雷劫`, `贪婪黑洞`, `反向滑铲`, `敌我同冰`, `烫手山芋`) that **cannot** be removed by Grindstone, Anvil, or Fusion Table — cleansable **only** via *Chiseled Enchanting Table* (`雕纹附魔台` / `纂刻附魔台`).

## Installation

Grab `enchantment-fusion-0.2.0.jar` from GitHub Actions build artifacts and place it in `mods/` alongside Fabric API `0.151.0+26.1.2`.

## License

MIT License — see [`LICENSE`](LICENSE).

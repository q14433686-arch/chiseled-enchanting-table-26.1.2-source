# Enchantment Fusion (融合附魔)

![Enchantment Fusion icon](./icon.png)

**Current version:** `0.1.0`
**Target:** Minecraft `26.1.2`, Fabric Loader `>=0.19.3`, Fabric API `0.151.0+26.1.2`, Java `>=25`.

📖 **Full Game Design Document (完整玩法与系统设计定稿): [`docs/GDD.md`](docs/GDD.md)**

**Enchantment Fusion (`enchantment_fusion`)** is a Vanilla+ enchanting mod built around **order-sensitive binary enchantment fusion (`[A ▷ B] ≠ [B ▷ A]`)** without breaking vanilla enchantment level caps:

- **Primary Carrier `[A]` + Boundless Secondary Graft `[B]`** — Slot `A` determines equipment eligibility and trigger cadence (`8` trigger carriers), while Slot `B` accepts **any** enchantment across all equipment domains (`14` adaptive payload cores across Melee, Ranged, Tool, and Armor hosts).
- **Restrained Vessel Capacity (`器魂槽`)** — Standard gear holds `1` fused pair (`◇`), Gold gear naturally holds `2` fused pairs (`◇◇`) out of the box, and non-gold gear can inlay `+1` extra slot (strictly capped at `2` total) via `Echo Shard + Amethyst Cluster`. Unfused vanilla enchantments consume `0` vessel slots.
- **Enchantment Fusion Table (`附魔融合台`)** — Persistent 4-slot workstation with a one-click **`[⇄]` Swap Order** button, live `[A ▷ B]` vs `[B ▷ A]` preview, `Fused Enchanted Book` creation, direct gear grafting, half-level top-ups, order flipping, clean splitting, and optional zero-hard-dependency synergy with *Chiseled Enchanting Table*.

## Installation

Grab `enchantment-fusion-0.1.0.jar` from GitHub Actions build artifacts and place it in `mods/` alongside Fabric API `0.151.0+26.1.2`.

## License

MIT License — see [`LICENSE`](LICENSE).

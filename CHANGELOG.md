# Changelog

## [0.3.0] - 2026-10-29

### Phase 3 — Cross-Domain Single Enchantment Transmutations ("异位单附魔化形")

- **Fusion-Table-Exclusive Single Cross-Domain Inscription (`0◇` Vessel Spirit Slots)**:
  - Keeps the vanilla Anvil unchanged to prevent accidental mis-clicks; inscribes single cross-domain Enchanted Books onto non-native equipment and eligible special relics (`Totem of Undying`, `Glass Bottle`, consumables/throwables) exclusively in **`附魔融合台` Tab ② (`器魂铭刻`)**, consuming **`0`** `◇` Vessel Spirit slots (right-click `[B]` to toggle single mode at any time).
  - Replaces vanilla standalone enchantment lines directly in-place on item tooltips with their transmuted variant titles (e.g., `✦ 自动熔炼 II`, `✦ 沂水`, `✦ 锯刃 II`), revealing source enchantment and detailed cross-domain mechanics when holding `[Shift]`.
- **111 Cross-Domain Variants Across All 42 Vanilla Enchantments**:
  - Implements all user-directed rules including `Fire Aspect / Flame` Auto-Smelt Pickaxe (low-level `Fire Aspect` mining slowdown) & Charcoal/Ash Axe (`~100%` Charcoal on Logs, Ash/Charcoal chance on Planks), `Aqua Affinity` *Yishui* (`+1.5` reach & wet bonus damage in water/rain), `Soul Speed` (*Soul-Chase Step / Phantom Hare / Soul-Seeker Bolt*), `Thorns` *Serrated Hook* (bleed + indiscriminate monster yank toward player including Creepers) / *Barbed Arrow* / *Spiked Shield*, `Fortune` *Three Dollar Bill* roulette, `Fire/Blast Protection` indestructible dropped items, `Channeling` strict open-sky thunder rod & 100% lightning-immune armor, `Wind Burst` underwater suppression, `Binding Curse` sticky return & hit-root, and `Unbreaking` / `Mending` on `Totem of Undying`, `Glass Bottle` (`Bottle o' Enchanting` condensation), and consumables.
  - Expands all 11 locale files to `588` keys with 100% parity, pure-JVM JUnit coverage, and live 26.1.2 `ClientSmoke` verification.

## [0.2.0] - 2026-10-28

### Phase 2 — 3-Tab Workstation, Universal Host Inscription, 108 Signature Resonances & 6 Aberrant Mutations

- Classic `16×12×16` 3D Enchantment Fusion Table block & item with localized `BlockItem` prefix, `308×214` 3-tab pixel-art UI, `universal_any_gear` binary inscription, `108` handcrafted directional Signature Resonances, and `6` Aberrant Fusions cleansable exclusively via *Chiseled Enchanting Table*.

## [0.1.0] - 2026-10-28

### Phase 1 — Core Skeleton, Enchantment Fusion Table & 8×14 Adaptive Matrix

- **Vessel Capacity (`器魂槽`) & Data Components (`fused_pairs`, `vessel_bonus`)**:
  - Standard equipment holds `1` fused pair (`◇`); Gold equipment naturally holds `2` fused pairs (`◇◇`); non-gold gear can expand once to `2` (`◇◇`) using `Echo Shard + Amethyst Cluster`.
  - Primary enchantment `A` remains in vanilla `DataComponents.ENCHANTMENTS` for `100%` native combat/mining compatibility while `[A(x) ▷ B(y)]` drives composite tooltips and secondary payload execution.
- **Enchantment Fusion Table (`附魔融合台`), `Fused Enchanted Book` & `Resonance Stabilizer`**:
  - Added custom pixel-art block, items, recipes, block-entity persistence, floating item renderer, and custom 9-slice GUI with instant **`[⇄]` Swap Order** preview, book-to-book fusion, book-to-gear inscription, direct gear grafting, half-level top-up, order flipping, and clean splitting.
  - Added zero-hard-dependency soft synergy bridge for `chiseled-enchanting-table` (`arcane_catalyst` as `0-XP` stabilizer, `cleansing_grit` as `0-XP` splitter/flipper, and `#minecraft:bookshelf_books` compatibility for `Fused Enchanted Book`).
- **8 Triggers × 14 Payloads Universal Cross-Domain Matrix**:
  - Datapack-driven `matrix_profiles.json` & `repulsive_pairs.json` covering all 42 vanilla enchantments plus automatic fallback inference for modded enchantments across Melee, Ranged, Tool, and Armor hosts.
- **11-Language Localization & CI Suite**:
  - Ships full localization across all 11 locales (`de_de`, `en_us`, `es_es`, `fr_fr`, `it_it`, `ja_jp`, `ko_kr`, `pt_br`, `ru_ru`, `zh_cn`, `zh_tw`), JUnit 5 verification, and headless server + client smoke probes.

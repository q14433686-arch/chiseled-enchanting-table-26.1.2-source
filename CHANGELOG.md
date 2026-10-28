# Changelog

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

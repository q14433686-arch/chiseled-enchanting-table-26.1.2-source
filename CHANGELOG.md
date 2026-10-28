# Changelog

## [0.4.0] - 2026-10-29

### Phase 4 — 26.1.2 Lunge & Spears, Standalone Fusion Grimoire, 3-Stage Discovery, Tiered Risk & Full Runtime Effect Fixes

- **Full Minecraft `26.1.2` Enchantment & Equipment Coverage (`minecraft:lunge` + Spears + Copper Gear)**:
  - Added `minecraft:lunge` (`突进`, `I..III`) across the `43`-enchantment matrix (`ON_MELEE_STRIKE / PIERCE`), `6` directional Signature Resonances (`疾风突刺`, `苍穹坠矛`, `赤焰龙枪`, `焚锋突袭`, `破浪突刺`, `激流贯矛`), and `4` non-Spear Cross-Domain Transmutations (`寸步突刺`, `跃射步`, `疾行突步`, `掘进冲刺`).
  - Added `TargetRole.SPEAR` (`#minecraft:spears` across Wooden/Stone/Copper/Iron/Golden/Diamond/Netherite Spears) with `minecraft:golden_spear` holding `2◇` innate Vessel Spirit slots and full cross-domain weapon support (`沂水`, `追魂步`, `锯刃`, `三美金`, `重劈横扫`, `飞剑掷斧`).
- **Standalone `《融合秘典》 (Fusion Grimoire)` + Streamlined 2-Tab Workstation + 3-Stage Discovery**:
  - Added craftable `《融合秘典》` (`Book + Lapis Lazuli + Amethyst Shard`) featuring a double-page parchment Grimoire UI (`360×224`, 4 chapters, live search bar) and an embedded top-right Grimoire button inside `附魔融合台` that opens the manual in-place without closing the container or dropping slot items.
  - Streamlined `附魔融合台` to `2` workshop tabs (`附魔融合` with integrated Fused-Book Flip/Split + `器魂铭刻`).
  - Implemented Herbcraft-style 3-stage progressive discovery (`未知 ??? -> 初识 [20-tick inventory Enchanted Book scanner or Tattered Page] -> 通晓 [Fusion Table synthesis/conversion, live gameplay trigger, or Ancient Manuscript]`) plus chest-loot and Librarian-traded `《融合残页》` (`Common / Rare / Aberrant`).
- **Frost Walker `Blocks.FROSTED_ICE` (`霜冰`) Cages & Snares**:
  - Replaced permanent `Blocks.ICE` / `PACKED_ICE` cages with vanilla Frost Walker `minecraft:frosted_ice` (`霜冰`) backed by scheduled cracking ticks (`stage 0..3`) and clean dry-land / water decay so terrain is never permanently griefed.
- **Tiered Fees & Failure Probability (`tiered_risk`)**:
  - Synthesis and cross-domain conversion now charge tiered XP Levels + Enchanting Reagents (`Lapis Lazuli / Amethyst Shard / Emerald`, deducted from Catalyst slot or player inventory) and roll failure chances reducible to `0%` via `Resonance Stabilizers`.
  - Normal failures keep host equipment / primary books 100% safe, consume `1` secondary/source book, and refund `50%` of fees while unlocking Stage I clues; max-level / repulsive failures mutate into Aberrant Fusions or apply curse taint.
- **Complete Runtime Trigger & Effect Overhaul**:
  - Fixed `ProjectileImpactFusionMixin` (`Projectile#onHit` with pre-hit weapon snapshot for Bows, Crossbows, Tridents, and Fishing Bobbers), preserved `target.invulnerableTime` across melee `AttackEntityCallback` bonus hits, added `LivingEntityConsumableFusionMixin` (`completeUsingItem`) for food/potion `经久省用`, wired Fishing Rod reel triggers, fixed `虚空归返` full-column void item rescue, and implemented all remaining `SignaturePairs` (`frost_meteor_fall`, `soul_stride_landing`, `reactive_plate`, `raging_hook`, `tidal_double_catch`) and `CrossDomainTransmutations` (`blast_breech`, `blast_guard`, `bracer_guard`, `miner_guard`, `guardian_ward`, `shroud_cloak`, `silent_pick`, `titan_brace`).

## [0.3.1] - 2026-10-29

### Hotfix — Server Tick DoT Crash Fix & Re-Entrancy Hardening

- Fixed a `java.lang.UnsupportedOperationException` server tick crash in `SignaturePairs.tickWorldDots` (`SOUL_FIRE_TICKS` / `SKYFIRE_BOLT_TICKS`, triggered by `[灵魂疾行 ▷ 火焰附加]` *Nether Soul-Fire* and `[火焰附加 ▷ 引雷]` *Skyfire Bolt*) caused by calling `Map.Entry#setValue` inside `ConcurrentHashMap.entrySet().removeIf(...)` on Java 25, and prevented multi-dimension ticks from prematurely clearing active Overworld DoTs.
- Added `ThreadLocal` re-entrancy guards around `AFTER_DAMAGE` reactive hooks, `CrossDomainTransmutations.onAfterDamage`, and 3×1 `SignaturePairs` mining, plus JUnit & live `ClientSmoke` regression assertions.

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

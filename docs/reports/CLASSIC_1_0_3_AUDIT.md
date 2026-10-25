# Classic 1.0.3 Preset Audit

Ground truth: git tag `26.1.2_1.0.3` (`09f854ebf8a677b8387606001ffedb6662ae7611`),
extracted via `git archive` and diffed file-by-file against the 1.1.0 tree.
Principle: restore 1.0.3 **mechanisms** under the preset; keep genuine post-1.0.3
**bug fixes** (they are not gameplay rules).

## Confirmed deviations found and fixed

| # | Deviation | Fix |
|---|-----------|-----|
| 1 | `classic_1_0_3_mode` was a lone flag: turning it on without the preset button left Epiphany chances, resonance discounts, cleanse, tome wear/regen and batch transformations at Standard values — mechanism live, panel hidden. | `ModRuntimeConfig` compact constructor now forces the full classic field bundle whenever `classic103Mode` is true (single choke point for preset / toggle / JSON / datapack merge). Also forces `bookshelfEnchantedProb=0.03` (1.0.3 `bookshelf_book_pool.json`). |
| 2 | Resonance discount: only the total cap was 0; JSON sub-caps (global 20 / base 35 / specific 15) survived in the runtime `LibraryResonance.Config`, so components computed >0 and leaked into floating texts (`额外 -15%（共 -0%)`, cleanse tooltip). Discount was "hidden text", not disabled. | `ChiseledEnchantingTableData.applyRuntimeConfig` zeroes every sub-cap under classic. Handler `epiphanyChance→0`, `epiphanyMaxSlots→0` hard-guards added as belt-and-braces. |
| 3 | Epiphany ↔ treasure refresh: probability was clamped by `maxChance=0`, but slot/tier machinery (`epiphanyMaxSlots`, `rollEpiphanyTier`, `TREASURE_EPIPHANY` widget rows, freeze/reroll server handlers, epiphany seed advance, tome-page sacrifice) had no classic guard and stayed reachable by payload. | Data layer zeroes per-shelf/per-tome chances in classic; handler returns empty epiphany list / 0 slots / 0 chance; `canAffordFreeze/Reroll` reject; `advanceTableEpiphanySeed` no-op; `handleTomePageAction` rejects at entry (sacrifice previously ran before the unlock check). In 1.0.3 treasure enchantments are static default/stored unlocks — no refresh relation exists. |
| 4 | 「仅补差价」 text remained while classic charges full material + full XP: widget `upgrade_from` annotation and screen `pricing.incremental` / `pricing.master_tome`（原版 1–3 级） triggered with no classic check, contradicting the XP badge's 「消耗规则：全额经验费用」. | Widget annotation gated on `!isClassic103Mode()`; row pricing tooltip gains a classic branch (`pricing.classic_1_0_3`) stating full XP cost; `xp_mode.full.master_hint` suppressed in classic; `pricing.book_transfer` gets a classic variant (`pricing.book_transfer_classic`: source book returns as plain Book, not Depleted Tome). New keys in all 11 locales. |
| 5 | Ritual transcription slip （抄错） / cross-line pollution could still fire in classic for wear-immune tomes (tome KEEP still yields `usedInfinityTome=true`). 1.0.3 inscriptions are instant and exact. | Slip probability forced to NONE/`0.0` in classic in both `ChiseledEnchantingTableBlockEntity.completeActiveRitual` and the headless `EnchantmentApplication` fallback. |
| 6 | Server direct-upgrade fallback `canDirectUpgradeExistingEnchantment` accepted classic payloads the widget had hidden. | Gated with `!isClassic103Mode()` in `EnchantmentApplication.apply`. |
| 7 | Classic unlocked set expanded to levels `1..top` (post-1.0.3 direct-tier system); 1.0.3 granted only the single shelf-boosted highest level. | `ChiseledEnchantingTableBlock` classic branch adds only the top level. |
| 8 | Research: widget offered classic research rows unconditionally, but the server required `canDirectResearchUpgradeTo` (post-1.0.3 bookshelf tier gate) — UI offered, server rejected. | Classic bypass in `canDirectResearchUpgradeTo` (any non-curse target up to definition max), aligned with widget + 1.0.3. |

## Deliberately NOT reverted (post-1.0.3 fixes, not mechanisms)

- `XpCostConversion.pointsForLevelCost` (1.0.3 used `giveExperienceLevels(-N)`, which mis-charges across level boundaries).
- LAN-leak guard on synced cost rows; audit H-01 multi-enchant combine charge.
- Anvil flat repair cost / chest-loot: shipped 1.0.3 `build.gradle` **excluded** `RemoveXpCostScaling`, `structureProcessor/**` and `modifyChestLootTable/**` from compilation, so current `anvilFlatRepairCost=false` + `KEEP_BOTH` are already faithful — the stale source files in the 1.0.3 tree were never active.
- Worldgen deadlock fix, cost-table sync, JEI/REI integration.

## Known remaining datapack-level drift (out of preset scope)

The preset is config-only; datapack JSON still differs from 1.0.3
(`enchantment_costs.json` level costs, `book_upgrades.json` material multipliers,
`bookshelf_book_pool.json` `max_level_weight_ratio` 0.15 vs 0.1). Servers wanting
byte-exact 1.0.3 numbers can override via datapack; the preset does not silently
rewrite datapack content.

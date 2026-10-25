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

## Second round — external report `analyze-103-preset-discrepancies.zip`

That report audited revision `360551d` (before the fixes above). Findings 01–05
and 10 were already addressed by the first round (slip NONE, epiphany server
gates, incremental copy, pricing source, resonance sub-caps, 0.03 pool rate).
The remaining items were handled as follows:

| Finding | Disposition |
|---|---|
| 06 cost table | **Fixed.** `enchantment_costs_classic_1_0_3.json` (byte copy of the tag) ships in the jar; the classic RuntimeFrame parses it instead of the 1.1.0 table (Mending I 35, Protection IV 25, …). |
| 07 book upgrades | **Fixed.** `book_upgrades_classic_1_0_3.json` likewise supplies classic research material multiplier (0.5), default Lapis 1/level and themed overrides (Sharpness→Flint, …). The multi-enchant combine anti-arbitrage charge is kept (report caveat). |
| 08 apple emerald | **Fixed.** Classic transformations now parse from `item_transformations_classic_1_0_3.json`; the handler resolves the emerald 2–5 range per player with the original stable-hash formula (`classicTransformationCosts`). |
| 09 unlock filter | **Fixed.** In classic, `defaultEnchantments(lookup)` applies no treasure/curse/epiphany exclusions, matching the old tag expansion; dual-tagged third-party enchantments unlock as in 1.0.3. |
| 11 recipes | **Boundary labelled, not switched.** Recipes are static datapack content and cannot follow a runtime toggle; the preset tooltip (`inert_note`, all 11 locales) now says so explicitly, along with the restart timing of the 64-book stack. A future strict-classic resource pack can build on the bundled classic JSONs. |
| 12 grimoire / JEI / REI | **Fixed.** JEI+REI enchanting categories show full XP cost in classic; JEI station/bookshelf info pages have classic variants; REI hides the "up to 0%" resonance line; Grimoire chapters 3p3/4/5/6/8/9 carry a "not in Classic" notice. |
| 13 config linkage | **Fixed.** `ConfigCatalog.CLASSIC_LOCKED_KEYS`: every classic-forced or classic-meaningless option is greyed out under classic with reason `reason.classic_1_0_3`; stored preferences survive and re-activate when classic is turned off. Bookshelf enchanted-prob slider step is now 0.01 so 0.03 is selectable outside classic too. |
| 14 policy truth | **Verified + labelled.** `RuntimeRules.prepare` ends in `Draft.build()`, whose classic normalization re-forces classic fields after any datapack resolution, so a datapack cannot quietly resurrect Epiphany/discounts while classic is on (it can still turn classic itself off — that is governance, not drift). The 64-stack option was already scoped `RESTART_REQUIRED`; the preset tooltip now repeats that timing. |

## Known remaining datapack-level drift (out of preset scope)

`bookshelf_book_pool.json` `max_level_weight_ratio` is 0.15 vs 0.1 in 1.0.3 and
loot-injection tables are 1.1.0 content; both only affect newly generated
structures/chests. The classic enchanted-book probability (0.03) is restored;
pool weights are datapack territory and are not rewritten by the preset.

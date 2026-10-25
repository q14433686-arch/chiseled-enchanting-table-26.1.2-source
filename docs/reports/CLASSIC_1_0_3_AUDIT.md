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

## Third round — recheck report `analyze-103-preset-discrepancies (1).zip` (R2-01…R2-12)

The recheck confirmed rounds 1–2 but found residuals. Five mechanism/data
choices were put to the user (2026-10-25); dispositions follow those decisions.
Note the deliberate semantics change vs. rounds 1–2: **classic is now a mode
flag + an editable profile**, not constructor-level forcing. Hard mechanism
gates (epiphany 0, resonance sub-caps 0, slip NONE, tab/page hiding, salted
evaluation) key on `classic103Mode` itself; every *value* is a live
preference that stays editable after the profile is applied once (preset
button or flipping the switch on). Tests were rewritten to this contract.

| Finding | Disposition |
|---|---|
| R2-01 stack vs `startupOnly` | **Fixed (`suggest_restart`).** `Draft.build()`/compact-ctor no longer force `enchanted_book_stack_size=64`; the preset/toggle writes 64 into the draft (scope `RESTART_REQUIRED`), `RuntimeRules.prepare()` keeps the startup effective value in the snapshot so a draft ≠ boot value no longer throws at bootstrap, and runtime saves with stack 1 under classic succeed. Startup-conflict rejections get a dedicated message (`msg.save_failed_startup`). |
| R2-02 literal `\n` in JEI prose | **Fixed.** `info.station_classic` now carries real line breaks in all 11 locales; `scripts/ci_static_checks.py` fails any lang value containing a literal backslash-n. |
| R2-03 salted evaluation | **Fixed (`faithful_salted`).** 1.0.3 restored verbatim: single material option picked by `stablePositiveHash(id:level:maxLevel:salt:option) % options`, ranged counts by the `:count:` hash, `countPerLevel` added; salt = viewing player's UUID. Applies to table rows, synced cost payload (per viewing player) and JEI/REI rows alike (also closes R2-05). |
| R2-04 research rounding | **Fixed.** Classic research XP uses `floor` (Sharpness III: 15 × 0.5 → 7). |
| R2-05 payload midpoint | **Fixed** by R2-03: `EnchantCostTablePayload.build(player)` emits the same salted rows the table charges. |
| R2-06 JEI resonance line | **Fixed.** JEI tooltip hides the bookshelf resonance-discount line under classic, mirroring REI. |
| R2-07 Grimoire coverage | **Fixed.** The classic notice now shows on every non-home chapter (1, 2, 3 all sub-pages, 4–9). |
| R2-08 JEI info staleness | **Fixed (explicit notice).** JEI/REI info prose registers once per session; when the synced classic flag flips, the client shows `msg.compat_info_refresh` stating the pages refresh after rejoining (recipes follow live). |
| R2-09 datapack overlay | **Fixed (custom decision).** The three `*_classic_1_0_3.json` tables load through the `ResourceManager` in the reload listener — a datapack file at the same path replaces that classic table (`/reload` effective). The config screen gains a **Classic merged page**: `classic_1_0_3_mode` + every classic-locked key, all editable there (numbers included). |
| R2-10 naming | **Fixed.** Preset renamed to "1.0.3 Gameplay Rules / 1.0.3 玩法规则" (11 locales); description lists the rules plus the retained differences (XP-point deduction, registered inert items, current recipes, JEI/REI rejoin note). `pricing.classic_1_0_3` neutralized; Wiki rewritten to match (no more "逐项还原/原汁原味"). |
| R2-11 tome locks | **Fixed (`ignore_locks_classic`).** Under classic, `EnchantmentFinder` and resonance candidate collection ignore lock state (1.0.3 had no locks); the lock flag on the item is untouched and bites again in standard mode. |
| R2-12 inert-but-unlocked keys | **Fixed.** `tier1/2_bookshelves`, `tier3_qualifying_enchantments`, `early_unlock_xp_levels`, `early_unlock_grit_count`, `sacrifice_required`, `tome_page_required_shelves` plus tome-regen/refund/strip/purge companions added to `CLASSIC_LOCKED_KEYS` (greyed out with reason, editable on the Classic merged page). |

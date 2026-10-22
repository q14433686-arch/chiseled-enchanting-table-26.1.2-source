# Sync Report — `26.1.2` line → `26.2` line (2026-10-22)

> **Scope:** port every feature, fix, data file, asset and doc change that landed on the sister
> `chiseled-enchanting-table-26.1.2-source` branch between the shared `1.0.3` baseline (`09f854e`,
> the 26.3-feature back-port) and its tip (`2f62762`, PR #9), onto **this** `26.2` branch.
> **Verdict:** the sync is feature-complete; the only remaining source delta between the lines is
> the set of APIs that genuinely differ between Minecraft 26.1.2 and 26.2 (listed in §3), plus the
> 26.2 toolchain pins (Loom `1.17`, Gradle `9.5.1`, Fabric API `0.155.2+26.2`, JEI
> `30.47.0.239`, REI `26.2.821`, Architectury `21.0.4`), which stay exactly as this branch had them.

---

## 1. What was synced

Both lines shared the same `1.0.3` baseline, so the sync is the 26.1.2-only commit range:

```
09f854e  (baseline, identical content on both lines)
  └── 4a10aaa  Fix unported features, advancement/UI/sync/viewer bugs, and repo debris
  └── 641095e  Implement A+B+C enchanting progression, resonance & multi-option economy
  └── 6655fcb  Fix header badge & table cloth ARGB/Z-fighting; Treasure Epiphany & Arcane Catalyst
  └── 5a47c51  Strict two-path treasure gate, dual scaling, dual-material costs, painted freeze UI
  └── 5c4ea47  Persistent cryo-freeze, single-currency master tomes, 1:1 scrollbar, painted UI, 8 advancements
  └── 022172a  Depleted Tome & Blank Overwrite ritual, book component stacking fix
  └── ec38eb3  Yield-scaled material curves, 12-shelf XP floor, relative-tier Master Tome resonance
  └── bd88b22  Dual-mode vanilla mass-production XP formula, orb/flask badges, 8x crying obsidian recipe
  └── 354ebd1  Row anti-overlap bounds, horizontal XP badge, scissor clipping, centered container slots
  └── b02af0f  Trident rebalance, 7x5x7 converging particles, plain UI copy + Wiki, bidirectional JEI/REI
  └── 5c76af9  fix(rei): DisplayCategory.getDisplayHeight() no-arg signature
2f62762  (tip)
```

**135 paths** changed in that range; **126** of them had no local counterpart on this branch and
were taken verbatim (`git checkout <26.1.2 tip> -- <path>`, including the deletions). The remaining
**nine** are files both lines had edited and were merged by hand — see §2.

### Feature surface now in this tree

| Area | What arrived |
|---|---|
| Progression | Two-track `7×5×7` library: wooden bookshelves (`+1` default level per 5 shelves up to `ceil(maxLevel/2)`), stored Master Tomes in chiseled bookshelves unlocking every level `1..k` 1:1, graduation tiers gated behind tomes |
| Economy | `mechanics/resonance/LibraryResonance` (relative mastery `r = level/maxLevel`, `w(r) = 0.45r + 0.55r²`, `>=12`-shelf discount floor, `-40%` cap), `XpCostConversion.XpRequirement` dual mode, yield-aware `base_count`/`max_level_count`/`level_exponent` curves in every cost table |
| Treasure Epiphany | `data/.../treasure_epiphany.json` + menu/UI support: `>=32` wooden bookshelves, chance/cap scaling, persistent cryo-stasis freeze (`CUSTOM_DATA` + loot `copy_components`), manual reroll, `MM:SS` countdown |
| Items & recipes | `arcane_catalyst`, `depleted_tome`, `blank_overwrite` cleanse loop, `recipe/crying_obsidian.json` (8×), `recipe/chiseled_enchanting_table_upgrade.json`, cheapened base recipe, trident transformation rebalance |
| Book stacking | `DefaultItemComponentEvents.MODIFY` → `MAX_STACK_SIZE = 64` on `Items.ENCHANTED_BOOK`; `RemoveEnchantMerge` now also preserves `count - 1` stacked books in `AnvilMenu#onTake` |
| Anvil | `RemoveXpCostScaling` (`AnvilMenu#calculateIncreasedRepairCost` → flat) is **re-enabled**; `mixin/accessor/ItemAccessor` deleted |
| Dye pipeline | table dyeing via the dye component, `BlockTintSource` + `getRenderData()` sync, `dyed_color` preserved by the block loot table, `color_table` advancement |
| Viewers | `TransformationQueryIndex` + `TransformationRecipeCategory` + `ReiTransformationCategory`; enchantment category now shows primary **and** secondary material slots plus an XP-tier badge; S2C cache invalidation via `SyncedEnchantmentCosts.addListener` |
| UI | painted `gui/chiseled_enchanting_table.png` (680×332), `epiphany_panel*.png`, six row plaques, `xp_orb_1..3` / `xp_ritual_flask` badges, hardware scissor clipping, bounded auto-scaling + ellipsis, `1:1` scrollbar drag, centered container slots `(183, 28)` / `(183, 57)`, plain-spoken copy in 11 locales |
| Advancements | 18 in total (8 new), all localized, `root` now also keys on the upgrade recipe |
| Loot | `mechanics/loot/DepthBasedEfficiencyFunction` restored (Mineshaft Efficiency II–V by Y-depth), wired from `EnchantedBookLootInjector` when an injection is flagged `special: "depth_based_efficiency"` |
| Particles | `ChiseledEnchantingTableBlock#animateTick` and `ChiseledBookshelfTick` now stream `ENCHANT` glyphs from both wooden and occupied chiseled bookshelves across the `7×5×7` volume into the floating book |
| Docs | `docs/WIKI.md` (Chinese gameplay + upstream comparison), rewritten `README`/`CHANGELOG`/`CURRENT_BASELINE`/`NEXT_TASK` |
| Repo hygiene | removed `Dummy.java`/`.class`, `RawOutput.log`, `adapted_handler.java`, `adapt_handler.py`, `append.py`, `fix*.patch`/`fix_*.py` leftovers, `*.orig`, `*.rej`, committed `AbstractContainerScreen.class`, the retired `chiseled-enchanting-table.mixins.json` + `chiseled-enchanting-table.client.mixins.json`, and `block/FloatingBook.java` |

---

## 2. The nine overlapping files (merged by hand)

`CHANGELOG.md`, `README.md`, `fabric.mod.json`, `build.gradle`, `build-reports/compile-java.log`,
`RawOutput.log`, `mechanics/loot/EnchantedBookLootInjector.java`,
`menu/ChiseledEnchantingMenuPayloads.java`, `advancement/root.json`.

| File | Resolution |
|---|---|
| `EnchantedBookLootInjector.java` | took the 26.1.2 version (it already carries this branch's translatable `LORE` doc-comment, because that came from the shared 26.3 back-port) — the only real delta is the `depth_based_efficiency` branch |
| `ChiseledEnchantingMenuPayloads.java` | took the 26.1.2 version: it adds `xp_discount_percent`/`regular_bookshelves`/`chiseled_book_count`/`epiphany_seed`/`stored_master_levels`, the `TREASURE_EPIPHANY` option type, and the `RerollEpiphanyPayload`/`FreezeEpiphanyPayload` pair. It uses `FriendlyByteBuf#writeCollection`/`readCollection`/`readList`, which **26.2 still provides** (only 26.3 removed them; this branch had adopted 26.3's hand-rolled VarInt loops cosmetically) |
| `advancement/root.json` | took the 26.1.2 version (new `upgrade` criterion + translatable title/description); `recipe_id` is already the 26.2 key |
| `build.gradle` | kept this branch's toolchain (Loom `1.17-SNAPSHOT`, viewer pins) and only removed the two exclusions that 26.1.2 lifted (`mixin/anvil/RemoveXpCostScaling.java`, `mixin/accessor/**` — the latter is now an empty directory) |
| `fabric.mod.json` | kept the 26.2 identity/depends block; rewrote `port_notes` for the combined feature set and the 26.2 adaptations |
| `CHANGELOG.md`, `README.md` | rewritten for this line: same user-visible content, 26.2 version strings, and an explicit "synced from the 26.1.2 line" note |
| `RawOutput.log` | deleted (repo debris, as on the 26.1.2 line) |
| `build-reports/compile-java.log` | left to CI — the workflow rewrites it from every `arena/**` compile |

---

## 3. Version differences that required code changes / deliberate non-changes

### 3.1 Changed for 26.2

* **`ChatFormatting` does not exist in 26.2** (it was gutted in favour of `Style`, see
  [`PORTING_26_2_REPORT.md`](PORTING_26_2_REPORT.md) §3.1). The 26.1.2 feature code styles ~40
  tooltip/list lines with `ChatFormatting.*` in three files
  (`gui/ChiseledEnchantingTableScreen`, `block/ChiseledEnchantingTableBlockEntity`,
  `registry/BlockRegistry`). Every use was rewritten to the 26.2-safe form this branch already uses
  elsewhere — `.withStyle(style -> style.withColor(0xAARRGGBB))` (or
  `style.withItalic(false).withColor(...)`) — with the vanilla `ChatFormatting` RGB values preserved
  exactly (`GOLD 0xFFFFAA00`, `AQUA 0xFF55FFFF`, `LIGHT_PURPLE 0xFFFF55FF`, `GRAY 0xFFAAAAAA`,
  `DARK_GRAY 0xFF555555`, `GREEN 0xFF55FF55`, `RED 0xFFFF5555`, `DARK_PURPLE 0xFFAA00AA`). The
  `ChatFormatting` import is gone from all three files.
* **Data-pack / codec wiring was re-checked against the 26.2 schemas already enforced by
  `scripts/compat_check.py`**: plural `conditions[]`/`functions[]` loot tables (the new
  `loot_table/blocks/chiseled_enchanting_table.json` `copy_components` block), the
  `minecraft:recipe_crafted` → `recipe_id` criterion in all four advancement files that use it, and
  singular `advancement/`, `recipe/`, `loot_table/` directories. No 26.1.2-only schema slipped in.

### 3.2 Deliberately kept as 26.2 (not overwritten from 26.1.2 or 26.3)

* **Structure-processor subsystem** — `StructureProcessor` is an *interface* with `codec()` in 26.2;
  registration goes through `BuiltInRegistries.STRUCTURE_PROCESSOR`, and `processBlock` carries the
  extra template-`BlockPos`. `BookshelfReplacerProcessor` / `StructureProcessorRegistry` /
  the four structure mixins therefore stay exactly as this branch had them (26.1.2 still has the
  abstract-class + `getType()` + `StructureProcessorType.register(...)` shape). The sync touched none
  of these files, which is itself the confirmation: the 26.1.2 range did not modify them.
* **`ChunkAccess#markPosForPostProcessing`** (capital `P`) — the 26.2 spelling; 26.1.2 uses
  `markPosForPostprocessing`. All new code paths that mark positions live in files this branch
  already ported.
* **Loot builder API** — `LootItemConditionalFunction` + `MapCodec` + `List<LootItemCondition>` +
  `LootPoolSingletonContainer` are unchanged in 26.2, so the restored
  `DepthBasedEfficiencyFunction` uses the same shape as the existing
  `RemoveEnchantedBooksFunction` (no 26.3-style `ConstantValue`/`MapCodec` migration needed).
* **`LivingEntity#drop(ItemStack, boolean)`** — 26.2 keeps the two-argument form; the new
  inventory-overflow paths (`ChiseledEnchantingTableScreenHandler`, `InventoryUtils`) call it with
  `(stack, false)`.
* **Viewer coordinates** — this branch stays on `jei-26.2-fabric:30.47.0.239` /
  `RoughlyEnoughItems-api-fabric:26.2.821` / cloth `0.6.1` / architectury `21.0.4` (the 26.1.2 line
  uses `29.47.0.111` / `26.1.819` / `20.0.6`). The new viewer classes only use APIs that exist in
  both: `IRecipeType.create`, `AbstractRecipeCategory`, `addInputSlot(...).add(...)` /
  `addItemStacks(...)` (both `add` and the deprecated `addItemStack` exist in JEI 30),
  `DisplayCategory#getDisplayHeight()` (no-arg in both REI lines — the 26.1.2 range *fixed* a
  two-argument regression back to the no-arg form this branch already had).
* **Rendering entry points** — `GuiGraphicsExtractor` keeps `.text(...)`, `.blit(Identifier, x0, y0,
  x1, y1, u0, u1, v0, v1)`, `.fill`, `.pose()`, `.item(...)`, `.nextStratum()`,
  `.enableScissor/.disableScissor` and `.setTooltipForNextFrame(...)`; `Font.plainSubstrByWidth` is a
  measurement helper, not a draw call. The new UI code only uses these forms (checked call-by-call
  against the base tree that compiled green on 26.2).

### 3.3 Residual risk (verified by CI, not locally)

The dev sandbox has no JDK and no route to `maven.fabricmc.net`, so this sync was validated with the
three offline gates plus a file-by-file API audit; the real check is `compile-check` / `build` on
Actions, which is why the log is committed back to the branch. The surfaces worth watching if a
compile fails are, in order of likelihood:

1. `GuiGraphicsExtractor#setTooltipForNextFrame(Font, List<Component>, Optional<…>, int, int,
   Identifier)` and `#enableScissor/#disableScissor` — new to this code base (the base tree drew no
   custom tooltips and clipped nothing).
2. `BlockColorRegistry.register(List.of(new BlockTintSource() { … }), Block)` +
   `BlockEntity#getRenderData()` + `BlockAndTintGetter#getBlockEntityRenderData(BlockPos)` — the new
   dye-tinting path.
3. `DataComponents.DYE` (returns a `DyeColor`) in the table's `useItemOn` — the 26.1.2 line moved off
   `DyeItem` deliberately; `DyeColor#getTextureDiffuseColor()` + `ARGB.average(int, int)` are used
   for the blend.
4. `CompoundTag#getIntOr/getLongOr` + `CustomData` round-trip used for the persistent freeze.
5. JEI `IIngredientAcceptor#add(ItemStack)` in the two category classes.

---

## 4. Validation done in this round

```bash
python3 scripts/ci_static_checks.py                 # PASS — 9 mixins, 98 lang keys x 11 locales, 35 md files
python3 scripts/compat_check.py                     # PASS — Tier 1 (now also validates book_upgrades.json
                                                    #        and treasure_epiphany.json; see §5)
bash scripts/check_release_consistency.sh --strict  # PASS — 12 checks
python3 -c "import json,glob;[json.load(open(p)) for p in glob.glob('src/**/*.json',recursive=True)]"
```

Added in this round (Tier 1 coverage, `docs/EXTENDING.md` §7-§8): a shared cost-option validator
(`count` / `count_per_level` / `min_count+max_count` / `base_count+max_level_count` +
`level_exponent` + nested `extras` / `additional_costs`), a `book_upgrades.json` validator
(`combine_route` / `research_route` shapes and per-enchantment overrides) and a
`treasure_epiphany.json` validator (probability bounds, `max_freeze_ticks >= freeze_duration_ticks`,
slot cap, freeze/reroll cost lists, treasure id/tag lists, per-treasure cost tables). Both files are
now also part of the Tier-2 referenced-id sweep.

---

## 5. Maintenance rule going forward

The three branches (`26.1.2`, `26.2`, `26.3`) now share one feature set and one `1.0.3` SemVer.
When a change lands on one of them:

1. port the **data files, assets, lang files and gameplay logic** verbatim;
2. re-run the API deltas listed in §3 before touching source — `ChatFormatting` (26.2+), the
   structure-processor shape (26.2), `markPosForPostProcessing` (26.2), the loot builder API (26.2),
   `FriendlyByteBuf` collection helpers (26.3 only), the plural/singular loot schema and
   `recipe_id` vs `recipes` (per line);
3. keep the toolchain pins of the *target* branch;
4. update `CHANGELOG.md` + `CURRENT_BASELINE.md` + the matching `SYNC_FROM_*_REPORT.md`, and run the
   three offline gates before pushing.

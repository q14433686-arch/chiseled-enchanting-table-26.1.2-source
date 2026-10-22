# Changelog

> **Unofficial community port.** This changelog covers the unofficial 26.2 port maintained at
> `q14433686-arch/chiseled-enchanting-table-26.1.2-source`. It is not affiliated with or endorsed
> by the original author (Sacramentix). The original mod remains MIT-licensed; this port re-uses
> the original code/assets under that licence and updates them to the 26.2 toolchain.

Only the current release is kept here. Historical pre-`1.0.2` porting rounds and older release notes
are archived under [`docs/CHANGELOG_HISTORY.md`](docs/CHANGELOG_HISTORY.md).

> **26.2 branch (2026-07-19, synced 2026-10-21 and 2026-10-22):** This line targets **Minecraft
> 26.2**. Toolchain uses Fabric API `0.155.2+26.2`, Loom `1.17-SNAPSHOT`, Gradle `9.5.1`, Fabric
> Loader `0.19.3`, and `mod_version` `1.0.3`. See
> [`docs/reports/PORTING_26_2_REPORT.md`](docs/reports/PORTING_26_2_REPORT.md) for the 26.2 port
> details. On **2026-10-21** all feature additions and fixes from the `26.3` branch were synced back
> to this line with 26.2 API/schema compatibility; on **2026-10-22** the whole progression /
> economy / UI overhaul that had landed on the sister `26.1.2` line was synced as well — the
> change-by-change mapping and the 26.2 adaptations (notably: `ChatFormatting` no longer exists in
> 26.2, so every styled line uses `Style#withColor(ARGB)`) are recorded in
> [`docs/reports/SYNC_FROM_26_1_2_SIDE_REPORT.md`](docs/reports/SYNC_FROM_26_1_2_SIDE_REPORT.md).

## [1.0.3] - 2026-10-22

### Synced — full `26.1.2`-line progression, economy & UI overhaul

Ported from the sister `26.1.2` branch (see
[`docs/reports/SYNC_FROM_26_1_2_SIDE_REPORT.md`](docs/reports/SYNC_FROM_26_1_2_SIDE_REPORT.md)).
Minecraft, Loom, Gradle, Fabric API and the viewer coordinates are unchanged — this is a feature
sync adapted to the 26.2 APIs, not a version bump.

- **Two-track library progression (`7×5×7`)**: wooden bookshelves raise default-unlocked
  enchantments by `+1` per `5` shelves up to `ceil(maxLevel / 2)`; enchanted books stored in
  chiseled bookshelves become permanent **Master Tomes** that unlock every level `1..k` 1:1, and the
  graduation tiers are only reachable through them. Treasure enchantments are gated behind either a
  stored tome or a Grand-Library epiphany.
- **`LibraryResonance` — relative-mastery discounts**: XP discounts start at `12` wooden shelves
  (`2%`) and rise smoothly to `12%` at `32`, global mastery adds up to `13%`, per-enchantment
  specialisation adds `round(15 * r^1.5)` with `r = level / maxLevel`, capped at `-40%`. The
  mastery weights are mod-agnostic (`Enchantment#getMaxLevel`, `#canEnchant`), so modded
  enchantments work out of the box.
- **Dual-mode XP formula (`XpCostConversion.XpRequirement`)**: item transformations and
  Master-Tome inscribing (when `storedMasterLevel >= option.level()`) use vanilla's enchanting-table
  thresholds (`Lv.1..30`) but deduct only `1 / 2 / 3` levels — batch mass production. Base
  enchanting, book combine/research upgrades and Treasure Epiphanies keep the full XP curve. The two
  modes are distinguishable in the row badges: `gui/xp_orb_1..3.png` + `Lv.X` versus
  `gui/xp_ritual_flask.png` + `-XL`.
- **Yield-aware material curves**: every cost table now uses `base_count` / `max_level_count` /
  `level_exponent` so the counts follow survival yield (`Lapis Lazuli > Emerald >> Amethyst Shard`)
  and adapt to any modded `maxLevel` (e.g. `3→6→10→15→20` lapis, `2→5→8→13→18` emerald,
  `1→2→4→6→8` amethyst shard for a 5-tier enchantment).
- **Grand-Library Treasure Epiphany (`treasure_epiphany.json`)** at `>= 32` wooden bookshelves: proc
  chance and simultaneous slot cap scale with shelves *and* stored Master Tomes, rolled tiers are
  multi-level, and the whole panel can be **cryo-frozen** (persistent across mining/re-placing via
  `CUSTOM_DATA` + `copy_components`, live `MM:SS` lore preview) or rerolled for materials.
- **Depleted Tome & Blank Overwrite**: merging or draining an enchanted book yields a 64-stackable
  `chiseled_enchanting_table:depleted_tome`; `blank_overwrite` cleanses up to 8 at once
  (`1` XP level + a scribe reagent) back into canonical `minecraft:book`s and grants the *Tabula
  Rasa* advancement. Enchanting a plain book now builds a canonical enchanted book so identical
  books stack to `64`.
- **New items & recipes**: `chiseled_enchanting_table:arcane_catalyst` (universal treasure-synthesis
  catalyst, also accepted by Master-Tome research), `recipe/crying_obsidian.json`
  (`8× Obsidian + 1× Ghast Tear → 8× Crying Obsidian`), a cheaper base recipe
  (`2 Diamond + 1 Book + 1 Chiseled Bookshelf + 3 Crying Obsidian`) and an
  upgrade recipe (`1 Enchanting Table + 1 Chiseled Bookshelf + 4 Amethyst Shard + 3 Crying
  Obsidian`). The trident transformation is rebalanced to `1 Nautilus Shell + 3 Prismarine Shard +
  2 Prismarine Crystals`.
- **Viewers**: `TransformationQueryIndex` + `TransformationRecipeCategory` +
  `ReiTransformationCategory` add bidirectional item-transformation lookup (`R`/`U`) next to the
  existing enchantment category, which now shows the primary **and** secondary material slots and
  the XP-tier badge; both categories drop their caches when the S2C cost table is re-synced.
- **UI**: repainted full-window background (`680×332`), epiphany panel (normal/frozen), six row
  plaques and the orb/flask XP badges; hardware scissor clipping plus auto-scaling/ellipsizing text
  so rows can never overlap; `1:1` scrollbar thumb dragging; centered container slots `(183, 28)` /
  `(183, 57)`; plain-spoken copy across all 11 locales; `docs/WIKI.md` gameplay + upstream
  comparison guide.
- **Table dyeing, particles, loot**: right-click dying now blends colours on the block entity,
  syncs to clients (`getUpdateTag` / `getUpdateData` / `getRenderData`), tints block and item
  (`BlockColorRegistry` + `items/chiseled_enchanting_table.json`) and survives breaking via the
  block loot table; both wooden and occupied chiseled bookshelves inside the `7×5×7` volume stream
  `ENCHANT` glyphs into the floating book; the Mineshaft `DepthBasedEfficiencyFunction` is restored
  (Efficiency II–V by depth).
- **Anvil**: `RemoveXpCostScaling` is re-enabled (flat prior-work penalty, no more exponential
  "Too Expensive!"), and `RemoveEnchantMerge` now also preserves `count - 1` of a stacked enchanted
  book placed in the right slot. The parked `mixin/accessor/ItemAccessor` was deleted.
- **Advancements**: 18 in total (8 new: `transmute_item`, `enchanted_golden_apple`,
  `arcane_catalyst`, `library_resonance`, `grand_library`, `freeze_epiphany`, `inscribe_epiphany`,
  `multi_epiphany`), all localized; `root` additionally keys on the upgrade recipe.
- **26.2 adaptations** (details in the sync report): `ChatFormatting` was removed from all three
  files that used it and replaced with `Style#withColor(0xAARRGGBB)`; the 26.2 structure-processor
  API, `markPosForPostProcessing`, the plural loot schema, `recipe_id` criteria, the two-argument
  `LivingEntity#drop` and the pinned 26.2 viewer coordinates are all kept.
- **Repo hygiene**: removed leftover scratch files (`Dummy.*`, `RawOutput.log`, `adapted_handler.java`,
  `adapt_handler.py`, `append.py`, stale `fix*.patch`/`fix_*.py`, `*.orig`, `*.rej`, a committed
  `.class`), the retired duplicate mixin configs, and `block/FloatingBook.java`.
- **Data-pack tooling**: `scripts/compat_check.py` gained Tier-1 validators for
  `book_upgrades.json` and `treasure_epiphany.json` plus a shared cost-option schema check, and both
  files joined the Tier-2 referenced-id sweep.

### Added — JEI and REI integration (bidirectional enchantment lookup)

- **New viewer category** (`chiseled_enchanting_table:enchanting`) modelling the mechanic as a
  two-input recipe — `[target item] + [enchanted book] + [material] -> [enchanted item]` — which is
  what gives both query directions from one dataset: focus an enchanted book to list every item it
  can enchant, focus an item to list every book that works on it. Catalysts are the chiseled
  enchanting table and the chiseled bookshelf; the category also lists XP cost, material cost,
  conflicting enchantments, and the "book must sit in a nearby chiseled bookshelf" requirement
  (live world state, so a tooltip/info line rather than an ingredient).
- **Shared, viewer-agnostic index** — `compat/EnchantQueryIndex` derives one row per
  (enchantment, level) from the live registries (`Enchantment#canEnchant`, so modded items and
  universal-enchant datapacks are included), plus the mod's own cost tables.
- **Enchanted-book subtypes come free in both viewers**: REI's default plugin already registers a
  stored-enchantment comparator and JEI's own `VanillaPlugin` already registers an
  `EnchantedBookSubtypeInterpreter` for `Items.ENCHANTED_BOOK`, so each enchantment is its own
  searchable entry without this mod registering anything. (The 26.3 adapter originally called
  `registerFromDataComponentTypes(..., STORED_ENCHANTMENTS)`; JEI rejects the duplicate with an
  `[ERROR] An interpreter is already registered for this: minecraft:enchanted_book` on every world
  load and discards it — removed here.)
- Both viewers are `compileOnly` and optional: `enable_jei=false` / `enable_rei=false` in
  `gradle.properties` (or `-Penable_jei=false` / `-Penable_rei=false`) exclude the respective
  adapter from the build; nothing is required at runtime. On 26.2, coordinates are pinned to
  `mezz.jei:jei-26.2-fabric:30.47.0.239` and
  `me.shedaniel:RoughlyEnoughItems-api-fabric:26.2.821` along with compile-only
  `me.shedaniel.cloth:basic-math:0.6.1` and `dev.architectury:architectury-fabric:21.0.4`, all
  configured with `toolchainPins` (`exclude group: 'net.fabricmc.fabric-api'`, `'net.fabricmc'`,
  `'com.mojang'`) so viewer POMs never displace the pinned Fabric API (`0.155.2+26.2`).
- **Server-authoritative costs (S2C)** — the material cost could not be shown truthfully before: the
  cost table is server data (`PackType.SERVER_DATA`) and the chosen option is salted with the player
  UUID. The server now resolves one row per (enchantment, level) for the joining player and sends
  `chiseled_enchanting_table:enchant_cost_table` on join and after every successful data-pack reload;
  the client caches it (`SyncedEnchantmentCosts`, cleared on disconnect) and both the viewers and the
  table's own GUI read it, so a dedicated server with a customised `enchantment_costs.json` shows the
  real cost instead of the jar defaults. Server-side resolution is unchanged and the client-side read
  is guarded by `world.isClientSide()`, so a LAN host can never show one player another player's
  salted option.
- **Localization:** all viewer strings go through the mod's translation keys, now shipping in 11
  languages (`de_de`, `en_us`, `es_es`, `fr_fr`, `it_it`, `ja_jp`, `ko_kr`, `pt_br`, `ru_ru`,
  `zh_cn`, `zh_tw`), including the server-cost tooltip line, the two book-upgrade GUI labels, and a
  translatable `book.chiseled_enchanting_table.lore` component on generated enchanted books.
- See [`docs/reports/VIEWER_INTEGRATION_JEI_REI.md`](docs/reports/VIEWER_INTEGRATION_JEI_REI.md).

### Fixed — worldgen deadlock (integrated server freeze) & advancement data bugs

- **`BookshelfReplacerProcessor#finalizeProcessing` no longer touches the live `ServerLevel`.**
  It used to unwrap the `WorldGenRegion`, then call `getBlockState` / `getBlockEntity` /
  `setBlock` on the chunk that was *currently being generated*. For an off-thread caller
  `ServerChunkCache#getChunk` does `supplyAsync(..., mainThreadProcessor).join()`, while the server
  thread sits in `managedBlock` waiting for the same chunk. With Distant Horizons' `FEATURES` LOD
  generator (its own thread pool) all workers deadlocked deterministically: no TPS, no log,
  unsavable world. The fill now only reads the datapack pool + enchantment registry + seed, and
  returns a finished `StructureBlockInfo` (state with `slot_*_occupied` + block-entity NBT), which
  vanilla places. Same fix also makes template-placed shelves actually *filled* — previously the
  fill ran against a chunk that was not placed yet, so villages/mansions kept empty chiseled
  bookshelves. See [`docs/reports/WORLDGEN_BOOKSHELF_DEADLOCK_REPORT.md`](docs/reports/WORLDGEN_BOOKSHELF_DEADLOCK_REPORT.md).
- **The shelf-facing heuristic moved to the server thread.** `finalizeProcessing` marks the
  position for chunk post-processing; `LevelChunkPostProcessMixin` then calls
  `BookshelfBookFiller.applyFacingToPlacedBookshelf` when the chunk starts ticking, so the v3
  rubric sees the finished geometry.
- **Live-world fills are now server-thread-only and never load a chunk.** The fillers log one
  error and no-op when called off-thread, and neighbour scans use `ServerChunkCache#getChunkNow`.
  Slot contents are written through `getItems()` rather than `setItem()`.
- **Advancement data (`craft.json`):** removed invalid `display.background` from the non-root
  `craft` advancement and moved `show_toast` / `announce_to_chat` from the top level into `display`
  (where `DisplayInfo.CODEC` actually reads them), while keeping the 26.2 `recipe_id` criterion
  and 26.2 loot-table schema (`conditions[]` / `functions[]`).
- **Tooling / CI:** synced `scripts/ci_static_checks.py`, `scripts/check_release_consistency.sh`,
  26.2 schema guards in `scripts/compat_check.py`, `.githooks/pre-commit`, `scripts/install-hooks.sh`,
  executable `gradlew` (`100755`), and installed all four 26.2 CI workflows (`compile-check.yml`,
  `build.yml`, `consistency.yml`, `publish.yml`) into both `docs/ci/` and `.github/workflows/`
  (including `bash -e`-safe `status=0` + `|| status=$?` status capture, resolved coordinate logging,
  failure `compile-deps.log` upload, and the `no-viewers` build job).

## [1.0.2] - 2026-07-01

### Upstream parity and balance

- Restored vanilla enchanting-table book blocking: ordinary `BOOK` items no longer receive vanilla
  enchanting-table offers, keeping enchanted-book creation in the chiseled-table progression loop.
- Restored narrow anvil balance: vanilla anvil no longer upgrades equal-level enchanted books.
- Added table-hosted book upgrading so the anvil route has a mod-native replacement:
  - **Combine Upgrade / 合书升阶** — anvil-like enchanted-book + matching enchanted-book upgrade at
    the Chiseled Enchanting Table. The lower/cost book now visibly becomes a plain `BOOK` after
    contributing its enchantment knowledge.
  - **Research Upgrade / 材料升阶** — one enchanted book + themed material + XP. Costs are controlled
    by `book_upgrades.json` and are intentionally lower than direct specified enchanting because the
    existing low-level book is the knowledge seed.
- Restored enchanted-book chiseled-bookshelf ambience: shelves containing enchanted books can emit a
  quiet enchantment-table sound and visible enchant/sparkle particles.

### Data-driven content

- Added `data/chiseled_enchanting_table/chiseled_enchanting_table/book_upgrades.json` for the two
  book-upgrade routes, XP multipliers, default research material, and per-enchantment themed
  material overrides.
- Existing content/rule tables remain JSON-owned: default unlocks, enchantment costs,
  transformations, loot injections, bookshelf fill rules, and book-upgrade rules.

### Advancement guide and localization

- Reworked advancement display text to use translation keys instead of hardcoded English.
- Added Simplified Chinese translations for the advancement guide and book-upgrade UI labels.
- Added book-upgrade guide milestones (`upgrade_enchanted_book`, `max_level_book`) and Java-side
  triggers for writing books, upgrading books, max-level books, book-to-item transfer,
  book-to-book transfer, and the hidden all-enchantments book challenge.
- Updated in-game guidance to explain that low-level enchanted books are upgraded through the
  Chiseled Enchanting Table, not by vanilla anvil book synthesis.

### Fixes included in this release line

- Fixed enchant-from-book rows that appeared but were inert when the enchantment was not in the
  unlocked set.
- Fixed XP deduction to use vanilla-style XP points for level costs instead of subtracting raw
  levels.
- Tuned structure bookshelf generated-book probability and weighting.
- Fixed stronghold / structure bookshelf replacement paths and shelf facing behavior.
- Fixed chiseled-bookshelf particle visibility after live testing showed sound without particles.

### Validation

- JSON syntax validation for metadata, lang files, advancement files, and `book_upgrades.json`.
- `python3 scripts/compat_check.py` → PASS.
- `./gradlew compileJava compileClientJava` → BUILD SUCCESSFUL.
- `./gradlew build --rerun-tasks` → BUILD SUCCESSFUL.
- Jar metadata/resource spot check passed for version `1.0.2` and current resources/classes.
- Release output refreshed under `release_output/chiseled_enchanting_table_1_0_2/`.

### Client-only validation still pending

These still need confirmation in a real Minecraft 26.1.2 client: all active mixins applying cleanly,
vanilla enchanting-table book blocking, anvil same-level book blocking, combine/research book-upgrade
UI and consumption behavior, chiseled-bookshelf ambience, advancement triggers/translations, custom
menu interactions, structure bookshelf fill outcomes, loot injection outcomes, and multiplayer sync.

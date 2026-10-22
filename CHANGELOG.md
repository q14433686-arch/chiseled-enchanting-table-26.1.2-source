# Changelog

> **Unofficial community port.** This changelog covers the unofficial 26.1.2 port maintained at
> `q14433686-arch/chiseled-enchanting-table-26.1.2-source`. It is not affiliated with or endorsed
> by the original author (Sacramentix). The original mod remains MIT-licensed; this port re-uses
> the original code/assets under that licence and updates them to the 26.1.2 toolchain.

Only the current release and the previous one are kept here. Historical pre-`1.0.2` porting rounds
and older release notes are archived under [`docs/CHANGELOG_HISTORY.md`](docs/CHANGELOG_HISTORY.md).

> **1.0.3 on this branch (2026-10-21):** this is **not** a Minecraft port — the line still targets
> **Minecraft 26.1.2** with the same toolchain (Fabric Loader `0.19.3`, Fabric API
> `0.151.0+26.1.2`, Loom `1.16-SNAPSHOT`, Gradle wrapper 9.4.1, Java 25). `1.0.3` is the back-port
> of everything version-independent that landed on the sister `26.3` branch after the shared `1.0.2`
> baseline, adapted to the 26.1.2 API and data schemas. The change-by-change mapping — including
> what was deliberately *not* taken because it is 26.3-only — is in
> [`docs/reports/SYNC_FROM_26_3_REPORT.md`](docs/reports/SYNC_FROM_26_3_REPORT.md).

## [1.0.3] - 2026-10-22

### Changed — Unified Progression, Library Resonance & Player-Choice Economy Overhaul (A × B × C)

- **Plain-Spoken UI Copy, Trident Recipe Rebalance, Full-Library Glyph Particles, GitHub Wiki & JEI/REI Transformations**:
  - **Trident Transformation Cost Rebalance**: streamlined `chiseled_enchanting_table:trident` to
    `1× Nautilus Shell` + `3× Prismarine Shard` + `2× Prismarine Crystals`.
  - **Full `7×5×7` Converging Glyph Particles**: fixed `ChiseledEnchantingTableBlock.animateTick`
    (which previously checked client-unsynced shelf inventories and ignored wooden bookshelves) and
    `ChiseledBookshelfTick` so both wooden bookshelves and occupied chiseled bookshelves across the
    `7×5×7` library stream `ENCHANT` glyphs smoothly into the floating book atop the table.
  - **Plain-Spoken In-Game Copy & `docs/WIKI.md`**: rewrote all UI labels, tooltips, and JEI/REI
    descriptions across all 11 locales in concise, natural *Enchanting Infuser*-style prose, and
    added a full gameplay & upstream-comparison guide in [`docs/WIKI.md`](docs/WIKI.md).
  - **Bidirectional JEI & REI Item Transformations**: added `TransformationQueryIndex`,
    `TransformationRecipeCategory`, and `ReiTransformationCategory` so all item transformations can
    be queried forward (`R`) and backward (`U`) in both JEI and REI.
- **Dual-Mode Coexisting XP Formula (`XpCostConversion.XpRequirement`) & Crying Obsidian Recipe**:
  - **Vanilla Enchanting Table Mass-Production Formula**: both **Item Transformations** and
    **Master Tome Inscribing** (`storedMasterLevel >= option.level()`) now use vanilla's
    Enchanting Table formula — checking a player level threshold (`Lv.1..30`, discounted up to
    `-40%` by Library & Specific Tome Resonance) while consuming only **`1 / 2 / 3` XP levels**
    via `player.giveExperienceLevels(-tierCost)`, enabling seamless batch mass-production.
  - **Full-Level Arcane Infusion Coexistence & Distinct Badges**: un-enshrined base enchanting,
    Book Combine/Research Upgrades, and Treasure Epiphanies retain full XP curve deduction and
    coexist side-by-side via horizontal centered pixel-art row badges (`gui/xp_orb_1..3.png` +
    `Lv.X` vs `gui/xp_ritual_flask.png` + `-XL`), dynamic right-aligned secondary item icons,
    bounded auto-scaling/ellipsizing text + hardware scissor clipping across all list rows and the
    Sanctum panel, and centered input/material container slots `(183, 28)` / `(183, 57)`.
  - **Shaped Crafting Recipe (`8× Crying Obsidian`)**: added `recipe/crying_obsidian.json` and
    matching recipe unlock advancement (`8× minecraft:obsidian` border around `1× minecraft:ghast_tear`
    center -> `8× minecraft:crying_obsidian`).
- **Yield-Aware Material Curves & Relative-Mastery Library Resonance (`LibraryResonance`)**:
  - **Yield-Differentiated Level Scaling Across All Scenes**: upgraded `ItemCostOption` with
    smooth relative-tier curves (`base_count`, `max_level_count`, `level_exponent`, auto-adapting to
    any modded `maxLevel = 1..255`) across `enchantment_costs.json`, `book_upgrades.json`,
    `treasure_epiphany.json`, and `item_transformations.json` following survival yield rates:
    `Lapis Lazuli > Emerald >> Amethyst Shard` (e.g. 5-tier enchants scale `3→6→10→15→20 Lapis`,
    `2→5→8→13→18 Emerald`, `1→2→4→6→8 Amethyst Shard`).
  - **`W >= 12` Wooden Bookshelf Discount Floor**: fewer than `12` regular bookshelves grant `0%`
    XP discount; from `12` to `32` shelves, base shelf discount rises smoothly from `2%` to `12%`.
  - **Mod-Agnostic Relative Master-Tome Resonance (`r = L / maxLevel`)**: dynamically queries
    `Enchantment#getMaxLevel()` and `Enchantment#canEnchant(ItemStack)` for every stored Chiseled
    Bookshelf tome (`w(r) = 0.45r + 0.55r²`), driving:
    1. **XP Discount**: up to `25%` table-wide base (`12%` wooden + `13%` global mastery) PLUS up
       to `15%` per-enchantment specialization (`round(15 * r^1.5)`), capped at `40%`;
    2. **Treasure Epiphany Probability & Per-Item Cap**: scales both trigger chance (up to `90%`)
       and simultaneous slot cap (`1..4`) with global mastery `E_all` and target-equipment matching
       mastery `E_affinity(target)`, plus multi-level tier rolls in `[storedLevel + 1, maxLevel]`.
- **Depleted Tome (`chiseled_enchanting_table:depleted_tome`) & Blank Overwrite (`blank_overwrite`)**:
  - Fixed a component-pollution stacking bug where `ItemStack#transmuteCopy` left residual
    `STORED_ENCHANTMENTS` / glint data patches on drained `minecraft:book`s and re-enchanted
    `minecraft:enchanted_book`s, preventing stacking with clean items.
  - Merging or fully stripping an Enchanted Book now yields a 64-stackable **Depleted Tome**
    (`褪魔残卷`) bearing scorched arcane residue that cannot be directly re-enchanted or crafted with
    until cleansed via **Blank Overwrite** (`chiseled_enchanting_table:blank_overwrite`, batchable up
    to `8` tomes at once for `1` XP level + any scribe reagent: `2× Paper`, `2× Bone Meal`,
    `1× Ink Sac`, `1× Glow Ink Sac`, or `1× Amethyst Shard`), restoring canonical component-free
    `minecraft:book`s and granting the **Tabula Rasa** (`blank_overwrite`) advancement. Existing
    saves' residual `minecraft:book` items are also detected and can be cleansed via Blank Overwrite.
  - Enchanting a `minecraft:book` now constructs a canonical `new ItemStack(Items.ENCHANTED_BOOK, 1)`
    (preserving custom names only) so identical Enchanted Books always stack cleanly up to `64`.
- **Smooth Early-Game Entry & Upgrade Recipes**:
  - Reduced direct crafting recipe (`recipe/chiseled_enchanting_table.json`) from `2 × Diamond Block`
    (18 diamonds) to **`2 × Diamond` + `1 × Book` + `1 × Chiseled Bookshelf` + `3 × Crying Obsidian`**.
  - Added an **Upgrade Recipe** (`recipe/chiseled_enchanting_table_upgrade.json`:
    `1 × Enchanting Table` + `1 × Chiseled Bookshelf` + `4 × Amethyst Shard` + `3 × Crying Obsidian`)
    so existing vanilla Enchanting Tables upgrade cleanly with zero wasted diamonds; both recipes
    grant `root` and `craft` advancements.
- **Two-Track Bookshelf Progression & Library Resonance**:
  - **Baseline (`default_enchantments.json`)**: `#minecraft:in_enchanting_table` enchantments start
    unlocked at Level I (supporting third-party modded enchantments automatically); Treasure
    enchantments require discovering a book and storing it in a nearby Chiseled Bookshelf.
  - **Track 1 — Wooden Bookshelves (`radius 2`)**: smoothly raise default-unlocked enchantments by
    `+1` per `5` shelves up to the mid-tier ceiling `ceil(maxLevel / 2)` (`V → III`, `IV → II`,
    `III → II`, `II → I`) and grant **`-1%` XP cost per shelf** (up to `-10%`).
  - **Track 2 — Chiseled Bookshelves**: unlock stored Master Tomes **1:1** up to `maxLevel`
    (emitting every level `1..k`), required for graduation tiers (`IV/V`, max `III`, max `II`) and
    permanent Treasure unlocks, while granting **`-2%` XP cost per stored Enchanted Book** (combined
    Library Resonance cap **`-35% XP`**), displayed live in the embossed opaque header pill.
  - **Strict Two-Path Treasure Gate, Dual Scaling & Stasis Freeze Panel (`treasure_epiphany.json`)**:
    - **Strict Two-Path Gate**: All 11 Treasure / pinnacle enchantments (`aqua_affinity`, `flame`,
      `multishot`, `channeling`, `silk_touch`, `infinity`, `mending`, `frost_walker`, `soul_speed`,
      `swift_sneak`, `wind_burst`) plus `#minecraft:treasure` are strictly excluded from default unlocks
      and only ever appear via **(1) Master Tome in a nearby Chiseled Bookshelf** OR **(2) Grand Library
      Treasure Epiphany (`≥ 32` Wooden Bookshelves)**.
    - **Dual Scaling (Bookshelves + Master Tomes)**: Both Epiphany proc chance (`30% + 1%/shelf +
      2%/Master Tome`, capped at `85%`) and maximum simultaneous compatible Epiphanies (`1..4` slots)
      scale dynamically with nearby Wooden Bookshelves and stored Master Tomes.
    - **Dual-Material Epiphany Economy vs. Single-Material Master Tomes**:
      - Unlocking any enchantment (including Treasure enchantments) via a **Master Tome in a nearby
        Chiseled Bookshelf** requires **only single basic currency** (`Lapis Lazuli`, `Amethyst
        Shard`, or `Emerald`) with zero secondary materials.
      - Inscribing an unowned Treasure enchantment via **Grand Library Epiphany** requires **two
        thematic materials (`primary` + `extra`)** — with **`1× Arcane Synthesis Crystal` (`秘法凝晶`)**
        as the sole single-material universal bypass.
    - **Persistent Cryo-Stasis Freeze, Cohesive Full-Window Painted UI, 1:1 Scrollbar Drag & 18
      Advancements**:
      - **Persistent Freeze Across Mining**: Stasis freeze remaining duration (`pendingFreezeTicks`),
        frozen cycle index, and seed persist across block breaking/re-placing via `CUSTOM_DATA` +
        `copy_components` with live inventory `LORE` preview (`❄ 00:00`).
      - **Cohesive Full-Window & Row Plaque Artwork**: Redesigned `gui/chiseled_enchanting_table.png`
        (`680×332`), `epiphany_panel.png`, `epiphany_panel_frozen.png`, and custom row plaques
        (`row_normal`, `row_hover`, `row_disabled`, `row_epiphany`, `row_transform`), fixed scrollbar
        thumb drag mapping to `maxScroll / thumbTravel` (`1:1` cursor tracking), and ensured full
        unclipped `MM:SS` countdown display.
      - **Expanded Signature Transmutations & Advancements**: Added transmutations for Enchanted
        Golden Apple, Bottle o' Enchanting, Totem of Undying, Trident, and Echo Shard plus 8 new
        advancements (`transmute_item`, `enchanted_golden_apple`, `arcane_catalyst`,
        `library_resonance`, `grand_library`, `freeze_epiphany`, `inscribe_epiphany`,
        `multi_epiphany` — 18 total).
  - **Universal Expensive Synthesis Catalyst (`chiseled_enchanting_table:arcane_catalyst`)**: added
    the craftable **Arcane Synthesis Crystal / 秘法凝晶** (`1× Diamond Block + 2× Emerald Block +
    1× Gold Block + 2× Amethyst Block + 2× Lapis Block + 1× Crying Obsidian`), accepted universally
    alongside per-treasure thematic catalysts (`22–28 XP Levels`) and all Master-Tome Research upgrades.
  - **Fixed item & UI rendering alpha / Z-fighting bugs**: fixed `0xFF` ARGB alpha channel on the
    header Resonance badge pill (`ChiseledEnchantingTableScreen`), fixed opaque purple default dye
    color (`-6283024` / `0xA020F0` default `DYED_COLOR`), converted cloth overlay PNGs to 8-bit truecolor
    RGBA, and offset the cloth overlay element (`±0.02`) so inventory icons, held items, dropped
    entities, and world blocks all display purple/dyed cloth cleanly with zero vanilla-red bleed.
- **Dual Economy, Zero UUID Lock-In & Capped High-Level XP Curve**:
  - Removed per-player UUID material lock-in: every option in `options[]` is simultaneously valid
    for every player, cycled in JEI/REI and the table list, and pinned live as soon as a matching
    material is placed into the cost slot.
  - Separated **Daily Gear Inscribing** (`enchantment_costs.json`: `2–12` XP levels before library
    discounts; player's choice of `Lapis Lazuli`, `Amethyst Shard`, or `Emerald`, plus thematic
    alternatives for treasures) from one-time **Master-Tome Upgrades** (`book_upgrades.json`:
    material-free `combine_route` at `0.6×` XP alongside `research_route` at `1.4×` XP with 3
    biome-diverse alternatives across all 33 multi-level vanilla enchantments and zero `heavy_core`
    softlocks).
  - Capped per-level XP point width in `XpCostConversion` at vanilla Level `30` (`107` XP/level) so
    high-level players are never penalized thousands of raw XP points for enchanting above Level 30.
  - Increased worldgen structure Chiseled Bookshelf enchanted-book rate from `0.03` to `0.12`
    (`max_level_weight_ratio: 0.15`) in `bookshelf_book_pool.json`.

### Fixed — obvious port & runtime bugs, unported upstream features, UI/sync/viewer polish

- **Restored complete Chiseled Enchanting Table dyeing & tinting pipeline**: right-clicking with a
  `DyeItem` averages RGB color onto `ChiseledEnchantingTableBlockEntity`, syncs `ClientboundBlockEntityDataPacket`
  + `getUpdateTag` + `getRenderData` to clients, tints the block (`BlockColorRegistry`) and item
  model (`items/chiseled_enchanting_table.json`), preserves `minecraft:dyed_color` on break via
  `loot_table/blocks/chiseled_enchanting_table.json`, and awards `color_table`.
- **Restored Enchanted Book stack size of 64 & Anvil prior-work scaling removal**: registered
  `DefaultItemComponentEvents.MODIFY` for `Items.ENCHANTED_BOOK` (`MAX_STACK_SIZE = 64`), protected
  stacked enchanted books in `AnvilMenu#onTake` (`RemoveEnchantMerge`), and ported/enabled
  `RemoveXpCostScaling` (`AnvilMenu#calculateIncreasedRepairCost`).
- **Completed and localized all 10 advancements across all 11 languages**: added missing
  `upgrade_enchanted_book.json` and `max_level_book.json`, wired missing `enchant_to_book`,
  `enchant_from_book`, `transfer_enchanted_book`, and `all_enchant_book` triggers in
  `EnchantmentApplication`, preserved mutually exclusive enchantments on books, fixed false-positive
  `unlock_new_enchant` when no nearby chiseled bookshelf holds enchanted books, and added native
  translations across all 11 `lang/*.json` files.
- **Fixed XP, book-upgrade, loot, container, UI, and JEI/REI bugs**: atomic validation before XP
  deduction in `BookUpgradeApplication#applyResearch`; preserved fractional `experienceProgress` in
  `XpCostConversion`; restored Mineshaft `DepthBasedEfficiencyFunction` (Efficiency II–V by Y-depth)
  and `BookshelfBookFiller` `enchant_sets` support; restored table-to-bookshelf `ParticleTypes.ENCHANT`
  particles; fixed Shift-click slot index bug and `stillValid` range/block check in
  `ChiseledEnchantingTableScreenHandler`; added per-item affordability coloring, accurate multi-conflict
  detection, and hover item tooltips on cost icons in the table screen; fixed multi-material cost
  slots, visible XP labels, S2C cache invalidation, and zero-deprecation JEI/REI integration; and
  removed root scratch files / `*.orig` / `*.rej` / committed bytecode artifacts.

### Fixed — worldgen deadlock / unloadable chunks ([issue #3](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/issues/3))

- **Structure-placed bookshelves are no longer filled from a worldgen worker thread against the live
  `ServerLevel`.** `BookshelfReplacerProcessor#finalizeProcessing` used to unwrap the `WorldGenRegion`
  and then call `getBlockState` / `getBlockEntity` / `setBlock` on the chunk that was *currently being
  generated*. For an off-thread caller that read goes through `ServerChunkCache#getChunk`, which does
  `supplyAsync(..., mainThreadProcessor).join()` while the server thread sits in `managedBlock`
  waiting for the same chunk — a hard deadlock: server keeps running, world stops, no log, no crash,
  work is lost, the world cannot be saved. Deterministic with Distant Horizons' `FEATURES` LOD
  generator (its own thread pool), intermittent on vanilla/C2ME workers.
- The fill is now **pure** (`BookshelfBookFiller.computeFill` + `toBlockEntityTag`: pool JSON +
  enchantment registry + seed only) and travels inside the finished `StructureBlockInfo`
  (slot-occupied state + block-entity NBT) that vanilla's `StructureTemplate.placeInWorld` applies.
- **Template-placed chiseled bookshelves are actually filled now.** Previously the fill ran against a
  chunk that was not placed yet, so village libraries / mansions / ancient cities kept coming out
  *empty* — the second half of the same bug.
- **The shelf-facing heuristic moved to the server thread**: `finalizeProcessing` marks the position
  for chunk post-processing and `LevelChunkPostProcessMixin` calls
  `BookshelfBookFiller.applyFacingToPlacedBookshelf` once the chunk starts ticking, so the score-based
  rubric sees the finished geometry (a piece's own walls are still in the template during worldgen).
  If the mark is unavailable the shelf keeps its books and the template's rotated default facing.
- **Live-world fills are server-thread-only and never load a chunk.** `applyToPlacedBookshelf` /
  `applyFacingToPlacedBookshelf` log one error and no-op when called off-thread, and neighbour scans
  use `ServerChunkCache#getChunkNow`, so an unloaded neighbour counts as "unknown" instead of
  triggering a synchronous chunk load. Slot contents are written through `getItems()` rather than
  `setItem()`, so enchanted books are no longer subject to the `#minecraft:bookshelf_books`
  re-validation / per-slot state rewrite.
- 26.1.2-specific adaptation of the same fix (this branch keeps `StructureProcessor` as an abstract
  class with `getType()`, `processBlock(..., StructureBlockInfo originalBlockInfo, ...)`, the
  lower-case `ChunkAccess#markPosForPostprocessing`, and no `processBlockInfos` clipping — so the
  deferred facing pass skips positions outside the worldgen region).
  Full analysis: [`docs/reports/WORLDGEN_BOOKSHELF_DEADLOCK_REPORT.md`](docs/reports/WORLDGEN_BOOKSHELF_DEADLOCK_REPORT.md).

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
  searchable entry without this mod registering anything. (The 26.3 adapter this was ported from did
  call `registerFromDataComponentTypes(..., STORED_ENCHANTMENTS)`; JEI rejects the duplicate with an
  `An interpreter is already registered` ERROR and discards it — found in a real client log and
  removed here.)
- Both viewers are `compileOnly` and optional: `enable_jei=false` / `enable_rei=false` in
  `gradle.properties` (or `-Penable_jei=false` / `-Penable_rei=false`) exclude the respective
  adapter from the build; nothing is required at runtime. Coordinates for this line:
  `mezz.jei:jei-26.1.2-fabric` (JEI's `26.1` branch pins `minecraftVersion=26.1.2`),
  `me.shedaniel:RoughlyEnoughItems-api-fabric:26.1.+`, `me.shedaniel.cloth:basic-math:0.6.1`,
  `dev.architectury:architectury-fabric:20.0.6`. The adapter sources are byte-identical to the 26.3
  branch: both viewers' API files were diffed between their own `26.1` and `26.3` branches first and
  differ only in javadoc `@since` numbers (JEI) or not at all (REI).
  See [`docs/reports/VIEWER_INTEGRATION_JEI_REI.md`](docs/reports/VIEWER_INTEGRATION_JEI_REI.md).

### Added — server-authoritative enchantment costs (S2C)

- The material cost could not be shown truthfully before: the cost table is server data
  (`PackType.SERVER_DATA`) and the chosen option is salted with the player UUID. The server now
  resolves one row per (enchantment, level) for the joining player and sends
  `chiseled_enchanting_table:enchant_cost_table` on join and after every successful data-pack reload;
  the client caches it (`SyncedEnchantmentCosts`, cleared on disconnect) and both the viewers and the
  table's own GUI read it — so a dedicated server with a customised `enchantment_costs.json` shows
  the real cost instead of the jar defaults. Server-side resolution is unchanged and the client-side
  read is guarded by `world.isClientSide()`, so a LAN host can never show one player another player's
  salted option.

### Added — localization

- Nine more languages alongside `en_us` / `zh_cn`: `de_de`, `es_es`, `fr_fr`, `it_it`, `ja_jp`,
  `ko_kr`, `pt_br`, `ru_ru`, `zh_tw` (15 keys each, exact parity — now enforced by CI).
- The lore line stamped on generated enchanted books is translatable
  (`book.chiseled_enchanting_table.lore`) instead of a hardcoded English literal, so a book generated
  by a server reads in each player's own language. Converted in **both** places the stamp is written:
  the structure bookshelf filler (as on the 26.3 branch) and the chest-loot injector (which the 26.3
  fix missed).

### Fixed — advancement data

- `advancement/craft.json` carried `display.background` while also having a `parent`, and had
  `show_toast` / `announce_to_chat` at the **top level**. 26.1.2 ignores all three keys (the intended
  "no toast, no chat" never applied); in 26.3 the background one is a hard load error that stops the
  world from opening. The background key is gone (the parent owns the tab background) and the two
  flags moved into `display`, where `DisplayInfo` actually reads them.
- **Kept `recipe_id`** in `recipe_crafted` criteria: 26.3 renamed it to a required `recipes` list,
  which is a schema change of that Minecraft version, not a fix. `scripts/compat_check.py` now fails
  if a 26.3-shaped advancement or loot table is copied into this branch.

### Tooling / CI (new on this branch)

- Added the CI triad, live in `.github/workflows/` with byte-identical reference copies in `docs/ci/`
  and a drift check (`bash docs/ci/install.sh --check`): `compile-check.yml` (compiles **both** source
  sets on Actions and pushes `build-reports/compile-java.log` back to `arena/**` branches — the only
  compile-verification channel for a sandbox without maven access), `build.yml` (static gates →
  full build → jar artifact, **plus a `no-viewers` job** that compiles with both viewers disabled so
  the optional-dependency escape hatch cannot rot), `consistency.yml` (version gate) and
  `publish.yml` (manual, `26.1.2` / `1.0.3`).
- Added `scripts/ci_static_checks.py`: mixin configs must resolve to real sources, a mixin registered
  in `fabric.mod.json` must not be excluded from compilation by `build.gradle`, every `lang/*.json`
  must have exactly the `en_us` key set, and (warn-only) relative markdown links must resolve.
- Added `scripts/check_release_consistency.sh`: keeps `gradle.properties`, `README.md`,
  `fabric.mod.json`, `.github/workflows/publish.yml` and `docs/ci/publish.yml` in sync.
- Added a warn-only pre-commit reminder (`.githooks/pre-commit` + `scripts/install-hooks.sh`).
- `scripts/compat_check.py` gained Tier-1 gates for the **26.1.2** loot-table and advancement
  schemas — the inverse of the 26.3 branch's gates, negative-tested against both the pre-fix
  `craft.json` (3 failures) and the 26.3 versions of `craft.json` + the block loot table
  (3 failures).
- `gradlew` is committed executable (`100644` → `100755`), and the workflows `chmod +x` it anyway —
  the first run on this branch died with `./gradlew: Permission denied` before the mode was fixed.
- All four viewer `compileOnly` dependencies now exclude `net.fabricmc.fabric-api`, `net.fabricmc`
  and `com.mojang`. Without that, a viewer's POM upgrades the pinned Fabric API, Loom's interface
  injections follow it, and the **main** source set fails to compile with
  `cannot access EntityFluidExtension` — which is exactly how the first full CI run on this branch
  failed while the same push's `no-viewers` job was green.
- `jei_version` / `rei_version` are pinned to what CI resolved (`29.47.0.111` / `26.1.819`) instead
  of floating (`latest.release` / `26.1.+`), and `compile-check.yml` records the resolved coordinates
  of both compile classpaths in the log it commits back, so a bump is a deliberate, evidenced change.
- `compile-check.yml` captures Gradle's exit status with `|| status=$?`: Actions runs `run:` steps
  under `bash -e`, so the previous `status=$?` line was dead code and the failure-only dependency
  report would never have been written. On failure it now also commits back
  `build-reports/compile-deps.log` (`dependencyInsight` for Fabric API on both classpaths plus the
  first 400 lines of each dependency tree).
- Documentation: `docs/EXTENDING.md` now describes the `BookshelfBookFiller` two-halves split
  (pure/worldgen vs server-thread) and its default-unlock tag was corrected to the shipped
  `#minecraft:in_enchanting_table`; `docs/DOCUMENTATION_INDEX.md` gained the current-round report
  section; `CURRENT_BASELINE.md` / `NEXT_TASK.md` were re-synced with the tree (including the
  correction that the advancement display strings are *not* localised yet).

### Validation

- `python3 scripts/ci_static_checks.py` → PASS. `python3 scripts/compat_check.py` → PASS.
  `bash scripts/check_release_consistency.sh --strict` → PASS (12 checks).
  `bash docs/ci/install.sh --check` → ok ×4. All eight workflow YAMLs parse.
- **Compile: green on GitHub Actions**, 2026-10-21 — on commit `da77188`, `compile-check.yml` run
  `39670181582` (`compileJava` + `compileClientJava`, `BUILD SUCCESSFUL in 48s`, 2 JEI deprecation
  warnings), `build.yml` run `39670181557` (static gates, full `build` with `remapJar` + sources jar
  artifact, and the `no-viewers` job) and `consistency.yml` run `39670181572` all success; the
  preceding commit `315fb6e` was green as well (runs `39668301472` / `39668301133`) and is the run
  that produced the pinned viewer versions. The dev sandbox itself has no JDK and no maven access, so Actions is the
  compile channel: its log is committed back to the branch as `build-reports/compile-java.log`, and
  the two preceding runs (what each proved, and the two build fixes they forced) are tabled in
  `docs/reports/SYNC_FROM_26_3_REPORT.md` §7.1.
- In-game verification still open: the deadlock reproduction (fresh world, affected structure chunks,
  ideally with Distant Horizons), filled + correctly facing shelves, JEI/REI checklists, and the
  dedicated-server cost-sync pass.

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

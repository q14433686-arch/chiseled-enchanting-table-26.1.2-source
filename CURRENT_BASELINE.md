# Current Baseline

> **Unofficial community port.** This repository is an unofficial community port of
> [Sacramentix/chiseled-enchanting-table](https://github.com/Sacramentix/chiseled-enchanting-table)
> to Minecraft **26.1.2** on Fabric. It is **not affiliated with, endorsed by, or maintained by
> the original author**. All credit for the original mod design and assets belongs to Sacramentix
> (MIT licensed); this port only updates the code/data to the 26.2 toolchain.

> **26.2 branch (2026-07-19, synced 2026-10-21):** This line targets **Minecraft 26.2**. See
> `docs/reports/PORTING_26_2_REPORT.md` for the 26.1.2→26.2 port details. On 2026-10-21, all
> feature additions and fixes from the `26.3` branch were synced to this `26.2` line with 26.2
> compatibility: worldgen bookshelf deadlock fix (`WORLDGEN_BOOKSHELF_DEADLOCK_REPORT.md`), JEI &
> REI integration (`VIEWER_INTEGRATION_JEI_REI.md`), server-resolved enchantment cost sync (S2C),
> 11 language files + translatable book lore, `craft.json` advancement fixes, and the offline CI
> static / release-consistency gates (`docs/ci/`).

**Date:** 2026-10-21
**Minecraft Version:** 26.2
**Fabric Loader:** 0.19.3
**Fabric API:** 0.155.2+26.2
**Gradle:** 9.5.1
**Loom:** 1.17
**Java:** 25
**Mod Version:** `1.0.2`  *(unofficial-port versioning, see "Versioning" below)*

## Versioning

This repository currently publishes the unofficial 26.1.2 port as **`1.0.2`**. Earlier internal
porting labels are treated as historical implementation notes only; current metadata, release
artefacts, documentation, and marketing copy should all refer to `1.0.2`.

## Current Source-Truth Snapshot (what the code/data actually contains today)

### Build / packaging
- `gradle.properties`: `mod_version=1.0.2`, `minecraft_version=26.2`,
  `loader_version=0.19.3`, `fabric_version=0.155.2+26.2`. Build tooling: Loom `1.17-SNAPSHOT`,
  Gradle wrapper `9.5.1`.
- `src/main/resources/fabric.mod.json`:
  - `version: "${version}"` (substituted by Loom to `1.0.2`).
  - `name`: `Chiseled Enchanting Table (Unofficial 26.2 Port)`.
  - `description`: explicitly states this is an unofficial port and not affiliated with the
    original author.
  - `authors`: original `Sacramentix` plus the unofficial-port maintainer `q14433686-arch`.
  - `contact.sources`: this port's GitHub; `contact.homepage`: original upstream.
  - `depends`: `fabricloader >=0.19.3`, `minecraft 26.2`, `java >=25`, `fabric-api *`.
  - `mixins: ["chiseled-enchanting-table-port.mixins.json"]` — the port config now uses package
    `chiseled_enchanting_table.mixin` and declares 8 active mixins: 5 structure mixins, vanilla
    enchanting-table book blocking, narrow anvil enchanted-book same-level merge blocking, and
    chiseled-bookshelf random-tick ambience.
  - `custom.chiseled_enchanting_table:port_notes`: documents the unofficial-port status and the
    current 1.0.2 mixin/loot/structure boundary.
- `.github/workflows/publish.yml`: staged in `docs/ci/publish.yml` (`MINECRAFT_VERSION: 26.2`,
  `VERSION: 1.0.2`, `RELEASE_NAME: ... (Unofficial 26.2 Port)`), ready to apply with
  `bash docs/ci/install.sh` using a maintainer token that has `workflows` write permission.
- **CI status (2026-10-21):** GitHub Actions `build` workflow (run `39663201859`, commit `8b00060`)
  is **green** on Minecraft 26.2 — `compat_check.py`, `:compileJava`, `:compileClientJava`,
  `remapJar` (with both JEI 26.2 and REI 26.2 enabled), and artifact upload (`369,768` bytes) all
  succeeded.

### Source layout
- `chiseled_enchanting_table.block.*` — `ChiseledEnchantingTableBlock`,
  `ChiseledEnchantingTableBlockEntity`, `FloatingBook`, `ChiseledBookshelfTick`
  (the legacy mixed `chiseledEnchantingTable` package has been retired).
- `chiseled_enchanting_table.data.ChiseledEnchantingTableData` — server-data reload listener that
  loads JSON-driven default unlocks, costs, transformations.
- `chiseled_enchanting_table.mechanics.enchanting.EnchantmentApplication` and
  `chiseled_enchanting_table.mechanics.transformation.ItemTransformationApplication`.
- `chiseled_enchanting_table.mechanics.loot.EnchantedBookLootInjector` +
  `RemoveEnchantedBooksFunction` — chest-loot injection driven by `loot_injections.json`.
- `chiseled_enchanting_table.mechanics.structure.{BookshelfReplacerProcessor, BookshelfBookFiller}`
  — structure bookshelf replacement processor + weighted book fill; called from **5 mixins**
  under `chiseled_enchanting_table.mixin.structure.*` (4 injectors + 1 accessor).
  `REPLACEMENT_ENABLED = true`. Two-phase pipeline, **worldgen-safe by contract**: nothing in the
  `finalizeProcessing`/`processBlock` path may touch the real `ServerLevel` (a worldgen worker
  reading the chunk it is generating deadlocks the server — see
  [`docs/reports/WORLDGEN_BOOKSHELF_DEADLOCK_REPORT.md`](docs/reports/WORLDGEN_BOOKSHELF_DEADLOCK_REPORT.md)).
  - **Template-pool path** (villages / mansions / ancient cities): `processBlock` swaps
    `BOOKSHELF` → empty `CHISELED_BOOKSHELF`, then `finalizeProcessing` resolves the dynamic
    enchantment registry through the passed `ServerLevelAccessor` (registry + seed only) and
    returns a finished `StructureBlockInfo` — state with `slot_*_occupied` **plus** the
    block-entity NBT (`ContainerHelper.saveAllItems` layout). Vanilla's `placeInWorld` applies the
    tag, so the books are never written to a live chunk from a worker thread. The position is also
    marked for post-processing so the server thread can refine the facing once the piece is placed.
  - **Programmatic path** (stronghold libraries): `StructurePieceMarkMixin` (uses
    `StructurePieceAccessor` invoker to convert piece-local → world coords via vanilla
    `getWorldPos`) marks the position; `LevelChunkPostProcessMixin` at `@At("HEAD")` iterates the
    still-full `postProcessing` lists using vanilla's own
    `ProtoChunk.unpackOffsetCoordinates(short, int sectionY, ChunkPos)` and delegates to
    `BookshelfReplacerProcessor.postProcessBookshelf` (plain `BOOKSHELF` → full fill) or
    `postProcessFilledBookshelf` (already-filled `CHISELED_BOOKSHELF` → facing refinement only).
    Both run on the server thread; the live-world filler refuses to run anywhere else.
- `chiseled_enchanting_table.compat.EnchantQueryIndex` + `compat.jei.*` + `compat.rei.*` —
  recipe-viewer support. The index is viewer-agnostic (registry + `enchantment_costs.json` only);
  each adapter turns every (enchantment, level) row into a two-input recipe so a single category
  answers both "book → items" and "item → books". Optional: `enable_jei=false` /
  `enable_rei=false` drop the respective adapter from the build; REI additionally needs
  `me.shedaniel.cloth:basic-math` + `dev.architectury:architectury-fabric` at compile time only
  (`26.2.+` / `0.6.1` / `21.0.4`).
  Costs are server-authoritative: `menu.EnchantCostTablePayload` + `menu.EnchantCostSync` push a
  per-player resolved table (`chiseled_enchanting_table:enchant_cost_table`) on join and after each
  successful data-pack reload; `compat.SyncedEnchantmentCosts` caches it on the client (cleared on
  disconnect) and both the viewers and `ChiseledEnchantingTableScreenHandler` read it.
- `chiseled_enchanting_table.menu.*` — `ChiseledEnchantingTableScreenHandler`,
  `ChiseledEnchantingMenuPayloads` (`OptionType`, `TransformationDisplay`,
  `AvailableEnchantmentPayload`, `ApplyEnchantmentPayload`), `AvailableOption`
  (typed enchantment vs. transformation), `EnchantCostSync`, `EnchantCostTablePayload`.
- `chiseled_enchanting_table.registry.*` — `BlockRegistry`, `EntityRegistry`,
  `ScreenHandlerRegistry`, `StructureProcessorRegistry`.
- `chiseled_enchanting_table.utils.*` — `Advancement`, `BlockPosStream`, `EnchantmentFinder`,
  `EnchantmentWithLevel`, `InventoryUtils`.
- Client (`src/client/java`): `ChiseledEnchantingTableClient`, `ChiseledEnchantingTableScreen`,
  `EnchantementListWidget`, `gui.row.*` (`RowRenderSupport`, `EnchantmentRowRenderer`,
  `TransformationRowRenderer`).

### Data / JSON
- `data/chiseled_enchanting_table/chiseled_enchanting_table/default_enchantments.json` —
  `values` (small list of explicitly-unlocked vanilla treasure enchantments) + `tags`
  (default `#minecraft:non_treasure`, level 1) for cross-mod tag-driven unlocks.
- `data/chiseled_enchanting_table/chiseled_enchanting_table/enchantment_costs.json` — per-level
  item + XP costs, with the `scute` -> `turtle_scute` fix.
- `data/chiseled_enchanting_table/chiseled_enchanting_table/item_transformations.json` — JSON
  source of truth for item transformations; the compat check enforces no drift with the Java
  fallback table.
- `data/chiseled_enchanting_table/chiseled_enchanting_table/loot_injections.json` — enchanted-book
  injections into 41 vanilla chest / archaeology loot tables (196 sub-entries). Auto-derived from
  upstream `ReworkEnchantedBookChestLoot.java` by `scripts/import_upstream_loot.py`.
- `data/chiseled_enchanting_table/chiseled_enchanting_table/bookshelf_book_pool.json` — v2 fill
  rules for structure-placed chiseled bookshelves: `book_count = { min: 3, max: 5 }`,
  `enchanted_book_probability = 0.03`, and an `enchanted_book_selection` block that describes how
  the enchanted-book candidate pool from `loot_injections.json` is weighted (base weight ×
  live-registry `Enchantment.getWeight()` × `maxLevelWeightRatio^relativeLevel` where
  `relativeLevel = (level-1)/(maxLevel-1)`). Single source of truth for "which enchanted books
  appear in world content" is `loot_injections.json`; this file only configures how often / how
  weighted.
- Block loot table at the modern singular path
  `data/chiseled_enchanting_table/loot_table/blocks/chiseled_enchanting_table.json` (drops-self,
  with `CopyName` from the block entity). The legacy plural `loot_tables/blocks/` path is gone.
- Advancement guide resources under `data/chiseled_enchanting_table/advancement/` now use
  translatable display components. The guide line covers crafting, shelf unlocks, writing to books,
  upgrading books at the chiseled table, max-level books, book-to-item transfer, book-to-book
  transfer, and the hidden all-enchantments book challenge. English and Simplified Chinese strings
  live in the client lang files.
- `data/chiseled_enchanting_table/chiseled_enchanting_table/book_upgrades.json` — JSON source for book-upgrade routes: anvil-like combine upgrades and themed-material research upgrades.

### Tooling
- `scripts/compat_check.py` — Tier 1 (offline, fails the build) covers singular-dir convention,
  no stale plural dirs, non-empty block-loot pools, all-JSON parses, JSON/Java transformation
  drift, and `default_enchantments.json` `tags` schema. Tier 2 (best-effort, warn-only) checks
  id existence against `--mc-jar` and/or `--extra-jar` paths.

### Active Temporary Boundaries
- Main-source exclusions still parked in `build.gradle`:
  - `chiseled_enchanting_table.integration.*` (third-party mod integration; port later as an
    opt-in layer)
  - `mixin/anvil/RemoveXpCostScaling.java` (broader upstream anvil XP/prior-work-cost tweak; kept
    separate from the now-restored same-level book merge block)
  - `mixin/accessor.*` (old item-component / enchanted-book stack-size accessor still needs its own
    26.1.2 mapping and compatibility pass)
  - Legacy `modifyChestLootTable/**`, `structureProcessor/**`, `utils/ChiseledBookshelfLootTable`
    (fully superseded by the JSON-driven injector + `mechanics/structure/*`)
- Runtime feature gaps:
  - Shelf-power formula final balancing.
  - Full live-client gameplay validation of the actual bookshelf fills in villages / mansions /
    strongholds / ancient cities is still pending (mixin call sites are verified; the fill
    outcome — visible books in the shelves — is what needs the next round of live testing).

### Release artifacts (this round)
Staged under `release_output/chiseled_enchanting_table_1_0_2/`:
- `chiseled-enchanting-table-1.0.2.jar` (in-jar icon matches the 256×256 cover)
- `chiseled-enchanting-table-1.0.2-sources.jar`
- `chiseled-enchanting-table-1.0.2-source.zip` (working-tree zip; excludes
  `.git/`, `.gradle/`, `build/`, `release_output/`).
- `cover-icon-256.png` — 256×256 RGBA cover icon, ready to upload to Modrinth / CurseForge /
  MC百科 directly.
- `cover-banner.png` — repo root banner, staged alongside for optional gallery use.

Release output is consolidated under the current `1_0_2` directory; older internal porting-round artefact directories are not part of the current release output.

## Verified This Session (2026-07-01)

This session added the localized advancement guide pass and rebuilt the release artefacts as 1.0.2.

- Advancement JSON display text was moved from hardcoded English to `translate` components.
- Added Simplified Chinese and refreshed English advancement strings in client lang files.
- Added `upgrade_enchanted_book` and `max_level_book` guide advancements.
- `EnchantmentApplication` now awards book-route guide advancements for table-written books,
  book upgrades, max-level books, book-to-item transfer, and book-to-book transfer.
- `Advancement.checkAllEnchantBook` now delegates to real book milestone detection, including the
  hidden registry-sized all-enchantments-book challenge.
- Documentation/backlog updated to stop treating floating-book animation as a blocker per current
  user feedback.
- `chmod +x ./gradlew`; Java 25.0.3 from `/home/user/jdkwork/jdk-25.0.3`; Gradle 9.4.1, Loom 1.16.3.
- Resource processing: `processResources --stacktrace` -> BUILD SUCCESSFUL.
- Main/client compile: `compileJava compileClientJava --stacktrace` -> BUILD SUCCESSFUL.
- `python3 scripts/compat_check.py` -> **`PASS (0 warning(s))`** (Tier 2 not exercised — no
  `--mc-jar` / `--extra-jar` provided).
- Full build: `build --rerun-tasks --stacktrace` -> BUILD SUCCESSFUL.
- Built artefacts checked under `build/libs/`: `chiseled-enchanting-table-1.0.2.jar` and
  `chiseled-enchanting-table-1.0.2-sources.jar`.
- Jar metadata/resource spot check: `fabric.mod.json` has `version=1.0.2`, unofficial-port name,
  active port mixin config, advancement JSONs, and both `en_us` / `zh_cn` advancement translations.
- `release_output/chiseled_enchanting_table_1_0_2/` recreated with the current jars and a refreshed
  working-tree source zip.

## Client-Only / Live-Game Validation Still Pending

Same as the prior baseline — these require a real MC 26.1.2 + Fabric runtime and were not run in
this headless sandbox: mod-metadata load, the restored balance / ambience mixins and advancement triggers/translations in a real
Vanilla Enchanting Table / Anvil / Chiseled Bookshelf / Chiseled Enchanting Table setup, block placement, custom menu open, slot/scroll/floating book
renderer, enchanting + transformation application, chiseled-bookshelf scanning, P19 tag-based
cross-mod default unlocks, multiplayer sync beyond payload registration, and mining drop in a
live world. The full list is kept in `docs/reports/CURRENT_AGENT_BRIEF.md`.


## 1.0.2 Hotfix Note

- Book combine upgrade now explicitly replaces the cost-slot enchanted book with a plain book after the upgrade, matching the expected visible consumption behavior.
- Validation: `compileJava compileClientJava`, `compat_check.py`, and `build --rerun-tasks` all passed.

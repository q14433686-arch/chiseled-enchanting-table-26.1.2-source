# Current Baseline

> **Unofficial community port.** This repository is an unofficial community port of
> [Sacramentix/chiseled-enchanting-table](https://github.com/Sacramentix/chiseled-enchanting-table)
> to Minecraft **26.1.2** on Fabric. It is **not affiliated with, endorsed by, or maintained by
> the original author**. All credit for the original mod design and assets belongs to Sacramentix
> (MIT licensed); this port only updates the code/data to the 26.1.2 toolchain.

**Date:** 2026-10-21
**Minecraft Version:** 26.1.2
**Fabric Loader:** 0.19.3
**Fabric API:** 0.151.0+26.1.2
**Gradle:** 9.4.1 (wrapper; unchanged this round on purpose)
**Loom:** 1.16-SNAPSHOT (1.16.3 was the resolved version for the 1.0.2 build)
**Java:** 25 required (`options.release = 25`). **No JDK exists in the current dev sandbox**, so this
round was verified with the offline gates + CI instead of a local `./gradlew` run — see
"Verified this session".
**Mod Version:** `1.0.3`  *(unofficial-port versioning, see "Versioning" below)*

## Versioning

This repository currently publishes the unofficial 26.1.2 port as **`1.0.3`**. Earlier internal
porting labels are treated as historical implementation notes only; current metadata, release
artefacts and documentation should all refer to `1.0.3`.

`1.0.3` on *this* branch is **not** a Minecraft port. It is the back-port of the sister `26.3`
branch's version-independent fixes and features onto the 26.1.2 line, which is why both branches
carry the same `mod_version`: the number answers "which feature set", the branch /
`minecraft_version` answers "which Minecraft". Mapping and rationale:
[`docs/reports/SYNC_FROM_26_3_REPORT.md`](docs/reports/SYNC_FROM_26_3_REPORT.md).
`docs/marketing/*` still describes `1.0.2` and needs a pass before any publish run.

## Current Source-Truth Snapshot (what the code/data actually contains today)

### Build / packaging
- `gradle.properties`: `mod_version=1.0.3`, `minecraft_version=26.1.2`,
  `loader_version=0.19.3`, `fabric_version=0.151.0+26.1.2`, plus the optional-viewer block
  (`enable_jei=true`, `jei_version=29.47.0.111`, `enable_rei=true`, `rei_version=26.1.819`,
  `rei_math_version=0.6.1`, `rei_architectury_version=20.0.6`) — each value's provenance is
  commented in the file itself. The two viewer versions are **pinned to what CI actually resolved**
  on 2026-10-21 rather than left floating (`latest.release` / `26.1.+`): a `compileOnly` coordinate
  has no runtime effect, so floating only buys the ability to break without a commit here
  (`docs/reports/VIEWER_INTEGRATION_JEI_REI.md` §6).
- `build.gradle`: Loom `1.16-SNAPSHOT`; three extra repositories (BlameJared / Shedaniel /
  Architectury) and four `compileOnly` viewer dependencies behind `enable_jei` / `enable_rei`
  (JEI, REI api, cloth basic-math, architectury-fabric); the client source set excludes
  `compat/jei/**` and `compat/rei/**` when the matching toggle is `false`. `options.release = 25`,
  Java 25 source/target, `withSourcesJar()`.
  Every viewer dependency is configured with a shared `toolchainPins` closure that excludes
  `net.fabricmc.fabric-api`, `net.fabricmc` and `com.mojang`: without it a viewer's POM upgrades the
  Fabric API out from under Loom's interface injections and the **main** source set stops compiling
  (`cannot access EntityFluidExtension`). The 26.3 branch declares the same dependencies without
  exclusions and only escapes the failure because its Fabric API is already the newest in its graph —
  `docs/reports/VIEWER_INTEGRATION_JEI_REI.md` §6.1, sync report §8 finding 7.
- `src/main/resources/fabric.mod.json`:
  - `version: "${version}"` (substituted by Loom to `1.0.3`).
  - `name`: `Chiseled Enchanting Table (Unofficial 26.1.2 Port)`.
  - `description`: explicitly states this is an unofficial port and not affiliated with the
    original author.
  - `authors`: original `Sacramentix` plus the unofficial-port maintainer `q14433686-arch`.
  - `contact.sources`: this port's GitHub; `contact.homepage`: original upstream.
  - `depends`: `fabricloader >=0.19.3`, `minecraft 26.1.2`, `java >=25`, `fabric-api *`.
  - `mixins: ["chiseled-enchanting-table-port.mixins.json"]` — the port config now uses package
    `chiseled_enchanting_table.mixin` and declares 8 active mixins: 5 structure mixins, vanilla
    enchanting-table book blocking, narrow anvil enchanted-book same-level merge blocking, and
    chiseled-bookshelf random-tick ambience. Unchanged by the 1.0.3 sync (the deadlock fix adds no
    mixin); `scripts/ci_static_checks.py` now proves all 8 resolve to real sources and none of them
    is excluded from compilation by `build.gradle`.
  - `entrypoints`: `main`, `client`, plus the two optional viewer hooks `jei_mod_plugin`
    (`compat.jei.ChiseledEnchantingJeiPlugin`) and `rei_client`
    (`compat.rei.ChiseledEnchantingReiPlugin`). `suggests: { jei: "*", rei: "*" }` — neither viewer
    is required, and neither class is loaded unless the viewer itself is installed.
  - `custom.chiseled_enchanting_table:port_notes`: documents the unofficial-port status, the 1.0.3
    sync (and, explicitly, the 26.3-only items deliberately *not* taken), the 1.0.2 mixin/loot/
    structure base, and pointers to `CURRENT_BASELINE.md`, the sync report and `docs/EXTENDING.md`.
- CI (new this round — this branch had **no** `.github/workflows/` before): four workflows live in
  `.github/workflows/` with byte-identical reference copies in `docs/ci/` —
  `compile-check.yml` (compiles both source sets, writes `build-reports/compile-java.log` back to
  `arena/**` branches via the Contents API), `build.yml` (three offline static gates → full build →
  jar artifact, plus a `no-viewers` job that compiles with `-Penable_jei=false -Penable_rei=false`),
  `consistency.yml` (version gate + warn-only `docs/ci` drift check) and `publish.yml`
  (`MINECRAFT_VERSION: 26.1.2`, `JAVA_VERSION: 25`, `VERSION: 1.0.3`,
  `RELEASE_NAME: Chiseled Enchanting Table 1.0.3 (Unofficial 26.1.2 Port)`, `workflow_dispatch`
  only). Reference: [`docs/ci/README.md`](docs/ci/README.md).
- `gradlew` is committed `100755` (was `100644`), because `compile-check.yml` calls `./gradlew`
  without a `chmod` step.

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
  `REPLACEMENT_ENABLED = true`. Two-phase pipeline, **rewritten by the 1.0.3 deadlock fix** —
  `BookshelfBookFiller` is now split into a pure half and a server-thread half, and no worldgen
  worker ever touches a level:
  - **Template-pool path** (villages / mansions / ancient cities): `processBlock` swaps
    `BOOKSHELF` → empty `CHISELED_BOOKSHELF` at placement; `finalizeProcessing` resolves the
    dynamic enchantment registry through the `ServerLevelAccessor` **only** (registry access is
    thread-safe), computes the fill purely (`computeFill` + `toBlockEntityTag`) and returns a
    finished `StructureBlockInfo` (slot-occupied state + block-entity NBT) that vanilla's
    `StructureTemplate.placeInWorld` applies via `loadWithComponents`. It then marks the position
    (`ChunkAccess#markPosForPostprocessing`) so the shelf-facing pass can run later on the server
    thread. It never calls `getBlockState` / `getBlockEntity` / `setBlock` on the generating chunk —
    that was the deadlock
    ([report](docs/reports/WORLDGEN_BOOKSHELF_DEADLOCK_REPORT.md)).
  - **Programmatic path** (stronghold libraries): `StructurePieceMarkMixin` (uses
    `StructurePieceAccessor` invoker to convert piece-local → world coords via vanilla
    `getWorldPos`) marks the position; `LevelChunkPostProcessMixin` at `@At("HEAD")` iterates the
    still-full `postProcessing` lists using vanilla's own
    `ProtoChunk.unpackOffsetCoordinates(short, int sectionY, ChunkPos)` and delegates to
    `BookshelfReplacerProcessor.postProcessBookshelf`, which reuses the same filler — and, since
    1.0.3, also runs the deferred `applyFacingToPlacedBookshelf` pass for `CHISELED_BOOKSHELF`
    positions marked by `finalizeProcessing`.
  - Threading contract: `applyToPlacedBookshelf` / `applyFacingToPlacedBookshelf` are server-thread
    only (they log one error and no-op elsewhere) and read neighbours through
    `ServerChunkCache#getChunkNow`, so an unloaded neighbour is "unknown" rather than a synchronous
    chunk load. Slots are written via `getItems()`, not `setItem()`, so enchanted books are not
    re-validated against `#minecraft:bookshelf_books`.
- `chiseled_enchanting_table.menu.*` — `ChiseledEnchantingTableScreenHandler`,
  `ChiseledEnchantingMenuPayloads` (`OptionType`, `TransformationDisplay`,
  `AvailableEnchantmentPayload`, `ApplyEnchantmentPayload`), `AvailableOption`
  (typed enchantment vs. transformation), plus (1.0.3) `EnchantCostTablePayload` (S2C
  `chiseled_enchanting_table:enchant_cost_table`, hand-rolled VarInt wire format) and
  `EnchantCostSync` (registers the clientbound payload, pushes it on
  `ServerPlayConnectionEvents.JOIN` and after every successful
  `ServerLifecycleEvents.END_DATA_PACK_RELOAD`, guarded by `ServerPlayNetworking.canSend`).
- `chiseled_enchanting_table.compat.*` (1.0.3, **main** source set, no third-party imports) —
  `EnchantQueryIndex` (one row per (enchantment, level): supported items via
  `Enchantment#canEnchant`, costs, XP, conflicts; shared by both viewers) and
  `SyncedEnchantmentCosts` (client-side cache of the server-resolved rows, cleared on disconnect).
- `chiseled_enchanting_table.registry.*` — `BlockRegistry`, `EntityRegistry`,
  `ScreenHandlerRegistry`, `StructureProcessorRegistry`.
- `chiseled_enchanting_table.utils.*` — `Advancement`, `BlockPosStream`, `EnchantmentFinder`,
  `EnchantmentWithLevel`, `InventoryUtils`.
- Client (`src/client/java`): `ChiseledEnchantingTableClient` (menu screen + block-entity renderer
  + the cost-table payload receiver / disconnect-clear), `ChiseledEnchantingTableScreen`,
  `EnchantementListWidget`, `gui.row.*` (`RowRenderSupport`, `EnchantmentRowRenderer`,
  `TransformationRowRenderer`), `render.ChiseledEnchantingTableBlockEntityRenderer`, and (1.0.3)
  the two optional viewer adapters `compat.jei.*` (`ChiseledEnchantingJeiPlugin`,
  `EnchantingRecipeCategory`, `EnchantingRecipe`) and `compat.rei.*`
  (`ChiseledEnchantingReiPlugin`, `ReiEnchantingCategory`, `ReiEnchantingDisplay`). Both packages
  are excluded from compilation when their gradle toggle is `false`.

### Data / JSON
- `data/chiseled_enchanting_table/chiseled_enchanting_table/default_enchantments.json` —
  `values` (7 explicitly-unlocked vanilla treasure enchantments) + `tags`
  (shipped default **`#minecraft:in_enchanting_table`**, level 1 — the tag the vanilla enchanting
  table itself reads; its content defaults to `#minecraft:non_treasure`, so this is a pure superset
  that also picks up mods appending directly to `in_enchanting_table.json`) for cross-mod
  tag-driven unlocks. (`docs/EXTENDING.md` documented `non_treasure` here until 1.0.3; the data was
  always `in_enchanting_table`.)
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
  copying `minecraft:custom_name` from the block entity via `minecraft:copy_components`). The
  legacy plural `loot_tables/blocks/` path is gone. **Schema note:** this branch keeps the 26.1.2
  form — plural `conditions[]` / `functions[]` arrays whose elements dispatch on the `condition` /
  `function` string keys. Minecraft 26.3 collapsed that to a singular `condition` / `modifier`
  dispatched on `type`; `scripts/compat_check.py` fails on the 26.3 form here (and the 26.3 branch
  fails on this form there).
- Advancement guide resources under `data/chiseled_enchanting_table/advancement/` (8 files) cover
  crafting, shelf unlocks, writing to books, upgrading books at the chiseled table, max-level books,
  book-to-item transfer, book-to-book transfer, and the hidden all-enchantments book challenge.
  **Correction (1.0.3):** earlier baselines claimed these use translatable display components with
  English + Simplified Chinese strings in the client lang files. They do not — all eight files carry
  literal English `title` / `description`, and no `advancements.*` key exists in any `lang/*.json`,
  on this branch or on `26.3`. Localising them is backlog item **E** in `NEXT_TASK.md`.
  What *was* fixed this round in `craft.json`: the child advancement no longer carries
  `display.background` (ignored by 26.1.2, a hard world-load error in 26.3 — "Only advancement roots
  can have background"), and `show_toast` / `announce_to_chat` moved from the top level into
  `display`, where `DisplayInfo` actually reads them, so the intended "no toast, no chat" now
  applies. `recipe_id` stays: the 26.3 `recipes[]` rename is that version's schema, not a fix.
- `data/chiseled_enchanting_table/chiseled_enchanting_table/book_upgrades.json` — JSON source for book-upgrade routes: anvil-like combine upgrades and themed-material research upgrades.

### Tooling
- `scripts/compat_check.py` — Tier 1 (offline, fails the build) covers singular-dir convention,
  no stale plural dirs, non-empty block-loot pools, all-JSON parses, JSON/Java transformation
  drift, `default_enchantments.json` `tags` schema, and — new in 1.0.3 — the **26.1.2** loot-table
  and advancement schemas (`check_loot_tables_use_26_1_2_schema`,
  `check_advancements_use_26_1_2_schema`; both negative-tested against the 26.3-shaped files).
  Tier 2 (best-effort, warn-only) checks id existence against `--mc-jar` and/or `--extra-jar` paths.
- `scripts/ci_static_checks.py` (new) — mixin configs resolve to real `.java` files; a mixin
  registered in `fabric.mod.json` is never excluded from compilation by `build.gradle`; every
  `lang/*.json` has exactly the `en_us` key set (15 keys × 11 languages); warn-only relative
  markdown-link check.
- `scripts/check_release_consistency.sh` (new) — the five-place version gate
  (`gradle.properties` / `README.md` / `fabric.mod.json` / `.github/workflows/publish.yml` /
  `docs/ci/publish.yml`), with `--strict` for CI, `--staged` for the hook and `--porcelain` for
  machines.
- `scripts/install-hooks.sh` + `.githooks/pre-commit` (new) — warn-only local reminder; never blocks.
- `docs/ci/install.sh` (new) — copies the four reference workflows into `.github/workflows/`;
  `--check` reports drift without writing (used by `consistency.yml`, warn-only).
- `scripts/import_upstream_loot.py` — unchanged; regenerates `loot_injections.json` from upstream.

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
- Client-source exclusions, both **toggle-driven** rather than parked (1.0.3):
  `chiseled_enchanting_table/compat/jei/**` when `-Penable_jei=false`, and
  `chiseled_enchanting_table/compat/rei/**` when `-Penable_rei=false`. `build.yml`'s `no-viewers`
  job compiles that configuration on every push, so the escape hatch stays real.
- Runtime feature gaps:
  - Shelf-power formula final balancing.
  - The menu still has only `ENCHANTABLE_SLOT` + `COST_SLOT`: multi-item costs are consumed silently
    from the player's main inventory. Since 1.0.3 the client *knows* the true cost list (it is
    synced), so the missing multi-slot cost panel is now a pure UI/container gap — `NEXT_TASK.md`
    item B.
  - Advancement display strings are still hardcoded English (see the "Data / JSON" correction) —
    `NEXT_TASK.md` item E.
  - Live-client validation of the structure bookshelf fills is still pending, but the *reason*
    changed with 1.0.3: the fill now travels in the `StructureBlockInfo`, so what needs confirming
    in a real client is the new code path (books present, correct facing, no freeze), not the old
    mixin call sites.

### Release artifacts
**1.0.3 is not staged yet.** `release_output/` is not part of the repository tree, and the dev
sandbox for this round has no JDK, so no jar was produced locally: the jar for this round comes from
`build.yml`'s Actions artifact (`chiseled-enchanting-table-<sha>`, 14-day retention). When a release
is cut, stage it under `release_output/chiseled_enchanting_table_1_0_3/` with the same contents as
the 1.0.2 set below.

Previous (1.0.2) staging, under `release_output/chiseled_enchanting_table_1_0_2/`:
- `chiseled-enchanting-table-1.0.2.jar` (in-jar icon matches the 256×256 cover)
- `chiseled-enchanting-table-1.0.2-sources.jar`
- `chiseled-enchanting-table-1.0.2-source.zip` (working-tree zip; excludes
  `.git/`, `.gradle/`, `build/`, `release_output/`).
- `cover-icon-256.png` — 256×256 RGBA cover icon, ready to upload to Modrinth / CurseForge /
  MC百科 directly.
- `cover-banner.png` — repo root banner, staged alongside for optional gallery use.

The `1_0_2` directory above is the last committed staging set; older internal porting-round
artefact directories are not part of any current release output.

## Verified This Session (2026-10-21, the 1.0.3 sync round)

The sandbox for this round has **no JDK and no maven access** (`java` is not installed;
`maven.fabricmc.net`, `maven.blamejared.com`, `maven.shedaniel.me` and `maven.architectury.dev` are
all unreachable), so `./gradlew` cannot even resolve dependencies. Verification was therefore
"every offline gate + the viewers' own source trees + CI":

- `git apply --check -p1` clean for the 26.3 branch's pre-adapted
  `docs/patches/26.1.2-worldgen-bookshelf-deadlock.patch` (sha256
  `751b27f81e7e1db1424335453414abb77da2bf493dbbe171efdc6ed6a9040344`) against base `79717b5`;
  afterwards `BookshelfBookFiller` / `LevelChunkPostProcessMixin` differ from the 26.3 branch only
  by the intended 26.1.2 API adaptations plus the translatable-lore change.
- `python3 scripts/ci_static_checks.py` → PASS: 17 mixin class references resolve, no registered
  mixin is excluded, lang parity 15 keys × 11 files, markdown links ok (34 files).
- `python3 scripts/compat_check.py` → PASS (2 Tier-2 warnings: no MC jar available). The two new
  schema gates were negative-tested both ways: this branch's pre-fix `craft.json` → 3 failures; the
  26.3 branch's `craft.json` + block loot table → 3 failures.
- `bash scripts/check_release_consistency.sh --strict` → PASS, 12 checks.
- `bash docs/ci/install.sh --check` → ok ×4 (`docs/ci/` ≡ `.github/workflows/`).
- All eight workflow YAMLs parse (`build.yml`: `static` / `build` / `no-viewers`;
  `compile-check.yml`: `compile`; `consistency.yml`: `check`; `publish.yml`: `build`).
- Every added/changed JSON parses; `fabric.mod.json` re-validated after the entrypoint edit.
- **Viewer API drift check** (what makes the byte-identical adapter port defensible without a
  compiler): the JEI API files this mod touches were diffed between JEI's own `26.1` and `26.3`
  branches — 5 byte-identical, 5 differing only in `@since` javadoc; the 11 REI API files plus
  `api/build.gradle` and `settings.gradle` are byte-identical between REI's `26.1` and `26.3`
  branches. Coordinates for this line: `mezz.jei:jei-26.1.2-fabric` (JEI `26.1` pins
  `minecraftVersion=26.1.2`), `me.shedaniel:RoughlyEnoughItems-api-fabric:26.1.+`,
  `dev.architectury:architectury-fabric:20.0.6` (REI `26.1`'s own `architectury_version`),
  `me.shedaniel.cloth:basic-math:0.6.1`.
- **Compile: GREEN on GitHub Actions.** `compile-check.yml` run `39668301472` and all three
  `build.yml` jobs (static gates, full `build`, `no-viewers`) in run `39668301133`, both on commit
  `315fb6e`, 2026-10-21 — `BUILD SUCCESSFUL in 1m 12s` for `compileJava` + `compileClientJava` with
  JEI and REI enabled, 2 warnings (JEI `addItemStack` deprecated-for-removal; nothing from the
  structure/menu/compat code). `consistency.yml` run `39666575001` green. The log is committed back
  to the branch as `build-reports/compile-java.log` (plus `compile-deps.log` on failure) — the two
  earlier runs and what each one proved are tabled in the sync report §7.1.

## Verified for 1.0.2 (2026-07-01)

That session added the book-upgrade guide pass and rebuilt the release artefacts as 1.0.2.

- Advancement JSON display text was reported as moved from hardcoded English to `translate`
  components, and Simplified Chinese advancement strings were reported as shipped in the client lang
  files. **Neither is true of the committed tree** (see the correction under "Data / JSON" above);
  the guide advancements added that round are real, their localisation is not.
- Added the `upgrade_enchanted_book` / `max_level_book` guide advancements and their Java triggers.
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
  active port mixin config, advancement JSONs — and, as reported at the time, both `en_us` / `zh_cn`
  advancement translations (again: not present in the tree, see the correction above).
- `release_output/chiseled_enchanting_table_1_0_2/` recreated with the current jars and a refreshed
  working-tree source zip.

## Client-Only / Live-Game Validation Still Pending

Everything below requires a real MC 26.1.2 + Fabric runtime and was not run in this headless
sandbox. The 1.0.3 items are listed first, with the concrete reproduction each one needs
(`NEXT_TASK.md` §A0 carries the same list as a checklist):

1. **Worldgen deadlock fix** — fresh world, fly into an affected structure chunk (the reporter's
   case: a mesa village with Distant Horizons' `FEATURES` LOD generator); no freeze, and the shelves
   in village libraries / mansions / ancient cities actually **contain books** and face the open side;
   regression-test stronghold libraries (the mark-and-post-process path).
2. **JEI** — category appears under the table and the bookshelf; book → items and item → books both
   work; enchanted-book subtypes searchable.
3. **REI** — same pass with REI only; then JEI **and** REI together (both categories coexist).
4. **Cost sync** — dedicated server with a modified `enchantment_costs.json`: viewer row, tooltip and
   table GUI all show the server's cost; `/reload` updates without reconnecting; disconnect clears the
   cache.
5. **11 languages** — switch client language and confirm the mod's own strings (incl. the book lore
   line on a server-generated book) resolve.

Carried over from the prior baseline: mod-metadata load, the restored balance / ambience mixins and advancement triggers/translations in a real
Vanilla Enchanting Table / Anvil / Chiseled Bookshelf / Chiseled Enchanting Table setup, block placement, custom menu open, slot/scroll/floating book
renderer, enchanting + transformation application, chiseled-bookshelf scanning, P19 tag-based
cross-mod default unlocks, multiplayer sync beyond payload registration, and mining drop in a
live world. The full list is kept in `docs/reports/CURRENT_AGENT_BRIEF.md`.


## 1.0.2 Hotfix Note

- Book combine upgrade now explicitly replaces the cost-slot enchanted book with a plain book after the upgrade, matching the expected visible consumption behavior.
- Validation: `compileJava compileClientJava`, `compat_check.py`, and `build --rerun-tasks` all passed.

# Current Baseline

> **Unofficial community port.** This repository is an unofficial community port of
> [Sacramentix/chiseled-enchanting-table](https://github.com/Sacramentix/chiseled-enchanting-table)
> to Minecraft **26.3** on Fabric. It is **not affiliated with, endorsed by, or maintained by
> the original author**. All credit for the original mod design and assets belongs to Sacramentix
> (MIT licensed); this port only updates the code/data to the 26.3 toolchain.

> **History:** the 26.2 round (2026-07-19, commit `9ef1889`) was a declaration-only bump whose
> "live `./gradlew build` still required" caveat is now resolved — see
> `docs/reports/PORTING_26_2_REPORT.md`. The 26.1.2 state is at commit `8ecc4d2`.

> **26.3 branch (2026-10-13):** This line now targets **Minecraft 26.3**. Every declaration was
> bumped (Minecraft `26.3`, Fabric Loader `0.19.5`, Fabric API `0.162.0+26.3`, `mod_version`
> `1.0.3`; Loom stays on the 1.17 line because Fabric's own 26.3 branch pins `1.17.20`). Unlike the
> 26.2 round — which was a declaration bump that could not be compiled in the sandbox — this round
> was verified against decompiled **Minecraft 26.3** sources and against a git-blob diff of
> `FabricMC/fabric` `0.155.2+26.2` → `0.162.0+26.3`, and 5 source files + 3 data files really
> changed. Full evidence: `docs/reports/PORTING_26_3_REPORT.md`. The 26.2 state is preserved in git
> history (commit `9ef1889`).

**Date:** 2026-10-13
**Minecraft Version:** 26.3
**Fabric Loader:** 0.19.5
**Fabric API:** 0.162.0+26.3
**Gradle:** 9.5.1
**Loom:** 1.17 (Fabric's 26.3 branch pins 1.17.20)
**Java:** 25
**Mod Version:** `1.0.3`  *(unofficial-port versioning, see "Versioning" below)*

> **Compilation status:** this sandbox has no route to `maven.fabricmc.net`, so `./gradlew` cannot
> run here. The 26.3 source changes are verified by reading the 26.3 class files; the authoritative
> compile signal is the `compile-check` workflow, which writes `build-reports/compile-java.log` back
> onto `arena/**` branches.

## Versioning

This repository currently publishes the unofficial 26.3 port as **`1.0.3`**. Earlier internal
porting labels are treated as historical implementation notes only; current metadata, release
artefacts, documentation, and marketing copy should all refer to `1.0.3`.

Five places must agree on the version/Minecraft declarations — `gradle.properties`,
`README.md`, `src/main/resources/fabric.mod.json`, `.github/workflows/publish.yml` and this file.
`bash scripts/check_release_consistency.sh --strict` is the gate (CI runs it on every push).

## Current Source-Truth Snapshot (what the code/data actually contains today)

### Build / packaging
- `gradle.properties`: `mod_version=1.0.3`, `minecraft_version=26.3`,
  `loader_version=0.19.5`, `fabric_version=0.162.0+26.3`. Build tooling: Loom `1.17-SNAPSHOT`,
  Gradle wrapper `9.5.1`, `options.release = 25`.
- `src/main/resources/fabric.mod.json`:
  - `version: "${version}"` (substituted by Loom to `1.0.3`).
  - `name`: `Chiseled Enchanting Table (Unofficial 26.3 Port)`.
  - `description`: explicitly states this is an unofficial port and not affiliated with the
    original author.
  - `authors`: original `Sacramentix` plus the unofficial-port maintainer `q14433686-arch`.
  - `contact.sources`: this port's GitHub; `contact.homepage`: original upstream.
  - `depends`: `fabricloader >=0.19.5`, `minecraft 26.3`, `java >=25`, `fabric-api *`.
  - `mixins: ["chiseled-enchanting-table-port.mixins.json"]` — the port config now uses package
    `chiseled_enchanting_table.mixin` and declares 8 active mixins: 5 structure mixins, vanilla
    enchanting-table book blocking, narrow anvil enchanted-book same-level merge blocking, and
    chiseled-bookshelf random-tick ambience.
  - `custom.chiseled_enchanting_table:port_notes`: documents the unofficial-port status, the
    current 1.0.3 mixin/loot/structure boundary, and the 26.3 adaptation list.
- `.github/workflows/`: the TACZR-style CI triad is **installed and live** (applied by the
  maintainer on 2026-10-14, after the sandbox token was refused `workflows` write access):
  `compile-check.yml`, `build.yml`, `consistency.yml` and the corrected `publish.yml`
  (`MINECRAFT_VERSION: 26.3`, `VERSION: 1.0.3`). `docs/ci/` keeps byte-identical reference copies —
  `bash docs/ci/install.sh --check` currently reports all four `ok`.
- **CI status:** the whole triad is live and **green** for 26.3 — run `38431074401` on commit
  `1bc7e77` finished all 8 steps `success` with artifact `Artifacts` (295,720 bytes), so
  `:compileJava`, `:compileClientJava` and `remapJar` all pass on the 26.3 pins. `compile-check.yml`
  also commits `build-reports/compile-java.log` back to this branch, which is the only compile
  output a sandbox without maven access can read.
- **Runtime status:** the first 26.3 world load failed on `chiseled_enchanting_table:craft`
  (`Only advancement roots can have background`) and refused to open the save. Fixed by dropping
  `display.background` from that child advancement; `scripts/compat_check.py` now fails on either
  direction of that rule. See `docs/reports/PORTING_26_3_REPORT.md` §8.

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
  `me.shedaniel.cloth:basic-math` + `dev.architectury:architectury-fabric` at compile time only.
  See `docs/reports/VIEWER_INTEGRATION_JEI_REI.md`.
- `chiseled_enchanting_table.menu.*` — `ChiseledEnchantingTableScreenHandler`,
  `ChiseledEnchantingMenuPayloads` (`OptionType`, `TransformationDisplay`,
  `AvailableEnchantmentPayload`, `ApplyEnchantmentPayload`), `AvailableOption`
  (typed enchantment vs. transformation).
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
  drift, `default_enchantments.json` `tags` schema, and (new in 1.0.3) the **26.3 loot-table and
  advancement schemas** (`condition`/`modifier` + `type`, `recipe_crafted.recipes`, display-only
  `show_toast`/`announce_to_chat`). Tier 2 (best-effort, warn-only) checks id existence against
  `--mc-jar` and/or `--extra-jar` paths.
- `scripts/ci_static_checks.py` — mixin configs must resolve to real `.java` files; a mixin
  registered in `fabric.mod.json` must not be excluded from compilation by `build.gradle` (that
  combination ships a config pointing at a class missing from the jar); `en_us`/`zh_cn` key parity.
  Runs offline, so it works in the sandbox too.
- `scripts/check_release_consistency.sh` — version gate across `gradle.properties`, `README.md`,
  `fabric.mod.json` and `publish.yml`. Report-only by default, `--strict` for CI/release.
- `.githooks/pre-commit` + `scripts/install-hooks.sh` — warn-only local reminder; never blocks a
  commit.
- `docs/ci/` — byte-identical **reference copies** of the now-installed TACZR-style CI triad
  (`compile-check.yml` compiles both source sets and pushes `build-reports/compile-java.log` back to
  `arena/**`; `build.yml` runs the static gates then `./gradlew build` and uploads the jar;
  `consistency.yml` is the version gate; `publish.yml` is the version-bumped release workflow).
  `bash docs/ci/install.sh --check` reports drift without writing; a maintainer still has to apply
  any change, because this sandbox's token lacks the `workflows` scope
  permission — see `docs/ci/README.md`.

### Active Temporary Boundaries
- Main-source exclusions still parked in `build.gradle`:
  - `chiseled_enchanting_table.integration.*` (third-party mod integration; port later as an
    opt-in layer)
  - `mixin/anvil/RemoveXpCostScaling.java` (broader upstream anvil XP/prior-work-cost tweak; kept
    separate from the now-restored same-level book merge block)
  - `mixin/accessor.*` (old item-component / enchanted-book stack-size accessor still needs its own
    mapping and compatibility pass)
  - Legacy `modifyChestLootTable/**`, `structureProcessor/**`, `utils/ChiseledBookshelfLootTable`
    (fully superseded by the JSON-driven injector + `mechanics/structure/*`)
- Runtime feature gaps:
  - Shelf-power formula final balancing.
  - Full live-client gameplay validation of the actual bookshelf fills in villages / mansions /
    strongholds / ancient cities is still pending (mixin call sites are verified; the fill
    outcome — visible books in the shelves — is what needs the next round of live testing).
  - 26.3-specific: mixin **method-level** injection points are only proven at runtime, and nobody
    has looked at the floating book under 26.3's OIT transparency path (F3+X both states).

### Release artifacts
No `release_output/` staging directory exists in this tree (it was never committed). Build artifacts
come from either:
- the `build` workflow — `actions/upload-artifact` named `chiseled-enchanting-table-<sha>`
  (`build/libs/*.jar`, sources jar excluded, 14-day retention), or
- a local `./gradlew build` with JDK 25 → `build/libs/chiseled-enchanting-table-1.0.3.jar`.

If a maintainer wants the old staging layout back, the directory name follows
`release_output/chiseled_enchanting_table_<version with _ for .>` (i.e. `..._1_0_3`).

## Verified This Session (2026-10-13)

This session put the CI triad in place and ported the tree to Minecraft 26.3. Everything below was
run in the sandbox; the Gradle compile is the one signal that has to come from CI (see
`docs/reports/PORTING_26_3_REPORT.md` for the per-claim evidence).

- `python3 scripts/ci_static_checks.py` -> **PASS** (17 declared mixin references resolved; the
  unregistered legacy `chiseled-enchanting-table.mixins.json` reported as parked; 5 lang keys in
  parity).
- `python3 scripts/compat_check.py` -> **PASS (2 warnings)**; the warnings are the Tier-2 "no MC jar
  available" skips. Negative-tested: with the pre-port 26.2 loot table + `craft.json` restored, the
  two new 26.3 schema checks report **7 failures**.
- `bash scripts/check_release_consistency.sh --strict` -> on the pre-port tree it reported the two
  real `publish.yml` drifts (`MINECRAFT_VERSION: 26.1.2`, `RELEASE_NAME` on 26.1.2). Now:
  **12 passed, 0 warned, 0 failed** now that the corrected `publish.yml` is installed. The
  warn-instead-of-fail escape hatch that carried the port through the unpushable period was
  negative-tested both ways: staged copy broken (`VERSION: 9.9.9`) -> **3 failed, exit 1**.
- `bash docs/ci/install.sh --check` -> all four workflow files `ok`, exit 0
  (expected until a maintainer installs them).
- 26.3 signature verification by reading decompiled MC 26.3 sources: `PoseStack`
  (`rotate`/`rotateDegrees`, no `mulPose(Quaternionf)`), `EnchantTableRenderer` + `BlockEntityRenderer`
  + `OrderedSubmitNodeCollector.submitModel` (9 args) + `submitCrumblingOverlay` (8 args),
  `LootPool`/`LootPoolEntryContainer`/`UniformContainerBase`/`ContextIntProviders` builders,
  `LootItemConditionalFunction(Optional<Holder<…>>)`, `FriendlyByteBuf` (no collection helpers;
  `writeVarInt`/`readVarInt` present), `LootPool.CODEC` / `RecipeCraftedTrigger` / `Advancement` /
  `DisplayInfo` / `ClientAsset.ResourceTexture` schemas, and all 8 mixin targets.
- Fabric API blob diff `0.155.2+26.2` → `0.162.0+26.3` for all 10 imported classes: 9 identical,
  `ExtendedMenuType` javadoc-only.
- **Not run here:** `./gradlew` (no `maven.fabricmc.net` / Mojang maven route in this sandbox). The
  `compile-check` workflow supplies that signal and writes it to
  `build-reports/compile-java.log` on this branch.

## Client-Only / Live-Game Validation Still Pending

Same as the prior baseline — these require a real MC 26.3 + Fabric runtime and were not run in
this headless sandbox: mod-metadata load, mixin **method-level** injection (the 5 structure mixins
plus the anvil / enchanting-table / chiseled-bookshelf ones), the balance / ambience mixins and
advancement triggers/translations in a real Vanilla Enchanting Table / Anvil / Chiseled Bookshelf /
Chiseled Enchanting Table setup, block placement, custom menu open, slot/scroll/floating book
renderer (including 26.3's OIT transparency path, F3+X both states), enchanting + transformation
application, chiseled-bookshelf scanning, tag-based cross-mod default unlocks, multiplayer sync
beyond payload registration, and mining drop in a live world. The full list is kept in
`docs/reports/CURRENT_AGENT_BRIEF.md`.


## 1.0.2 Hotfix Note (historical, still shipped in 1.0.3)

- Book combine upgrade explicitly replaces the cost-slot enchanted book with a plain book after the
  upgrade, matching the expected visible consumption behavior.
- Validation at the time: `compileJava compileClientJava`, `compat_check.py`, and
  `build --rerun-tasks` all passed against the 26.2 toolchain.

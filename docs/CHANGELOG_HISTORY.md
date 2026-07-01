# Changelog History (pre-1.0.2)

> **Unofficial community port** — archive of the port's own historical changelog entries. See
> [`../CHANGELOG.md`](../CHANGELOG.md) for the currently-shipping release. This file preserves
> everything at and below `1.0.2` verbatim, including the pre-`1.0.2` internal porting rounds
> (`P1`-`P19`, dated `2026-06-30 → 2026-07-01`) that built the port up before the first
> versioned release, and the full `[1.0.2]` rename/rebrand entry.

The entries below kept their original wording and (where applicable) version strings
(`0.2.3+26.1.2-pNN`) on purpose — they are a historical record of how the port was built, not a
description of currently-shipping behaviour. If you're evaluating whether a feature is present
in a current release, read `CURRENT_BASELINE.md` and the top of the main changelog instead.

## [1.0.2] - 2026-07-01

First stable, publishable build of the unofficial 26.1.2 port. No source/resource bytes were
changed relative to the previous internal `0.2.3+26.1.2-p18` snapshot — this release just
renumbers and rebrands.

### Versioning / metadata
- Switched the unofficial-port version line from `0.2.3+26.1.2-pNN` to clean SemVer starting at
  **`1.0.2`** (`gradle.properties` `mod_version=1.0.2`). The MC target version is no longer baked
  into `mod_version`; it lives in `fabric.mod.json` `depends.minecraft` and the publish workflow.
- `fabric.mod.json`:
  - `name` -> `Chiseled Enchanting Table (Unofficial 26.1.2 Port)`.
  - `description` now explicitly states this is an unofficial community port, not affiliated with
    or endorsed by the original author.
  - `authors` lists both `Sacramentix (original mod)` and `q14433686-arch (unofficial 26.1.2 port)`.
  - `contact.sources` points to this port's repo; `contact.homepage` points to the upstream repo.
  - `custom.chiseled_enchanting_table:port_notes` rewritten to describe the unofficial-port status
    and the current mixin/loot/structure boundary instead of the obsolete "P3 temporary" note.
- `.github/workflows/publish.yml`: `MINECRAFT_VERSION` set to `26.1.2`, `JAVA_VERSION` to `25`,
  `VERSION` to `1.0.2`, `RELEASE_NAME` to `Chiseled Enchanting Table 1.0.2 (Unofficial 26.1.2 Port)`.
- Docs marked as Unofficial Port at the top of `README.md`, `CURRENT_BASELINE.md`, `NEXT_TASK.md`,
  `CHANGELOG.md`, `docs/DOCUMENTATION_INDEX.md`.

### Release artefacts
- Removed the now-stale `release_output/chiseled_enchanting_table_26_1_2_p18/` directory.
- Created `release_output/chiseled_enchanting_table_1_0_2/` containing:
  - `chiseled-enchanting-table-1.0.2.jar`
  - `chiseled-enchanting-table-1.0.2-sources.jar`
  - `chiseled-enchanting-table-1.0.2-source.zip` (working-tree zip; excludes `.git/`, `.gradle/`,
    `build/`, `release_output/`)

### Validators run this round
- `python3 scripts/compat_check.py` -> `PASS (0 warning(s))` (Tier 1 only; Tier 2 needs MC/extra
  jar).
- `./gradlew dependencies --configuration compileClasspath` -> BUILD SUCCESSFUL.
- `./gradlew processResources --stacktrace` -> BUILD SUCCESSFUL.
- `./gradlew compileJava compileClientJava --stacktrace` -> BUILD SUCCESSFUL.
- `./gradlew build --rerun-tasks --stacktrace` -> BUILD SUCCESSFUL.
- Jar metadata spot check on `chiseled-enchanting-table-1.0.2.jar`: `version=1.0.2`, name updated,
  authors updated, `depends.minecraft=26.1.2`, `mixins=[]`.

### Notes on historical version labels
- The internal porting rounds **P1-P19** (each previously tagged
  `0.2.3+26.1.2-pNN`) are preserved as historical entries below and in
  `docs/reports/PORTING_26_1_2_PNN_*.md`. Those `pNN` labels no longer correspond to a
  `mod_version` value; they are kept only as an accurate record of how the unofficial port was
  built up. Future post-1.0.2 work should bump SemVer normally.

## Historical Internal Pre-1.0.2 Rounds

> The entries below kept their original wording and version strings on purpose — they are the
> historical record of the P1-P19 porting work that landed in this `1.0.2` release.

### P19 Cross-Mod Enchantment Compatibility (tag-driven default unlocks)
- Verified the repository builds clean from a fresh clone of
  `q14433686-arch/chiseled-enchanting-table-26.1.2-source` on a sandbox that only ships JDK 11.
- Installed `openjdk-25-jdk-headless` (-> `/usr/lib/jvm/java-25-openjdk-amd64`, Java 25.0.3).
- Restored execute bit on `./gradlew`.
- Ran the full required Gradle chain: `dependencies --configuration compileClasspath`,
  `processResources`, `compileJava compileClientJava`, `build --rerun-tasks`. All BUILD SUCCESSFUL.
- Ran `python3 scripts/compat_check.py` -> PASS (2 warnings: MC jar / extra-jar not provided, so
  Tier 2 id-existence is intentionally skipped).
- Recreated `release_output/chiseled_enchanting_table_26_1_2_p18/` (absent from the repo snapshot)
  and staged: `chiseled-enchanting-table-0.2.3+26.1.2-p18.jar`,
  `chiseled-enchanting-table-0.2.3+26.1.2-p18-sources.jar`, and a `git archive HEAD`-based
  `chiseled-enchanting-table-26.1.2-p18-source.zip`.
- No Java/JSON/resource bytes were touched, so `mod_version` stays at `0.2.3+26.1.2-p18` and no new
  porting report file is created. CURRENT_BASELINE / NEXT_TASK / CURRENT_AGENT_BRIEF are updated
  in-place to reflect the verified environment.
- Note: the agent-brief instructions reference `python3 scripts/generate_agent_brief.py --write`,
  which does not exist in this repo (only `scripts/compat_check.py` is present); this is consistent
  with `docs/reports/CURRENT_AGENT_BRIEF.md`'s own note that the script is not runnable here.

### P19 Cross-Mod Enchantment Compatibility (tag-driven default unlocks)
- Investigated compatibility with three categories of third-party enchantment/anvil mods (Ore
  Enchants-style new-enchantment mods, Universal Enchants-style `supported_items`/`exclusive_set`
  rewrites, Merge Enchantments-style anvil merge mods). Findings: item-compatibility checks
  (`Enchantment.canEnchant`/`areCompatible`) and chiseled-bookshelf book discovery already read
  live registry/component data by id, so they were already compatible with the first two
  categories without any code change; Merge Enchantments operates on the vanilla anvil, a separate
  interaction path from this table's menu, so the two don't currently conflict either way.
- The one real gap: `default_enchantments.json` was a hand-maintained list of `minecraft:` ids, so
  a third-party enchantment never appeared in the table's *default* unlock set even though it could
  already be discovered via a stored book. Closed this gap with a JSON-only extension: a new `tags`
  array (alongside the existing `values` list) that expands to every enchantment present in a
  vanilla or modded enchantment tag, resolved against the live registry at lookup time. Defaults to
  `#minecraft:non_treasure` — the same tag the vanilla enchanting table already uses to decide
  "normal" enchantments, and the standard tag enchantment-adding mods place their own content in.
- `ChiseledEnchantingTableData.defaultEnchantments(HolderGetter<Enchantment>)` (new overload) does
  the tag resolution; `ChiseledEnchantingTableBlock.enchantments_unlocked_by_default(Level)` now
  passes the world's enchantment registry lookup through. The existing no-arg
  `defaultEnchantments()` is preserved for callers without registry access.
- `default_enchantments.json` no longer hand-lists every "normal" vanilla enchantment (now covered
  by the `#minecraft:non_treasure` tag); `values` only keeps the small set of vanilla
  treasure-only enchantments this table intentionally also unlocks by default.
- `scripts/compat_check.py`: validates the new `tags` schema (Tier 1); the id-existence check
  (Tier 2) now recognises any `namespace:path` id (not just `minecraft:`), reports modded ids
  referenced from our own JSON as an informational warning, and accepts repeatable `--extra-jar`
  paths to check referenced ids against a third-party mod's jar.
- New `docs/COMPATIBILITY.md`: documents the three mod categories investigated, which combinations
  are already expected to work and why, which are untested, and how to extend
  `default_enchantments.json`/`enchantment_costs.json` for a specific third-party mod.
- No gameplay change for installations without any of the investigated mods (the `non_treasure`
  tag's vanilla membership is unchanged from the previous explicit `values` list it replaces).
- Bumped local port version to `0.2.3+26.1.2-p18`.
- Report: `docs/reports/PORTING_26_1_2_P19_CROSS_MOD_COMPATIBILITY_REPORT.md`.

### P18 Typed Available-Option Model
- Stopped overloading `EnchantmentWithLevel` as a transformation pseudo-option. The available enchantment set is now strictly real enchantments; transformations travel only on their own synced `TransformationDisplay` channel (they were previously sent twice).
- Added `menu.AvailableOption` (explicit `OptionType` + id + level) and rebuilt the client list around it, so the UI no longer guesses "is this a transformation?" via id lookups.
- Removed the now-dead `ChiseledEnchantingTableData.allTransformationEnchantments()` and the transformation merge in `ChiseledEnchantingTableBlock`.
- No gameplay change; the same enchantments/transformations appear.
- Bumped local port version to `0.2.3+26.1.2-p17`.
- Report: `docs/reports/PORTING_26_1_2_P18_AVAILABLE_OPTION_MODEL_REPORT.md`.

### P17 Retire Legacy `chiseledEnchantingTable` Package
- Moved `ChiseledEnchantingTableBlock`, `ChiseledEnchantingTableBlockEntity`, `FloatingBook`, and `ChiseledBookshelfTick` from the legacy mixed `chiseledEnchantingTable` package into a dedicated `block` package; updated all importers and the `build.gradle` exclude path.
- Removed leftover `*.orig`/`*.rej` scratch files and deleted the now-empty legacy package. Package split (block / data / mechanics / menu / client.gui) is now complete.
- Bumped local port version to `0.2.3+26.1.2-p16`.
- Report: `docs/reports/PORTING_26_1_2_P17_BLOCK_PACKAGE_REPORT.md`.

### P16 Mining Drop Fix + Compatibility Tooling
- Fixed: the chiseled enchanting table dropped nothing when mined. The block loot table was at the legacy plural path `loot_tables/blocks/` (ignored in MC 26.1.2) and had empty pools. Moved it to the singular `loot_table/blocks/` and wrote a proper drop-self table (modelled on vanilla `enchanting_table`, copies custom name from the block entity).
- Added `scripts/compat_check.py`: offline data-pack sanity checks (singular dir convention, non-empty block loot pools, JSON-vs-Java transformation drift) plus best-effort id existence vs the MC data jar.
- The new check caught a real compat bug: `minecraft:scute` (removed in 1.20.5) in `enchantment_costs.json` -> changed to `minecraft:turtle_scute`.
- Wired the compat check into `.github/workflows/build.yml` and corrected its JDK from 21 to 25.
- Added `docs/EXTENDING.md` documenting data-driven extension points and the singular-dir trap.
- Bumped local port version to `0.2.3+26.1.2-p15`.
- Report: `docs/reports/PORTING_26_1_2_P16_LOOT_FIX_AND_COMPAT_TOOLING_REPORT.md`.

### P15 Package/UI Decoupling + Enchanted Golden Apple XP Tweak
- Lowered **Enchanted Golden Apple** transformation XP cost from 30 levels to **3 levels**.
- Moved wire payloads into a dedicated `menu.ChiseledEnchantingMenuPayloads` (`OptionType`, `TransformationDisplay`, `AvailableEnchantmentPayload`, `ApplyEnchantmentPayload`).
- Moved `ChiseledEnchantingTableScreenHandler` into the `menu` package; it now only handles container/menu concerns and dispatch.
- Extracted gameplay mechanics: `mechanics.transformation.ItemTransformationApplication` and `mechanics.enchanting.EnchantmentApplication` (unlocked-enchant + book-enchant logic).
- Split the client list row rendering into `gui.row.RowRenderSupport` + `EnchantmentRowRenderer` + `TransformationRowRenderer`; transformation rows now have their own renderer instead of sharing the enchantment row's inline code.
- Updated all importers (registry, block, finder, screen, widget) to the new packages; removed the old screen-handler file.
- Report: `docs/reports/PORTING_26_1_2_P15_DECOUPLING_REPORT.md`.
- Still deferred: multi-slot cost panel, fully separate available-option model, moving block/blockentity out of the legacy package.

### P14 Transformation Fix + Payload Decoupling & Client Sync
- Fixed transformation: now **Golden Apple -> Enchanted Golden Apple** (was Apple -> Golden Apple), costing 8 gold blocks + 2-5 emeralds + 30 levels.
- Lowered **Experience Bottle** transformation XP cost from 12 levels to **1 level**.
- Added an explicit `OptionType` (`enchantment`/`transformation`) to `ApplyEnchantmentPayload`; server now dispatches on the wire type instead of guessing from a data lookup (still backwards-compatible with older clients via fallback).
- Removed the unused `ewl` local in `applyTransformation`.
- Synced a compact transformation display table (`TransformationDisplay`: id/input/output/level) to the client via the menu-open `AvailableEnchantmentPayload`, so datapack-defined transformations display/filter correctly even on dedicated servers; client UI now reads these from the screen handler with a local-data fallback.
- Bumped local port version to `0.2.3+26.1.2-p14`.
- Refreshed P14 release artifacts under `release_output/chiseled_enchanting_table_26_1_2_p14/`.
- Report: `docs/reports/PORTING_26_1_2_P14_TRANSFORMATION_FIX_AND_PAYLOAD_REPORT.md`.
- Deferred (user-confirmed): package split, separate transformation row renderer, multi-slot cost panel, full available-option model.

### Added
- P5/P6 port report: `docs/reports/PORTING_26_1_2_P5_P6_REPORT.md`.
- JSON data table for default unlocked enchantments:
  - `data/chiseled_enchanting_table/chiseled_enchanting_table/default_enchantments.json`
- JSON data table for enchantment item/XP costs:
  - `data/chiseled_enchanting_table/chiseled_enchanting_table/enchantment_costs.json`
- `ChiseledEnchantingTableData` server-data reload listener for loading/validating mechanics data.
- Refreshed P6 release artifacts under `release_output/chiseled_enchanting_table_26_1_2_p6/`:
  - `chiseled-enchanting-table-0.2.3+26.1.2-p6.jar`
  - `chiseled-enchanting-table-0.2.3+26.1.2-p6-sources.jar`
  - `chiseled-enchanting-table-26.1.2-p6-source.zip`

### Changed
- Bumped local port version to `0.2.3+26.1.2-p6`.
- Default enchantment unlocks now come from JSON-backed data instead of a Java literal table.
- Enchantment item/XP costs now come from JSON-backed data instead of the previous Java hardcoded mapping.
- `ChiseledEnchantingTableScreenHandler.get_cost_item()` now returns the actual cost slot stack instead of the previous empty-stack P4 stub.
- Updated `CURRENT_BASELINE.md`, `NEXT_TASK.md`, `docs/DOCUMENTATION_INDEX.md`, and current agent/report documentation for P5/P6.

### Validation
- PASS: JSON syntax validation for new P6 data tables.
- PASS: `JAVA_HOME=/tmp/herbcraft-jdk25/jdk-25 ./gradlew --no-daemon --max-workers=1 dependencies --configuration compileClasspath`
- PASS: `JAVA_HOME=/tmp/herbcraft-jdk25/jdk-25 ./gradlew --no-daemon --max-workers=1 processResources --stacktrace`
- PASS: `JAVA_HOME=/tmp/herbcraft-jdk25/jdk-25 ./gradlew --no-daemon --max-workers=1 -Dorg.gradle.jvmargs='-Xmx1024m -Dfile.encoding=UTF-8' compileJava compileClientJava --stacktrace`
- PASS: `JAVA_HOME=/tmp/herbcraft-jdk25/jdk-25 ./gradlew --no-daemon --max-workers=1 -Dorg.gradle.jvmargs='-Xmx1024m -Dfile.encoding=UTF-8' build --rerun-tasks --stacktrace`
- PASS: jar metadata/resource spot check for `0.2.3+26.1.2-p6`.


### P13 Item Transformations and Extensibility
- Added JSON-driven item transformations via `item_transformations.json`.
- Added Apple -> Golden Apple and Glass Bottle -> Experience Bottle prototype transformations.
- Server now validates and applies transformation pseudo-options.
- Client list now shows matching transformation options for input items.
- Documented current extensibility/compatibility limitations and recommended package split.
- Refreshed P13 release artifacts under `release_output/chiseled_enchanting_table_26_1_2_p13/`.

### P12 Multi-Item Cost Support
- Added JSON `extra`/`extras` cost support so diamond/emerald options can require additional thematic materials.
- Server validation now checks and consumes extra item costs from player inventory.
- Client list now displays up to two extra cost stacks and blocks enchanting when extras are missing.
- Updated cost data so expensive diamond/emerald options usually require a related material too.
- Refreshed P12 release artifacts under `release_output/chiseled_enchanting_table_26_1_2_p12/`.

### P11 Enchantment Coverage and Recipe Balance
- Expanded `default_enchantments.json` to cover the broad current vanilla/26.1.2 enchantment surface, including Crossbow Multishot/Quick Charge/Piercing, mace Wind Burst/Density/Breach, and Spear Lunge.
- Increased Chiseled Enchanting Table recipe cost by changing side diamonds to diamond blocks.
- Refreshed P11 release artifacts under `release_output/chiseled_enchanting_table_26_1_2_p11/`.

### P10 Balance Rework
- Rebalanced P9 costs so low/mid levels no longer broadly require diamonds/emeralds.
- XP costs now scale by enchantment level relative to that enchantment's max level: common max level is around 25, rare single-level enchantments around 35.
- Added JSON `max_level` rules and Java max-level-aware cost resolution.
- Refreshed P10 release artifacts under `release_output/chiseled_enchanting_table_26_1_2_p10/`.

### P9 Data-Driven Balance
- Expanded `enchantment_costs.json` to cover most vanilla enchantments with richer item-cost options.
- Added support for deterministic varied cost options, count ranges, and per-level overrides.
- Made top-tier enchantments substantially more expensive in items; e.g. Sharpness V can require diamonds/emeralds rather than cheap flint.
- Added special mace/wind enchantment costs using breeze rods, wind charges, heavy cores, diamonds, and related materials.
- Refreshed P9 release artifacts under `release_output/chiseled_enchanting_table_26_1_2_p9/`.

### P8 Feedback Fixes
- Bumped local port version to `0.2.3+26.1.2-p13`.
- Forwarded container-screen mouse wheel/click/drag/release events explicitly to the enchantment list so scrolling/scrollbar dragging can work in-game.
- Changed chiseled bookshelf mechanics so empty shelves do not affect levels; stored enchanted books contribute their actual enchantments and levels.
- Refreshed P8 release artifacts under `release_output/chiseled_enchanting_table_26_1_2_p8/`.

### P7 Feedback Fixes
- Bumped local port version to `0.2.3+26.1.2-p7`.
- Added shelf-power level boosting for nearby bookshelves/chiseled bookshelves.
- Nearby chiseled bookshelves now contribute stored enchantments to the available enchantment set.
- Added explicit mouse-wheel and scrollbar-drag handling to the enchantment list.
- Restored floating book animation state and registered a 26.1.2 block-entity renderer.
- Added Simplified Chinese translation (`zh_cn.json`) for block/item/container names.
- Adjusted the item model to avoid losing/over-tinting the center overlay in inventory.
- Refreshed P7 release artifacts under `release_output/chiseled_enchanting_table_26_1_2_p7/`.

### Client-Only Pending
- Live Minecraft/Fabric boot.
- Block placement/open UI.
- UI rendering and interaction.
- Enchantment application, item/XP consumption, bookshelf scanning, and multiplayer sync.

## [Unreleased] - 2026-06-30
### Added
- Environment configured with OpenJDK 25.
- Re-ran local Gradle verification build (`./gradlew build`).
- Generated P3 release artifacts into `release_output/chiseled_enchanting_table_26_1_2_p3/`.
- Synchronized `CURRENT_BASELINE.md`, `NEXT_TASK.md`, and this changelog based on porting reports.

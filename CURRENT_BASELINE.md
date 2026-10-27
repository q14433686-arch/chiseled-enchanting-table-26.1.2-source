# Current Baseline

> **2026-10-27 full port:** this line now ships the 26.3 rev12 / 26.1.2 rev18 feature set at wire
> `26_2_revision_7` (14 mixins, 1019 lang keys x 11 locales). Real CI (`compile-check`+`build`) green
> on MC 26.2. See [`docs/reports/FULL_PORT_26_3_REV12_SEVEN_PATH_AUDIT_2026_10_27.md`](docs/reports/FULL_PORT_26_3_REV12_SEVEN_PATH_AUDIT_2026_10_27.md). The revision-6 text below
> is kept as the historical baseline for that earlier round.


> **Unofficial community port.** This repository is an unofficial community port of
> [Sacramentix/chiseled-enchanting-table](https://github.com/Sacramentix/chiseled-enchanting-table)
> to Minecraft **26.2** on Fabric. It is **not affiliated with, endorsed by, or maintained by
> the original author**. All credit for the original mod design and assets belongs to Sacramentix
> (MIT licensed); this port only updates the code/data to the 26.2 toolchain.

> **26.2 branch (2026-07-19, synced 2026-10-21 and 2026-10-22):** This line targets **Minecraft
> 26.2**. See `docs/reports/PORTING_26_2_REPORT.md` for the 26.1.2→26.2 port details. On 2026-10-21,
> all feature additions and fixes from the `26.3` branch were synced to this `26.2` line with 26.2
> compatibility: worldgen bookshelf deadlock fix (`WORLDGEN_BOOKSHELF_DEADLOCK_REPORT.md`), JEI &
> REI integration (`VIEWER_INTEGRATION_JEI_REI.md`), server-resolved enchantment cost sync (S2C),
> 11 language files + translatable book lore, `craft.json` advancement fixes, and the offline CI
> static / release-consistency gates (`docs/ci/`). On **2026-10-22** the full progression / economy /
> UI overhaul from the sister `26.1.2` line was synced on top of that baseline — see
> `docs/reports/SYNC_FROM_26_1_2_SIDE_REPORT.md`. The one 26.2-forced source change in that sync:
> **`ChatFormatting` no longer exists in 26.2**, so all styled text uses
> `Style#withColor(0xAARRGGBB)` (vanilla RGB values preserved).
> A third sync (**2026-10-24**) ported the `26.3` branch's architecture/feature batch (source
> `3e61f5d`, wire revision **6**) — see ``docs/reports/SYNC_FROM_26_3_SIDE_REPORT.md`` §9 and
> "Synced This Session (2026-10-24)" below.

**Date:** 2026-10-24
**Minecraft Version:** 26.2
**Fabric Loader:** 0.19.3
**Fabric API:** 0.155.2+26.2
**Gradle:** 9.5.1
**Loom:** 1.17
**Java:** 25
**Mod Version:** `1.1.0`  *(unofficial-port versioning, see "Versioning" below)*

## Versioning

This repository currently publishes the unofficial 26.2 port as **`1.1.0`**. Earlier internal
porting labels are treated as historical implementation notes only; current metadata, release
artefacts, documentation, and marketing copy should all refer to `1.1.0`.

`1.1.0` is a **minor** bump over `1.0.3` because it adds content (data-pack files, items, recipes,
advancements, a second viewer category, the grimoire screens, the cleanse tab and a compiled test
suite) without breaking anything: the mod id, the existing data-pack paths under
`data/chiseled_enchanting_table/chiseled_enchanting_table/`, the payload ids and existing world
saves are unchanged.

`1.1.0` covers **two** sync passes: the 2026-10-22 progression/economy/UI overhaul from the
`26.1.2` line, and the 2026-10-23 follow-up batch (illustrated grimoire, dual-tab cleanse, economy
hardening, de-hardcoded configuration). The number was deliberately **not** bumped again: no `1.1.0`
jar was ever published, so the second batch is part of the same unreleased release.

> **Cross-branch note:** the sister `26.1.2` and `26.3` branches still declare `1.0.3` for the same
> feature set. They should be bumped to `1.1.0` on their next release so the version alone
> identifies the feature set again — see
> [`docs/reports/SYNC_FROM_26_1_2_SIDE_REPORT.md`](docs/reports/SYNC_FROM_26_1_2_SIDE_REPORT.md) §5.

## Current Source-Truth Snapshot (what the code/data actually contains today)

### Build / packaging
- `gradle.properties`: `mod_version=1.1.0`, `minecraft_version=26.2`,
  `loader_version=0.19.3`, `fabric_version=0.155.2+26.2`. Build tooling: Loom `1.17-SNAPSHOT`,
  Gradle wrapper `9.5.1`.
- `src/main/resources/fabric.mod.json`:
  - `version: "${version}"` (substituted by Loom to `1.1.0`).
  - `name`: `Chiseled Enchanting Table (Unofficial 26.2 Port)`.
  - `description`: explicitly states this is an unofficial port and not affiliated with the
    original author.
  - `authors`: original `Sacramentix` plus the unofficial-port maintainer `q14433686-arch`.
  - `contact.sources`: this port's GitHub; `contact.homepage`: original upstream.
  - `depends`: `fabricloader >=0.19.3`, `minecraft 26.2`, `java >=25`, `fabric-api *`.
  - `mixins: ["chiseled-enchanting-table-port.mixins.json"]` — the port config now uses package
    `chiseled_enchanting_table.mixin` and declares **9** active mixins: 5 structure mixins, vanilla
    enchanting-table book blocking, narrow anvil enchanted-book same-level merge blocking,
    chiseled-bookshelf random-tick ambience, and the flat anvil prior-work penalty
    (`anvil.RemoveXpCostScaling`, re-enabled on 2026-10-22). The parked `mixin/accessor/ItemAccessor`
    and the retired `chiseled-enchanting-table.mixins.json` /
    `chiseled-enchanting-table.client.mixins.json` configs are gone.
  - `custom.chiseled_enchanting_table:port_notes`: documents the unofficial-port status and the
    current 1.1.0 mixin/loot/structure/viewer boundary.
- `.github/workflows/` and `docs/ci/`: all four 26.2 workflows (`compile-check.yml`, `build.yml`,
  `consistency.yml`, `publish.yml`) are installed and verified byte-identical via
  `bash docs/ci/install.sh --check` (`MINECRAFT_VERSION: 26.2`, `VERSION: 1.1.0`,
  `RELEASE_NAME: ... (Unofficial 26.2 Port)`).
- **CI status (2026-10-23, 1.1.0):** GitHub Actions `compile-check` (run `39889855557`), `build`
  (run `39889855537` — `static`, `gradle build` **including the JUnit suite**, and `no-viewers`
  jobs) and `consistency` (run `39889855610`) are **all green** on Minecraft 26.2 at commit
  `6173b6f` (the second `26.1.2` sync), and the same three workflows were green at `9f2046c`
  (runs `39819932191` / `39819932163` / `39819931994`, the `1.1.0` version bump) and at `902e947`
  (runs `39818720349` / `39818720326` / `39818720363`, the first sync) — i.e.
  `ci_static_checks.py`, `compat_check.py`, `check_release_consistency.sh --strict`,
  `:compileJava`, `:compileClientJava`, `remapJar` (with pinned `jei-26.2-fabric:30.47.0.239` and
  `RoughlyEnoughItems-api-fabric:26.2.821` plus `toolchainPins` exclusions) and
  `-Penable_jei=false -Penable_rei=false` all succeeded with the synced feature set. The only javac
  notes are deprecation notices (`ChiseledEnchantingTableClient` + `Note: Some input files use or
  override a deprecated API`), no warnings-as-errors. The workflow committed the compiler log back
  as `68fc86e` (`build-reports/compile-java.log`).

### Source layout
- `chiseled_enchanting_table.block.*` — `ChiseledEnchantingTableBlock`,
  `ChiseledEnchantingTableBlockEntity`, `FloatingBook`, `ChiseledBookshelfTick`
  (the legacy mixed `chiseledEnchantingTable` package has been retired).
- `chiseled_enchanting_table.data.ChiseledEnchantingTableData` — server-data reload listener that
  loads JSON-driven default unlocks, costs, book upgrades, treasure epiphanies, transformations,
  loot injections, the structure bookshelf pool, library resonance, shelf gates and the
  disenchanting/cleanse table — **all ten data files**, read straight from the bundled classpath
  (`readBundledJsonObject`, no embedded Java copies that could drift).
- `chiseled_enchanting_table.mechanics.resonance.LibraryResonance` — the relative-mastery maths
  (`r(e) = level / maxLevel`, mastery sum `Σ r(e)^1.25`), the `12`-shelf discount floor, the
  `-15%` wooden / `-35%` public / `-40%` total caps and the epiphany-affinity weights, all parsed
  from the three nested objects of `library_resonance.json` and synced to clients in
  `EnchantCostTablePayload`. `mechanics.resonance.BookshelfProgression` holds the three shelf roles,
  the `0..38` step-up curve (`shelf_gates.json`, `gamma = 1.25`) and the Depleted-Tome gradients.
  `mechanics.enchanting.XpCostConversion.XpRequirement` carries both XP modes (vanilla
  mass-production tiers versus the full curve).
- `chiseled_enchanting_table.mechanics.enchanting.DisenchantmentApplication` — free disenchant /
  step-down / single-trait strip / curse purge, with the inductive potential table
  (`buildPotentialTable`) and its startup/reload conservation guard (`verifyRuntimeConservation`),
  so a refund can never beat a Master-Tome re-enchant for any `maxLevel = 1..255`.
- `chiseled_enchanting_table.config.ModBootstrapConfig` — early bootstrap config
  (`config/chiseled_enchanting_table.json`, e.g. `enchanted_book_max_stack_size`) read before the
  item default-component events run.
- `chiseled_enchanting_table.item.ArcaneGrimoireItem` — the illustrated manual item; it delegates
  screen opening through a client-only `CLIENT_OPEN_ACTION` so `src/main` stays free of client
  imports.
- `chiseled_enchanting_table.compat.TransformationQueryIndex` — viewer-agnostic transformation rows
  (input, primary/secondary cost alternatives, output, XP requirement) consumed by both the JEI and
  REI transformation categories.
- `chiseled_enchanting_table.mechanics.enchanting.EnchantmentApplication` and
  `chiseled_enchanting_table.mechanics.transformation.ItemTransformationApplication`.
- `chiseled_enchanting_table.mechanics.loot.EnchantedBookLootInjector` +
  `RemoveEnchantedBooksFunction` + `DepthBasedEfficiencyFunction` — chest-loot injection driven by
  `loot_injections.json`; entries flagged `special: "depth_based_efficiency"` roll Efficiency II–V
  from the structure depth instead of a static level.
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
  `enable_rei=false` drop the respective adapter from the build; all four viewer `compileOnly`
  dependencies (`mezz.jei:jei-26.2-fabric:30.47.0.239`,
  `me.shedaniel:RoughlyEnoughItems-api-fabric:26.2.821`, `me.shedaniel.cloth:basic-math:0.6.1`,
  `dev.architectury:architectury-fabric:21.0.4`) share `toolchainPins` exclusions
  (`net.fabricmc.fabric-api`, `net.fabricmc`, `com.mojang`). `ChiseledEnchantingJeiPlugin`
  intentionally omits `registerItemSubtypes` because JEI's own `VanillaPlugin` already registers
  `EnchantedBookSubtypeInterpreter.INSTANCE` for `Items.ENCHANTED_BOOK`.
  Costs are server-authoritative: `menu.EnchantCostTablePayload` + `menu.EnchantCostSync` push a
  per-player resolved table (`chiseled_enchanting_table:enchant_cost_table`) on join and after each
  successful data-pack reload; `compat.SyncedEnchantmentCosts` caches it on the client (cleared on
  disconnect) and both the viewers and `ChiseledEnchantingTableScreenHandler` read it.
- `chiseled_enchanting_table.menu.*` — `ChiseledEnchantingTableScreenHandler` (now constructed with
  a `ContainerLevelAccess`), `ChiseledEnchantingMenuPayloads` (`OptionType` incl.
  `TREASURE_EPIPHANY`, `TransformationDisplay`, `AvailableEnchantmentPayload` carrying the shelf
  power / epiphany seed / stored master levels, `ApplyEnchantmentPayload`,
  `RerollEpiphanyPayload`, `FreezeEpiphanyPayload`), `AvailableOption` (typed enchantment vs.
  transformation), `EnchantCostSync`, `EnchantCostTablePayload`.
- `chiseled_enchanting_table.registry.*` — `BlockRegistry`, `EntityRegistry`,
  `ScreenHandlerRegistry`, `StructureProcessorRegistry`.
- `chiseled_enchanting_table.utils.*` — `Advancement`, `BlockPosStream`, `EnchantmentFinder`,
  `EnchantmentWithLevel`, `InventoryUtils`.
- Client (`src/client/java`): `ChiseledEnchantingTableClient`, `ChiseledEnchantingTableScreen`
  (dual-tab `Inscribe` / `Cleanse` console), `EnchantementListWidget`, `gui.row.*`
  (`RowRenderSupport`, `EnchantmentRowRenderer`, `TransformationRowRenderer`,
  `CategoryRowRenderer`), and `gui.grimoire.*` — `ArcaneGrimoireScreen` (8 chapters, keyboard/mouse
  navigation), `GrimoireDiagramRenderer` (live recipes/costs/transformations), `GrimoireTextLayout`
  (single-pass column planning), `GrimoireSoundHelper` (vanilla book/UI sounds) and
  `GrimoireViewerBridge` (optional JEI/REI lookups without leaking viewer types into core GUI).

### Data / JSON
- `data/chiseled_enchanting_table/chiseled_enchanting_table/default_enchantments.json` —
  `values` (small list of explicitly-unlocked vanilla treasure enchantments) + `tags`
  (default `#minecraft:non_treasure`, level 1) for cross-mod tag-driven unlocks.
- `data/chiseled_enchanting_table/chiseled_enchanting_table/enchantment_costs.json` — per-level
  item + XP costs on the yield-aware curve (`options[]` of
  `{item, base_count, max_level_count, level_exponent}`, plus `count` / `count_per_level` /
  `min_count+max_count` for flat rules), with the `scute` -> `turtle_scute` fix.
- `data/chiseled_enchanting_table/chiseled_enchanting_table/item_transformations.json` — JSON
  source of truth for item transformations; the compat check enforces no drift with the Java
  fallback table.
- `data/chiseled_enchanting_table/chiseled_enchanting_table/loot_injections.json` — enchanted-book
  injections into 41 vanilla chest / archaeology loot tables (196 sub-entries). Auto-derived from
  upstream `ReworkEnchantedBookChestLoot.java` by `scripts/import_upstream_loot.py`.
- `data/chiseled_enchanting_table/chiseled_enchanting_table/bookshelf_book_pool.json` — v2 fill
  rules for structure-placed chiseled bookshelves: `book_count = { min: 3, max: 5 }`,
  `enchanted_book_probability = 0.12`, `max_level_weight_ratio = 0.15`, and an
  `enchanted_book_selection` block that describes how
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
- `data/chiseled_enchanting_table/chiseled_enchanting_table/book_upgrades.json` — JSON source for
  book-upgrade routes: `combine_route` (material-free, `0.6×` XP) and `research_route`
  (`1.4×` XP, `3` biome-diverse material alternatives per enchantment, `arcane_catalyst` accepted
  universally), each material table on the same yield-aware curve.
- `data/chiseled_enchanting_table/chiseled_enchanting_table/treasure_epiphany.json` — Grand-Library
  Treasure Epiphany + cryo-stasis freeze/reroll table: `min_regular_bookshelves = 32`,
  `base_chance` / per-bookshelf / per-tome chance scaling with a `max_chance` cap, `max_slots_cap`,
  `cycle_ticks`, freeze/reroll XP + material options, the treasure id/tag lists, and per-treasure
  cost overrides. Validated by `scripts/compat_check.py` (Tier 1).
- `data/chiseled_enchanting_table/chiseled_enchanting_table/library_resonance.json` — three nested
  config objects (`wooden_bookshelves`: `12..38` shelves, `-15%`, `0.90` material multiplier;
  `chiseled_mastery`: exponent `1.25`, `-20%` global / `-35%` public / `-40%` total, `0.80` tome
  multiplier, `0.85` step credit; `epiphany_affinity`: probability and blank-book weights), guarded
  against `LibraryResonance.Config.DEFAULT` by the JUnit suite.
- `data/chiseled_enchanting_table/chiseled_enchanting_table/shelf_gates.json` — scan volume
  (`3`/`2` radii), the `direct_upgrade_gates` block (`max_bookshelves: 38`, `gamma: 1.25`,
  `blank_max_tier_mode: "half_ceil"`, per-enchantment `overrides`) and the three Depleted-Tome
  restoration gradients (`8` / `18` shelves, `6` qualifying enchantments, batch `8` / `64`).
- `data/chiseled_enchanting_table/chiseled_enchanting_table/disenchanting.json` — the cleanse table:
  full wipe, step-down, single-trait strip and curse purge, each with a custom-catalyst option and a
  standalone pure-vanilla alternative (plus the ritual XP levels).
- Tags: `#chiseled_enchanting_table:regular_bookshelves` (which blocks count as wooden shelves),
  `#chiseled_enchanting_table:epiphany_exclusive`, and the vanilla
  `#minecraft:bookshelf_books` extension that lets the grimoire sit in a chiseled bookshelf.
- `data/chiseled_enchanting_table/recipe/` — the chiseled table, the vanilla-table upgrade recipe,
  the `8× Crying Obsidian` recipe, the Arcane Catalyst recipe, the `4× Cleansing Grit` recipe and
  the two `Arcane Grimoire` recipes (7 in total); matching `advancement/recipes/*.json` unlock files.
- **25 advancements** under `data/chiseled_enchanting_table/advancement/` (21 guide advancements +
  4 recipe unlocks), all translatable in the 11 shipped locales (`307` keys each); `root.json` keys
  on both the craft and the upgrade recipe.

### Tooling
- `scripts/compat_check.py` — Tier 1 (offline, fails the build) covers singular-dir convention,
  no stale plural dirs, non-empty block-loot pools, all-JSON parses, the 26.2 plural
  `conditions[]`/`functions[]` loot schema, the 26.2 advancement schema (`recipe_id`,
  root-only `background`, `show_toast`/`announce_to_chat` inside `display`), JSON/Java
  transformation drift, `default_enchantments.json` `tags` schema, `loot_injections.json`,
  `bookshelf_book_pool.json`, `book_upgrades.json`, `treasure_epiphany.json`, the shared
  **cost-option** validator (`count` / `count_per_level` / `min_count+max_count` /
  `base_count+max_level_count` + `level_exponent` + nested `extras` / `additional_costs`),
  **`disenchanting.json`** (anti-arbitrage structure), **grimoire recipes / sounds / textures** and a
  **`docs/WIKI.md` sync gate** (the wiki must keep documenting the shipped formulas and keys).
  Tier 2 (best-effort, warn-only) checks id and tag existence against `--mc-jar` and/or
  `--extra-jar` paths, for all ten data files.
- `src/test/java/chiseled_enchanting_table/EconomyAndConfigVerificationTest.java` — JUnit 5 suite
  wired through `build.gradle` (`junit-bom` 5.11.4, `useJUnitPlatform()`), so `./gradlew build` and
  the CI `build` job execute it: nested `library_resonance.json` schema and mutation coverage, the
  `M = 1..255` full-wipe and step-upgrade conservation sweep over the real
  `DisenchantmentApplication` / `XpCostConversion` code paths (~391,680 checks), Book-Combine ≤
  Book-Research across every tier, the transformation thresholds / Bottle-o'-Enchanting arbitrage
  and the bounded high-level shelf scan (`M = 1..255`).

### Active Temporary Boundaries
- Main-source exclusions still parked in `build.gradle`:
  - `chiseled_enchanting_table.integration.*` (third-party mod integration; port later as an
    opt-in layer)
  - Legacy `modifyChestLootTable/**`, `structureProcessor/**`, `utils/ChiseledBookshelfLootTable`
    (fully superseded by the JSON-driven injector + `mechanics/structure/*`)
- Now active (previously parked): `mixin/anvil/RemoveXpCostScaling.java` (flat anvil prior-work
  penalty) and `DefaultItemComponentEvents.MODIFY` for the 64-stack enchanted book. The
  `mixin/accessor/ItemAccessor` was deleted rather than enabled.
- The JUnit suite runs on the plain JUnit platform (no Loom test bootstrap): it only exercises the
  compiled data/DTO/mechanics classes (`Identifier`, `RegistryFriendlyByteBuf` with a null
  `RegistryAccess`), which keeps `./gradlew build` free of a Minecraft test runtime.
- Runtime feature gaps:
  - Full live-client gameplay validation of the 25 advancements, the epiphany freeze/reroll
    round-trip and the dye pipeline is still pending; the three offline gates and the CI compile are
    the only automated coverage in this sandbox.
  - Full live-client gameplay validation of the actual bookshelf fills in villages / mansions /
    strongholds / ancient cities is still pending (mixin call sites are verified; the fill
    outcome — visible books in the shelves — is what needs the next round of live testing).

### Release artifacts (this round)
**Not re-staged yet for `1.1.0`.** The dev sandbox cannot run Gradle, so the release directory is
generated from the `build` workflow's jar artifact (Actions → *build* → **Artifacts**, kept 14 days)
on a machine with JDK 25. The expected layout is
`release_output/chiseled_enchanting_table_1_1_0/`:
- `chiseled-enchanting-table-1.1.0.jar` (in-jar icon matches the 256×256 cover)
- `chiseled-enchanting-table-1.1.0-sources.jar`
- `chiseled-enchanting-table-1.1.0-source.zip` (working-tree zip; excludes
  `.git/`, `.gradle/`, `build/`, `release_output/`).
- `cover-icon-256.png` — 256×256 RGBA cover icon, ready to upload to Modrinth / CurseForge /
  MC百科 directly.
- `cover-banner.png` — repo root banner, staged alongside for optional gallery use.

Release output is consolidated under the current `1_1_0` directory; older internal porting-round
artefact directories (including the `1_0_2` one still described by the historical block below) are
not part of the current release output.

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

## Synced This Session (2026-10-22) — 26.1.2 line → 26.2 line

- Synced the full `26.1.2`-line feature range (`09f854e..2f62762`): 135 paths, of which 126 had no
  local counterpart and were taken verbatim (including deletions). The nine overlapping files were
  merged by hand — see `docs/reports/SYNC_FROM_26_1_2_SIDE_REPORT.md` §2.
- 26.2 adaptations applied: `ChatFormatting` (removed in 26.2) → `Style#withColor(0xAARRGGBB)` in
  `gui/ChiseledEnchantingTableScreen`, `block/ChiseledEnchantingTableBlockEntity` and
  `registry/BlockRegistry`; the 26.2 structure-processor API, `ChunkAccess#markPosForPostProcessing`,
  the plural loot schema, the `recipe_id` criterion, the two-argument `LivingEntity#drop` and the
  pinned 26.2 viewer coordinates were kept.
- `build.gradle`: re-enabled `mixin/anvil/RemoveXpCostScaling.java` and dropped the
  `mixin/accessor/**` exclusion (the mixin itself is deleted).
- `scripts/compat_check.py`: added Tier-1 validators for `book_upgrades.json` and
  `treasure_epiphany.json`, a shared cost-option schema check, and extended the Tier-2 referenced-id
  sweep to both files (negative-tested against deliberately malformed JSON).
- Validation in this headless sandbox (no JDK, no maven):
  - `python3 scripts/ci_static_checks.py` → **PASS** (9 mixin refs, 98 lang keys × 11 locales,
    35 markdown files).
  - `python3 scripts/compat_check.py` → **PASS** (2 warnings: no MC jar for Tier 2).
  - `bash scripts/check_release_consistency.sh --strict` → **12 passed, 0 warned, 0 failed**.
  - Every `*.json` under `src/` parses; every project-local `import` resolves; no stale references
    to deleted classes (`FloatingBook`, `ItemAccessor`, `ItemRegistry`).
  - `compileJava` / `compileClientJava` are exercised by the GitHub Actions `compile-check` /
    `build` workflows (the sandbox cannot run Gradle); the resulting log lands in
    `build-reports/compile-java.log`.

## Synced This Session (2026-10-23) — second 26.1.2 batch → 26.2 line

- Synced everything the `26.1.2` line gained after the first sync (`2f62762..b78b244`, PR #13 on
  the sister repository): 128 paths — 49 added, 23 deleted (scratch `fix*.patch` / `fix_*.py`,
  `build-reports/compile-deps.log`), 43 taken verbatim because this tree had no local divergence,
  and 13 merged by hand (the docs, `build.gradle`, `scripts/compat_check.py`).
- 26.2 adaptations applied to the new code:
  - `ChatFormatting` (gone in 26.2) → `Style#withColor(0xAARRGGBB)` in the four files that use
    colours: `gui/ChiseledEnchantingTableScreen` (74 references, including ternary style
    expressions), `gui/grimoire/ArcaneGrimoireScreen` (5), `block/ChiseledEnchantingTableBlockEntity`
    (1) and `registry/BlockRegistry` (4).
  - `build.gradle`: added the JUnit 5 wiring (`junit-bom` 5.11.4 + `useJUnitPlatform()`) and
    refreshed the client-source-set comment; the 26.2 Loom/`toolchainPins`/mixin-exclusion block is
    otherwise untouched.
  - `scripts/compat_check.py`: three-way merge — the 26.2 schema wording and the
    `check_loot_tables_use_26_2_schema` / `check_advancements_use_26_2_schema` direction are kept,
    while the new Tier-1 validators (`check_disenchanting_and_anti_arbitrage`,
    `check_grimoire_recipes_sounds_and_textures`) and the wiki-sync gate are added, so all nine
    checks run.
  - `docs/WIKI.md`: taken from the 26.1.2 side (it is a full gameplay-guide rewrite) with the target
    line adjusted to `Minecraft 26.2` / Fabric API `0.155.2+26.2`.
  - `gradle.properties`, both `publish.yml` copies and `build-reports/compile-java.log` keep this
    branch's values (`mod_version=1.1.0`, `MINECRAFT_VERSION: 26.2`, CI-written log).
- Version: **unchanged at `1.1.0`** (see "Versioning" above) — the first `1.1.0` was never released.
- Validation in this headless sandbox (no JDK, no maven):
  - `python3 scripts/ci_static_checks.py` → **PASS** (9 mixin refs, 307 lang keys × 11 locales,
    code-to-lang coverage 297 refs, 36 markdown files).
  - `python3 scripts/compat_check.py` → **PASS** (2 warnings: no MC jar for Tier 2).
  - `bash scripts/check_release_consistency.sh --strict` → **12 passed, 0 warned, 0 failed**.
  - `bash docs/ci/install.sh --check` → all four workflow copies up to date.
  - `compileJava` / `compileClientJava` / the JUnit suite are exercised by the GitHub Actions
    `compile-check` and `build` workflows; the compile log lands in `build-reports/compile-java.log`.

## Synced This Session (2026-10-24) — 26.3 line (`3e61f5d`) → 26.2 line

Ported the `26.3` branch's `6a8f371..d2abe21` increment (60 non-log paths; 16 added, 44 modified)
onto this line, keeping every 26.2 pin and every 26.2-only API. Full mapping, adopt/skip decisions
and evidence: [`docs/reports/SYNC_FROM_26_3_SIDE_REPORT.md`](docs/reports/SYNC_FROM_26_3_SIDE_REPORT.md) §9.

- **New modules**: `config/rules/{RuleBook,RuntimeRules}` (an immutable effective-rule snapshot —
  domain tables, costs, loot, policy and the effective DTO published as one generation),
  `mechanics/ritual/InscriptionRitualMechanics` (bounded, owner/session-scoped queued inscription
  rituals with the two skip paths), `mechanics/resonance/{RegenerationScheduler,WorldRegeneration}`
  (one 20-tick world-session settlement for shared shelf tome healing) and
  `mechanics/loot/DepletedTomeWorldMechanics` (depleted-tome chest loot, wild bookshelf weathering,
  librarian recycling/commissioning, Infinity trade restrictions).
- **Gameplay**: persistent two-slot table inventory (items survive closing the GUI and drop on
  break instead of being copied into the dropped block), deep-resonance wait with XP-sacrifice and
  force-rush skips, Infinity acquisition limits plus the overloaded-tome transcription slip
  (capped at 20%), weighted Treasure Epiphany, tome passive regeneration at
  `24,000 / 12,000 / 3,600` seconds for `38 / 42` shelves, five new advancements and the
  `pack_policy.json` datapack governance file.
- **UI**: Grimoire repagination and the new diagrams (bookshelf taxonomy, ritual/skip comparison,
  regen and Infinity, world remnants/librarian trades), 621 translation keys × 11 locales,
  config-screen effective-vs-draft display with the `📦 datapack-priority` / `🔒 modpack-lock`
  reasons.
- **Wire**: revision **6** (`26_2_revision_6`). Two legacy probes only — the unversioned
  `enchant_cost_table` and `..._26_2_revision_5`; 26.3 needs three because it also shipped a
  `..._26_3_revision_4` layout that never existed here. Ritual/menu payloads now carry
  container + session routing and every collection read is bounded.
- **26.2 adaptations re-applied** (the port re-introduces them, exactly as documented):
  `ChatFormatting` → `Style#withColor(0xAARRGGBB)` (53 sites across 6 files, now 0 references),
  `ContextIntProviders` → `ConstantValue` in the new loot builders (26.3 split that provider
  namespace; 26.2 still ships the flat one), `ValueInput`/`ValueOutput` persistence for the new
  block-entity fields, plural `functions[]`/`conditions[]` in the smoke fixtures, and the
  `chiseled_enchanting_table:...` datapack paths with singular `advancement/`.
- **Re-applied 26.2 guards on merged files**: the per-block `MapCodec`/`codec()`, the interface
  `StructureProcessor` + `codec()` registration, capital-P `markPosForPostProcessing`, the
  two-argument `LivingEntity#drop`, and the JUnit-5 wiring in `build.gradle`.
- **Version**: **unchanged at `1.1.0`** (user instruction "版本号不变"); no 26.3 toolchain pin,
  no 26.3 JVM args and no 26.3 build logs were taken.
- **Validation**: `ci_static_checks.py` PASS (9 mixins, 621 keys × 11 locales, 470 referenced keys,
  40 markdown files), `compat_check.py` PASS (2 Tier-2 warnings), `check_release_consistency.sh
  --strict` 12/0/0, `docs/ci/install.sh --check` clean. `compileJava`/`compileClientJava`, the JUnit
  suites (`compile-check` now also runs `test`, 52 tests) and the new `server-smoke` job run in CI.

## Client-Only / Live-Game Validation Still Pending

Same as the prior baseline — these require a real MC 26.2 + Fabric runtime and were not run in
this headless sandbox: mod-metadata load, the restored balance / ambience mixins and advancement triggers/translations in a real
Vanilla Enchanting Table / Anvil / Chiseled Bookshelf / Chiseled Enchanting Table setup, block placement, custom menu open, slot/scroll/floating book
renderer, enchanting + transformation application, chiseled-bookshelf scanning, P19 tag-based
cross-mod default unlocks, multiplayer sync beyond payload registration, and mining drop in a
live world. Added by the 2026-10-23 batch and still unverified in a live client: the grimoire
overlay and its JEI/REI round-trip, the dual-tab Cleanse console, the cleanse / downgrade /
curse-purge rituals and their XP refunds, the live shelf rescan payload, and the `1..38` shelf
step-up curve on a real world. Added by the 2026-10-24 `26.3` sync and still unverified in a live
game: the persistent table inventory (close/re-enter/break/hopper/creative), the queued ritual
lifecycle (owner lease, XP-sacrifice and force-rush skips, registry-failure quarantine,
`/cet cancel-ritual`), tome passive regeneration across world sessions, librarian recycled/commissioned
trades and wild-bookshelf weathering, the repaginated Grimoire and its new diagrams, the
`pack_policy.json` lock/priority combinations, and revision-6 dual-end refusal. The full list is kept in
`docs/reports/CURRENT_AGENT_BRIEF.md`.


## 1.0.2 Hotfix Note

- Book combine upgrade now explicitly replaces the cost-slot enchanted book with a plain book after the upgrade, matching the expected visible consumption behavior.
- Validation: `compileJava compileClientJava`, `compat_check.py`, and `build --rerun-tasks` all passed.

<!-- BEGIN GENERATED MIXIN INVENTORY -->
## Registered Mixin inventory (generated)

**Registered Mixin declarations: 14.**

Source: `fabric.mod.json` registered configs, including common/client/server sections.
Regenerate with `python3 scripts/check_baseline_mixins.py --write`.

| Config | Section | Class |
| --- | --- | --- |
| `chiseled-enchanting-table-port.mixins.json` | `mixins` | `chiseled_enchanting_table.mixin.anvil.RemoveEnchantMerge` |
| `chiseled-enchanting-table-port.mixins.json` | `mixins` | `chiseled_enchanting_table.mixin.anvil.RemoveXpCostScaling` |
| `chiseled-enchanting-table-port.mixins.json` | `mixins` | `chiseled_enchanting_table.mixin.bookshelf.AddRandomTick` |
| `chiseled-enchanting-table-port.mixins.json` | `mixins` | `chiseled_enchanting_table.mixin.bookshelf.BookshelfCreativeExtractionMixin` |
| `chiseled-enchanting-table-port.mixins.json` | `mixins` | `chiseled_enchanting_table.mixin.bookshelf.LockedBookContainerMixin` |
| `chiseled-enchanting-table-port.mixins.json` | `mixins` | `chiseled_enchanting_table.mixin.enchanting_table.DisableBookEnchanting` |
| `chiseled-enchanting-table-port.mixins.json` | `mixins` | `chiseled_enchanting_table.mixin.grindstone.GrindstoneBookDepleteMixin` |
| `chiseled-enchanting-table-port.mixins.json` | `mixins` | `chiseled_enchanting_table.mixin.loot.ReloadableServerRegistriesMixin` |
| `chiseled-enchanting-table-port.mixins.json` | `mixins` | `chiseled_enchanting_table.mixin.structure.LevelChunkPostProcessMixin` |
| `chiseled-enchanting-table-port.mixins.json` | `mixins` | `chiseled_enchanting_table.mixin.structure.StructurePieceAccessor` |
| `chiseled-enchanting-table-port.mixins.json` | `mixins` | `chiseled_enchanting_table.mixin.structure.StructurePieceMarkMixin` |
| `chiseled-enchanting-table-port.mixins.json` | `mixins` | `chiseled_enchanting_table.mixin.structure.StructurePlaceSettingsMixin` |
| `chiseled-enchanting-table-port.mixins.json` | `mixins` | `chiseled_enchanting_table.mixin.structure.StructureTemplatePoolProjectionMixin` |
| `chiseled-enchanting-table-port.mixins.json` | `mixins` | `chiseled_enchanting_table.mixin.villager.VillagerInfinitySanitizeMixin` |
<!-- END GENERATED MIXIN INVENTORY -->

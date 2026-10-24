# Changelog

> **Unofficial community port.** This changelog covers the unofficial 26.3 port maintained at
> `q14433686-arch/chiseled-enchanting-table-26.1.2-source`. It is not affiliated with or endorsed
> by the original author (Sacramentix). The original mod remains MIT-licensed; this port re-uses
> the original code/assets under that licence and updates them to the 26.3 toolchain.

Only the current release and the previous one are kept here. Older porting rounds and release notes
are archived under [`docs/CHANGELOG_HISTORY.md`](docs/CHANGELOG_HISTORY.md).

> **26.3 branch (2026-10-13):** This line now targets **Minecraft 26.3** — Fabric API
> `0.162.0+26.3`, Fabric Loader `0.19.5`, Loom `1.17-SNAPSHOT`, Java 25, `mod_version` `1.0.3`.
> See [`docs/reports/PORTING_26_3_REPORT.md`](docs/reports/PORTING_26_3_REPORT.md) for the
> evidence-backed impact assessment (Fabric API side: 10 imported classes, 9 byte-identical and 1
> javadoc-only; Mojang side: renderer rotation API, `submitModel` arity, loot builder/number
> providers, `FriendlyByteBuf` collection helpers, loot-table and advancement schemas).

## [Unreleased] — 2026-10-24 设计审计包

- **世代褪色**：母本传抄出的新书世代 +1，可用次数上限随世代衰减（`cet_generation`）。
- **替罪羊精华定价（默认开）**：溢出献祭按烧掉词条的精华占比恢复次数，`scapegoat_pricing` 可切回 `flat`。
- **等待曲线数据化**：`ritual.wait_curve` 六系数进数据包并同步客户端；新增 `#wear_immune` 豁免标签。
- **全新安装默认**：铁砧平价修复与村民无限书限制默认关（已有配置不受影响）；砂轮返枯竭典籍新增开关 `grindstone_depleted_tome`（默认开）。
- **单一事实源**：机制默认值由 JSON 生成 `BundledDefaults`，CI 增漂移门禁；`TomeWearData`/`EnchantingNetworking`/`DataIds` 有界拆分。
- **Wire 升级 `26_3_revision_10`**：客户端与服务端必须同版；详见 `docs/reports/AUDIT_2026_10_24_DESIGN_PACKAGE.md`。

## [1.1.0] — Full 26.1.2 increment and hardened configuration, 2026-10-23

- Restore the requested release label **1.1.0** while retaining MC **26.3**, pinned dependencies and existing worldgen, mouse, menu-routing and authoritative-catalog fixes.
- Port the complete source increment through **3e081f3** using user-selected **option 1**: D1–D7 progression/economy/discovery/batching plus classified runtime configuration, commands and Mod Menu integration.
- Retain source defaults: config-over-datapack priority, wear 16 / copy cost 4 / step-down, external/unmarked refund scale 0.25 and books-only vanilla table. **Back up old worlds**; absent old provenance cannot be inferred.
- Harden LAN ownership/permissions, atomic save-before-publish, strict explicit reload, request-ID server acknowledgements, integrated-server routing, stale-menu closure and per-player catalog refresh. Match mother-tome wear candidates to line-of-sight scanning and preserve safe 26.3 structure processing.
- Advance the independent wire to **revision 5**, detecting old revision 4 without reusing old decoder channels. Update both sides even if their displayed version is the same.
- Verify final functional source **181e996**: complete build, no-viewer compile, 29 JUnit, 15 Python and actual no-player dedicated-server smoke. Real GUI/multiplayer interaction is still untested. [Evidence and limits](docs/reports/SYNC_26_1_2_3E081F3_OPTION1.md).
- Older 1.1.1 entries below are historical; their fixes are retained.

## [1.1.1] — 26.3 runtime and synchronization audit (historical)

- Synchronize complete server transformation catalogs, including tag inputs and empty catalogs;
  refresh actual JEI runtime recipes and use dynamic REI displays after synchronization.
- Bind live shelf updates to their originating container; close old table menus on successful
  datapack reload so reopened menus receive a fresh transformation snapshot.
- Reject unknown action tokens, invalid collection lengths and incompatible same-version protocol
  builds. **Update client and server together**: the current capability is `protocol_26_3_revision_4`.
- Preserve custom loot-function conditions and server automatic-deduction settings; log failing
  synchronization listeners rather than swallowing exceptions. Preserve native inventory right click.
- Add real dedicated-server load/reload/loot-component checks, conditional-loot probes, and
  headless payload-codec regressions. See [audit and validation boundaries](docs/reports/AUDIT_26_3_RUNTIME_AND_SYNC.md).

## [1.1.1] — Grimoire mouse-input fix (version unchanged)

- Use platform mouse-button constants in the Grimoire instead of old GLFW button numbers.
  Fixes directory/bookmarks, page navigation, close/return, recipe tabs and diagram controls
  on 26.3 (SDL left=1, right=3); both standalone and table-overlay views share this handler.
- Restore intended recipe/usage lookup clicks on item icons without treating left click as right.
- Add a regression gate against hardcoded mouse IDs in the Grimoire click handler.

## [1.1.1] — Additional 26.1.2 sync, 2026-10-23 (version unchanged)

- Sync through source `b78b244`: full/targeted disenchanting, curse purge, Cleansing Grit,
  XP-conservation economy, configurable shelf progression and real-time shelf synchronization.
- Interactive illustrated Arcane Grimoire, recipes and sounds, table shortcut and return packet,
  category/tab GUI, JEI/REI cleansing support and live reagent information.
- Updated Wiki and all 11 locales (307 keys), language coverage and single-source data gates,
  headless JUnit economy/config verification suite, removal of legacy scratch patches/scripts.
- Adapt new screen navigation and live-shelf collection codecs for 26.3. Retain 1.1.1
  recipe-unlock and block-loot world-load fixes. Minecraft/toolchain/viewer pins unchanged.

## [1.1.1] — 2026-10-23

### Fixed — save/world entry blocked by datapack registry errors

- Recipe-unlock advancement now uses the 26.3 `recipes` holder list instead of `recipe`.
- Table block loot now uses a single `condition` object rather than an invalid predicate array.
  Keeps custom name, dye, custom data (including freeze state) and lore when mined.
- Schema gates and regression tests now reproduce both failures from the user's runtime log;
  valid inline modifier sequences remain supported.
- This fixes packaged data; no save editing or world deletion is required.

## [1.1.0] — 2026-10-22

Synced all current 26.1.2 gameplay, UI, assets and translations from `0304f9d` to
Minecraft **26.3**, retaining the 26.3 toolchain and version-specific APIs/data schemas.

- Dual-track 7×5×7 library resonance, Treasure Epiphany, reroll and persistent freeze.
- Dual-mode XP costs, selectable multi-material bundles, Arcane Condensate, Depleted Tome,
  Blank Overwrite, rebalanced book upgrades and item transformations (including trident).
- Painted GUI, XP animations, dye-aware block/item appearance, converging bookshelf particles,
  11-language plain-spoken text, expanded advancements and recipes.
- Bidirectional JEI/REI transformation lookup alongside enchantment lookup.
- Flat anvil prior-work penalty, stacked enchanted-book remainder preservation, and
  depth-dependent mineshaft Efficiency books.
- 26.3 adaptations: explicit collection codecs, prediction-aware overflow drops, optional
  conditional loot holders, modern loot builders, `LootContext#getOptional`, and conversion
  of every new loot/recipe-crafted advancement to the 26.3 schema.
- See [sync report](docs/reports/SYNC_FROM_26_1_2_REPORT.md) and [player Wiki](docs/WIKI.md).

## [1.0.3] — Previous baseline

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
  universal-enchant datapacks are included), plus the mod's own cost tables. REI will reuse it.
- **REI adapter** (`compat/rei/*`, `rei_client` entrypoint) rendering the same rows from the same
  index: three input slots (items / book / material), arrow, output, XP line, and a tooltip carrying
  the cost caveat, the bookshelf rule and the conflict list. REI already distinguishes enchanted
  books by stored enchantment in its own default plugin, so each enchantment level is a separate,
  searchable entry there without extra registration.
- **Per-enchantment enchanted-book entries on JEI** — supplied by JEI's own vanilla subtype
  interpreter; this mod registers none of its own (see the fix below, which removes a redundant
  registration that made JEI log an ERROR on every world load).
- Both viewers are `compileOnly` and optional: `enable_jei=false` / `enable_rei=false` in
  `gradle.properties` (or `-Penable_jei=false` / `-Penable_rei=false`) exclude the respective
  adapter from the build; nothing is required at runtime. The REI adapter additionally needs
  `me.shedaniel.cloth:basic-math` and `dev.architectury:architectury-fabric` as compile-only
  dependencies, because REI's own interfaces leak `me.shedaniel.math` and Architectury fluid types.
- **Server-authoritative costs (S2C)** — the material cost could not be shown truthfully before: the
  cost table is server data (`PackType.SERVER_DATA`) and the chosen option is salted with the player
  UUID. The server now resolves one row per (enchantment, level) for the joining player and sends
  `chiseled_enchanting_table:enchant_cost_table` on join and after every successful data-pack reload;
  the client caches it (`SyncedEnchantmentCosts`, cleared on disconnect) and both the viewers and the
  table's own GUI read it, so a dedicated server with a customised `enchantment_costs.json` shows the
  real cost instead of the jar defaults. Server-side resolution is unchanged and the client-side read
  is guarded by `world.isClientSide()`, so a LAN host can never show one player another player's
  salted option.
- All viewer strings go through the mod's existing translation keys, which ship in 11 languages —
  including the new server-cost tooltip line, and the two book-upgrade GUI labels that were still
  English-only in 9 of them.
- See [`docs/reports/VIEWER_INTEGRATION_JEI_REI.md`](docs/reports/VIEWER_INTEGRATION_JEI_REI.md)
  (verified API surface for both viewers, compile-classpath notes, in-game checklist).

### Fixed — viewer integration hardening

- **Viewer POMs can no longer move this build's toolchain.** All four viewer `compileOnly`
  dependencies (JEI, REI, cloth `basic-math`, `architectury-fabric`) now carry a shared
  `toolchainPins` closure excluding `net.fabricmc.fabric-api`, `net.fabricmc` and `com.mojang`. Loom
  builds the Minecraft jar javac sees by applying Fabric API's declared `injected_interfaces`, so a
  viewer that transitively upgraded Fabric API to another Minecraft line used to kill the *main*
  source set with `cannot access EntityFluidExtension` — observed on the 26.1.2 sister branch. This
  branch only escaped it because its own `fabric-api 0.162.0+26.3` happened to win the version
  conflict.
- **No more per-load JEI ERROR.** `ChiseledEnchantingJeiPlugin#registerItemSubtypes` registered an
  enchanted-book subtype interpreter that JEI's own `VanillaPlugin` already provides, which made JEI
  log `An interpreter is already registered for this: minecraft:enchanted_book` plus a stack trace
  on every world load and then discard ours. The override is gone; per-enchantment book entries are
  unchanged (they come from JEI's own interpreter), with a comment in place so it is not re-added.
- **`compile-check.yml` no longer loses its failure output.** It ran under Actions' default `bash -e`,
  so `status=$?` after `./gradlew …` was dead code: on failure the step exited before writing the
  exit code or the dependency dump. Now `status=0` + `|| status=$?` catches it, and the step records
  the resolved `compileClasspath` / `clientCompileClasspath` coordinates in
  `build-reports/compile-java.log` so a floating viewer version is traceable.

### Fixed — worldgen deadlock (integrated server freeze)

- **`BookshelfReplacerProcessor#finalizeProcessing` no longer touches the live `ServerLevel`.**
  It used to unwrap the `WorldGenRegion`, then call `getBlockState` / `getBlockEntity` /
  `setBlock` on the chunk that was *currently being generated*. In 26.3 that read is
  `ServerChunkCache#getChunk`, which for an off-thread caller does
  `supplyAsync(..., mainThreadProcessor).join()`, while the server thread sits in
  `managedBlock` waiting for the same chunk. With Distant Horizons' `FEATURES` LOD generator
  (its own thread pool) all workers deadlocked deterministically: no TPS, no log, unsavable
  world. The fill now only reads the datapack pool + enchantment registry + seed, and returns a
  finished `StructureBlockInfo` (state with `slot_*_occupied` + block-entity NBT), which vanilla
  places. Same fix also makes template-placed shelves actually *filled* — previously the fill ran
  against a chunk that was not placed yet, so villages/mansions kept empty chiseled bookshelves.
  See [`docs/reports/WORLDGEN_BOOKSHELF_DEADLOCK_REPORT.md`](docs/reports/WORLDGEN_BOOKSHELF_DEADLOCK_REPORT.md).
- **The shelf-facing heuristic moved to the server thread.** `finalizeProcessing` marks the
  position for chunk post-processing; `LevelChunkPostProcessMixin` then calls
  `BookshelfBookFiller.applyFacingToPlacedBookshelf` when the chunk starts ticking, so the v3
  rubric sees the finished geometry (a piece's own walls are still in the template during
  worldgen). If the mark is unavailable the shelf keeps its books and the template's rotated
  default facing.
- **Live-world fills are now server-thread-only and never load a chunk.** The fillers log one
  error and no-op when called off-thread, and neighbour scans use `ServerChunkCache#getChunkNow`
  (an unloaded neighbour counts as "unknown" instead of triggering a synchronous chunk load).
  Slot contents are written through `getItems()` rather than `setItem()`, so enchanted books are
  no longer subject to the `#minecraft:bookshelf_books` re-validation / per-slot state rewrite.

## [1.0.3] - 2026-10-13

### Minecraft 26.3 port

- **Renderer:** `PoseStack#mulPose(Quaternionf)` is gone in 26.3 — the floating book now rotates via
  `rotate(Axis.YP, radians)` / `rotateDegrees(Axis.ZP, degrees)`, matching vanilla's own
  `EnchantTableRenderer`.
- **Renderer:** `SubmitNodeCollector#submitModel` lost its trailing break-progress parameter (9 args
  now); the crumbling overlay is submitted separately through `order(1).submitCrumblingOverlay(...)`,
  so breaking the table still shows the crack overlay.
- **Loot:** migrated to the 26.3 loot builder API — `ContextIntProviders.exactly(...)` replaces
  `ConstantValue.exactly(...)` and `UniformContainerBase.Builder` replaces
  `LootPoolSingletonContainer.Builder`.
- **Loot:** `RemoveEnchantedBooksFunction` now takes the 26.3 `Optional<Holder<LootItemCondition>>`
  condition instead of a condition list.
- **Networking:** `FriendlyByteBuf`'s collection helpers (`writeCollection` / `readCollection` /
  `readList`) were removed in 26.3; the available-options payload hand-rolls the same VarInt-prefixed
  wire format. No protocol change for clients on the same build.
- **Item handling:** `LivingEntity#drop(ItemStack, boolean)` gained a third `Prediction` parameter in
  26.3. Both overflow-drop call sites (`InventoryUtils.insertItem` and the enchanting-table slot
  handler) now pass `Prediction.PREDICTED`, which is what vanilla's own `AbstractContainerMenu`
  passes for container-click drops. Found by CI, not by static review — see
  [`docs/reports/PORTING_26_3_REPORT.md`](docs/reports/PORTING_26_3_REPORT.md) §8.
- **Client:** dropped a dead `com.mojang.blaze3d.pipeline.RenderPipeline` import (that package moved
  to `com.mojang.renderpearl.api.pipeline` in 26.3; the screen never used it).
- **Block:** 26.3 removed the per-block codec entirely — `Block.simpleCodec(...)` no longer exists and
  vanilla's own `EnchantingTableBlock` carries no `CODEC` field — so the table block's `CODEC` field
  and `codec()` override are gone. (`StructureProcessor#codec()` and
  `LootItemConditionalFunction#codec()` still exist and are untouched.)

### Data-pack schemas

- Block loot table moved to the 26.3 schema: singular `condition` / `modifier` dispatched on `type`
  (the 26.2 `conditions[]` / `functions[]` arrays are silently ignored by 26.3).
- **World-load blocker fixed:** `craft.json` carried `display.background` while also having a
  `parent`. 26.3's `Advancement.validate()` rejects that outright ("Only advancement roots can have
  background"), the whole `minecraft:advancement` registry fails to load and the world refuses to
  open. 26.2 ignored the key. `scripts/compat_check.py` now guards both directions of that rule.
- `recipe_crafted` advancements (`craft`, `root`) migrated from `recipe_id` to the required
  `recipes` list — the old key no longer exists in 26.3, which made those advancements fail to load.
- `craft.json`'s `show_toast` / `announce_to_chat` moved from the top level into `display`, where
  26.3 (and vanilla) actually reads them — the intended "no toast, no chat" now applies.

### Tooling / CI

- Added the TACZR-style CI triad: `compile-check.yml` (compiles **both** source sets on Actions and
  pushes `build-reports/compile-java.log` back to `arena/**` branches), `build.yml` (static gates →
  full build → jar artifact) and `consistency.yml` (version gate). **Installed and live in
  `.github/workflows/` as of 2026-10-14**; `docs/ci/` keeps byte-identical reference copies and
  `bash docs/ci/install.sh --check` reports drift.
- **26.3 build verified green on GitHub Actions** (run `38431074401`, commit `1bc7e77`): all 8 steps
  `success`, `:compileJava` + `:compileClientJava` + `remapJar` clean, artifact `Artifacts`
  (295,720 bytes) uploaded.
- `gradlew` is now committed executable (`100644` → `100755`; it had been non-executable since the
  26.2 baseline, which only worked because `build.yml` runs `chmod +x` first — `compile-check.yml`
  calls `./gradlew` directly and died with `Permission denied`).
- A temporary `gradlew` error bridge (build failures re-emitted as chunked `::error` annotations,
  because the Actions log-blob domain is unreachable from the sandbox) carried the two compile
  fixes above, and was **removed again** once `compile-check.yml`'s log write-back went live.
  `gradlew` is once more byte-identical to the 26.2 baseline apart from the exec bit.
- Added `scripts/ci_static_checks.py`: mixin configs must resolve to real sources, a mixin registered
  in `fabric.mod.json` must not be excluded from compilation by `build.gradle`, and `en_us`/`zh_cn`
  key sets must match.
- Added `scripts/check_release_consistency.sh`: keeps `gradle.properties`, `README.md`,
  `fabric.mod.json` and `publish.yml` in sync. On the pre-port tree it immediately flagged the
  long-standing `publish.yml` drift (`MINECRAFT_VERSION: 26.1.2` vs `minecraft_version=26.2`), which
  is now fixed.
- Added a warn-only pre-commit reminder (`.githooks/pre-commit` + `scripts/install-hooks.sh`).
- `scripts/compat_check.py` gained Tier-1 guards for both 26.3 data schemas (negative-tested against
  the pre-port files: 7 failures), plus the advancement root/`background` rule — negative-tested by
  re-adding the key to `craft.json`, which the gate now fails.

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

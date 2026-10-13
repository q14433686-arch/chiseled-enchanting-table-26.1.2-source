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

# Changelog

> **Unofficial community port.** This changelog covers the unofficial 26.3 port maintained at
> `q14433686-arch/chiseled-enchanting-table-26.1.2-source`. It is not affiliated with or endorsed
> by the original author (Sacramentix). The original mod remains MIT-licensed; this port re-uses
> the original code/assets under that licence and updates them to the 26.3 toolchain.

Only the current release and the previous release are kept here. Older release notes are archived
under [`docs/CHANGELOG_HISTORY.md`](docs/CHANGELOG_HISTORY.md).

> **26.3 branch (2026-10-13):** This line now targets **Minecraft 26.3** as `1.1.0`. Toolchain
> bumped to Fabric Loader `0.19.5` + Fabric API `0.160.7+26.3` (Loom `1.17-SNAPSHOT`, Java 25
> unchanged). See [`docs/reports/PORTING_26_3_REPORT.md`](docs/reports/PORTING_26_3_REPORT.md) for
> the full impact assessment (3 source touch-points, one loot-table schema migration, and the new
> TaCZ-style CI gate). The previous 26.2 line stays at `1.0.2` (tag `chiseled-enchanting-table-26.2`).

## [1.1.0] - 2026-10-13 — Unofficial 26.3 Port

### Retarget

- Minecraft `26.2` → `26.3`; Fabric Loader `0.19.3` → `0.19.5`; Fabric API `0.155.2+26.2` →
  `0.160.7+26.3`. Mod version `1.0.2` → `1.1.0` (new MC target = minor bump).
- Source adaptation (26.2→26.3 API deltas that actually hit this codebase, verified against the
  sister-repo porting manuals):
  - removed the `Block#codec()` override (and its now-unused `CODEC` field) from
    `ChiseledEnchantingTableBlock` — 26.3 dropped the abstract method;
  - `PoseStack#mulPose` → `PoseStack#rotate` in the floating-book block-entity renderer;
  - dropped an unused `blaze3d.pipeline.RenderPipeline` import from the table screen
    (`blaze3d.pipeline` moved to `renderpearl.api.pipeline` in 26.3, but the import was dead).
- Migrated `loot_table/blocks/chiseled_enchanting_table.json` to the 26.3 loot schema
  (singular `condition` object / `modifier` instead of `functions` / `type` keys). The legacy
  shape is **silently ignored** by 26.3 (block would drop as a purple-black nameless item); the
  copy function is now `minecraft:copy_name` (behaviour-identical to the old custom-name-only
  `copy_components`).

### Tooling / CI (adopted from TaCZ_Refabricated_Unofficial's conventions)

- New version-consistency gate `scripts/check_release_consistency.sh` (gradle.properties ↔
  README ↔ fabric.mod.json ↔ publish.yml) plus staged GitHub workflows under `docs/ci/`
  (`compile-check` with sandbox log write-back, `build` with jar artifacts + static checks,
  `consistency`, and a version-aligned `publish`) awaiting the maintainer's one-time manual
  copy into `.github/workflows/` (the sandbox App token cannot push workflow files).
- `scripts/compat_check.py` gained a Tier-1 26.3 loot-schema check so the silent-load-failure
  class is caught offline.
- Fixed pre-existing drift the new gate immediately flagged: `publish.yml` was still pinned at
  26.1.2; registered the orphaned client mixin config; deleted the stale legacy
  `chiseled-enchanting-table.mixins.json` (superseded by `-port.mixins.json`, never registered).

## [1.0.2] - 2026-07-01 — Unofficial 26.2 line

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

# Current Baseline

> **Unofficial community port.** This repository targets Minecraft **26.3** on Fabric and is not affiliated with or endorsed by the original author. Original content is MIT-licensed; see [LICENSE](LICENSE).
>
> **Sync status (2026-10-22):** the current code/data now includes the full gameplay and UI feature set developed on the 26.1.2 line, adapted to 26.3. This is a file-level port between independent snapshot histories, not a Git merge. The mapping and API/schema adaptations are documented in [`docs/reports/SYNC_FROM_26_1_2_REPORT.md`](docs/reports/SYNC_FROM_26_1_2_REPORT.md).

## Target and build

- Minecraft `26.3`; Fabric Loader `0.19.5`; Fabric API `0.162.0+26.3`; Java `25`.
- Loom `1.17-SNAPSHOT`; Gradle wrapper `9.5.1`; mod version `1.1.0`.
- Optional client viewers: JEI and REI (compile-only; disabled independently with `-Penable_jei=false` / `-Penable_rei=false`). Their Minecraft-specific coordinates remain the 26.3 pins/configuration in `gradle.properties`.
- Run `python3 scripts/ci_static_checks.py`, `python3 scripts/compat_check.py`, and `bash scripts/check_release_consistency.sh --strict` for offline checks. A full Java build needs JDK 25 and the Minecraft/Fabric/viewer Maven repositories; CI's compile-check is the authoritative compiler gate.

## Current capabilities

- **Library progression:** regular bookshelves advance default enchantments; stored enchanted books in chiseled bookshelves act as master tomes that unlock levels. `LibraryResonance` calculates total and enchantment-specific XP discounts and mastery affinity.
- **Treasure Epiphany:** data-driven treasure gates, chance/slot scaling, cycle timing, freeze/stasis, rerolls, cost bundles and advancement triggers.
- **XP modes:** vanilla-table-style level thresholds with small level deductions for repeatable production coexist with full XP-curve deductions for base enchanting, book upgrades and treasure epiphanies.
- **Costs and upgrades:** yield-aware material curves, configurable alternative material bundles, book combine/research upgrades, transformations, and server-synced costs.
- **Book lifecycle:** stackable enchanted books; canonical book conversion; Depleted Tome output and Blank Overwrite cleansing; Arcane Catalyst universal materials.
- **Recipes and progression:** direct table crafting, vanilla enchanting-table upgrade, catalyst and crying-obsidian recipes, expanded advancements.
- **Client UX / interoperability:** redesigned table and list UI, XP/cost badges, optional JEI/REI enchanting and bidirectional transformation categories, 11 locale files.
- **Worldgen and loot:** chiseled bookshelf replacement/filling remains on the 26.3-safe structure-processor implementation and worldgen deadlock fix; loot injection and custom loot functions use the 26.3 builders/codecs/schema.

The player-facing guide is [`docs/WIKI.md`](docs/WIKI.md); datapack contracts are described in [`docs/EXTENDING.md`](docs/EXTENDING.md).

## 26.3 compatibility boundary retained during sync

The 26.1.2 source was **not** copied wholesale over the 26.3 port. Minecraft-version-specific code/schema stays on the 26.3 form:

- `ChiseledEnchantingTableBlock` has no per-block codec override.
- Overflow drops pass `Prediction.PREDICTED`.
- The block-entity renderer uses 26.3 `PoseStack#rotate` / `rotateDegrees` and the 9-argument `submitModel`, with a separate crumbling overlay.
- Loot pools use `ContextIntProviders` and `UniformContainerBase`; conditional loot functions take `Optional<Holder<LootItemCondition>>`.
- The menu payload manually encodes collection lengths because 26.3 removed `FriendlyByteBuf` collection helpers.
- 26.3 loot-table JSON uses singular `condition` / `modifier` entries dispatched by `type`; recipe-crafted advancements use `recipes` lists.
- The 26.3 `StructureProcessor` interface/codec registration, chunk post-processing and server-thread/worldgen safety implementation are preserved.

See [`docs/reports/PORTING_26_3_REPORT.md`](docs/reports/PORTING_26_3_REPORT.md) for original API evidence and the sync report for the files changed in this port.

## Verification boundary

The three offline checks pass in the current worktree. This environment has no Java runtime, so `./gradlew compileJava compileClientJava` could not be executed here. Runtime-only checks (dedicated-server cost sync, UI interaction, epiphany lifecycle, structure generation, and JEI/REI queries) remain for CI and in-game verification; see [`NEXT_TASK.md`](NEXT_TASK.md).

# Chiseled Enchanting Table — Unofficial 26.3 Port

![Chiseled enchanting table banner](./banner.png)

> **Unofficial community port.** This repository ports [Sacramentix/chiseled-enchanting-table](https://github.com/Sacramentix/chiseled-enchanting-table) to Minecraft **26.3** on Fabric. It is not affiliated with or endorsed by the original author. Original design, code, and assets are credited to Sacramentix and used under the [MIT License](LICENSE). Please report issues about this port here, not to upstream.

**Current version:** `1.1.0` *(unofficial-port versioning)*
**Target:** Minecraft `26.3`, Fabric Loader `>=0.19.5`, Fabric API `0.162.0+26.3`, Java `>=25`.

This branch now includes the full gameplay and UI overhaul synced from the 26.1.2 development line, adapted for Minecraft 26.3 APIs and datapack schemas. The sync/adaptation record is in [`docs/reports/SYNC_FROM_26_1_2_REPORT.md`](docs/reports/SYNC_FROM_26_1_2_REPORT.md).

## Features

- **Two-track library progression:** ordinary bookshelves raise default enchantment tiers; enchanted books stored in nearby chiseled bookshelves unlock master-tome levels and contribute library resonance.
- **Treasure Epiphanies:** discover rare enchantments through a grand library, with configurable rerolls and freeze/stasis controls.
- **Two XP modes:** level-threshold costs for efficient repeat crafting, alongside full XP-curve costs for research and rare offerings.
- **Cost and item progression:** level/yield-aware material curves, book combine/research upgrades, item transformations, and an Arcane Catalyst universal material.
- **Clean book lifecycle:** stackable enchanted books, Depleted Tomes, and batchable Blank Overwrite to restore clean books.
- **Accessible progression:** table upgrade and crafting recipes, expanded advancements, UI polish, and 11 shipped languages.
- **Optional JEI / REI:** enchanting and transformation categories, including forward and reverse item-use lookups. Neither viewer is a hard dependency.
- **Worldgen and loot:** safe bookshelf filling in structures, configurable chest-loot injection, and the existing 26.3 deadlock/API fixes.

For mechanics, formulas, recipes, and upstream comparisons, see the Chinese [gameplay wiki](docs/WIKI.md). For datapack customization, see [`docs/EXTENDING.md`](docs/EXTENDING.md).

## Installation and build

This unofficial port is not currently published on the original Modrinth or CurseForge listings. Use a jar produced by the repository's CI/build pipeline, or build locally with JDK 25:

```bash
./gradlew build
```

Place the generated `chiseled-enchanting-table-1.1.0.jar` in your Fabric `mods/` folder alongside Fabric API `0.162.0+26.3`. JEI and REI integrations are optional; their compile-time toggles are documented in `gradle.properties`.

## Checks

```bash
python3 scripts/ci_static_checks.py
python3 scripts/compat_check.py
bash scripts/check_release_consistency.sh --strict
```

CI also compiles both main and client source sets and packages the jar. Minecraft 26.3-specific API and data-schema adaptations are recorded in [`docs/reports/PORTING_26_3_REPORT.md`](docs/reports/PORTING_26_3_REPORT.md); the new cross-line sync is detailed in [`docs/reports/SYNC_FROM_26_1_2_REPORT.md`](docs/reports/SYNC_FROM_26_1_2_REPORT.md).

## Contributing

Contributions and bug reports are welcome. This is a community port, not an upstream release.

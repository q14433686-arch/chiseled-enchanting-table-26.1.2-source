# Chiseled Enchanting Table 26.1.2 port — P10 balance rework

**Date:** 2026-07-01

## Scope

Fix P9 overcorrection where many low/mid-level enchantments required diamonds/emeralds and XP costs were too high. P10 makes balance depend on both enchantment level and that enchantment's max level.

## Changes

### XP formula now depends on max level

The JSON `xp` schema now uses:

- `level_one_cost`: default low-level cost, currently 5 levels;
- `common_max_cost`: common maximum-level cost, currently 25 levels;
- `rare_single_level_cost`: rare/single-level cost, currently 35 levels;
- optional per-enchantment/level overrides.

For normal multi-level enchantments the XP cost interpolates from 5 at level I to 25 at that enchantment's max level. Single-level rare enchantments default to 35 unless overridden.

### Item cost now knows max level

Java now passes the enchantment max level into the data cost resolver. JSON supports:

- ordinary `options` for low/mid levels;
- `max_level` override for the top level of any enchantment;
- exact `level_overrides` for special levels.

This means low levels of high-cap enchantments can use thematic cheap materials, while only the true top level asks for rarer materials.

### Cost table rebalanced

- Removed most diamonds/emeralds from low and mid-level rules.
- Top levels of common high-cap enchantments now use limited emeralds/diamonds.
- Special/rare one-level enchantments can still require emeralds/diamonds or thematic rare items.
- Mace/wind enchantments keep thematic materials such as breeze rods, wind charges, heavy cores, diamonds, and netherite scrap where appropriate.

## Validation performed

- PASS: JSON syntax validation for all `src/**/*.json` files.
- PASS: `dependencies --configuration compileClasspath`.
- PASS: `processResources`.
- PASS: `compileJava compileClientJava`.
- PASS: `build --rerun-tasks`.
- PASS: jar metadata spot check for `0.2.3+26.1.2-p10`.

## Artifacts

Refreshed under `release_output/chiseled_enchanting_table_26_1_2_p10/`.

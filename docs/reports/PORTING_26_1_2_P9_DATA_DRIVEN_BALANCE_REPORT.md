# Chiseled Enchanting Table 26.1.2 port — P9 data-driven balance pass

**Date:** 2026-07-01

## Scope

Respond to balance feedback that top-tier bookshelf-provided enchantments were too cheap in item cost. The goal of this pass is to data-drive most of the enchantment cost system rather than add more hardcoded Java rules.

## Changes

### Cost schema expanded

`enchantment_costs.json` now supports:

- `options`: multiple possible item-cost options per enchantment;
- `min_count` / `max_count`: deterministic pseudo-random count ranges;
- `count_per_level`: level-scaled counts;
- `level_overrides`: per-level cost rules for top-tier or special enchantments.

The selected option/count is deterministic per player UUID + enchantment + level, so it feels varied but remains stable enough for UI/server validation.

### Cost table expanded

The JSON cost table now covers most vanilla enchantments visible in the current 26.1.2 registry surface, including armor, weapon, tool, bow, fishing, trident, crossbow, mace, curse, treasure, and movement enchantments.

Examples:

- `sharpness` level 5 now uses expensive top-tier overrides such as 6-10 diamonds or 8-10 emeralds instead of only 10 flint.
- `wind_burst` has level-specific costs using `breeze_rod`, `wind_charge`, and diamonds.
- mace-related enchantments such as `density`, `breach`, `wind_burst`, and `lunge` use heavy core / breeze / wind / diamond / netherite-related costs.

### Java responsibility kept narrow

Java now loads and validates the richer JSON structure, picks deterministic options, and creates `ItemStack` costs. It does not hardcode the per-enchantment balance table.

## Version/artifacts

- Updated local port version to `0.2.3+26.1.2-p9`.
- Refreshed artifacts under `release_output/chiseled_enchanting_table_26_1_2_p9/`.

## Validation performed

- PASS: JSON syntax validation for all `src/**/*.json` files.
- PASS: dependency/classpath check.
- PASS: `processResources`.
- PASS: `compileJava compileClientJava`.
- PASS: `build --rerun-tasks`.
- PASS: jar metadata/resource spot check for `0.2.3+26.1.2-p9` and packaged `enchantment_costs.json`.

## Client-only validation still required

- Confirm UI shows stable but varied item costs per player/enchantment/level.
- Confirm top-tier enchantments feel appropriately expensive.
- Confirm server-side apply validation matches the displayed client cost.
- Confirm registry names such as `wind_burst`/`lunge` appear only where the 26.1.2 runtime includes them.

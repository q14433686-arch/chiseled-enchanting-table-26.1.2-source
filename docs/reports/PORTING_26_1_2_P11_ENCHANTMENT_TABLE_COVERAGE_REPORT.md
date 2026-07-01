# Chiseled Enchanting Table 26.1.2 port — P11 enchantment coverage and recipe balance

**Date:** 2026-07-01

## Scope

Respond to feedback that item/enchantment coverage was incomplete, e.g. crossbows missing Multishot, and raise the block recipe cost because book-based enchanting still has no XP cost by design.

## External reference notes

A web check against current enchantment lists/mechanics confirmed several missing categories in the local default unlock JSON, including Crossbow enchantments (`Multishot`, `Quick Charge`, `Piercing`), Mace enchantments (`Density`, `Breach`, `Wind Burst`), and Spear `Lunge` in the 26.1.2 surface. The data table was expanded accordingly.

## Changes

### Default unlock table expanded

`default_enchantments.json` now lists the broad current vanilla/26.1.2 enchantment surface at level 1. The UI still filters by actual item compatibility, so crossbows should now be able to show compatible options such as:

- `multishot`
- `quick_charge`
- `piercing`
- `unbreaking`
- `mending`

Other categories such as armor, tools, bow, fishing rod, trident, mace, spear, curse, and treasure enchantments are also represented.

### Recipe cost increased

`chiseled_enchanting_table` recipe now uses diamond blocks in the two side slots instead of diamonds:

```json
"D": "minecraft:diamond_block"
```

This compensates for the mod's book-transfer path where enchanted-book enchanting does not consume XP.

## Validation performed

- PASS: JSON syntax validation for all `src/**/*.json` files.
- PASS: dependency/classpath check.
- PASS: `processResources`.
- PASS: `compileJava compileClientJava`.
- PASS: `build --rerun-tasks`.
- PASS: jar metadata/resource spot check for version `0.2.3+26.1.2-p11`.
- PASS: jar spot check confirmed `diamond_block` recipe key and default unlock entries for `multishot`, `quick_charge`, `wind_burst`, and `lunge`.

## Client-only validation still required

- Confirm crossbow shows Multishot/Quick Charge/Piercing when compatible.
- Confirm other equipment no longer misses obvious compatible enchantments.
- Confirm new recipe requires diamond blocks in-game.

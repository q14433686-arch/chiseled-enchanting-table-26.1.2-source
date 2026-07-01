# Chiseled Enchanting Table 26.1.2 port — P13 item transformation prototype

**Date:** 2026-07-01

## Scope

Prototype data-driven non-enchantment transformations requested by the user, e.g. apple -> golden apple and glass bottle -> experience bottle, while reviewing project extensibility.

## Added data file

`data/chiseled_enchanting_table/chiseled_enchanting_table/item_transformations.json`

Current rules:

- Apple -> Golden Apple
  - primary cost: 8 gold ingots
  - extra cost: 1-3 emeralds
  - XP cost: 18
- Glass Bottle -> Experience Bottle
  - primary cost: 6 lapis lazuli
  - extra cost: 2 amethyst shards
  - XP cost: 12

## Java changes

- `ChiseledEnchantingTableData` now loads item transformation rules from JSON, with built-in fallback rules.
- `ChiseledEnchantingTableBlock` includes transformation pseudo-options in the available payload.
- `ChiseledEnchantingTableScreenHandler` recognizes transformation pseudo-IDs, validates costs, consumes costs, and replaces the input stack with the configured output stack.
- `EnchantementListWidget` shows transformation options only for matching input items and displays the output item name.

## Extensibility review

The project is improving but still partially coupled:

### Good

- Default enchantment unlocks, cost tables, multi-item cost rules, and transformations are now JSON/data driven.
- Java is mostly responsible for loading, validation, menu/network/UI, and applying mechanics.
- The transformation system is generic enough for datapacks to add more input -> output conversions.

### Still weak

- UI naming/display for transformations is still minimal and piggybacks on the enchantment list widget.
- Menu payload still sends a single `Set<EnchantmentWithLevel>` for both real enchantments and pseudo-transformations, which is semantically mixed.
- Cost-slot UI only has one primary cost slot; extra costs are pulled from player inventory and displayed as small icons.
- Server/client data sync currently relies on built-in fallback transformation definitions being identical client/server; true datapack-defined transformations need an explicit sync payload.

### Recommended future split

- `data/` package: JSON loaders and validation.
- `mechanics/enchanting/`: real enchantment application logic.
- `mechanics/transformation/`: input/output transformation logic.
- `menu/`: sync payloads that distinguish real enchantments from transformations.
- `client/gui/`: separate row renderers for enchantments vs transformations.

## Validation performed

- PASS: JSON syntax validation for all `src/**/*.json` files.
- PASS: dependency/classpath check.
- PASS: `processResources`.
- PASS: `compileJava compileClientJava`.
- PASS: `build --rerun-tasks`.
- PASS: jar metadata/resource spot check for `0.2.3+26.1.2-p13` and packaged transformation JSON.

## Client-only validation required

- Apple in the enchantable slot shows Golden Apple transformation.
- Glass Bottle in the enchantable slot shows Experience Bottle transformation.
- Costs display correctly.
- Missing costs disable the transformation.
- Successful transformation consumes primary + extra costs + XP and replaces the input item.

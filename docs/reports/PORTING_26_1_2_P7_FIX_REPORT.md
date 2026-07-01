# Chiseled Enchanting Table 26.1.2 port — P7 user feedback fixes

**Date:** 2026-07-01

## Scope

Address six client/gameplay feedback items reported after P6:

1. enchantment choices looked like the same baseline set for every item;
2. nearby bookshelves/chiseled bookshelves did not affect available enchantment levels;
3. the left enchantment list could not scroll or drag the scrollbar;
4. the placed table had no floating book;
5. the inventory item lacked the center cloth/overlay visual;
6. missing Chinese translation.

## Changes

### Enchantment availability / bookshelf effect

- Reworked `ChiseledEnchantingTableBlock.get_unlocked_enchantements(...)`:
  - counts nearby vanilla bookshelves and chiseled bookshelves in radius 2;
  - boosts default enchantment levels by shelf power (`+1 level per 3 shelves`, clamped by the enchantment max level);
  - reads stored enchantments from nearby chiseled bookshelves and boosts them with the same shelf power;
  - merges duplicate enchantments by keeping the highest level.

This is a gameplay-restoration pass, not a final balancing pass. The exact shelf-power formula is intentionally simple and should be verified/tuned in live client tests.

### Scrollable enchantment list

- Added explicit `mouseScrolled(...)` handling to `EnchantementListWidget`.
- Added explicit scrollbar drag handling via `mouseDragged(...)`/`updateScrolling(...)`.

### Floating book renderer

- Restored client block-entity renderer inclusion in `build.gradle`.
- Registered `ChiseledEnchantingTableBlockEntityRenderer` from `ChiseledEnchantingTableClient`.
- Ported the renderer to the 26.1.2 submit/render-state API using `EnchantTableRenderState` and the vanilla book model/sprite path.
- Added vanilla-style book animation fields/tick logic to `ChiseledEnchantingTableBlockEntity`.

### Inventory item visual

- Removed the item-model dye tint entry from `assets/chiseled_enchanting_table/items/chiseled_enchanting_table.json` so the block model renders consistently in inventories instead of losing/over-tinting the center overlay.

### Translation

- Added `assets/chiseled_enchanting_table/lang/zh_cn.json` with block/item/container translations:
  - `纂刻附魔台`
- Normalized English capitalization and added container title translation.

## Version/artifacts

- Updated `gradle.properties` to `mod_version=0.2.3+26.1.2-p7`.
- Refreshed artifacts under `release_output/chiseled_enchanting_table_26_1_2_p7/`:
  - `chiseled-enchanting-table-0.2.3+26.1.2-p7.jar`
  - `chiseled-enchanting-table-0.2.3+26.1.2-p7-sources.jar`
  - `chiseled-enchanting-table-26.1.2-p7-source.zip`

## Validation performed

- PASS: JSON syntax validation for all `src/**/*.json` files.
- PASS: `dependencies --configuration compileClasspath`.
- PASS: `processResources`.
- PASS: `compileJava compileClientJava`.
- PASS: `build --rerun-tasks`.
- PASS: jar metadata spot check for `0.2.3+26.1.2-p7`.
- PASS: jar content spot check for renderer class, `zh_cn.json`, and item model JSON.

## Client-only validation still required

These fixes compile and package, but still need live Minecraft confirmation:

- Whether the P7 renderer displays the floating book correctly in-world.
- Whether the list scroll wheel and scrollbar drag feel correct with enough enchantments.
- Whether nearby shelf power produces the intended level progression.
- Whether chiseled bookshelf stored enchantments are discovered and merged correctly.
- Whether the item inventory model now shows the center overlay as expected.
- Whether Simplified Chinese strings appear correctly in-game.

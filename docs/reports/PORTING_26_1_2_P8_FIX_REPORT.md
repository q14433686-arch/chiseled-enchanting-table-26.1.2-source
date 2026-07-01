# Chiseled Enchanting Table 26.1.2 port — P8 feedback fixes

**Date:** 2026-07-01

## Scope

Follow-up fixes for two issues reported after P7:

1. enchantment list scrolling/scrollbar dragging still did not work in-game;
2. chiseled enchanting table levels did not depend on whether nearby chiseled bookshelves actually contained enchanted books or on those books' levels.

## Changes

### Screen-level event forwarding

`EnchantementListWidget` already had widget-level mouse handlers, but in a live container screen the parent `AbstractContainerScreen` can consume or reroute mouse events before the child list receives them.

P8 adds explicit event forwarding in `ChiseledEnchantingTableScreen`:

- `mouseScrolled(...)` forwards wheel events to the list when the pointer is over the list/scrollbar area.
- `mouseClicked(...)` focuses the list and begins scrollbar dragging when the scrollbar area is clicked.
- `mouseDragged(...)` forwards drag events while the list is dragging or while the pointer is over the list/scrollbar.
- `mouseReleased(...)` releases list dragging state.

The list hitbox was also widened slightly around the scrollbar to make the drag target easier to catch.

### Chiseled bookshelf book-level dependence

P7 counted chiseled bookshelves as generic shelf power, so an empty/low-level chiseled bookshelf could affect results too similarly to a filled/high-level one.

P8 changes the rule:

- regular bookshelves boost JSON default baseline enchantments;
- chiseled bookshelves do **not** boost merely by existing;
- only actual stored enchanted-book entries from chiseled bookshelves contribute enchantments;
- the contributed level starts from the stored book's own enchantment level;
- regular bookshelf power can still slightly boost that stored-book level, clamped by the enchantment max level;
- duplicate enchantments merge by highest level.

This makes chiseled bookshelf usefulness depend on whether it contains enchanted books and on those books' levels.

## Version/artifacts

- Updated `gradle.properties` to `mod_version=0.2.3+26.1.2-p8`.
- Refreshed artifacts under `release_output/chiseled_enchanting_table_26_1_2_p8/`.

## Validation performed

- PASS: JSON syntax validation for all `src/**/*.json` files.
- PASS: `dependencies --configuration compileClasspath`.
- PASS: `processResources`.
- PASS: `compileJava compileClientJava`.
- PASS: `build --rerun-tasks`.
- PASS: jar metadata spot check for `0.2.3+26.1.2-p8`.

## Client-only validation still required

- Confirm scroll wheel now scrolls the left list.
- Confirm scrollbar click/drag now scrolls the left list.
- Confirm empty chiseled bookshelves no longer create/enhance enchantments by themselves.
- Confirm enchanted books inside chiseled bookshelves appear at their stored level.
- Confirm higher-level enchanted books contribute higher levels than lower-level books.

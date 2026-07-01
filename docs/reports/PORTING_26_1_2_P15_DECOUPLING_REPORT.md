# P15 — Package/UI Decoupling + Enchanted Golden Apple XP Tweak

**Date:** 2026-07-01
**Mod Version:** `0.2.3+26.1.2-p14` (in-place iteration on the p14 artifact line)

## Scope

User request (Chinese, paraphrased):

1. Proceed with the deferred decoupling work (the package split + separate transformation row renderer
   from the P14 backlog).
2. While at it, lower the **Enchanted Golden Apple** transformation XP cost to **3 levels**.

## Content change (JSON-driven)

`item_transformations.json` (+ matching Java fallback literal):

- `enchanted_golden_apple` `xp_cost`: 30 -> **3**.
- `experience_bottle` `xp_cost` stays **1** (from P14).

## Decoupling performed

### 1. `menu` package — wire/payload semantics isolated
New `chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads` now owns:

- `OptionType` (`enchantment` / `transformation`) discriminator.
- `TransformationDisplay` (compact synced display record).
- `AvailableEnchantmentPayload` and `ApplyEnchantmentPayload`.

`ChiseledEnchantingTableScreenHandler` moved to the `menu` package and reduced to menu/container
responsibilities only: slot layout, cost resolution, cost condition/consumption, slot accessors, and
dispatch. It no longer defines the payloads inline.

### 2. `mechanics` packages — gameplay logic extracted from the menu
- `chiseled_enchanting_table.mechanics.transformation.ItemTransformationApplication` — applies a
  transformation (validate input, charge costs via the menu, swap output, award stat + sound).
- `chiseled_enchanting_table.mechanics.enchanting.EnchantmentApplication` — applies an unlocked
  enchantment and transfers an enchantment from an enchanted book (the previous `applyEnchantement`
  + `applyEnchantementFromBook` bodies).

The menu's `applyEnchantement` is now a thin dispatcher:

```java
if (isTransformation) { ItemTransformationApplication.apply(this, payload); return; }
if (costSlotIsEnchantedBook) { EnchantmentApplication.applyFromBook(this, payload); return; }
EnchantmentApplication.apply(this, payload);
```

The menu exposes minimal hooks for the mechanics: `world()`, `get_enchantable_item()`,
`get_cost_item()`, `setEnchantableSlot()`, `setCostSlot()`, `markInventoryChanged()`,
`applyEnchantementCondition()`.

### 3. `client/gui/row` package — separate row renderers
The previously monolithic `EnchantUiEntry.extractContent` is split into:

- `RowRenderSupport` — shared chrome (background color, 3D border, primary/extra cost icons, scaled
  text, XP label) and the shared color palette.
- `EnchantmentRowRenderer` — enchantment row, including the override-annotation second line
  (already-present / upgrade / downgrade / conflict). The annotation is precomputed in the entry as an
  `OverrideAnnotation(text, color)` so the renderer stays free of game-state lookups.
- `TransformationRowRenderer` — transformation row (input -> output), with a "-> output" sub-line and
  no enchantment-override logic.

`EnchantUiEntry.extractContent` now just gathers geometry/flags and dispatches to the correct
renderer based on `handler.isSyncedTransformation(...)`. The duplicated `draw3dBorder`/`renderText`
helpers were removed from the entry.

### Import/reference updates
- `ScreenHandlerRegistry`, `ChiseledEnchantingTableBlock`, `EnchantmentFinder` (main) and
  `ChiseledEnchantingTableScreen`, `EnchantementListWidget` (client) updated to the new
  `menu` package and `ChiseledEnchantingMenuPayloads` nested types.
- The old `chiseledEnchantingTable.ChiseledEnchantingTableScreenHandler.java` was deleted (logic moved
  to `menu`). Pre-existing `*.orig`/`*.rej` scratch files were left untouched.

## What is NOT changed (still backlog)

- Single cost slot remains; extra materials are still pulled from the player inventory and the UI
  shows up to two extra icons. A dedicated multi-slot recipe panel is still future work.
- The *available* option set still flows as `EnchantmentWithLevel` pseudo-options; only the *apply*
  payload is type-tagged (P14) and the renderers are now split (P15). A fully separate
  available-option model remains future work.
- `Block`, `BlockEntity`, `FloatingBook`, `ChiseledBookshelfTick` stay in the legacy
  `chiseledEnchantingTable` package this round to avoid touching the excluded mixin import surface;
  moving them to a `block`/`blockentity` package can be a later, isolated step.

## Validation

Executed in this sandbox:

- PASS: JSON syntax validation for all `src/**/*.json`.
- PASS: `compileJava compileClientJava`.
- PASS: `build --rerun-tasks`.
- PASS: jar spot check — `version=0.2.3+26.1.2-p14`, `mixins=[]`, new
  `menu/`, `mechanics/`, `gui/row/` classes present, `xp_cost` 3 / 1 in bundled JSON.

Client-only (pending live test):

- Enchanted Golden Apple option shows from a Golden Apple, costs 8 gold blocks + 2-5 emeralds + **3 levels**.
- Experience Bottle option costs **1 level**.
- Enchantment rows and transformation rows render correctly with the split renderers (names, cost
  icons, override annotations, XP label, conversion sub-line).
- No regression on normal enchanting / book enchanting / scrolling.

## Artifacts

`release_output/chiseled_enchanting_table_26_1_2_p14/` refreshed:

- `chiseled-enchanting-table-0.2.3+26.1.2-p14.jar`
- `chiseled-enchanting-table-0.2.3+26.1.2-p14-sources.jar`
- `chiseled-enchanting-table-26.1.2-p14-source.zip`

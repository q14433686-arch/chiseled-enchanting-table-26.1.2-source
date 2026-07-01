# P17 — Retire the legacy `chiseledEnchantingTable` package

**Date:** 2026-07-01
**Mod Version:** `0.2.3+26.1.2-p16`

## Scope

Continue the decoupling backlog. This round completes the package reorganisation by moving the
block-side classes out of the legacy mixed `chiseledEnchantingTable` package into a dedicated
`block` package, and fully retiring the old package.

## Changes

Moved (via `git mv`, package declarations updated):

| From | To |
|---|---|
| `chiseledEnchantingTable.ChiseledEnchantingTableBlock` | `block.ChiseledEnchantingTableBlock` |
| `chiseledEnchantingTable.ChiseledEnchantingTableBlockEntity` | `block.ChiseledEnchantingTableBlockEntity` |
| `chiseledEnchantingTable.FloatingBook` | `block.FloatingBook` |
| `chiseledEnchantingTable.ChiseledBookshelfTick` | `block.ChiseledBookshelfTick` |

Importers updated:
- `registry.BlockRegistry`, `registry.EntityRegistry` (main)
- `render.ChiseledEnchantingTableBlockEntityRenderer` (client)
- `mixin.chiseled_bookshelf.AddRandomTick` (excluded from compile, updated for source correctness)

Build config:
- `build.gradle` exclude path updated:
  `chiseled_enchanting_table/chiseledEnchantingTable/ChiseledBookshelfTick.java`
  -> `chiseled_enchanting_table/block/ChiseledBookshelfTick.java`
  (`ChiseledBookshelfTick` stays excluded from compilation exactly as before — only its location moved.)

Cleanup:
- Removed the stale `*.orig` / `*.rej` scratch files that remained in the old package and deleted the
  now-empty `chiseledEnchantingTable/` directory. The legacy package no longer exists.

## Resulting package layout (main source)

```
chiseled_enchanting_table/
  block/        ChiseledEnchantingTableBlock, BlockEntity, FloatingBook, ChiseledBookshelfTick(excluded)
  data/         ChiseledEnchantingTableData (loaders/resolvers)
  mechanics/
    enchanting/ EnchantmentApplication
    transformation/ ItemTransformationApplication
  menu/         ChiseledEnchantingTableScreenHandler, ChiseledEnchantingMenuPayloads
  registry/     Block/Entity/ScreenHandler/... registries
  utils/        helpers
  (mixin / integration / modifyChestLootTable / structureProcessor: excluded fragile areas)
```

Client source: `gui/`, `gui/row/`, `render/`, `utils/`.

This finishes the data / mechanics / menu / block / client.gui split: there is no longer a single
package mixing block + menu + mechanics.

## Decoupling backlog status

- [x] Split packages data / mechanics / menu / client.gui (P15)
- [x] Separate transformation row renderer (P15)
- [x] Move block/blockentity out of the legacy package (P17 — this report)
- [ ] Dedicated multi-slot cost panel (still inventory-pulled extras)
- [ ] Fully separate available-option model (apply payload is tagged; available set still uses
      `EnchantmentWithLevel` pseudo-options)

## Validation

Executed in this sandbox:
- PASS: `python3 scripts/compat_check.py` (0 warnings).
- PASS: JSON syntax validation for all `src/**/*.json`.
- PASS: `compileJava compileClientJava`.
- PASS: `build --rerun-tasks`.
- PASS: jar spot check — `version=0.2.3+26.1.2-p16`, `mixins=[]`, classes present under
  `chiseled_enchanting_table/block/`, no `chiseledEnchantingTable/` package in the jar.

Client-only (pending live test):
- Block places/opens/renders the floating book as before (pure package move, no behaviour change).
- Mining drops the block (P16 fix), enchant/transform unchanged.

## Artifacts

`release_output/chiseled_enchanting_table_26_1_2_p16/`:
- `chiseled-enchanting-table-0.2.3+26.1.2-p16.jar`
- `chiseled-enchanting-table-0.2.3+26.1.2-p16-sources.jar`
- `chiseled-enchanting-table-26.1.2-p16-source.zip`

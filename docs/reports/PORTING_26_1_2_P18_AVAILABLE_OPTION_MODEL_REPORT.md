# P18 — Typed Available-Option Model (retire EnchantmentWithLevel pseudo-options)

**Date:** 2026-07-01
**Mod Version:** `0.2.3+26.1.2-p17`

## Scope

Continue the decoupling backlog. This round removes the last semantic-pollution point: the
available option set was a `Set<EnchantmentWithLevel>` that overloaded the record to mean *both* a
real enchantment *and* a transformation pseudo-option (id = transformation id, level =
transformation level). Downstream code then had to "guess" what each entry was via a data lookup on
its id.

## Key insight

Transformations were already being synced to the client on their own channel
(`AvailableEnchantmentPayload.transformations`, a `List<TransformationDisplay>`). The block *also*
merged them into the enchantment set via `ChiseledEnchantingTableData.allTransformationEnchantments()`
— so transformations flowed **twice**, and the second path was pure pollution. The server apply path
for transformations never depended on set membership (it validates through
`ChiseledEnchantingTableData.itemTransformation(id)` + `rule.matchesInput`), so removing them from the
set is safe.

## Changes

### Server / data
- `ChiseledEnchantingTableBlock.createChiseledEnchantingTableMenuProvider`: the available set is now
  built from real enchantments only (`unlockedEnchantments` + JSON defaults). The
  `allTransformationEnchantments()` merge is removed.
- `ChiseledEnchantingTableData.allTransformationEnchantments()` deleted (it only existed to feed the
  old pollution path). `transformationEnchantmentsFor(ItemStack)` stays (used for the synced channel
  fallback).

`AvailableEnchantmentPayload.unlocked_enchantements` is now strictly real enchantments.

### New typed model
- `menu.AvailableOption(OptionType type, Identifier id, int level)` — an explicitly typed list
  option with factories `enchantment(...)` / `transformation(...)`, `isTransformation()`, and an
  `asEnchantmentWithLevel()` adapter for the existing cost-resolution helpers.

### Client UI
- `EnchantementListWidget.updateEntry` builds a single `List<AvailableOption>`: real enchantments
  from the book or the synced unlocked set, transformations from the synced transformation channel.
  Each option carries its `OptionType`.
- Filtering, sorting (`displayName(AvailableOption)`), and row construction all use the explicit
  `type` — no more `isSyncedTransformation(id)` guessing in the UI path.
- `EnchantUiEntry` now takes an `AvailableOption`, stores an `is_transformation` flag, and uses it in
  `serverSendApplyEnchant()` (to set the payload `OptionType`) and in `extractContent()` (to pick the
  row renderer). Removed the dead `HashSet`/`Collectors` imports.

## Result

- `EnchantmentWithLevel` once again means exactly one thing: a real enchantment + level.
- Transformations are a separate, typed concept end-to-end (synced channel -> `AvailableOption` ->
  type-tagged `ApplyEnchantmentPayload`).
- No behaviour change for the player: the same enchantments and transformations appear; the data just
  no longer travels twice and the code no longer guesses.

## Decoupling backlog status

- [x] Split packages data / mechanics / menu / client.gui (P15)
- [x] Separate transformation row renderer (P15)
- [x] Move block/blockentity into a `block` package; retire legacy package (P17)
- [x] Typed available-option model; stop overloading `EnchantmentWithLevel` (P18 — this report)
- [ ] Dedicated multi-slot cost panel (still inventory-pulled extras) — larger UI change, deferred

## Validation

Executed in this sandbox:
- PASS: `python3 scripts/compat_check.py` (0 warnings).
- PASS: JSON syntax validation for all `src/**/*.json`.
- PASS: `compileJava compileClientJava`.
- PASS: `build --rerun-tasks`.
- PASS: jar spot check — `version=0.2.3+26.1.2-p17`, `mixins=[]`, `menu/AvailableOption.class` present.

Client-only (pending live test):
- Enchantment list shows the same enchantments + transformations as before, correctly typed.
- Transformations still filter by input item, display the output name, and apply/consume costs.
- No regression on normal enchanting / book enchanting / scrolling / rendering.

## Artifacts

`release_output/chiseled_enchanting_table_26_1_2_p17/`:
- `chiseled-enchanting-table-0.2.3+26.1.2-p17.jar`
- `chiseled-enchanting-table-0.2.3+26.1.2-p17-sources.jar`
- `chiseled-enchanting-table-26.1.2-p17-source.zip`

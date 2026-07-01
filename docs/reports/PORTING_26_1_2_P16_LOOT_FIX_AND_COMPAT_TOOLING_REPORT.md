# P16 — Mining Drop Fix + Compatibility Tooling & Coupling Re-evaluation

**Date:** 2026-07-01
**Mod Version:** `0.2.3+26.1.2-p15`

## 1. Bug fix: block dropped nothing when mined

### Root cause
Two compounding problems in the block loot table:

1. **Wrong data-pack path.** The file lived at
   `data/chiseled_enchanting_table/loot_tables/blocks/chiseled_enchanting_table.json`
   (legacy **plural** `loot_tables`). MC 26.1.2 expects the **singular** `loot_table/blocks/`
   (the same singularisation already applied to `recipe` and `advancement`). A file under the old
   path is silently never loaded, so the block fell back to "no loot table -> no drop".
2. **Empty content.** Even if found, the table had `"pools": []` and `"functions": []`, i.e. it
   would have dropped nothing anyway.

The block uses `requiresCorrectToolForDrops()` and is in `tags/block/mineable/pickaxe` (and no
`needs_*_tool` tag), so any pickaxe is already the correct tool — the drop failure was entirely the
loot table, not the tool gating.

### Fix
- Moved the file to `data/chiseled_enchanting_table/loot_table/blocks/chiseled_enchanting_table.json`
  and deleted the old plural directory.
- Wrote a proper drop-self loot table modelled on vanilla `enchanting_table` (single pool,
  `survives_explosion` condition, `copy_components` of `custom_name` from the block entity, and a
  namespaced `random_sequence`).

Verified the jar now bundles it at the singular path with non-empty pools.

## 2. Automated compatibility script: `scripts/compat_check.py`

A dependency-free Python checker encoding the failure class above so it is caught in CI, not in a
live client.

**Tier 1 (offline, deterministic, fails the build):**
- All `src/**/*.json` parse.
- Data-pack directory naming uses the modern singular convention; no stale
  `loot_tables` / `recipes` / `advancements` / `tags/blocks` etc.
- Block loot tables have non-empty pools and at least one entry (drops something).
- `item_transformations.json` matches the Java built-in fallback (no drift between the two sources
  of truth).

**Tier 2 (best-effort, needs the MC 26.1.2 jar; warns only):**
- Item/enchantment ids referenced by recipe + transformations + costs exist somewhere in the
  vanilla data jar, else a warning (code-only registry entries can legitimately be absent).

### It immediately earned its keep
Tier 2 flagged `minecraft:scute` in `enchantment_costs.json`. That item was renamed to
`minecraft:turtle_scute` back in 1.20.5; under 26.1.2 the old id silently fell back to lapis in the
Java loader. Fixed the id; the check now passes with **0 warnings**.

### CI wiring
`.github/workflows/build.yml` now runs `python3 scripts/compat_check.py` before `./gradlew build`,
and its JDK was corrected from 21 to **25** to match the project's `java>=25` requirement.

## 3. Coupling / compatibility re-evaluation (post P14/P15)

Current state after the P14/P15 decoupling:

| Concern | Status |
|---|---|
| Wire/payload semantics | Isolated in `menu.ChiseledEnchantingMenuPayloads`; apply payload is type-tagged. **Good.** |
| Gameplay mechanics | Extracted to `mechanics.enchanting` / `mechanics.transformation`; menu is dispatch-only. **Good.** |
| Client row rendering | Split into `gui.row` (`RowRenderSupport` + per-kind renderers). **Good.** |
| Client/server transformation parity | Display table synced on menu open, with local fallback. **Good.** |
| Data-pack content | Fully JSON-driven (enchants, costs, transformations) + datapack-overridable. **Good.** |
| Dual source of truth (JSON vs Java fallback) | Risk of drift, now **guarded by `compat_check.py`**. |
| Data-pack path correctness | Was an unguarded silent-failure trap; now **guarded by `compat_check.py`**. |

Remaining backlog (unchanged, still deferred):
- Single cost slot; extras pulled from inventory (no multi-slot recipe panel yet).
- `available` option set still uses `EnchantmentWithLevel` pseudo-options (apply payload is tagged,
  renderers are split, but a fully separate available-option model is future work).
- `Block` / `BlockEntity` / `FloatingBook` / `ChiseledBookshelfTick` still in the legacy
  `chiseledEnchantingTable` package (kept to avoid touching the excluded mixin import surface).

New docs: `docs/EXTENDING.md` documents the data-driven extension points, the singular-dir trap, and
how to run the checks.

## Validation

Executed in this sandbox:
- PASS: `python3 scripts/compat_check.py` (0 warnings).
- PASS: JSON syntax validation for all `src/**/*.json`.
- PASS: `build --rerun-tasks`.
- PASS: jar spot check — `version=0.2.3+26.1.2-p15`, `mixins=[]`, loot table present at
  `loot_table/blocks/` with non-empty pools.

Client-only (pending live test):
- Mine the placed table with a pickaxe -> the block item drops (and keeps a custom name if renamed).
- Enchanted Golden Apple costs 3 levels; Experience Bottle 1 level (carried from P15).
- No regression on enchanting / transformations / scrolling / rendering.

## Artifacts

`release_output/chiseled_enchanting_table_26_1_2_p15/`:
- `chiseled-enchanting-table-0.2.3+26.1.2-p15.jar`
- `chiseled-enchanting-table-0.2.3+26.1.2-p15-sources.jar`
- `chiseled-enchanting-table-26.1.2-p15-source.zip`

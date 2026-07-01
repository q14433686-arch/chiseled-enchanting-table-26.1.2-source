# P14 — Transformation Content Fix + Payload Decoupling & Client Sync

**Date:** 2026-07-01
**Mod Version:** `0.2.3+26.1.2-p14`

## Scope (this round)

User request (Chinese, paraphrased):

1. The "golden apple" transformation was wrong: it should be **Golden Apple → Enchanted Golden Apple**
   (金苹果 → 附魔金苹果), not Apple → Golden Apple.
2. The Experience Bottle transformation's **XP cost was too high**; change it to only **1 level**.
3. Begin addressing the architecture/coupling concerns the previous report listed — chosen scope
   this round (confirmed with user): **content fixes + safe lightweight improvements** (payload
   `type` discrimination + sync transformation rules to the client). Full package-level refactor and
   separate row renderers are deferred to a later round.
4. No additional transformations this round (user chose "暂时不用").

## Content changes (JSON-driven)

`src/main/resources/data/chiseled_enchanting_table/chiseled_enchanting_table/item_transformations.json`
(and the matching built-in fallback string in `ChiseledEnchantingTableData`):

| id | input | output | xp_cost | primary | additional |
|----|-------|--------|---------|---------|------------|
| `enchanted_golden_apple` | `minecraft:golden_apple` | `minecraft:enchanted_golden_apple` | 30 | 8× gold_block | 2–5× emerald |
| `experience_bottle` | `minecraft:glass_bottle` | `minecraft:experience_bottle` | **1** | 6× lapis_lazuli | 2× amethyst_shard |

- Enchanted golden apple is intentionally expensive (gold blocks + emeralds + 30 levels) because it
  is a high-value vanilla item.
- Experience bottle XP cost lowered from 12 → 1 level per request.

Per the architecture rule (JSON owns content/values), these are pure data edits; the Java fallback
literal is kept byte-identical so single-player and missing-datapack cases behave the same.

## Decoupling: explicit option type in the apply payload

`ApplyEnchantmentPayload` now carries an explicit `OptionType` (`enchantment` / `transformation`)
as the first wire field:

```java
public enum OptionType { ENCHANTMENT("enchantment"), TRANSFORMATION("transformation"); ... }
public record ApplyEnchantmentPayload(OptionType option_type, Identifier enchantment_id, int enchantment_level)
```

- The server dispatch (`applyEnchantement`) now branches on `option_type` first, and only falls back
  to a data lookup for older clients that always send `ENCHANTMENT`. This removes the "guess from a
  data lookup" semantics for the normal path while staying backwards-compatible.
- The unused `EnchantmentWithLevel ewl` local in `applyTransformation` was removed.
- A backwards-friendly single-arg-style constructor `(id, level)` defaulting to `ENCHANTMENT` is kept
  so existing call sites and book-enchant flows compile unchanged.
- The client sends `TRANSFORMATION` when the option is a synced transformation, otherwise
  `ENCHANTMENT`.

Unknown wire tokens decode to `ENCHANTMENT`, so future option kinds won't crash old clients.

## Client sync: transformation display table

Previously the client UI relied on its own `ChiseledEnchantingTableData` load matching the server's.
That is only guaranteed for built-in fallbacks; a datapack-defined transformation on a dedicated
server would not display correctly on the client.

`AvailableEnchantmentPayload` (already sent when the menu opens) now also carries a compact
`List<TransformationDisplay>`:

```java
public record TransformationDisplay(Identifier id, Identifier input_item, Identifier output_item, int level)
```

- Built from `ChiseledEnchantingTableData.transformationDisplayTuples()` on the server side (the
  data package exposes plain tuples to avoid a data→menu compile dependency).
- The screen handler stores it as `Map<Identifier, TransformationDisplay>` and exposes:
  `isSyncedTransformation`, `syncedTransformationsFor`, `syncedTransformationMatches`,
  `syncedTransformationOutputName` — each falling back to local data if the synced table is empty
  (e.g. talking to an older server).
- `EnchantementListWidget` now uses these handler helpers for gathering, filtering, display names,
  and row rendering instead of calling `ChiseledEnchantingTableData` directly. Costs remain
  server-authoritative through the existing cost methods.

Net effect: transformation **display info** is now server-authoritative and works for
datapack-defined transformations. (Full cost-structure sync for client preview of randomized counts
is still resolved per the existing cost methods and is not duplicated on the wire.)

## Still deferred (documented, not done this round)

These are the remaining coupling items from the P13 report; user chose to defer them:

1. Package split into `data/` `mechanics/` `menu/` `client/gui/`.
2. Separate row renderers for enchantments vs transformations (currently both share
   `EnchantementListWidget.EnchantUiEntry`).
3. Single cost slot only; extra materials are pulled from the player inventory and the UI shows up to
   two extra icons. A dedicated multi-slot recipe panel is a larger future change.
4. Transformations still flow as `EnchantmentWithLevel` pseudo-options on the *available* set; only
   the *apply* payload is now type-tagged. A fully separate available-option model is future work.

## Validation

Executed in this sandbox:

- PASS: JSON syntax validation for all `src/**/*.json`.
- PASS: `compileJava compileClientJava`.
- PASS: `build --rerun-tasks`.
- PASS: jar metadata spot check — `version=0.2.3+26.1.2-p14`, `mixins=[]`.

Client-only (cannot run here, pending live test):

- Boot MC 26.1.2 / Fabric Loader 0.19.3 / Fabric API 0.151.0+26.1.2.
- Golden Apple shows an **Enchanted Golden Apple** option; gold_block + emerald + 30 levels consumed.
- Glass Bottle shows an **Experience Bottle** option costing only **1 level** + lapis + amethyst.
- Names/filtering correct for transformations (esp. on a dedicated server with datapacks).
- Apply works via the new type-tagged payload (no regression on normal enchantments / book enchant).

## Artifacts

`release_output/chiseled_enchanting_table_26_1_2_p14/`:

- `chiseled-enchanting-table-0.2.3+26.1.2-p14.jar`
- `chiseled-enchanting-table-0.2.3+26.1.2-p14-sources.jar`
- `chiseled-enchanting-table-26.1.2-p14-source.zip`

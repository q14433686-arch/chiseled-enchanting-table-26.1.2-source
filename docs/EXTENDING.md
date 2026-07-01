# Extending the Chiseled Enchanting Table (data-driven)

This mod follows a strict split: **Java = mechanism / loading / registration / sync / UI /
validation**, **JSON = content / values / rule tables**. Almost everything a content creator
wants to change is JSON and works from a datapack — no Java edits or recompilation.

Every data file below is loaded by the `SimpleSynchronousResourceReloadListener` in
`chiseled_enchanting_table.data.ChiseledEnchantingTableData`. Both the bundled datapack
(`src/main/resources/data/<ns>/...`) and any user datapack that overrides these paths flow
through the same loader; the last override wins by pack order.

## Data-pack layout (MC 26.1.2 — singular directories!)

```
data/<namespace>/
  loot_table/blocks/<block>.json      # singular "loot_table", NOT "loot_tables"
  recipe/<recipe>.json                # singular "recipe"
  advancement/<adv>.json              # singular "advancement"
  tags/block/...                      # singular "block"
  chiseled_enchanting_table/
    default_enchantments.json         # extension point 1
    enchantment_costs.json            # extension point 2
    item_transformations.json         # extension point 3
    loot_injections.json              # extension point 4 (added 1.0.2)
    bookshelf_book_pool.json          # extension point 5 (added 1.0.2)
```

> The singular `loot_table` / `recipe` / `advancement` naming is the #1 silent-failure trap in
> 26.x: a file under the old plural path loads as if it did not exist.
> `scripts/compat_check.py` fails the build if a legacy plural directory appears.

## Extension points

### 1. Default unlocked enchantments — `default_enchantments.json`

```json
{
  "values": [ { "enchantment": "minecraft:sharpness", "level": 1 } ],
  "tags":   [ { "tag": "#minecraft:non_treasure", "level": 1 } ]
}
```

- `values` — explicit `{enchantment, level}` list. Loaded without needing the world's dynamic
  registries; use this for a hand-picked default set.
- `tags` — expands at lookup time to every enchantment in the tag (accepts `#namespace:path`
  or bare `namespace:path`). The default `#minecraft:non_treasure` matches the tag the vanilla
  enchanting table itself uses, so any well-behaved enchantment-adding mod is picked up with no
  configuration. See [`COMPATIBILITY.md`](COMPATIBILITY.md) for the full cross-mod investigation.

**Java entry point:** `ChiseledEnchantingTableData.defaultEnchantments(HolderGetter<Enchantment>)`
combines the `values` list with a live tag expansion; the no-arg overload only returns the
static `values` set (used before the world's dynamic registry is available).

### 2. Enchantment costs — `enchantment_costs.json`

Top-level:
```json
{
  "xp": { "level_one_cost": 5, "common_max_cost": 25, "rare_single_level_cost": 35,
          "overrides": { "minecraft:mending": { "1": 30 } } },
  "default_item_cost": { "options": [ { "item": "minecraft:lapis_lazuli", "count_per_level": 2 } ] },
  "item_costs": {
    "minecraft:mending": { "options": [ { "item": "minecraft:emerald", "count": 3 } ],
                            "level_overrides": { "1": { "options": [ ... ] } } }
  }
}
```

Every cost rule supports:

- `options` — a list of candidate cost specs; one is picked deterministically per
  `(enchantment, level, max_level, player)` seed so the same enchantment on the same player
  always costs the same.
- `count` / `count_per_level` / `min_count` / `max_count` — count strategies:
  - `count` = flat count;
  - `count_per_level` = flat × level;
  - `min_count`+`max_count` = uniform-random inside the deterministic seed above.
- `level_overrides` — per-level rule override (e.g. only level V has a special recipe).
- `max_level` — rule override that applies only when picking the enchantment's max level.
- `extra` / `extras` — recursive additional cost items on top of the primary one (consumed
  from the player's inventory during application).

**Java entry point:** `ChiseledEnchantingTableData.itemCosts(...)`,
`ChiseledEnchantingTableData.xpCost(...)`.

### 3. Item transformations — `item_transformations.json`

```json
{
  "values": [
    {
      "id": "<namespace>:enchanted_golden_apple",
      "input":  "minecraft:golden_apple",
      "output": "minecraft:enchanted_golden_apple",
      "level":  1,
      "xp_cost": 3,
      "primary_cost":     { "item": "minecraft:gold_block", "count": 8 },
      "additional_costs": [ { "item": "minecraft:emerald", "min_count": 2, "max_count": 5 } ]
    }
  ]
}
```

- `primary_cost` — consumed from the cost slot.
- `additional_costs` — consumed from the player inventory.
- The display tuple `(id, input, output, level)` is synced to the client on menu open, so
  datapack-defined transformations render correctly on dedicated servers.

**Java entry point:** `ChiseledEnchantingTableData.transformationEnchantmentsFor(ItemStack)`
etc.; `mechanics.transformation.ItemTransformationApplication.apply(...)` does the actual
work.

**Bundled fallback:** `item_transformations.json` is mirrored by a built-in Java literal in
`ChiseledEnchantingTableData.builtInItemTransformations()` so a missing datapack file behaves
identically to the bundled file. `scripts/compat_check.py` fails the build if the two drift.

### 4. Enchanted-book loot injections — `loot_injections.json`

Ports upstream's chest-loot injection into datapack form. Every entry appends a new pool to a
vanilla loot table without replacing it, so it composes cleanly with other mods that touch
the same table.

```json
{
  "entries": [
    {
      "table": "minecraft:chests/stronghold_library",
      "rolls": 1,
      "entries": [
        { "enchantment": "minecraft:mending",   "level": 1, "weight": 5 },
        { "enchantment": "minecraft:sharpness", "level": 3, "weight": 20 },
        { "empty": true, "weight": 40 }
      ]
    },
    {
      "table": "#minecraft:chests",
      "rolls": 1,
      "entries": [ { "enchantment": "minecraft:unbreaking", "level": 1, "weight": 3 } ]
    }
  ]
}
```

- `table` — either a specific loot-table id, or a prefix selector starting with `#` (e.g.
  `#minecraft:chests` matches every loot table whose path starts with `chests/`; this is a
  cheap prefix match, not true tag resolution — loot tables are not tagged in vanilla).
- `entries[*].empty` — vanilla `EmptyLootItem` filler that keeps the pool's total roll weight
  balanced without adding a book (upstream uses these to tune the "no book this roll" rate).
- `entries[*].special` — optional string flag; currently only `"depth_based_efficiency"` is
  recognised (upstream port kept for structural parity — its runtime effect is currently
  disabled and the book emits as its declared static level).
- Every injected book gets a purple **"Chiseled Enchanting Table"** lore stamp so
  players (and the optional `remove_enchanted_books` loot function) can tell injected books
  apart from vanilla ones.

**Java entry point:** `mechanics.loot.EnchantedBookLootInjector` (runtime listener),
`mechanics.loot.RemoveEnchantedBooksFunction` (data-pack utility, registered as
`chiseled_enchanting_table:remove_enchanted_books` in the vanilla loot-function registry —
usable directly from any `.mcfunction`-style loot pool JSON).

**Derivation script:** `scripts/import_upstream_loot.py` regenerates this file from the
upstream Java source (`ReworkEnchantedBookChestLoot.java`, kept in-tree under
`src/main/java/chiseled_enchanting_table/modifyChestLootTable/` but excluded from the compile
slice). The script also normalises pre-1.17 vanilla loot-table paths (e.g.
`chests/village_armorer` → `chests/village/village_armorer`).

### 5. Structure bookshelf book pool — `bookshelf_book_pool.json` (schema v2)

Controls the 6-slot fill of every chiseled bookshelf placed by the structure processor when
generating villages / mansions / ancient cities / stronghold libraries.

```json
{
  "_schema_version": 2,
  "book_count": { "min": 3, "max": 5 },
  "enchanted_book_probability": 0.03,
  "enchanted_book_selection": {
    "enchant_pool_source":      "loot_injections",
    "use_base_weight":          true,
    "use_vanilla_weight_factor": true,
    "max_level_weight_ratio":   0.1
  },
  "enchant_sets": []
}
```

- `book_count.{min,max}` — inclusive count range (default 3-5) for how many of the shelf's 6
  slots hold a book.
- `enchanted_book_probability` — per-book chance the book is enchanted (default 0.03).
- `enchanted_book_selection.enchant_pool_source` — where the candidate enchanted-book pool
  comes from. `"loot_injections"` (default) aggregates every non-empty entry from
  `loot_injections.json`, so any book that appears in world loot also has a chance of appearing
  in structure shelves — single source of truth. `"inline"` uses only the `enchant_sets` list.
- `use_base_weight` / `use_vanilla_weight_factor` — weight-composition toggles. The final
  per-candidate weight is
  `baseWeight × Enchantment.getWeight() × maxLevelWeightRatio ^ relativeLevel`,
  where `relativeLevel = (level-1)/(maxLevel-1)` (or `0` if `maxLevel == 1`). That means a book
  at its enchantment's max level always eats the same `^1` penalty regardless of the numeric
  level, and single-level enchantments (Mending, Silk Touch, Infinity) are never level-
  penalised at all.

**Java entry point:** `mechanics.structure.BookshelfBookFiller.applyToPlacedBookshelf(
ServerLevel, BlockPos, RandomSource)` — the enrichment path used by both the template-pool
processor (`BookshelfReplacerProcessor#finalizeProcessing`) and the mark-and-post-process
mixin pipeline (stronghold libraries via `StructurePieceMarkMixin` +
`LevelChunkPostProcessMixin`).

### 6. Loot-item function utility (registry entry, not JSON)

`chiseled_enchanting_table:remove_enchanted_books` — a codec-registered `LootItemFunction`
that removes a vanilla enchanted book from its pool only if it lacks the "Chiseled Enchanting
Table" lore stamp. Not enabled by default; a datapack can opt in per-loot-table:

```json
{ "function": "chiseled_enchanting_table:remove_enchanted_books" }
```

This lets a modpack turn off vanilla-injected books in the tables they care about without
disabling the mod's own injections.

## Validating a datapack

```bash
python3 scripts/compat_check.py                    # Tier 1: JSON schemas + drift + dir naming
python3 scripts/compat_check.py --mc-jar <path>    # +Tier 2: id existence vs the vanilla data jar
python3 scripts/compat_check.py --extra-jar <path> # +Tier 2: id existence vs a third-party mod jar
```

Tier 1 failures block the build; Tier 2 emits warnings for ids the script can't confirm exist
(catches renames like `minecraft:scute` → `minecraft:turtle_scute` and typos in modded ids).

The exact Tier-1 rules for each file above are defined in the corresponding `check_*` function
in `scripts/compat_check.py` — they are the canonical schema definition, not just a linter.

## Where to add a new data-driven file

1. Add the JSON under `src/main/resources/data/chiseled_enchanting_table/chiseled_enchanting_table/`.
2. Add a loader method + record type in
   `chiseled_enchanting_table.data.ChiseledEnchantingTableData` (follow the pattern of the
   existing five).
3. Add a Tier-1 `check_*_well_formed()` in `scripts/compat_check.py` and wire it in `main()`.
4. Add a section here documenting the schema and the Java entry point.
5. Add the file to the id-referencing target list in `compat_check.py::collect_referenced_ids`
   if it contains item/enchantment ids that should be validated.

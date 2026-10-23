# Extending the Chiseled Enchanting Table (data-driven)

This mod follows a strict split: **Java = mechanism / loading / registration / sync / UI /
validation**, **JSON = content / values / rule tables**. Almost everything a content creator
wants to change is JSON and works from a datapack — no Java edits or recompilation.

Every data file below is loaded by the `SimpleSynchronousResourceReloadListener` in
`chiseled_enchanting_table.data.ChiseledEnchantingTableData`. Both the bundled datapack
(`src/main/resources/data/<ns>/...`) and any user datapack that overrides these paths flow
through the same loader; the last override wins by pack order.

## Data-pack layout (MC 26.2 — singular directories!)

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
    book_upgrades.json                # extension point 6 (master-tome routes)
    treasure_epiphany.json            # extension point 7 (grand-library epiphany + freeze)
    library_resonance.json            # extension point 8 (discounts, tome wear + regeneration)
    shelf_gates.json                  # extension point 9 (shelf stages, scan volume, vanilla mode)
    disenchanting.json                # extension point 10 (cleanse rituals, refunds, purge)
    pack_policy.json                  # extension point 11 (modpack/server governance)
```

> The singular `loot_table` / `recipe` / `advancement` naming is the #1 silent-failure trap in
> 26.x: a file under the old plural path loads as if it did not exist.
> `scripts/compat_check.py` fails the build if a legacy plural directory appears.

## Extension points

### 1. Default unlocked enchantments — `default_enchantments.json`

```json
{
  "values": [ { "enchantment": "minecraft:sharpness", "level": 1 } ],
  "tags":   [ { "tag": "#minecraft:in_enchanting_table", "level": 1 } ]
}
```

- `values` — explicit `{enchantment, level}` list. Loaded without needing the world's dynamic
  registries; use this for a hand-picked default set.
- `tags` — expands at lookup time to every enchantment in the tag (accepts `#namespace:path`
  or bare `namespace:path`). The shipped default is **`#minecraft:in_enchanting_table`** — the tag
  the vanilla enchanting table itself reads, and the documented extension point for mods. Its
  content defaults to `#minecraft:non_treasure`, so this is a pure superset of the older
  `non_treasure` default that this section used to document: on its own it changes nothing, but a
  mod that appends its own enchantments directly to `in_enchanting_table.json` (a common pattern,
  because `non_treasure` is meant to stay a vanilla-only list) is now picked up too. Either way a
  well-behaved enchantment-adding mod needs no configuration here.
  *(The cross-mod compatibility write-up that older revisions linked as `COMPATIBILITY.md` is not
  present in this snapshot on any branch of the repository; `scripts/ci_static_checks.py` reports
  the reference as a dangling link until it is restored. The behaviour it described is the tag
  expansion above, and `scripts/compat_check.py`'s `check_default_enchantment_tags_well_formed()`
  is its executable form.)*

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
- `base_count` + `max_level_count` (+ optional `level_exponent`, default `1.0`) — **yield-aware
  relative curve**: the count interpolates from `base_count` at level 1 to `max_level_count` at the
  enchantment's own `maxLevel`, so the same table works for modded enchantments with any
  `maxLevel = 1..255`. This is the form the bundled data uses today, following survival yield rates
  (`Lapis Lazuli > Emerald >> Amethyst Shard`), e.g. a 5-tier enchantment scales `3→6→10→15→20`
  lapis, `2→5→8→13→18` emerald, `1→2→4→6→8` amethyst shard at `level_exponent = 1.3`.
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

**Java entry points:** `mechanics.structure.BookshelfBookFiller` is split in two halves because
one of them runs on worldgen workers and one on the server thread:

- `computeFill(BlockState, RandomSource, HolderGetter<Enchantment>)` → `Fill(state, items)` and
  `toBlockEntityTag(NonNullList<ItemStack>, HolderLookup.Provider)` — **pure**, no level access.
  This is what `BookshelfReplacerProcessor#finalizeProcessing` uses: it returns a finished
  `StructureBlockInfo` (slot-occupied state + block-entity NBT) that vanilla places. Never call
  a level from there; the chunk being generated is not in the chunk cache, and reading it from a
  worker thread deadlocks the server
  ([report](reports/WORLDGEN_BOOKSHELF_DEADLOCK_REPORT.md)).
- `applyToPlacedBookshelf(ServerLevel, BlockPos, RandomSource)` and
  `applyFacingToPlacedBookshelf(ServerLevel, BlockPos)` — **server thread only** (they log one
  error and no-op anywhere else). Used by the mark-and-post-process mixin pipeline (stronghold
  libraries via `StructurePieceMarkMixin` + `LevelChunkPostProcessMixin`) and for the deferred
  facing pass on template-placed shelves. Neighbour reads there use
  `ServerChunkCache#getChunkNow`, so an unloaded neighbour is "unknown", never a chunk load.

### 6. Master-Tome book upgrades — `book_upgrades.json`

Two routes for turning a stored book into the next Master-Tome tier:

```json
{
  "combine_route":  { "enabled": true, "xp_cost_multiplier_of_vanilla_like": 0.6 },
  "research_route": {
    "enabled": true,
    "xp_cost_multiplier_of_direct_enchant": 1.4,
    "material_cost_multiplier_of_direct_enchant": 1.0,
    "default_material_cost": { "options": [ { "item": "minecraft:lapis_lazuli",
                                              "base_count": 6, "max_level_count": 26,
                                              "level_exponent": 1.3 } ] },
    "enchantment_overrides": {
      "minecraft:protection": { "material_cost": { "options": [ ... ] } }
    }
  }
}
```

- `combine_route` — material-free same-tier combine (two books), `0.6×` the direct-enchant XP.
- `research_route` — direct research of the next tier with `1.4×` XP; `material_cost` accepts the
  same cost-option vocabulary as §2 (including `base_count`/`max_level_count`/`level_exponent`) and
  the universal `chiseled_enchanting_table:arcane_catalyst` is accepted alongside every thematic
  alternative.
- `enchantment_overrides.<id>.material_cost` / `.xp_levels` — per-enchantment research cost.

**Java entry point:** `ChiseledEnchantingTableData` book-upgrade accessors;
`mechanics.enchanting.BookUpgradeApplication` applies the result.

### 7. Grand-Library Treasure Epiphany — `treasure_epiphany.json`

```json
{
  "min_regular_bookshelves": 32,
  "base_chance": 0.28,
  "chance_per_extra_bookshelf": 0.008,
  "chance_per_master_tome": 0.022,
  "max_chance": 0.90,
  "max_slots_cap": 4,
  "score_per_extra_slot": 12,
  "cycle_ticks": 12000,
  "freeze_duration_ticks": 24000,
  "max_freeze_ticks": 72000,
  "freeze_xp_levels": 1,
  "freeze_cost_options": [ { "item": "minecraft:packed_ice", "count": 16 } ],
  "reroll_xp_levels": 2,
  "reroll_cost_options": [ { "item": "minecraft:lapis_lazuli", "count": 10 } ],
  "treasure_enchantments": [ "minecraft:mending" ],
  "treasure_tags":  [ "#minecraft:treasure" ],
  "exclude_tags":    [ "#minecraft:curse" ],
  "default_xp_levels": 24,
  "default_material_cost": { "options": [ { "item": "minecraft:diamond", "count": 6,
                                            "extra": { "item": "minecraft:amethyst_shard",
                                                       "count": 8 } } ] },
  "treasure_costs": {
    "minecraft:aqua_affinity": { "xp_levels": 18,
                                 "material_cost": { "options": [ ... ] } }
  }
}
```

- Gate: only fires with `min_regular_bookshelves` (or more) wooden bookshelves nearby.
- Proc chance = `base_chance + chance_per_extra_bookshelf × extraShelves +
  chance_per_master_tome × storedMasteryUnits`, clamped to `max_chance`; the number of simultaneous
  epiphany slots scales with `score_per_extra_slot` up to `max_slots_cap`.
- `cycle_ticks` is the reroll cycle; `freeze_duration_ticks` / `max_freeze_ticks` bound the
  cryo-stasis freeze, which persists across breaking and re-placing the table (`CUSTOM_DATA` +
  `loot_table` `copy_components`, with a live `MM:SS` lore preview).
- `freeze_cost_options` / `reroll_cost_options` are **single-material** picks (`{item, count}`);
  epiphany inscription costs use the full cost-option vocabulary and may require
  `primary` + `extra` materials.
- `treasure_costs.<id>` overrides the per-enchantment XP and material cost;
  `default_*` covers anything not listed.

**Java entry point:** `ChiseledEnchantingTableData.treasureEpiphany*` accessors;
`mechanics.enchanting.EnchantmentApplication` / `ChiseledEnchantingTableScreenHandler` roll and
apply epiphanies.

### 8. Tome wear, discounts and regeneration — `library_resonance.json`

Controls the two discount tracks and the Master-Tome durability loop:

| Key | Meaning |
| --- | --- |
| `master_tome.tome_consumption_mode` | `"wear"` (default), `"keep"` (never wears) or `"consume"` (one use) |
| `master_tome.max_tome_uses` / `book_copy_wear_cost` | charges per level (default `16`) and wear for copying onto a book (`4`) |
| `master_tome.tome_deplete_behavior` | `"step_down"` (default) or `"to_remnant"` |
| `master_tome.tome_regen_enabled` | passive healing of stored tomes (default `true`) |
| `master_tome.tome_regen_base_seconds` | seconds per `+1` durability at `0` broad shelves (default `24000`) |
| `master_tome.tome_regen_tier1_shelves` / `_tier1_seconds` | first shelf tier (`38` → `12000` s) |
| `master_tome.tome_regen_tier2_shelves` / `_tier2_seconds` | second shelf tier (`42` → `3600` s) |

Regeneration is settled once per 20-tick window per world session and only counts time with the
shelves actually loaded — there is no catch-up for unloaded chunks, inventories or other worlds.

### 9. Shelf stages, scan volume and vanilla mode — `shelf_gates.json`

`max_bookshelves` anchors the `0..38` step-up curve (`gamma = 1.25`), `tier1_bookshelves` /
`tier2_bookshelves` (`12` / `32`) are the depletion-recovery and epiphany thresholds, the scan
radius makes the `7 × 5 × 7` volume configurable, and the "breathing room" keys refuse to count
shelves when the table itself is entombed.

### 10. Cleanse rituals and refunds — `disenchanting.json`

`enabled`, `allow_curse_purge`, `xp_refund_scale`, `external_refund_scale` (levels that arrived
from loot/creative rather than this table), the single-strip level bounds, and a
`material_options` list per ritual — each with a pure-vanilla alternative. Every entry accepts
`#tag` selectors and multiple options, so a modpack can re-cost the entire cleanse loop.

### 11. Modpack / server governance — `pack_policy.json`

```json
{
  "enforce_datapack_priority": false,
  "lock_in_game_config": false,
  "locked_keys": []
}
```

With `enforce_datapack_priority: true` a datapack declaration always wins, even if a player sets
`override_datapacks: true` in `config/chiseled_enchanting_table.json`. `lock_in_game_config: true`
(or a specific `locked_keys` list) turns those rows into read-only inspection in the config screen
and rejects them over the network, so a modpack can pin balance without shipping a custom build.
The file is resolved by *source pack* (the final selected resource), so overriding it from a higher
datapack, KubeJS `ServerEvents.highPriorityData`, Paxi or a companion jar works as expected.

### 12. Loot-item function utility (registry entry, not JSON)

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
   existing ones).
3. Add a Tier-1 `check_*_well_formed()` in `scripts/compat_check.py` and wire it in `main()`.
4. Add a section here documenting the schema and the Java entry point.
5. Add the file to the id-referencing target list in `compat_check.py::collect_referenced_ids`
   if it contains item/enchantment ids that should be validated.

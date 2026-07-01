# Chiseled Enchanting Table 26.1.2 port — P12 multi-item cost support

**Date:** 2026-07-01

## Scope

Address balance feedback that diamond/emerald costs should usually require an additional thematic item as well, based on enchantment level and rarity.

## Changes

### JSON schema: extra item costs

`enchantment_costs.json` item options now support:

- `extra`: one additional required item stack;
- `extras`: multiple additional required item stacks.

Example:

```json
{
  "item": "minecraft:diamond",
  "min_count": 3,
  "max_count": 6,
  "extra": { "item": "minecraft:flint", "count_per_level": 2 }
}
```

### Server validation and consumption

`ChiseledEnchantingTableScreenHandler` now:

- resolves all item costs for an enchantment;
- checks the primary cost in the table cost slot;
- checks extra costs in the player inventory;
- consumes the primary stack from the cost slot;
- consumes extra stacks from player inventory.

### Client display

`EnchantementListWidget` now displays up to two additional cost stacks alongside the primary cost stack and considers them in `canEnchant()`.

### Balance data update

Diamond/emerald options in the JSON table now generally include a thematic `extra` requirement. Examples:

- Sharpness max-level diamond/emerald options also require flint.
- Efficiency max-level diamond/emerald options also require redstone.
- Wind Burst high-level diamond options also require wind charges.
- Mending emerald options also require experience bottles.

## Validation performed

- PASS: JSON syntax validation for all `src/**/*.json` files.
- PASS: dependency/classpath check.
- PASS: `processResources`.
- PASS: `compileJava compileClientJava`.
- PASS: `build --rerun-tasks`.
- PASS: jar metadata/resource spot check for `0.2.3+26.1.2-p12` and packaged `extra` cost rules.

## Client-only validation still required

- Confirm UI displays the additional item costs clearly enough.
- Confirm server refuses enchantment when extra item costs are missing.
- Confirm server consumes extra item costs after successful enchanting.
- Confirm balance feels fair for high-tier diamond/emerald options.

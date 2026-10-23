# 1.1.1 — 26.3 world-load hotfix

Date: 2026-10-23. Evidence: user-provided latest.log, registry load at 01:41:59.

## Root causes (both in this mod)

1. `chiseled_enchanting_table:recipes/crying_obsidian`: recipe_unlocked still used
   `conditions.recipe`. 26.3 requires `conditions.recipes` (Recipe.LIST_CODEC).
   Log: `No key recipes ...`; the subsequent unknown `has_the_recipe` requirement is a
   consequence of that criterion failing to decode, not a reason to delete the requirement.
2. `chiseled_enchanting_table:blocks/chiseled_enchanting_table`: condition was an array.
   Log: `Not a JSON object: [{"type":"minecraft:survives_explosion"}]`.
   LootItemCondition.CODEC is a single holder; multiple conditions need an all_of object.
   In contrast LootItemFunctions.DIRECT_CODEC supports inline modifier sequences, so the
   checker must not blanket-ban all arrays. The shipped single modifier is normalized to an object.

Confirmed by reading actual 26.3 source from `0khs/minecraft_source_26.3`:
`advancements/triggers/RecipeUnlockedTrigger.java`, `LootItemCondition.java`,
`LootItemFunctions.java`, `SequenceFunction.java`, `AllOfCondition.java`.

## Fix and prevention

- Correct both bundled JSONs without dropping advancement criteria or copy-components fields.
- Extend advancement schema checks to recipe_unlocked; reject arrays under condition.
- Add exact failing fixtures, bundled-data validation, preservation of name/dye/custom-data/lore,
  and a positive test for allowed modifier sequences. Nine offline regression tests in total.
- Release metadata identifies the patched jar as 1.1.1 to distinguish it from the failing 1.1.0.

Compilation alone does not execute the game's registry codecs. The earlier compile/build passes
were real but insufficient; the prior static checker mistakenly accepted condition arrays and
only checked recipe_crafted. These gaps are now covered. No in-game world-load retest has been
performed in the sandbox. Replace the old mod jar, keep a backup, then retry normally; do not
remove data from the save or force safe-mode loading as a workaround.

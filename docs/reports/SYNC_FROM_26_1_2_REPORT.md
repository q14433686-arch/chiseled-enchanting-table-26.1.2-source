# Sync report: 26.1.2 development line → Minecraft 26.3

**Date:** 2026-10-22
**Target:** this branch, Minecraft `26.3` / Fabric API `0.162.0+26.3`
**Source snapshot:** `origin/chiseled-enchanting-table-26.1.2-source` at `2f627624` (feature-overhaul snapshot)
**Scope:** bring the source branch's gameplay, UI, recipe-viewer, assets, language and datapack updates to the 26.3 port while retaining 26.3-only API/schema changes.

## Method

The source and target are separate snapshot histories with no merge base. Changes were compared at the tree level. Added/modified client/server Java, resources and data were ported from the 26.1.2 snapshot, then version-sensitive files were retained from or adapted to the 26.3 implementation. Do not replace this work with a wholesale tree copy: the Minecraft-line adaptations below are required for 26.3.

## Synced feature set

- **Two-track bookshelf progression and resonance:** ordinary bookshelves support baseline enchantments and level progression; chiseled bookshelves hold master tomes; `LibraryResonance` computes general and enchantment-affinity bonuses.
- **Treasure Epiphany:** JSON-configured treasure enchantments, progression chance/slot caps, scheduled cycles, reroll and freeze/stasis controls, per-enchantment material costs, network state and UI controls.
- **Coexisting XP economies:** level-threshold/low-deduction production mode for master-tome enchantments and transformations, alongside full-curve XP deductions for direct enchanting, book upgrades and treasure epiphanies.
- **Materials and book progression:** level/yield-aware item cost curves; combine/research book upgrades; Arcane Catalyst; stackable books; Depleted Tome output; Blank Overwrite recovery.
- **Recipes and progression:** table crafting and upgrade recipes, catalyst and crying-obsidian recipes, additional advancement triggers.
- **UI and viewers:** redesigned list/table layout, XP badges, epiphany panel/buttons and row assets; JEI and REI transformation recipe categories and bidirectional item lookup; refreshed translations across all 11 locales.
- **Other changes:** table dye interaction and bookshelf particles, resource/model/texture updates, and updated Chinese gameplay documentation (`docs/WIKI.md`).

## 26.3-specific adaptations applied or retained

| Surface | 26.3 requirement | Port treatment |
|---|---|---|
| Table block codec | 26.3 removed the per-block `MapCodec` / `codec()` pattern used on the 26.1.2 line. | Removed the copied `MapCodec` field and `codec()` override from `ChiseledEnchantingTableBlock`. |
| Item overflow drops | `LivingEntity#drop` now accepts a `Prediction` argument. | Both overflow-drop paths pass `Prediction.PREDICTED`, matching the container-click behavior. |
| Floating-book renderer | Rotation moved to `PoseStack#rotate` / `rotateDegrees`; `submitModel` takes 9 args and crumbling is separate. | Kept the known-good 26.3 renderer; new block-entity animation state remains compatible with it. |
| Loot pool builders | `ConstantValue` / `LootPoolSingletonContainer.Builder` were replaced by `ContextIntProviders` / `UniformContainerBase.Builder`. | Adapted the synced loot injector while retaining its depth-based efficiency function and translated lore. |
| Conditional loot functions | Constructor condition is `Optional<Holder<LootItemCondition>>`. | Adapted `RemoveEnchantedBooksFunction` and `DepthBasedEfficiencyFunction`. |
| Network collection helpers | `FriendlyByteBuf` no longer has `writeCollection`, `readCollection` or `readList`. | Kept 26.3's explicit VarInt-length collection encoding, extended to the new mastery and transformation payload fields. |
| Loot-table schema | Singular `condition` / `modifier`, with `type` dispatch. | Kept the 26.3 block-loot file; no 26.1.2 plural-schema JSON was copied over it. |
| Recipe-crafted advancements | 26.3 uses `recipes: [...]` instead of `recipe_id`. | Migrated recipe-crafted criteria in the newly synced advancement files. |
| Structure processors | 26.3 uses the `StructureProcessor` interface and codec registration; the 26.1.2 implementation is an abstract class. | Preserved 26.3 versions of the structure processor, registry, and deadlock-sensitive post-process mixins. |
| Viewer toolchain | JEI/REI coordinates are Minecraft-line-specific. | Kept 26.3 dependency pins/settings instead of copying 26.1.2 viewer artifacts. |

## Validation

Passed in this worktree:

- `python3 scripts/ci_static_checks.py`
- `python3 scripts/compat_check.py` (Tier-2 checks warn because no Minecraft jar is present)
- `bash scripts/check_release_consistency.sh --strict`
- JSON parse of all `src/**/*.json`

**Compilation was not run locally:** this environment has no `java` executable/JDK. Run the repository's `compile-check.yml` / full build on GitHub Actions before treating the sync as compiler-verified. Runtime tests are also still needed for the new menu and epiphany flows, dedicated-server payloads, transformation lookup in both viewers, and structure/loot behavior.

## Deliberately preserved / deferred

- 26.3-only code, data schemas, and dependency coordinates listed above remain authoritative.
- Version-specific 26.1.2 workflow publishing coordinates and `compat_check.py` rules were not copied.
- Version metadata is now bumped to `1.1.0`. Release credentials and external listing copy were not changed; verify the CI build and update any listing details before publishing.

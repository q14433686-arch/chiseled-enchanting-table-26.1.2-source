# Sync 26.1.2 → 26.3 — 1.1.0

Date: 2026-10-22. Source: `origin/chiseled-enchanting-table-26.1.2-source` at
`0304f9dd065af4f145e5547fdeb822da30e9c527`. Target baseline: `61853f5` (26.3).

## Imported

The complete current source/data/assets/locales feature set, not only release metadata:
resonance, Treasure Epiphany and persisted freezing/reroll, XP modes, material bundles,
Arcane Condensate, Depleted Tome/Blank Overwrite, book upgrades, transformed-item economy,
painted GUI and XP animations, dye tint, 7×5×7 converging particles, new recipes/advancements,
11-locale copy, bidirectional transformation viewers, flat anvil penalty and stacked book fix,
depth-based mineshaft Efficiency. Player documentation is in [the Wiki](../WIKI.md).

CI improvements include a viewer-disabled compile job, dependency-log writeback,
all-locale key parity, documentation link warnings, workflow copy drift detection.
Historical scratch files and 26.1.2 build logs are not runtime functionality and were not
copied. Existing 26.3 migration reports are retained, not deleted by the upstream cleanup.

## Version adaptations

| Area | Required 26.3 implementation |
| --- | --- |
| Toolchain | Keep MC 26.3, loader 0.19.5, Fabric API 0.162.0+26.3, Loom 1.17, Gradle 9.5.1, Java 25 |
| Viewers | Keep 26.3 artifact coordinates and Architectury 22.0.3, not 26.1.2's pinned artifacts |
| Structure processor/registry/mixins | Retain interface + codec(), STRUCTURE_PROCESSOR codec registry, template BlockPos argument, capital-P markPosForPostProcessing |
| Table block | Omit removed simpleCodec/codec override while retaining all new dye, particle and interaction behaviour |
| Book renderer | Retain rotate/rotateDegrees, nine-argument submitModel and separate crumbling-overlay submission |
| Loot builder | Convert imported injector to ContextIntProviders and UniformContainerBase without losing new depth-based function or translated lore |
| Custom depth loot function | Optional<Holder<LootItemCondition>> constructor instead of list; LootContext#getOptional instead of getOptionalParameter |
| Network | Replace removed writeCollection/readCollection/readList with explicit VarInt lengths and loops, preserving *all* new resonance/master-level fields and freeze/reroll packets |
| Overflow drop | Add Prediction.PREDICTED to new menu overflow drop, retain existing InventoryUtils adaptation |
| Loot JSON | Convert conditions/functions arrays to condition/modifier; function/condition dispatch to type, retaining copy-components drop payload |
| Advancements | Convert every recipe_crafted recipe_id to recipes list, including new catalyst and crying-obsidian recipe unlocks; root background only |
| Anvil | Enable newly ported RemoveXpCostScaling in source set as well as mixin config; confirm onTake and calculateIncreasedRepairCost targets exist |

API checks additionally read actual decompiled 26.3 source from
`0khs/minecraft_source_26.3`: LootContext, LootItemConditionalFunction, LootContextParams,
AnvilMenu, BlockTintSource, Style, ChatFormatting and GuiGraphicsExtractor. The tint-source
interface and Style ChatFormatting overloads used by the new GUI remain available.
See PORTING_26_3_REPORT.md for earlier renderer/structure/schema evidence.

## Verification

Offline gates: release declaration consistency, all-locale/mixin static checks,
26.3 datapack/schema/JSON-Java checks, and dedicated sync regression checks. The latter
assert both feature coverage and absence of known 26.1.2 API regressions, plus mutation-test
the loot and advancement guards against legacy schemas.

Java compile/build must be checked by GitHub Actions on this branch: sandbox network policy
blocks Fabric/Mojang Maven. Previous 26.3 and source-branch green builds are not proof that
this sync compiles. Runtime client/server validation remains a separate task (NEXT_TASK.md).

### Observed CI results

On source commit `7eb9827`, [compile-check 39824070708](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/runs/39824070708)
succeeded for main and client. [Build 39824070633](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/runs/39824070633)
passed all three jobs: offline static checks, full Gradle build and compilation without JEI/REI.
The compile log records JEI 31.16.0.89 and REI 26.3.824; these exact 26.3 versions are now pinned
instead of floating selectors. Runtime smoke tests are still outstanding, not implied by a build.

The first intermediate compile (`91d7f1d`) still had the old block codec override; it was
removed before the successful run and covered by the regression test. Any compile-deps report
from that intermediate failure is historical, not a failure of the current source.

Final pinned-dependency verification (source commit `a0412e9`):
`compile-check` **39824564921** succeeded; `build` **39824565189** completed
all three jobs successfully (static checks, full Gradle build, no-viewers compilation).
`build-reports/compile-java.log` records both exact pinned viewer versions and
`BUILD SUCCESSFUL`. This confirms compilation/packaging, not in-game runtime behaviour.

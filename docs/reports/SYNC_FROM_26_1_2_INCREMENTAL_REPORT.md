# Incremental sync 26.1.2 → 26.3

Date: 2026-10-23. Version remains **1.1.1**, as requested.
Source range: `0304f9d` → `b78b244` (26.1.2 branch, PR #13).
Target starts from the previous 26.3 world-load hotfix, not the old 1.0.3 base.

## Included

- Disenchant-all, tier downgrade, single enchantment stripping, curse purging and reagents.
- Configurable three-track shelf progression, high-tier mastery, live shelf synchronization,
  bootstrap config, updated transformation thresholds/material/tag inputs and XP conservation.
- Illustrated interactive Grimoire and diagrams, sounds, recipes, UI shortcut/return packet,
  cleanse tabs/categories, complete textures and JEI/REI extensions.
- Latest Wiki and 11-locale humanized text/live reagent facts, language-reference coverage,
  no embedded JSON drift gate, economy/config checks and headless JUnit verification.
- Upstream transient root cleanup is applied; old fix patches/scripts are retired.

## 26.3 adaptations / preserved fixes

- New client screen transitions use Minecraft.gui.setScreen, as confirmed from actual 26.3
  Minecraft/Screen sources (`0khs/minecraft_source_26.3`).
- New LiveShelfStatePayload uses explicit VarInt count + element encoding; no removed
  FriendlyByteBuf readCollection/writeCollection calls. All new payload fields are retained.
- New recipe_crafted/recipe_unlocked advancements use recipes lists; previous crying-obsidian
  advancement and single loot condition fixes remain intact, with regression tests.
- Keep structure processor, renderer, loot function/context, prediction-aware drop adaptations.
- Keep Minecraft 26.3, Loader 0.19.5, Fabric API 0.162.0+26.3, Loom 1.17, Gradle 9.5.1,
  Java 25, JEI 31.16.0.89, REI 26.3.824, Architectury 22.0.3. No version bump.
- Import JUnit dependencies/useJUnitPlatform without downgrading the build toolchain.

## Validation

Run release consistency, ci_static_checks, compat_check and test_sync_26_3 offline; full
`./gradlew build` on Actions executes the imported JUnit suite as well as both source sets.
CI also compiles with viewers disabled. Registry/tag existence checks needing the vanilla jar
are unavailable offline. Actual in-game loading, cleanse economy, Grimoire navigation/return,
viewer compatibility and live shelf updates still need runtime smoke tests. Compilation is not
proof of world-load success; the prior data-schema hotfix tests remain mandatory.

## Observed results

Source commit `aadcecc72ad38cfd9a656b23376a9f8a7ba53933`:
- [Full build 39888562095](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/runs/39888562095):
  all three jobs passed (offline checks, Gradle build including JUnit tests, viewer-disabled compile).
- [Compile-check 39888562097](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/runs/39888562097):
  main + client passed; build-reports/compile-java.log contains this run's output.
- Offline: 11 sync regressions, 12 release checks, 307-key parity across 11 languages,
  297 code-referenced language keys and all Tier-1 compatibility/economy checks passed.
- Version/toolchain declarations are byte-identical to the pre-sync 26.3 1.1.1 baseline.
- First attempt exposed one additional JEI-only old Minecraft.screen lookup; fixed to
  Minecraft.gui.screen() and covered by a regression check before the successful runs above.

Download the artifact from the successful build above, not the earlier 1.1.1 hotfix run:
this request deliberately keeps the filename/version unchanged. Replace the old jar rather
than installing both. Runtime testing is still outstanding.

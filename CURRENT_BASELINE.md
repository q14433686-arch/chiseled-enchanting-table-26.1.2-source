# Current Baseline

**Date:** 2026-10-22
**Minecraft Version:** 26.3
**Fabric Loader:** 0.19.5
**Fabric API:** 0.162.0+26.3
**Gradle:** 9.5.1
**Loom:** 1.17-SNAPSHOT
**Java:** 25
**Mod Version:** `1.1.1`

This is an **unofficial community port** of Sacramentix's MIT-licensed mod, not affiliated
with or endorsed by the original author. The repository name contains `26.1.2` for historical
reasons; this branch targets **26.3**.

## Source of truth

All gameplay, assets, languages and viewer functionality from the 26.1.2 branch through
`0304f9dd065af4f145e5547fdeb822da30e9c527` (1.1.0) are synced, with 26.3 adaptations.
See [sync report](docs/reports/SYNC_FROM_26_1_2_REPORT.md) for the exact boundary and verification.
[Player Wiki](docs/WIKI.md) describes resonance, Treasure Epiphany/freezing, XP, material
alternatives, book upgrades, Blank Overwrite and transformations.

- Dual-track 7×5×7 library resonance; Treasure Epiphany with reroll and persisted freeze.
- Dual XP payment modes, yield-scaled costs, multi-material alternatives, Arcane Condensate,
  Depleted Tome and Blank Overwrite.
- Painted GUI and XP animations, converging bookshelf particles, dyeable table appearance,
  11 locales, expanded advancements/recipes.
- Server-authoritative enchantment/upgrade/transformation costs pushed on join and reload;
  JEI and REI provide both enchantment and transformation lookup in both directions.
- Flat anvil prior-work penalty; same-level book merge blocking; stacked-book remainder fix.
- JSON-driven chest/archaeology loot, depth-based mineshaft Efficiency, weighted structure
  bookshelf replacement/fill with the worldgen-safe NBT pipeline (no live chunk access from workers).
- Nine registered mixins; `RemoveXpCostScaling` is now active and no longer excluded from compilation.

## Target-specific contracts (must not regress on future syncs)

- StructureProcessor is an interface, codecs register into STRUCTURE_PROCESSOR;
  `processBlock` takes template BlockPos; `markPosForPostProcessing` uses capital P.
- Renderer uses rotate/rotateDegrees, nine-argument submitModel and separate crumbling overlay.
- Loot builders use ContextIntProviders/UniformContainerBase; conditional functions take
  Optional<Holder<LootItemCondition>>; LootContext uses getOptional.
- Payload collections use explicit VarInt count + elements; overflow drops supply Prediction.
- Loot tables use condition/modifier with type dispatch; recipe_crafted uses recipes lists.
- Keep Minecraft, Loader, Fabric API, Loom, Gradle and Architectury on their **26.3** versions.
  Verified 26.3 pins: JEI 31.16.0.89, REI 26.3.824. Do not reuse 26.1.2 viewer pins.

## Validation and release

`bash scripts/check_release_consistency.sh --strict`, `python3 scripts/ci_static_checks.py`,
`python3 scripts/compat_check.py` and `python3 scripts/test_sync_26_3.py` are offline gates.
GitHub Actions is the authoritative Java compilation check: the sandbox cannot reach the
Fabric/Mojang Maven repositories. A green older 1.0.3 build is not evidence for this sync.
Runtime smoke tests still require a 26.3 client/server; see NEXT_TASK.md.

Version declarations must agree in gradle.properties, README.md, fabric.mod.json,
publish workflow and its docs/ci copy. Do not copy historical compile reports from 26.1.2.

## Verified sync build

- `compile-check` run **39824070708**, source commit `7eb9827`: success for both main and client.
- `build` run **39824070633**, same source: static checks, full Gradle build and the
  no-JEI/no-REI compile job all succeeded. Artifact is available from that run.
- JEI **31.16.0.89**, REI **26.3.824** resolved in that compile and are now pinned here;
  the final pin-only build is independently checked by CI.
- Offline: 12 release consistency checks, five sync regression tests, nine mixin references,
  98 translation keys in each of 11 locales, Markdown links and Tier-1 compatibility all pass.
- Tier-2 registry/tag existence checks need the Minecraft jar (unavailable in sandbox);
  gameplay/runtime smoke tests have **not** been performed.

Final pinned-dependency verification (source commit `a0412e9`):
`compile-check` **39824564921** succeeded; `build` **39824565189** completed
all three jobs successfully (static checks, full Gradle build, no-viewers compilation).
`build-reports/compile-java.log` records both exact pinned viewer versions and
`BUILD SUCCESSFUL`. This confirms compilation/packaging, not in-game runtime behaviour.

## 1.1.1 world-load hotfix (2026-10-23)

User runtime log exposed two missed data-codec changes: recipe_unlocked needs recipes,
and loot condition takes a single predicate holder (not an array). Both bundled files are
fixed, preserving all copy-components fields including freeze data. The offline gates now
cover both exact failures. Historical green compilation above did not validate datapack codecs.
See docs/reports/WORLD_LOAD_1_1_1_HOTFIX.md. Runtime retest is still required.

## Incremental 26.1.2 sync — 2026-10-23 (version remains 1.1.1)

Source advanced from `0304f9d` to `b78b244` (PR #13). Includes disenchanting/cleansing,
configurable shelf progression and live sync, illustrated Grimoire, new recipes/tags/assets,
viewer extensions, 307 translation keys per locale, headless economy/config tests and
stronger static checks. New 26.3 adaptations: Minecraft.gui screen navigation and explicit
VarInt collection codecs for LiveShelfStatePayload. Every new recipe advancement is converted
to the 26.3 recipes list; previous world-entry hotfixes remain intact. Source-branch tooling,
version declarations and historical build logs are not copied. Current verification and
runtime caveats: docs/reports/SYNC_FROM_26_1_2_INCREMENTAL_REPORT.md.

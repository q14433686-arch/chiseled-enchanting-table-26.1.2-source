# Validation / next steps — 26.3 1.1.0

1. Offline gates:
   ```sh
   bash scripts/check_release_consistency.sh --strict
   python3 scripts/ci_static_checks.py
   python3 scripts/compat_check.py
   python3 scripts/test_sync_26_3.py
   bash docs/ci/install.sh --check
   ```
2. On a network-enabled JDK 25 machine or Actions, run `./gradlew build` (both optional viewers
   enabled). Read the compile-check result for the **current commit**, not old 26.1.2 logs.
3. Minecraft 26.3 runtime smoke tests (not executable in this sandbox):
   - Existing save + new world load: all advancements, recipes and block loot parse.
   - 7×5×7 resonance counts, XP discount, treasure unlock, cycle/reroll/freezing;
     save/restart preserves freeze and cannot duplicate/spend materials twice.
   - Both XP modes, material alternatives, book combine/research, depleted tome/overwrite,
     all transformations including trident and Arcane Condensate.
   - GUI resizing, tooltips, XP animations; all 11 languages; table dye tint and break overlay.
   - JEI and REI separately and together: book→item, item→book, transformations both directions;
     dedicated-server datapack cost changes sync on join and reload; disconnect clears cache.
   - Anvil same-level book merges blocked, book→item works, stacked books lose only one,
     repeated repair penalty stays flat.
   - Mineshaft Efficiency depth scaling; structure shelf fill/facing (vanilla and DH worldgen).
4. Viewer dependencies are pinned to CI-verified **26.3** versions (JEI 31.16.0.89 /
   REI 26.3.824). Upgrade them deliberately and rerun both viewer-enabled and disabled builds.

Migration evidence: docs/reports/SYNC_FROM_26_1_2_REPORT.md.

## Additional runtime checks after incremental 26.1.2 sync (still 1.1.1)

- Use the artifact from build 39888562095; earlier 1.1.1 artifacts lack the new features.
- Open the Grimoire from its item and the table shortcut, navigate/scroll diagrams, invoke
  JEI/REI usages, close and reopen the same table through the server-authorized return packet.
- Change surrounding shelves with the menu open and verify synchronized mastery/gates/costs.
- Test cleanse-all, downgrade, strip and curse purge (including vanilla reagent alternatives),
  XP refund boundaries, tagged transformation inputs and custom configuration/datapack reloads.
- Load both an existing save and a fresh world. Static checks include every new recipe-unlock
  advancement and preserve the previous single-predicate block loot fix, but cannot substitute
  for an actual game session.

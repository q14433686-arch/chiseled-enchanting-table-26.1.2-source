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
4. Pin exact **26.3** JEI/REI versions from verified CI dependency output before release;
   26.1.2 viewer pins are intentionally not copied.

Migration evidence: docs/reports/SYNC_FROM_26_1_2_REPORT.md.

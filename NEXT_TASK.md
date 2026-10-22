# Next Task: post-1.1.0 backlog (Minecraft 26.2 line)

> **Unofficial community port.** All instructions below apply to the unofficial 26.2 port
> (this repository). The upstream `Sacramentix/chiseled-enchanting-table` project is not affected
> by this version line.

**Current mod version:** `1.1.0` · **Minecraft:** `26.2` · **Fabric API:** `0.155.2+26.2` ·
**Loader:** `0.19.3`. See `CURRENT_BASELINE.md` for the currently-shipping capability boundary,
`CHANGELOG.md` for the 1.1.0 content and
`docs/reports/SYNC_FROM_26_1_2_SIDE_REPORT.md` for the 2026-10-22 sync that brought the
progression/economy/UI overhaul onto this line.

## How to build 1.1.0 from a fresh clone

The source tree is Gradle + Loom + Fabric API + Java 25:

```bash
chmod +x ./gradlew

# Offline gates — these three run anywhere (no network, no Gradle, no JDK):
python3 scripts/ci_static_checks.py                 # mixin configs + registered-mixin-compiled + lang parity
python3 scripts/compat_check.py                     # data-pack schema / drift (Tier 1 must pass)
bash scripts/check_release_consistency.sh --strict  # version declarations agree

JAVA_HOME=/path/to/jdk-25 ./gradlew --no-daemon --max-workers=1 \
    -Dorg.gradle.jvmargs='-Xmx1536m -Dfile.encoding=UTF-8' build --rerun-tasks --stacktrace
```

Artefacts land in `build/libs/`; release staging is under
`release_output/chiseled_enchanting_table_1_1_0/`.

## Open items (roughly in priority order)

### A. Live-verify the newly synced gameplay surface

Everything below compiles and packages, but none of it has been played on a real client yet. This
is the highest-value round because the 2026-10-22 sync changed balance-facing numbers *and* a lot of
UI:

1. **Two-track progression**: with `5 / 10 / 20` wooden bookshelves, confirm default-unlocked
   enchantments stop at `ceil(maxLevel / 2)`; store Master Tomes in chiseled bookshelves and confirm
   levels `1..k` appear 1:1. Confirm the graduation tiers are *not* reachable from wooden shelves
   alone.
2. **XP discounts**: `0–11` shelves ⇒ `0%`; `12` shelves ⇒ `2%`; `32` ⇒ `12%`; add master tomes and
   confirm global (`13%`) + specialisation (`round(15 * r^1.5)`) never exceed `-40%`.
3. **Dual-mode XP**: item transformations and Master-Tome inscribing (`storedMasterLevel >=
   option.level()`) must deduct only `1 / 2 / 3` levels (orb badge + `Lv.X`); base enchanting,
   combine/research upgrades and epiphanies must still use the full curve (flask badge + `-XL`).
4. **Treasure Epiphany**: with `32+` wooden bookshelves confirm the freeze button, `MM:SS`
   countdown, reroll, and that the freeze survives breaking and re-placing the table (lore preview
   included). Confirm the two-path treasure gate (tome in a shelf **or** epiphany).
5. **Depleted Tome / Blank Overwrite**: drain or merge an enchanted book → `depleted_tome`; cleanse
   `1..8` at once → canonical `minecraft:book`; confirm books stack to `64` and that anvil
   book+book same-level merges are still blocked.
6. **Dye pipeline**: right-click with each dye colour, confirm block, item icon, dropped item and
   held item all show the blended colour with no red bleed; confirm `color_table` fires.
7. **Viewers**: JEI/REI must show both categories, both query directions, secondary material slots
   and XP badges; confirm the S2C cost table invalidates the viewer cache after `/reload`.
8. **Advancements**: all 18 (including the 8 new ones) must display in all 11 locales and fire at the
   expected moments.
9. **Particles / loot**: chiseled bookshelves and wooden bookshelves inside the `7×5×7` volume must
   stream `ENCHANT` glyphs into the floating book; abandoned-mineshaft enchanted books must roll
   Efficiency II–V by depth.
10. Confirm no unexpected crash from `chiseled-enchanting-table-port.mixins.json` with its 9 active
    mixins (the re-enabled `RemoveXpCostScaling` is the fresh one — it injects into
    `AnvilMenu#calculateIncreasedRepairCost`).

### B. Multi-slot cost panel (last big UI decoupling backlog item)

The menu still has two container slots: `ENCHANTABLE_SLOT` and `COST_SLOT`. Multi-item costs (both
`enchantment_costs.json` `additional_costs`/`extras` and `item_transformations.json`
`additional_costs`) are now *displayed* (per-row secondary icons + hover tooltip listing every
alternative) but still consumed silently from the player's main inventory when the enchant is
applied. The proper fix is a dedicated multi-slot cost panel in the menu.

Scope: menu layout, `AbstractContainerMenu` slot registration, `quickMoveStack` behaviour,
network protocol, client renderer, and a live-test pass. This is the largest remaining UI change.

### C. Remaining parked legacy / integration items

- `mixin/accessor/ItemAccessor.java` — deleted on 2026-10-22 (nothing referenced it); do not
  reintroduce without a compatibility pass.
- `chiseled_enchanting_table.integration.*` — Colorful Books integration; should remain opt-in and
  only load when the target mod is present.
- Legacy `modifyChestLootTable/**`, `structureProcessor/**` and `utils/ChiseledBookshelfLootTable`
  are still excluded from compilation and fully superseded — delete them once no report references
  them.

### D. Data-pack extension coverage

Every data file under `src/main/resources/data/chiseled_enchanting_table/chiseled_enchanting_table/`
must be documented in [`docs/EXTENDING.md`](docs/EXTENDING.md) and covered by a Tier-1 schema check
in `scripts/compat_check.py`. As of the 2026-10-22 sync all seven (default_enchantments,
enchantment_costs, item_transformations, loot_injections, bookshelf_book_pool, book_upgrades,
treasure_epiphany) are covered — keep it that way when adding new data files.

## Validation required after any change

- `python3 scripts/compat_check.py` (Tier 1 must pass; pass `--extra-jar <path>` when testing
  alongside a specific third-party mod to also exercise Tier 2 id-existence coverage for its ids).
- JSON syntax validation for changed JSON files.
- `./gradlew build --rerun-tasks` (or rely on the `build` workflow).
- Jar metadata / resource spot check: `version` matches `gradle.properties`, name still marked
  Unofficial, `mixins` list matches the intended config.
- Refresh `release_output/chiseled_enchanting_table_<version>/` if source / resource / jar bytes
  changed. Directory name follows `chiseled_enchanting_table_<version with _ for .>`, e.g.
  `chiseled_enchanting_table_1_1_0` for `1.1.0`.
- If the source-of-truth for an in-game gameplay tuning changes (probabilities, cost formulas,
  book-count ranges, ...), update the concrete live-test expectations above so the next live-test
  round has an unambiguous target.
- Update `CHANGELOG.md` with the visible change; when releasing, keep only the current release in
  the main changelog and roll older content into `docs/CHANGELOG_HISTORY.md`.

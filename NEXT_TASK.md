# Next Task: post-1.0.3 backlog (Minecraft 26.2 line)

> **Unofficial community port.** All instructions below apply to the unofficial 26.2 port
> (this repository). The upstream `Sacramentix/chiseled-enchanting-table` project is not affected
> by this version line.

**Current mod version:** `1.0.3` · **Minecraft:** `26.2` · **Fabric API:** `0.155.2+26.2` ·
**Loader:** `0.19.3`. See `CURRENT_BASELINE.md` for the currently-shipping capability boundary and
`CHANGELOG.md` for the 1.0.3 + 26.3-synced fix list.

## How to build 1.0.3 from a fresh clone

The source tree is Gradle + Loom + Fabric API + Java 25:

```bash
chmod +x ./gradlew

# Offline gates — these three run anywhere (no network, no Gradle):
python3 scripts/ci_static_checks.py                 # mixin configs + registered-mixin-compiled + lang parity
python3 scripts/compat_check.py                     # data-pack schema / drift (Tier 1 must pass)
bash scripts/check_release_consistency.sh --strict  # version declarations agree

JAVA_HOME=/path/to/jdk-25 ./gradlew --no-daemon --max-workers=1 \
    -Dorg.gradle.jvmargs='-Xmx1536m -Dfile.encoding=UTF-8' build --rerun-tasks --stacktrace
```

Artefacts land in `build/libs/`; release staging is under
`release_output/chiseled_enchanting_table_1_0_2/`.

## Open items (roughly in priority order)

### A. Live-verify restored mixins and advancement guide

These compile and package, but still need real-client confirmation:

1. Vanilla enchanting table: putting a plain `BOOK` in slot 0 should show no enchantment offers;
   swapping back to an enchantable item should refresh offers normally.
2. Anvil: `ENCHANTED_BOOK` + `ENCHANTED_BOOK` with the same enchantment at the same non-max level
   should produce no output. Different levels / different enchantments should keep vanilla behaviour.
3. Chiseled bookshelf: a shelf containing at least one enchanted book should occasionally emit
   visible purple/enchant particles from the occupied slot area and play a quiet enchantment-table
   sound. Empty shelves or shelves with only plain books should stay silent.
4. Advancements: confirm English and Simplified Chinese strings display, and that these manual
   triggers fire at the expected moments: unlock from shelf, write to book, upgrade book, max-level
   book, transfer book-to-item, transfer book-to-book.
5. Confirm no unexpected crash from `chiseled-enchanting-table-port.mixins.json` with 8 active
   mixins.

### B. Multi-slot cost panel (last big UI decoupling backlog item)

Currently the menu has two container slots: `ENCHANTABLE_SLOT` and `COST_SLOT`. Multi-item costs
(both `enchantment_costs.json` `additional_costs` and `item_transformations.json`
`additional_costs`) are consumed silently from the player's main inventory when the enchant is
applied. The proper fix is a dedicated multi-slot cost panel in the menu.

Scope: menu layout, `AbstractContainerMenu` slot registration, `quickMoveStack` behaviour,
network protocol, client renderer, and a live-test pass. This is the largest remaining UI change
and should be its own release (probably `1.0.2`).

### C. Remaining parked legacy / integration items

- `mixin/anvil/RemoveXpCostScaling.java` — broader upstream anvil XP/prior-work-cost tweak. Keep
  separate from 1.0.2's narrow same-level book merge block.
- `mixin/accessor/ItemAccessor.java` — old enchanted-book stack-size/component accessor; high
  compatibility risk, do not enable casually.
- `chiseled_enchanting_table.integration.*` — Colorful Books integration; should remain opt-in and
  only load when the target mod is present.
- `color_table` advancement trigger: JSON/lang exists, but the dye interaction path still needs a
  dedicated code pass if table recolouring is meant to be live in 26.1.2.
- Vanilla enchanting-table-style bookshelf-to-table particles may still be considered visual polish.
  Floating book animation is treated as implemented per current user feedback and should not be
  listed as a blocker unless a new regression is reported.

### D. Data-pack extension coverage

Cross-check that every data file under
`src/main/resources/data/chiseled_enchanting_table/chiseled_enchanting_table/` is documented in
[`docs/EXTENDING.md`](docs/EXTENDING.md) and covered by a Tier-1 schema check in
`scripts/compat_check.py`. As of 1.0.2 all six (default_enchantments, enchantment_costs,
item_transformations, loot_injections, bookshelf_book_pool, book_upgrades) are covered — keep it that way when
adding new data files.

## Validation required after any change

- `python3 scripts/compat_check.py` (Tier 1 must pass; pass `--extra-jar <path>` when testing
  alongside a specific third-party mod to also exercise Tier 2 id-existence coverage for its ids).
- JSON syntax validation for changed JSON files.
- `./gradlew build --rerun-tasks`.
- Jar metadata / resource spot check: `version` matches `gradle.properties`, name still marked
  Unofficial, `mixins` list matches the intended config.
- Refresh `release_output/chiseled_enchanting_table_<version>/` if source / resource / jar bytes
  changed. Directory name follows `chiseled_enchanting_table_<version with _ for .>`, e.g.
  `chiseled_enchanting_table_1_0_2` for `1.0.2`, `chiseled_enchanting_table_1_0_2` for `1.0.2`.
- If the source-of-truth for an in-game gameplay tuning changes (probabilities, cost formulas,
  book-count ranges, ...), update the concrete live-test expectations above so the next live-test
  round has an unambiguous target.
- Update `CHANGELOG.md` with the visible change; when releasing, keep only the current release and
  one previous release in the main changelog and roll older content into `docs/CHANGELOG_HISTORY.md`.

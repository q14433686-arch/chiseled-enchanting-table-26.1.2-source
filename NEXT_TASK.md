# Next Task: post-1.0.3 backlog (Minecraft 26.3 line)

> **Unofficial community port.** All instructions below apply to the unofficial 26.3 port
> (this repository). The upstream `Sacramentix/chiseled-enchanting-table` project is not affected
> by this version line.

**Current mod version:** `1.0.3` · **Minecraft:** `26.3` · **Fabric API:** `0.162.0+26.3` ·
**Loader:** `0.19.5`. See `CURRENT_BASELINE.md` for the currently-shipping capability boundary,
`CHANGELOG.md` for the 1.0.3 change list, and
`docs/reports/PORTING_26_3_REPORT.md` for the 26.2→26.3 migration evidence.

## How to build 1.0.3 from a fresh clone

The source tree is Gradle + Loom + Fabric API + Java 25:

```bash
# Install/provide Java 25 first. If apt is unavailable, download/extract a JDK 25 tarball
# and point JAVA_HOME at it.
chmod +x ./gradlew

# Offline gates — these three run anywhere (no network, no Gradle):
python3 scripts/ci_static_checks.py                 # mixin configs + registered-mixin-compiled + lang parity
python3 scripts/compat_check.py                     # data-pack schema / drift (Tier 1 must pass)
bash scripts/check_release_consistency.sh --strict  # version declarations agree

JAVA_HOME=/path/to/jdk-25 ./gradlew --no-daemon --max-workers=1 \
    -Dorg.gradle.jvmargs='-Xmx1536m -Dfile.encoding=UTF-8' build --rerun-tasks --stacktrace
```

Artefacts land in `build/libs/`.

> **Build status (2026-10-13):** the 26.3 tree **compiles and packages green on GitHub Actions** —
> run `38431074401` on commit `1bc7e77`, workflow `build.yml`, all 8 steps `success` (`:compileJava`, `:compileClientJava`, `remapJar`,
> artifact `Artifacts` = 295,720 bytes). Two `warning` annotations remain (deprecation notes only).
> What is *not* yet verified is everything at runtime — see §B.

**If you cannot run Gradle** (sandbox without maven access), push the branch: the `compile-check`
workflow compiles **both** source sets and writes the log back to the branch, which is the only
compile channel that works from a sandbox (the Actions log-blob domain is unreachable from it):

```bash
gh api "repos/q14433686-arch/chiseled-enchanting-table-26.1.2-source/contents/build-reports/compile-java.log?ref=<branch>" \
    --jq '.content' | base64 -d | tail -60
```

## Open items (roughly in priority order)

### A-1. ~~Install the CI triad~~ — **done 2026-10-14**

All four workflows are live in `.github/workflows/` (applied by the maintainer; the sandbox token is
still refused `workflows` write access). The 26.3 `publish.yml` (`MINECRAFT_VERSION: 26.3`,
`VERSION: 1.0.3`) landed with them, so `check_release_consistency.sh --strict` now reports
`12 passed, 0 warned, 0 failed`. `docs/ci/` keeps byte-identical reference copies:

```bash
bash docs/ci/install.sh --check          # reports MISSING / DRIFT per file; exits 1 on drift
bash docs/ci/install.sh                  # re-apply after an intentional change
```

Only a maintainer can re-apply them — the sandbox cannot push anything under `.github/workflows/`.

### A0. 26.3-specific runtime verification (new this round)

A green `compile-check` only proves the mixin **target classes** exist. The first real 26.3 client
already proved that point the hard way: the build was green and the world still would not open —
`chiseled_enchanting_table:craft` carried `display.background` on a *child* advancement, which 26.3
rejects outright (see `docs/reports/PORTING_26_3_REPORT.md` §8). That one is fixed. The rest still
need a real 26.3 client/server:

1. **Mixin method-level injection**: watch `latest.log` for `@Inject`/`@ModifyReturnValue` failures
   on the 5 structure mixins plus `AnvilMenu.createResult`, `EnchantmentMenu.slotsChanged` and the
   chiseled-bookshelf random-tick override.
2. **Floating book under OIT**: 26.3 replaced "Improved Transparency" with OIT (26.3-snapshot-2).
   Compare the book with F3+X on and off; also break the table to confirm the new
   `submitCrumblingOverlay` path renders the crack.
3. **Chest / archaeology loot injection** still rolls in-world (the injector now builds pools with
   `ContextIntProviders` / `UniformContainerBase`).
4. **Block drop**: mining the table must still drop itself with a preserved custom name — this is
   the one loot table migrated to the 26.3 `condition`/`modifier` schema.
5. **Advancement load**: `craft` / `root` must load (they moved to `recipe_crafted.recipes`);
   a bad key here logs a data-load error instead of crashing.
6. **Payload round-trip**: open the menu on a dedicated server + client pair — the available-options
   payload now hand-rolls its VarInt-prefixed collections (`FriendlyByteBuf` helpers are gone in
   26.3), so this is the one place a wire-format mistake would show up as a decode error.

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
and should be its own release (probably `1.1.0`).

### C. Remaining parked legacy / integration items

- `mixin/anvil/RemoveXpCostScaling.java` — broader upstream anvil XP/prior-work-cost tweak. Keep
  separate from the narrow same-level book merge block. Still needs its own 26.3 mapping pass.
- `mixin/accessor/ItemAccessor.java` — old enchanted-book stack-size/component accessor; high
  compatibility risk, do not enable casually.
- `chiseled_enchanting_table.integration.*` — Colorful Books integration; should remain opt-in and
  only load when the target mod is present.
- `color_table` advancement trigger: JSON/lang exists, but the dye interaction path still needs a
  dedicated code pass if table recolouring is meant to be live in 26.3.
- Vanilla enchanting-table-style bookshelf-to-table particles may still be considered visual polish.
  Floating book animation is treated as implemented per current user feedback and should not be
  listed as a blocker unless a new regression is reported.

### D. Data-pack extension coverage

Cross-check that every data file under
`src/main/resources/data/chiseled_enchanting_table/chiseled_enchanting_table/` is documented in
[`docs/EXTENDING.md`](docs/EXTENDING.md) and covered by a Tier-1 schema check in
`scripts/compat_check.py`. All six (default_enchantments, enchantment_costs,
item_transformations, loot_injections, bookshelf_book_pool, book_upgrades) are covered — keep it that
way when adding new data files.

## Validation required after any change

- `python3 scripts/ci_static_checks.py` — mixin configs resolve, registered mixins are compiled,
  `en_us`/`zh_cn` keys match.
- `python3 scripts/compat_check.py` (Tier 1 must pass; pass `--extra-jar <path>` when testing
  alongside a specific third-party mod to also exercise Tier 2 id-existence coverage for its ids).
  Loot tables must stay on the 26.3 schema (`condition`/`modifier` + `type`) and advancements on
  `recipe_crafted.recipes`.
- `bash scripts/check_release_consistency.sh --strict` when any version/Minecraft declaration moved.
- JSON syntax validation for changed JSON files.
- `./gradlew build --rerun-tasks` — or, where Gradle cannot run, the `compile-check` workflow log.
- Jar metadata / resource spot check: `version` matches `gradle.properties`, name still marked
  Unofficial, `mixins` list matches the intended config.
- If release staging is wanted, use `release_output/chiseled_enchanting_table_<version with _ for .>`
  (i.e. `chiseled_enchanting_table_1_0_3`); no such directory is committed today.
- If the source-of-truth for an in-game gameplay tuning changes (probabilities, cost formulas,
  book-count ranges, ...), update the concrete live-test expectations above so the next live-test
  round has an unambiguous target.
- Update `CHANGELOG.md` with the visible change; when releasing, keep only the current release and
  one previous release in the main changelog and roll older content into `docs/CHANGELOG_HISTORY.md`.

# Next Task: post-1.0.3 backlog (Minecraft 26.1.2 line)

> **Unofficial community port.** All instructions below apply to the unofficial 26.1.2 port
> (this repository). The upstream `Sacramentix/chiseled-enchanting-table` project is not affected
> by this version line.

**Current mod version:** `1.0.3` · **Minecraft:** `26.1.2` · **Fabric API:** `0.151.0+26.1.2` ·
**Loader:** `0.19.3` · **Loom:** `1.16-SNAPSHOT` · **Gradle wrapper:** 9.4.1. See
`CURRENT_BASELINE.md` for the currently-shipping capability boundary, `CHANGELOG.md` for the 1.0.3
change list, and [`docs/reports/SYNC_FROM_26_3_REPORT.md`](docs/reports/SYNC_FROM_26_3_REPORT.md)
for what the 1.0.3 round actually did (back-port of the sister `26.3` branch's fixes/features, *not*
a Minecraft port) — including the list of 26.3-only changes that must **not** be copied here.

## How to build 1.0.3 from a fresh clone

The source tree is Gradle + Loom + Fabric API + Java 25:

```bash
# Install/provide Java 25 first. If apt is unavailable, download/extract a JDK 25 tarball
# and point JAVA_HOME at it.
chmod +x ./gradlew        # the wrapper is committed executable since 1.0.3; this is a no-op then

# Offline gates — these four run anywhere (no network, no JDK, no Gradle):
python3 scripts/ci_static_checks.py                 # mixin configs + registered-mixin-compiled + lang parity (11 languages) + doc links
python3 scripts/compat_check.py                     # data-pack schema / drift (Tier 1 must pass)
bash scripts/check_release_consistency.sh --strict  # the five version declarations agree
bash docs/ci/install.sh --check                     # docs/ci ≡ .github/workflows

JAVA_HOME=/path/to/jdk-25 ./gradlew --no-daemon --max-workers=1 \
    -Dorg.gradle.jvmargs='-Xmx1536m -Dfile.encoding=UTF-8' build --rerun-tasks --stacktrace
```

Artefacts land in `build/libs/`. `release_output/chiseled_enchanting_table_1_0_3/` is **not**
committed and was not produced for 1.0.3 (no JDK in the authoring sandbox) — take the jar from
`build.yml`'s Actions artifact instead, or stage a release directory when cutting a release.

**If you cannot run Gradle** (sandbox without maven access — `maven.fabricmc.net`,
`maven.blamejared.com`, `maven.shedaniel.me` and `maven.architectury.dev` are all needed now), push
the branch: the `compile-check` workflow compiles **both** source sets and writes the log back to the
branch, which is the only compile channel that works from a sandbox (the Actions log-blob domain is
unreachable from it):

```bash
gh api "repos/q14433686-arch/chiseled-enchanting-table-26.1.2-source/contents/build-reports/compile-java.log?ref=<branch>" \
    --jq '.content' | base64 -d | tail -60
```

To build without the optional viewers (offline-friendly, and what CI's `no-viewers` job does):

```bash
./gradlew build -Penable_jei=false -Penable_rei=false
```

`jei_version` (`29.47.0.111`) and `rei_version` (`26.1.819`) are pinned to what CI resolved. Bumping
either is a deliberate act: JEI has already removed `IIngredientAcceptor#addItemStack` on its `26.1`
branch (it is deprecated-for-removal in the pinned jar, which is the two warnings in the green log),
so a bump must migrate `EnchantingRecipeCategory`'s `addItemStack` / `addItemStacks` calls in the
same commit — see `docs/reports/VIEWER_INTEGRATION_JEI_REI.md` §6.

## Open items (roughly in priority order)

### A0. 1.0.3-specific runtime verification (new this round — start here)

**Compilation is settled**: `compile-check.yml` run `39670181582`, `build.yml` run `39670181557`
(all three jobs: static gates, full build, `no-viewers`) and `consistency.yml` run `39670181572` are
green on commit `da77188` — both source sets, JEI + REI enabled, pinned viewer coordinates
resolved. Nothing in the 1.0.3 sync has been in a real client yet, and a green
`compile-check` only proves the code compiles and the mixin **target classes** exist.

1. **Worldgen deadlock fix** ([issue #3](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/issues/3)):
   on a **fresh** world, fly into an affected structure chunk — the reporter's reproduction is a mesa
   village with Distant Horizons (`chunkGeneratorMode = FEATURES`). Expect: no freeze, world saves,
   and the chiseled bookshelves in village libraries / mansions / ancient cities now **contain
   books** (they came out empty before) and face the open side. Then regression-test stronghold
   libraries, which use the other path (`StructurePieceMarkMixin` + `LevelChunkPostProcessMixin`).
   If a shelf ends up empty, check for the filler's off-thread error line in `latest.log` — it means
   something is still calling the live-world half from a worker.
2. **JEI**: category `Chiseled Enchanting` appears under both the table and the chiseled bookshelf;
   focus an enchanted book → every item it can enchant; focus a diamond sword → every book that works
   on it; enchanted books are distinct searchable entries per enchantment.
3. **REI**: same pass with REI only (no JEI), then with **both** installed — two categories coexist,
   no duplicate class / entrypoint error.
4. **Cost sync**: dedicated server whose `enchantment_costs.json` differs from the jar (e.g. swap
   lapis for amethyst) → the viewer row, its tooltip ("Material cost as resolved by the server for
   you") and the table's own GUI all show amethyst; `/reload` after editing the file updates costs
   without reconnecting; disconnect → the cache clears (single-player afterwards shows its own
   costs again). A LAN host must never see another player's salted option.
5. **Languages**: switch the client language and confirm the mod's own 15 keys resolve in all 11
   shipped languages, including the lore line on a **server-generated** enchanted book (structure
   shelf fill *and* chest loot — both stamps are translatable now).
6. **Advancement `craft.json`**: the child advancement must load (it lost `display.background`), and
   crafting the table must now produce **no toast and no chat announcement** (both flags moved into
   `display`, where they are actually read).
7. **Payload round-trip**: open the menu on a dedicated server + client pair. The available-options
   payload still uses 26.1.2's `FriendlyByteBuf#writeCollection` helpers here (26.3 hand-rolls it),
   and the new cost-table payload is hand-rolled — a wire mistake shows up as a decode error on menu
   open or on join.

### A. Live-verify restored mixins and advancement guide

These compile and package, but still need real-client confirmation:

1. Vanilla enchanting table: putting a plain `BOOK` in slot 0 should show no enchantment offers;
   swapping back to an enchantable item should refresh offers normally.
2. Anvil: `ENCHANTED_BOOK` + `ENCHANTED_BOOK` with the same enchantment at the same non-max level
   should produce no output. Different levels / different enchantments should keep vanilla behaviour.
3. Chiseled bookshelf: a shelf containing at least one enchanted book should occasionally emit
   visible purple/enchant particles from the occupied slot area and play a quiet enchantment-table
   sound. Empty shelves or shelves with only plain books should stay silent.
4. Advancements: confirm these manual triggers fire at the expected moments — unlock from shelf,
   write to book, upgrade book, max-level book, transfer book-to-item, transfer book-to-book.
   **Do not** test for translated advancement strings: the display `title`/`description` in all eight
   files under `data/.../advancement/` are still hardcoded English and no `advancements.*` key exists
   in any lang file (the old 1.0.2 baseline claimed otherwise — see item **E**).
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

Since 1.0.3 the client already knows the true multi-item cost (`SyncedEnchantmentCosts` /
`EnchantCostTablePayload`), so the panel can be drawn from synced data instead of a second lookup —
but the container still only has `ENCHANTABLE_SLOT` + `COST_SLOT`, and the extra costs are still
consumed silently from the player's main inventory.

### C. Remaining parked legacy / integration items

- `mixin/anvil/RemoveXpCostScaling.java` — broader upstream anvil XP/prior-work-cost tweak. Keep
  separate from the narrow same-level book merge block (1.0.2).
- `mixin/accessor/ItemAccessor.java` — old enchanted-book stack-size/component accessor; high
  compatibility risk, do not enable casually.
- `chiseled_enchanting_table.integration.*` — Colorful Books integration; should remain opt-in and
  only load when the target mod is present.
- `color_table` advancement trigger: JSON exists, but the dye interaction path still needs a
  dedicated code pass if table recolouring is meant to be live in 26.1.2 (and it has no lang strings
  at all yet — item **E**).
- Vanilla enchanting-table-style bookshelf-to-table particles may still be considered visual polish.
  Floating book animation is treated as implemented per current user feedback and should not be
  listed as a blocker unless a new regression is reported.

### D. Data-pack extension coverage

Cross-check that every data file under
`src/main/resources/data/chiseled_enchanting_table/chiseled_enchanting_table/` is documented in
[`docs/EXTENDING.md`](docs/EXTENDING.md) and covered by a Tier-1 schema check in
`scripts/compat_check.py`. All six (default_enchantments, enchantment_costs,
item_transformations, loot_injections, bookshelf_book_pool, book_upgrades) are covered — keep it that way when
adding new data files.

### E. Advancement guide localisation (documentation says it exists; it does not)

All eight files under `src/main/resources/data/chiseled_enchanting_table/advancement/` carry literal
English `display.title` / `display.description`. No `advancements.*` key exists in any of the eleven
`lang/*.json`. `CURRENT_BASELINE.md` and this file both claimed the guide was localised for 1.0.2;
that claim was wrong and has been corrected in the docs, but the work is still open. Same state on
the `26.3` branch.

Scope: switch the sixteen strings to `{"translate": "advancements.chiseled_enchanting_table.<name>.title"}`
/ `.description`, add the keys to `en_us` + `zh_cn` **and the other nine languages in the same
commit** — `scripts/ci_static_checks.py` now enforces exact key parity across all eleven files, so a
partial addition fails the gate (that is deliberate: the gate is what keeps the eleventh language
from silently rotting).

### F. Cross-branch hygiene (three Minecraft lines in one repository)

The `26.1.2`, `26.2` and `26.3` branches have **no common ancestor**, so nothing propagates by
itself. Known deltas after the 1.0.3 sync, each of which is a small independent commit on the other
branch:

- `26.3` still writes the chest-loot lore stamp as `Component.literal("Chiseled Enchanting Table")`
  in `EnchantedBookLootInjector` (fixed here, §4.1 of the sync report).
- `26.3`'s `docs/EXTENDING.md` still documents the default unlock tag as `#minecraft:non_treasure`
  while the data ships `#minecraft:in_enchanting_table` (fixed here, §4.6).
- `26.3` has no `no-viewers` CI job and no dangling-markdown-link check (both added here).
- `26.2` has **no CI at all** and never received the deadlock fix as a commit — only as
  `docs/patches/26.2-worldgen-bookshelf-deadlock.patch` on the `26.3` branch. Applying it there is
  the single highest-value follow-up in this list: the same freeze affects that line.
- `26.3` declares the four viewer `compileOnly` dependencies **without** the toolchain exclusions
  this branch needs, and its `compile-check.yml` still has the dead `status=$?` under `bash -e`. Both
  are worth porting *back* (sync report §8 findings 6 and 7) — they are the only two divergences
  where this branch is strictly more robust.
- Both branches still float `jei_version=latest.release` / a `+`-suffixed `rei_version`; this branch
  pinned them (29.47.0.111 / 26.1.819) and records the resolved coordinates in the CI log.
- `docs/marketing/*` on this branch still describes `1.0.2`; update before any `publish.yml` run.
- Committed debris on all three branches: `*.java.orig` / `*.java.rej` under `src/`, and root-level
  `fix*.patch` / `fix_*.py` / `adapted_handler.java` / `Dummy.class` / `Dummy.java` /
  `abstract_container_screen.txt`. None is referenced by the build.

## Validation required after any change

- `python3 scripts/ci_static_checks.py` — mixin configs resolve, registered mixins are compiled,
  every `lang/*.json` has exactly the `en_us` key set, doc links resolve (warn-only).
- `python3 scripts/compat_check.py` (Tier 1 must pass; pass `--extra-jar <path>` when testing
  alongside a specific third-party mod to also exercise Tier 2 id-existence coverage for its ids).
  **Loot tables must stay on the 26.1.2 schema** (plural `conditions[]` / `functions[]` dispatched on
  `condition` / `function`) and **advancements on `recipe_crafted.recipe_id`** — the gates fail on the
  26.3 forms, which is the point: do not copy data files between branches without converting them.
- `bash scripts/check_release_consistency.sh --strict` when any version/Minecraft declaration moved.
- `bash docs/ci/install.sh --check` when a workflow changed (edit `docs/ci/` first, then install).
- JSON syntax validation for changed JSON files; YAML parse for changed workflows.
- `./gradlew build --rerun-tasks` — or, where Gradle cannot run, the `compile-check` workflow log
  written back to the branch (command above).
- Jar metadata / resource spot check: `version` matches `gradle.properties`, name still marked
  Unofficial, `mixins` list matches the intended config, `entrypoints` still lists the two optional
  viewer hooks, and the eleven lang files are inside the jar.
- If release staging is wanted, use `release_output/chiseled_enchanting_table_<version with _ for .>`
  (i.e. `chiseled_enchanting_table_1_0_3`); no such directory is committed today.
- If the source-of-truth for an in-game gameplay tuning changes (probabilities, cost formulas,
  book-count ranges, ...), update the concrete live-test expectations above so the next live-test
  round has an unambiguous target.
- Update `CHANGELOG.md` with the visible change; when releasing, keep only the current release and
  one previous release in the main changelog and roll older content into `docs/CHANGELOG_HISTORY.md`.

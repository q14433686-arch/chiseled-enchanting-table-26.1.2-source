# Agent Execution Brief

**Date:** 2026-07-01 (refreshed for the environment-setup-only round at the bottom of this file)
**Original goal (P7 round, kept for history):** Execute P7 user feedback fixes for enchantment
availability, bookshelf effects, list scrolling, floating book rendering, inventory item model, and
Chinese translation.

## Requested Startup Sequence

1. Read root `AGENT_BRIEF.md`.
   - Result: file is absent/empty in this repository snapshot.
2. Execute `python3 scripts/generate_agent_brief.py --write` if runnable.
   - Result: not runnable; there is no `scripts/` directory or `generate_agent_brief.py` file in this repository snapshot.
3. Read `docs/reports/CURRENT_AGENT_BRIEF.md`.
4. Read, in order:
   - `CURRENT_BASELINE.md`
   - `docs/DOCUMENTATION_INDEX.md`
   - `NEXT_TASK.md`
   - `docs/JSON_EXTENSION_SPEC_1_1_3.md` (missing)
   - `docs/EXTENDING_HERBCRAFT.md` (missing)
   - `CHANGELOG.md`
5. Reviewed P1-P3 report output in `docs/reports/` to understand the current migration boundary.

## Execution Summary

- Cloned `q14433686-arch/chiseled-enchanting-table-source-26.1.2` into the workspace.
- Restored execute permission on `gradlew`.
- Installed/extracted JDK 25.0.3 into `/tmp/herbcraft-jdk25/jdk-25` for this workspace session.
- Confirmed Gradle wrapper version `9.4.1` and Fabric Loom resolution `1.16.3`.
- Ran the required Gradle validation sequence successfully.
- Executed P5 as a documented runtime-smoke boundary because the sandbox cannot boot a full Minecraft client.
- Executed P6 by moving default enchantments and enchantment costs to JSON-backed server-data resources.
- Added `ChiseledEnchantingTableData` reload loading/validation and rewired existing block/menu call sites to use it.
- Fixed the P4 `get_cost_item()` empty-stack stub so it returns the actual cost slot.
- Refreshed P6 release artifacts under `release_output/chiseled_enchanting_table_26_1_2_p7/`.
- Updated baseline/task/changelog/index/report documentation.
- Executed P7 feedback fixes for the six reported gameplay/UI/visual/localization issues.
- Refreshed P7 release artifacts under `release_output/chiseled_enchanting_table_26_1_2_p7/`.

## Current Technical Understanding

- The port has moved beyond P4: `gradle.properties` currently marks `mod_version=0.2.3+26.1.2-p7`.
- Default enchantment unlocks and enchantment cost values are now backed by JSON data tables loaded via a server-data reload listener.
- Client GUI classes are included in the compile slice and the final jar contains:
  - `ChiseledEnchantingTableClient.class`
  - `ChiseledEnchantingTableScreen.class`
  - `EnchantementListWidget.class`
- Renderer code remains excluded from the client compile slice.
- Main fragile hooks remain excluded: mixins, loot-table hooks, structure processor implementation, integration code, `ChiseledBookshelfTick`, and `ChiseledBookshelfLootTable`.
- `fabric.mod.json` still uses an empty `mixins` array for runtime safety.
- The project currently builds, but live Minecraft behavior is not yet validated in this environment.

## Validation Checklist Executed

- [x] Java 25 verification: `/tmp/herbcraft-jdk25/jdk-25/bin/java -version`
- [x] Java compiler verification: `/tmp/herbcraft-jdk25/jdk-25/bin/javac -version`
- [x] Gradle wrapper verification: `./gradlew --version`
- [x] Dependency/classpath check: `dependencies --configuration compileClasspath`
- [x] Resource processing: `processResources`
- [x] JSON syntax validation for P6 data tables
- [x] Main/client compilation: `compileJava compileClientJava`
- [x] Full build: `build --rerun-tasks`
- [x] Jar artifact presence check under `build/libs/`
- [x] Jar metadata spot check (`fabric.mod.json` version/dependencies/mixin list)
- [x] Jar resource spot check for packaged P6/P7 resources, renderer, and translations
- [x] Release output refresh under `release_output/chiseled_enchanting_table_26_1_2_p7/`

## Validation Commands

```bash
JAVA_HOME=/tmp/herbcraft-jdk25/jdk-25 ./gradlew --no-daemon --max-workers=1 dependencies --configuration compileClasspath
JAVA_HOME=/tmp/herbcraft-jdk25/jdk-25 ./gradlew --no-daemon --max-workers=1 processResources --stacktrace
JAVA_HOME=/tmp/herbcraft-jdk25/jdk-25 ./gradlew --no-daemon --max-workers=1 -Dorg.gradle.jvmargs='-Xmx1024m -Dfile.encoding=UTF-8' compileJava compileClientJava --stacktrace
JAVA_HOME=/tmp/herbcraft-jdk25/jdk-25 ./gradlew --no-daemon --max-workers=1 -Dorg.gradle.jvmargs='-Xmx1024m -Dfile.encoding=UTF-8' build --rerun-tasks --stacktrace
```

All listed Gradle commands completed with **BUILD SUCCESSFUL**.

## Client-Only / Live-Game Validation Still Pending

These checks require a real Minecraft client/runtime and were not performed in this sandbox:

- Boot Minecraft 26.1.2 with Fabric Loader 0.19.3 and Fabric API 0.151.0+26.1.2.
- Confirm mod metadata load with mixins disabled.
- Confirm custom block registration in a live world.
- Place the Chiseled Enchanting Table block.
- Open the custom table UI and verify `MenuScreens.register(...)` runtime behavior.
- Validate UI rendering, slot positions, scroll list behavior, mouse/keyboard input, and packet send/receive behavior.
- Validate enchantment application, item cost consumption, XP consumption, and enchanted-book conversion.
- Validate nearby chiseled-bookshelf scanning/discovery semantics.
- Validate loot generation, structure bookshelf replacement, and upstream mixin behavior after those systems are restored.
- Validate multiplayer synchronization beyond compile-level payload registration.

## Notes

- `docs/JSON_EXTENSION_SPEC_1_1_3.md` and `docs/EXTENDING_HERBCRAFT.md` are missing from this repository. The current documentation index says these are not native to this specific mod repository.
- Per architecture rules, future cost/rule/compatibility content should be moved toward JSON/data-driven configuration where feasible instead of expanding hardcoded Java tables.

---

## Refresh: 2026-07-01 Environment-Setup-Only Round

**Goal this round:** Per the user, "理解报告输出，完成环境搭建" — understand the existing report
output and stand the build environment up. No source/resource changes are required.

### Requested Startup Sequence (re-run today)

1. Read root `AGENT_BRIEF.md`.
   - Result: still absent in this repo snapshot.
2. Execute `python3 scripts/generate_agent_brief.py --write` if runnable.
   - Result: **still not runnable**. The `scripts/` directory exists now but only contains
     `compat_check.py`; there is no `generate_agent_brief.py`. Treat this step as a no-op
     for this repo (this matches the existing note above the "Refresh" header).
3. Read `docs/reports/CURRENT_AGENT_BRIEF.md` (this file).
4. Read, in order:
   - `CURRENT_BASELINE.md` ✅
   - `docs/DOCUMENTATION_INDEX.md` ✅
   - `NEXT_TASK.md` ✅
   - `docs/JSON_EXTENSION_SPEC_1_1_3.md` — **absent** (per the index, this mod's equivalents are
     `docs/EXTENDING.md` and `docs/COMPATIBILITY.md`, which were read instead)
   - `docs/EXTENDING_HERBCRAFT.md` — **absent** (same equivalence note)
   - `CHANGELOG.md` ✅

### Environment Bring-up Executed

- Cloned `q14433686-arch/chiseled-enchanting-table-26.1.2-source` (single `Initial commit`).
- The sandbox baseline image only had JDK 11; ran `apt-get install -y openjdk-25-jdk-headless`,
  which installs `/usr/lib/jvm/java-25-openjdk-amd64` (Java/Javac 25.0.3).
- Restored `+x` on `./gradlew`.
- Gradle wrapper resolves to 9.4.1; Fabric Loom resolves to 1.16.3 (matches baseline).
- All listed Gradle commands in the original P7-round brief were re-run on the current `p18`
  source and all returned **BUILD SUCCESSFUL** (see "Validation" below).
- `python3 scripts/compat_check.py` returns `PASS (2 warning(s))` — the two warnings are the
  expected "MC jar / extra-jar not supplied, so skipping Tier 2 id existence" notices.
- Built artefacts:
  - `build/libs/chiseled-enchanting-table-0.2.3+26.1.2-p18.jar` (130 071 bytes)
  - `build/libs/chiseled-enchanting-table-0.2.3+26.1.2-p18-sources.jar` (71 491 bytes)
- The repo snapshot did not ship a `release_output/` directory; recreated
  `release_output/chiseled_enchanting_table_26_1_2_p18/` and staged the two jars plus a
  `chiseled-enchanting-table-26.1.2-p18-source.zip` produced from `git archive HEAD`.

### Validation Checklist Executed This Round

- [x] Java 25 verification (`/usr/lib/jvm/java-25-openjdk-amd64/bin/java -version` -> `25.0.3`)
- [x] Java compiler verification (`javac -version` -> `25.0.3`)
- [x] Gradle wrapper verification (`./gradlew --version` -> Gradle `9.4.1`, Loom `1.16.3`)
- [x] Dependency/classpath check (`dependencies --configuration compileClasspath`)
- [x] Resource processing (`processResources`)
- [x] Main/client compilation (`compileJava compileClientJava`)
- [x] Full build (`build --rerun-tasks`)
- [x] Jar artefact presence check under `build/libs/`
- [x] `python3 scripts/compat_check.py` (Tier 1 PASS; Tier 2 intentionally skipped — no MC jar)
- [x] Release output refresh under `release_output/chiseled_enchanting_table_26_1_2_p18/`
- [x] Doc refresh: `CURRENT_BASELINE.md`, `NEXT_TASK.md`, `CHANGELOG.md`, this brief

### Client-Only / Live-Game Validation Still Pending

The same client-only items previously listed are still pending — they cannot be performed in a
headless sandbox and need a real Minecraft 26.1.2 client with Fabric Loader 0.19.3 +
Fabric API 0.151.0+26.1.2:

- Boot Minecraft and confirm mod metadata loads (with `"mixins": []`).
- Confirm custom block registration in a live world; place the Chiseled Enchanting Table.
- Open the custom table UI: `MenuScreens.register(...)` runtime behaviour, slot layout, scroll
  list, floating-book renderer, row hover/click, scrollbar drag.
- Apply enchantments and transformations: XP and item-cost consumption, enchanted-book conversion,
  multi-item cost rules.
- Nearby chiseled-bookshelf scanning / discovery; book contribution to available options.
- P19 cross-mod check: with `#minecraft:non_treasure` (default) and with a third-party enchantment
  mod that tags its own enchantments — confirm those modded enchantments now show in the default
  option list (the actual new behaviour added by P19).
- Multiplayer sync beyond compile-level payload registration; loot-table drop on mining (P16 fix)
  in a live world.

### Validation Commands Used This Round

```bash
sudo apt-get install -y openjdk-25-jdk-headless
chmod +x ./gradlew
python3 scripts/compat_check.py
JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 ./gradlew --no-daemon --max-workers=1 \
    dependencies --configuration compileClasspath
JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 ./gradlew --no-daemon --max-workers=1 \
    processResources --stacktrace
JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 ./gradlew --no-daemon --max-workers=1 \
    -Dorg.gradle.jvmargs='-Xmx1024m -Dfile.encoding=UTF-8' compileJava compileClientJava --stacktrace
JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 ./gradlew --no-daemon --max-workers=1 \
    -Dorg.gradle.jvmargs='-Xmx1024m -Dfile.encoding=UTF-8' build --rerun-tasks --stacktrace
```

All five Gradle commands completed with **BUILD SUCCESSFUL**.

---

## Refresh: 2026-07-01 — 1.0.2 Rename / Rebrand Round

**Unofficial community port.** This refresh records the rename of the unofficial 26.1.2 port from
the internal `0.2.3+26.1.2-pNN` versioning to a clean SemVer line starting at **`1.0.2`** and the
addition of explicit "unofficial port" labelling across the user-facing surface.

### Source-level changes this round

- `gradle.properties`: `mod_version=0.2.3+26.1.2-p18` -> `mod_version=1.0.2`.
- `src/main/resources/fabric.mod.json`:
  - `name` now `Chiseled Enchanting Table (Unofficial 26.1.2 Port)`.
  - `description` rewritten to state the unofficial-port status.
  - `authors` now lists both the original author and the unofficial-port maintainer.
  - `contact.sources` -> this port's GitHub; `contact.homepage` -> upstream.
  - `custom.chiseled_enchanting_table:port_notes` rewritten to describe the unofficial-port
    status and current mixin/loot/structure boundary instead of the stale "P3 temporary" note.
- `.github/workflows/publish.yml`: `MINECRAFT_VERSION=26.1.2`, `JAVA_VERSION=25`, `VERSION=1.0.2`,
  `RELEASE_NAME=Chiseled Enchanting Table 1.0.2 (Unofficial 26.1.2 Port)`.
- `README.md`: title prefixed with the unofficial-port marker; new prominent warning block; old
  "Modrinth (coming soon)" replaced with an unofficial-port-specific install note.
- `CURRENT_BASELINE.md`: full rewrite to reflect the actual current-source-truth snapshot at
  `1.0.2` (build config, packages, JSON data, tooling, boundaries, this-session validation).
- `NEXT_TASK.md`: relabelled to "1.0.2 live verification + multi-slot cost panel decision";
  keeps the P19 cross-mod live-check steps that are still pending.
- `CHANGELOG.md`: new top-of-file `[1.0.2] - 2026-07-01` section + an unofficial-port banner;
  the P1-P19 history is preserved verbatim under a "Historical Internal Pre-1.0.2 Rounds"
  heading.
- `docs/DOCUMENTATION_INDEX.md`: unofficial-port banner at top; relabelled the porting-reports
  section as "historical pre-1.0.2 internal rounds".

### Files intentionally NOT changed

- `docs/reports/PORTING_26_1_2_PNN_*.md` — these are historical records of how the port was built
  and intentionally keep their `0.2.3+26.1.2-pNN` strings as a true account. Their filenames are
  also kept; the README/index/baseline make clear that the labels are historical only.
- All Java source / JSON data / loot tables / assets — no behaviour change in this round.

### Validators run this round

- `python3 scripts/compat_check.py` -> `PASS (0 warning(s))` (Tier 1 only).
- `./gradlew build --rerun-tasks` -> BUILD SUCCESSFUL.
- Jar metadata spot check on `build/libs/chiseled-enchanting-table-1.0.2.jar`:
  - `version` field = `1.0.2`.
  - `name` field = `Chiseled Enchanting Table (Unofficial 26.1.2 Port)`.
  - `authors` includes both original author and unofficial-port maintainer.
  - `depends.minecraft` = `26.1.2`, `depends.java` = `>=25`, `mixins` = `[]`.

### Release output refreshed

- Removed stale `release_output/chiseled_enchanting_table_26_1_2_p18/`.
- Created `release_output/chiseled_enchanting_table_1_0_2/`:
  - `chiseled-enchanting-table-1.0.2.jar`
  - `chiseled-enchanting-table-1.0.2-sources.jar`
  - `chiseled-enchanting-table-1.0.2-source.zip` (working-tree zip; excludes `.git/`, `.gradle/`,
    `build/`, `release_output/`).

### Client-only / live-game validation still pending

Same as the prior round — the rename/rebrand is a no-behaviour change so the existing
pending-live-checks list (mod metadata load, block placement, menu open, scroll, floating book
render, enchanting/transformation apply, bookshelf scanning, P19 cross-mod default unlocks with a
third-party enchantment mod, multiplayer sync, mining drop in a live world) still applies.
---

## Refresh: 2026-07-01 — Environment Setup Revalidated from Fresh Clone

**Goal this round:** 理解报告输出，完成环境搭建 — understand the current report output and bring up
the build environment. No Java source files, gameplay JSON files, assets, or metadata files were
changed. Documentation and release-output staging were refreshed to record the completed setup.

### Requested Startup Sequence Re-run

1. Read root `AGENT_BRIEF.md`.
   - Result: **absent** in this repository snapshot.
2. Run `python3 scripts/generate_agent_brief.py --write` if runnable.
   - Result: **not runnable**; `scripts/` exists but contains `compat_check.py` and
     `import_upstream_loot.py`, not `generate_agent_brief.py`.
3. Read `docs/reports/CURRENT_AGENT_BRIEF.md`.
4. Read, in order:
   - `CURRENT_BASELINE.md` ✅
   - `docs/DOCUMENTATION_INDEX.md` ✅
   - `NEXT_TASK.md` ✅
   - `docs/JSON_EXTENSION_SPEC_1_1_3.md` — absent
   - `docs/EXTENDING_HERBCRAFT.md` — absent; `docs/EXTENDING.md` is the available extension doc
   - `CHANGELOG.md` ✅

### Environment Bring-up Executed

- Cloned `q14433686-arch/chiseled-enchanting-table-26.1.2-source` at `43c2dad` (`Initial commit`).
- Confirmed the sandbox default Java was JDK 11, which is insufficient because `build.gradle` uses
  Java 25 (`options.release = 25`).
- Tried system `apt-get install -y openjdk-25-jdk-headless`; it failed with permission denied on
  `/var/lib/apt/lists/lock`, so the environment was completed with a user-space JDK instead.
- Downloaded/extracted Oracle JDK 25.0.3 Linux x64 under `/home/user/jdks/jdk-25` and used
  `JAVA_HOME=/home/user/jdks/jdk-25` for Gradle.
- Restored executable permission on `./gradlew`.
- Verified Gradle wrapper `9.4.1` and Fabric Loom `1.16.3`.
- Built artefacts:
  - `build/libs/chiseled-enchanting-table-1.0.2.jar` (182,870 bytes)
  - `build/libs/chiseled-enchanting-table-1.0.2-sources.jar` (117,583 bytes)
- Recreated `release_output/chiseled_enchanting_table_1_0_2/` with both jars, a refreshed working-tree
  source zip, `cover-icon-256.png`, and `cover-banner.png`.

### Validation Checklist Executed This Round

- [x] Java runtime verification: `/home/user/jdks/jdk-25/bin/java -version` -> `25.0.3`
- [x] Java compiler verification: `/home/user/jdks/jdk-25/bin/javac -version` -> `25.0.3`
- [x] Gradle wrapper verification: `./gradlew --version` -> Gradle `9.4.1`, Loom `1.16.3`
- [x] Dependency/classpath check: `dependencies --configuration compileClasspath` -> BUILD SUCCESSFUL
- [x] Resource processing: `processResources --stacktrace` -> BUILD SUCCESSFUL
- [x] Main/client compilation: `compileJava compileClientJava --stacktrace` -> BUILD SUCCESSFUL
- [x] Data/schema validator: `python3 scripts/compat_check.py` -> `PASS (0 warning(s))`
- [x] Full build: `build --rerun-tasks --stacktrace` -> BUILD SUCCESSFUL
- [x] Jar artefact presence check under `build/libs/`
- [x] Jar metadata/resource spot check (`fabric.mod.json`, port mixin config, key JSON resources, client class)
- [x] Release output refresh under `release_output/chiseled_enchanting_table_1_0_2/`
- [x] Documentation refresh: `CURRENT_BASELINE.md`, `NEXT_TASK.md`, `CHANGELOG.md`, this brief

### Validation Commands Used This Round

```bash
chmod +x ./gradlew
/home/user/jdks/jdk-25/bin/java -version
/home/user/jdks/jdk-25/bin/javac -version
JAVA_HOME=/home/user/jdks/jdk-25 ./gradlew --no-daemon --max-workers=1 --version
JAVA_HOME=/home/user/jdks/jdk-25 ./gradlew --no-daemon --max-workers=1 dependencies --configuration compileClasspath
JAVA_HOME=/home/user/jdks/jdk-25 ./gradlew --no-daemon --max-workers=1 processResources --stacktrace
JAVA_HOME=/home/user/jdks/jdk-25 ./gradlew --no-daemon --max-workers=1 \
    -Dorg.gradle.jvmargs='-Xmx1536m -Dfile.encoding=UTF-8' compileJava compileClientJava --stacktrace
python3 scripts/compat_check.py
JAVA_HOME=/home/user/jdks/jdk-25 ./gradlew --no-daemon --max-workers=1 \
    -Dorg.gradle.jvmargs='-Xmx1536m -Dfile.encoding=UTF-8' build --rerun-tasks --stacktrace
```

### Client-Only / Live-Game Validation Still Pending

These remain outside a headless sandbox and must be checked in a real Minecraft 26.1.2 client with
Fabric Loader 0.19.3 and Fabric API 0.151.0+26.1.2:

- Mod metadata load and mixin config runtime behavior.
- Custom block registration, placement, and drop behavior in a live world.
- Custom menu open, screen registration, slot layout, scroll list, row hover/click, scrollbar drag,
  and floating-book renderer.
- Enchantment / transformation application, XP-point deduction, cost-slot and additional-cost
  consumption, enchanted-book conversion.
- Nearby chiseled-bookshelf scanning and contribution to available options.
- Structure bookshelf replacement/filling in villages, mansions, strongholds, and ancient cities.
- Loot injection outcomes in real generated chests/archaeology tables.
- P19 cross-mod tag behavior with a third-party enchantment mod.
- Multiplayer sync beyond compile-level payload registration.

---

## Refresh: 2026-07-01 — 1.0.2 Upstream Balance Mixin Restore

**Goal this round:** user approved starting with the upstream balance backlog. Implemented the two
highest-priority gameplay rules identified in the prior analysis: vanilla enchanting-table book
blocking and narrow enchanted-book same-level anvil merge blocking. Chiseled-bookshelf particle /
sound remains the next likely upstream parity item.

### Source/resource changes

- `src/main/java/chiseled_enchanting_table/mixin/enchanting_table/DisableBookEnchanting.java`
  - Remapped from the old Yarn-era `EnchantmentScreenHandler/onContentChanged` shape to
    26.1.2 Mojmap `EnchantmentMenu.slotsChanged(Container)`.
  - When the vanilla enchanting table's input slot contains `Items.BOOK`, clears all 3 offer costs
    and clue arrays, broadcasts the zeroed data, and cancels vanilla offer generation.
- `src/main/java/chiseled_enchanting_table/mixin/anvil/RemoveEnchantMerge.java`
  - Remapped from the old broad iterator redirect to a safer tail injection on
    `AnvilMenu.createResult()`.
  - Clears the anvil output only when both inputs are `ENCHANTED_BOOK`s and an overlapping
    enchantment has the same non-max level on both books. This blocks book+book same-level upgrades
    without changing normal item+book transfer behaviour.
- `build.gradle`
  - Re-enabled compilation of the restored enchanting-table mixin and narrow anvil merge mixin.
  - Kept `RemoveXpCostScaling`, chiseled-bookshelf random tick, accessor, integration, and legacy
    superseded packages parked.
- `src/main/resources/chiseled-enchanting-table-port.mixins.json`
  - Switched package root to `chiseled_enchanting_table.mixin` and declared 7 active mixins:
    5 structure mixins + `enchanting_table.DisableBookEnchanting` + `anvil.RemoveEnchantMerge`.
- Version / release metadata bumped to `1.0.2` in `gradle.properties`, publish workflow, and
  `fabric.mod.json` notes.

### Validation checklist executed this round

- [x] `JAVA_HOME=/home/user/jdks/jdk-25 ./gradlew --no-daemon --max-workers=1 -Dorg.gradle.jvmargs='-Xmx1536m -Dfile.encoding=UTF-8' compileJava processResources --stacktrace` -> BUILD SUCCESSFUL
- [x] `JAVA_HOME=/home/user/jdks/jdk-25 ./gradlew --no-daemon --max-workers=1 -Dorg.gradle.jvmargs='-Xmx1536m -Dfile.encoding=UTF-8' compileJava compileClientJava --stacktrace` -> BUILD SUCCESSFUL
- [x] `python3 scripts/compat_check.py` -> `PASS (0 warning(s))`
- [x] `JAVA_HOME=/home/user/jdks/jdk-25 ./gradlew --no-daemon --max-workers=1 -Dorg.gradle.jvmargs='-Xmx1536m -Dfile.encoding=UTF-8' build --rerun-tasks --stacktrace` -> BUILD SUCCESSFUL
- [x] Jar artefact presence check under `build/libs/`
- [x] Jar metadata/resource spot check: `fabric.mod.json` version `1.0.2`, active port mixin config,
  restored mixin class files, key JSON resources, client class
- [x] Release output refresh under `release_output/chiseled_enchanting_table_1_0_2/`
- [x] Documentation refresh: `CURRENT_BASELINE.md`, `NEXT_TASK.md`, `CHANGELOG.md`,
  `docs/DOCUMENTATION_INDEX.md`, this brief

### Client-only / live-game validation still pending

The following cannot be confirmed in the headless sandbox:

- Boot with Fabric Loader 0.19.3 + Fabric API 0.151.0+26.1.2 and confirm the updated
  `chiseled-enchanting-table-port.mixins.json` applies cleanly.
- Vanilla enchanting table: plain `BOOK` should show no offers; normal enchantable items should
  still show offers after swapping back.
- Anvil: equal-level enchanted book + equal-level enchanted book should produce no upgraded book;
  different-level / different-enchantment combinations should keep vanilla behaviour.
- All previously listed live-game UI, structure, loot, bookshelf scanning, multiplayer, and drop
  checks remain pending.

---

## Refresh: 2026-07-01 — 1.0.2 Chiseled-Bookshelf Ambience Restore

**Goal this round:** continue upstream parity after 1.0.2 by restoring the enchanted-book chiseled
bookshelf random-tick particle/sound effect. No JSON gameplay tables were changed.

### Source/resource changes

- `src/main/java/chiseled_enchanting_table/block/ChiseledBookshelfTick.java`
  - Remapped from old names (`ServerWorld`, `Random`, `Properties`, `net.minecraft.sound.*`) to
    26.1.2 Mojmap names (`ServerLevel`, `RandomSource`, `ChiseledBookShelfBlock.FACING`,
    `SoundEvents`, `SoundSource`).
  - Samples one of the 6 shelf slots on each random tick. If the sampled slot contains an
    `ENCHANTED_BOOK`, emits a quiet `ENCHANTMENT_TABLE_USE` sound and 3 `ENCHANT` particles from
    the matching front-face slot area.
  - Fixed the old coordinate typo (`dz` used the X offset); the new code uses `Direction` X/Z steps
    and a per-column right-vector offset.
- `src/main/java/chiseled_enchanting_table/mixin/chiseled_bookshelf/AddRandomTick.java`
  - Remapped against `ChiseledBookShelfBlock` and adds random ticking + random-tick delegation to
    `ChiseledBookshelfTick`.
- `build.gradle`
  - Re-enabled `mixin/chiseled_bookshelf/**` and `block/ChiseledBookshelfTick.java` compilation.
  - Still keeps `RemoveXpCostScaling`, accessor, integration, and legacy superseded packages parked.
- `src/main/resources/chiseled-enchanting-table-port.mixins.json`
  - Added `chiseled_bookshelf.AddRandomTick`; active port mixin count is now 8.
- Version / release metadata bumped to `1.0.2`.

### Validation checklist executed this round

- [x] Recreated Java 25 user-space toolchain at `/home/user/jdks/jdk-25` because cached toolchains
  are not persisted across turns.
- [x] `JAVA_HOME=/home/user/jdks/jdk-25 ./gradlew --no-daemon --max-workers=1 -Dorg.gradle.jvmargs='-Xmx1536m -Dfile.encoding=UTF-8' compileJava --stacktrace` -> BUILD SUCCESSFUL
- [x] `JAVA_HOME=/home/user/jdks/jdk-25 ./gradlew --no-daemon --max-workers=1 -Dorg.gradle.jvmargs='-Xmx1536m -Dfile.encoding=UTF-8' compileJava compileClientJava --stacktrace` -> BUILD SUCCESSFUL
- [x] `python3 scripts/compat_check.py` -> `PASS (0 warning(s))`
- [x] `JAVA_HOME=/home/user/jdks/jdk-25 ./gradlew --no-daemon --max-workers=1 -Dorg.gradle.jvmargs='-Xmx1536m -Dfile.encoding=UTF-8' build --rerun-tasks --stacktrace` -> BUILD SUCCESSFUL
- [x] Jar artefact presence check under `build/libs/`
- [x] Jar metadata/resource spot check: `fabric.mod.json` version `1.0.2`, active port mixin config,
  restored chiseled-bookshelf mixin/helper class files, key JSON resources, client class
- [x] Release output refresh under `release_output/chiseled_enchanting_table_1_0_2/`
- [x] Documentation refresh: `CURRENT_BASELINE.md`, `NEXT_TASK.md`, `CHANGELOG.md`,
  `docs/DOCUMENTATION_INDEX.md`, this brief, README/marketing version strings

### Client-only / live-game validation still pending

The following cannot be confirmed in the headless sandbox:

- Boot with Fabric Loader 0.19.3 + Fabric API 0.151.0+26.1.2 and confirm all 8 active mixins apply
  cleanly.
- Chiseled bookshelf with enchanted books: occasional enchant particles from slot area + quiet
  enchantment-table sound.
- Chiseled bookshelf with only plain books / empty slots: no ambient enchanted effect.
- Previously listed live checks for vanilla enchanting-table book blocking, anvil book+book same
  level merge blocking, UI, structure, loot, bookshelf scanning, multiplayer, and drops remain
  pending.

---

## Refresh: 2026-07-01 — 1.0.2 Chiseled-Bookshelf Particle Visibility Fix

User live observation after 1.0.2: shelf ambience sound works, but particles are not visible. This
confirms `AddRandomTick` is applying and the helper is called, so the issue was the particle packet
shape / visual subtlety rather than random ticking.

### Change

- `ChiseledBookshelfTick` now sends several exact `ParticleTypes.ENCHANT` particles with a non-zero
  outward vector from the shelf face instead of a small generic count-spread packet.
- Added a small `ParticleTypes.ENCHANTED_HIT` sparkle fallback so the cue remains visible even when
  ENCHANT particles are very subtle.
- Version / docs / release staging bumped to `1.0.2`.

### Validation

- [x] `compileJava --stacktrace` -> BUILD SUCCESSFUL
- [x] `compileJava compileClientJava --stacktrace` -> BUILD SUCCESSFUL
- [x] `python3 scripts/compat_check.py` -> PASS
- [x] `build --rerun-tasks --stacktrace` -> BUILD SUCCESSFUL
- [x] Jar metadata/resource spot check
- [x] Release output refresh under `release_output/chiseled_enchanting_table_1_0_2/`

### Still needs client confirmation

- Chiseled bookshelf containing enchanted books now shows visible particles as well as sound.
- Plain-only / empty chiseled bookshelves do not show the enchanted-book ambience.

---

## Refresh: 2026-07-01 — 1.0.2 Book Upgrade Routes

Implemented the two requested low-level enchanted-book upgrade routes in the Chiseled Enchanting
Table:

- `BOOK_COMBINE_UPGRADE`: anvil-like enchanted-book + matching enchanted-book upgrade, hosted by the
  mod table instead of vanilla anvil.
- `BOOK_RESEARCH_UPGRADE`: one enchanted book + themed material + XP, with costs defined by
  `book_upgrades.json` and intentionally discounted versus direct specified enchanting.

Validation:

- [x] JSON syntax validation for `book_upgrades.json`, lang files, `fabric.mod.json`
- [x] `compileJava compileClientJava` -> BUILD SUCCESSFUL
- [x] `python3 scripts/compat_check.py` -> PASS (0 warnings)
- [x] `build --rerun-tasks` -> BUILD SUCCESSFUL
- [x] Release output refreshed under `release_output/chiseled_enchanting_table_1_0_2/`

Client-only checks pending: confirm both row types appear with correct localized labels and that
combine/research upgrade consumption/output behavior matches expectations in-game.

---

## Refresh: 2026-07-01 — 1.0.2 Combine Upgrade Cost-Book Consumption Fix

User live-tested 1.0.2 and reported that book+book upgrade did not visibly consume the lower/cost
book into a plain book. Fixed `BookUpgradeApplication.applyCombine` to explicitly set the cost slot
to `costBook.transmuteCopy(Items.BOOK, 1)` after the upgrade.

Validation:
- [x] `compileJava compileClientJava` -> BUILD SUCCESSFUL
- [x] `python3 scripts/compat_check.py` -> PASS (0 warnings)
- [x] `build --rerun-tasks` -> BUILD SUCCESSFUL
- [x] Release output refreshed under `release_output/chiseled_enchanting_table_1_0_2/`

Client check still pending: combine upgrade should leave a plain book in the lower/cost slot.


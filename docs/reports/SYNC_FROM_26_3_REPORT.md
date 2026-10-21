# Sync report: 26.3 branch → 26.1.2 branch (mod 1.0.3 on this line)

> **Scope.** Back-port of every *version-independent* change and fix that landed on this
> repository's `26.3` branch after the shared `1.0.2` baseline, adapted to Minecraft **26.1.2**
> (Fabric Loader 0.19.3, Fabric API `0.151.0+26.1.2`, Loom `1.16-SNAPSHOT`, Gradle wrapper 9.4.1,
> Java 25). Written 2026-10-21 on branch `arena/3ae35b5e-chiseled-enchanting-table-26-1`.
>
> **Not in scope:** the Minecraft 26.3 port itself (`1.0.3` on that branch). Everything that exists
> only because 26.3 changed an API or a data schema is listed in §3 with the reason it was *not*
> taken.

## 1. Method — why this is a file-level port, not a merge

The three branches of this repository (`chiseled-enchanting-table-26.1.2-source`,
`chiseled-enchanting-table-26.2-source`, `26.3`) have **no common ancestor**: each is an independent
"Initial commit" snapshot, so `git merge-base HEAD origin/26.3` returns nothing and neither
`cherry-pick` nor `merge` can work.

The sync was therefore done per change, in this order:

1. `git fetch` the `26.3` and `chiseled-enchanting-table-26.2-source` branches and diff the trees.
2. Apply the 26.3 branch's own **pre-adapted** back-port patch
   `docs/patches/26.1.2-worldgen-bookshelf-deadlock.patch` (sha256
   `751b27f81e7e1db1424335453414abb77da2bf493dbbe171efdc6ed6a9040344`, `git apply --check -p1`
   clean against this branch's base commit `79717b5`).
3. Port the remaining changes file by file, keeping the 26.3 sources **byte-identical wherever the
   code is version-independent** (all of `compat/`, both viewer adapters, the cost-sync payload, the
   11 language files) and rewriting only what touches a 26.3-only API or schema.
4. Re-run every offline gate, then let `compile-check.yml` do the compile verification (§7).

## 2. What was synced

| # | Change (26.3 commit) | This branch | Adaptation |
| --- | --- | --- | --- |
| 1 | **Worldgen bookshelf deadlock fix** (`55a62fc`, + `6a8efac`) — [issue #3 "Chuck soft crashes"](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/issues/3) | applied via the 26.1.2 patch: `BookshelfBookFiller`, `BookshelfReplacerProcessor`, `LevelChunkPostProcessMixin`, `docs/reports/WORLDGEN_BOOKSHELF_DEADLOCK_REPORT.md` | the patch is the 26.3 branch's own 26.1.2 adaptation: `StructureProcessor` is still an **abstract class** here (`getType()`, `processBlock(..., StructureBlockInfo originalBlockInfo, ...)`), `ChunkAccess#markPosForPostprocessing` keeps the lower-case spelling, and `processBlockInfos` is **not** clipped to the generating chunk (the deferred facing pass skips out-of-region positions) |
| 2 | **Translatable book lore + 9 more languages** (`f55cc8f`) | `BookshelfBookFiller.LORE` → `Component.translatable("book.chiseled_enchanting_table.lore")`; `de_de`, `es_es`, `fr_fr`, `it_it`, `ja_jp`, `ko_kr`, `pt_br`, `ru_ru`, `zh_tw` added; `en_us`/`zh_cn` extended | none needed — language files are version-independent. **Extended** to the second copy of the same stamp, see §4.1 |
| 3 | **Server-authoritative enchantment costs (S2C)** (`6e10544`) | `menu/EnchantCostTablePayload`, `menu/EnchantCostSync`, `compat/SyncedEnchantmentCosts`, `ChiseledEnchantingTable#onInitialize`, `ChiseledEnchantingTableClient`, and the four cost getters in `ChiseledEnchantingTableScreenHandler` | none — the cost-resolution block of the handler is now **byte-identical** to the 26.3 branch (`diff` of the `---- Cost resolution ----` region is empty). 26.1.2 still has `FriendlyByteBuf#writeCollection`, so `ChiseledEnchantingMenuPayloads` keeps using it; the new payload was hand-rolled on the 26.3 side anyway |
| 4 | **JEI integration** (`9da6787`, `1b38fcc`) | `compat/EnchantQueryIndex` (main), `compat/jei/*` (client), `build.gradle`, `gradle.properties`, `fabric.mod.json` (`jei_mod_plugin` + `suggests`) | coordinate only: `mezz.jei:jei-${minecraft_version}-fabric` resolves to **`jei-26.1.2-fabric`** here (JEI's `26.1` branch pins `minecraftVersion=26.1.2` and names the publication `"${modId}-${minecraftVersion}-fabric"`). Adapter sources unchanged — JEI's API files were diffed between its `26.1` and `26.3` branches: 5 of 10 byte-identical, the other 5 differ **only** in `@since` javadoc numbers (`docs/reports/VIEWER_INTEGRATION_JEI_REI.md` §3.1). `jei_version` is **pinned** to `29.47.0.111` (what `latest.release` resolved to in CI) rather than left floating — §8 finding 8 |
| 5 | **REI integration** (`aa79aee`, `004433b`, `ae1fed5`) | `compat/rei/*` (client), `rei_client` entrypoint, the three compile-only deps | coordinates/versions only: `rei_version=26.1.+` (REI's `26.1` branch: `base_version=26.1`, `minecraft_version=26.1.2`), `rei_architectury_version=20.0.6` (REI 26.1's own `architectury_version`; 26.3 uses 22.0.3), `rei_math_version=0.6.1` (Minecraft-independent; CI confirmed the coordinate exists). All **eleven** REI API files the adapter touches are byte-identical between REI's `26.1` and `26.3` branches, as are `api/build.gradle` and `settings.gradle` (§4 of the same report). `rei_version` is pinned to `26.1.819` — §8 finding 8. **All four viewer dependencies carry toolchain exclusions** that the 26.3 branch does not need — §8 finding 7 |
| 6 | **CI triad** (`78c5582`, `d110449`, maintainer uploads `a918113`/`d4f34aa`) | `.github/workflows/{build,compile-check,consistency,publish}.yml` + byte-identical `docs/ci/` copies + `docs/ci/install.sh` + `docs/ci/README.md` + `.githooks/pre-commit` + `scripts/install-hooks.sh` + `scripts/ci_static_checks.py` + `scripts/check_release_consistency.sh` | branch filters (`arena/**`, `chiseled-enchanting-table-26.1.2-source`, `main` — the 26.3 copies' `26.**` entry is meaningless on a branch that runs its own copy, so it was dropped); `publish.yml` env `26.1.2` / `1.0.3`; lang-parity gate widened (§4.2); one extra CI job (§4.3). Details: [`../ci/README.md`](../ci/README.md) |
| 7 | **Data-pack schema gates** in `scripts/compat_check.py` (`2f4be58`) | two new Tier-1 gates + the `iter_data_json` helper | **inverted on purpose** (§4.4): they now enforce the *26.1.2* schema and fail on the 26.3 form |
| 8 | **Advancement `craft.json` fix** (`2f4be58`) | `display.background` removed from the child advancement; `show_toast` / `announce_to_chat` moved into `display` | `recipe_id` **kept** (26.3 renamed it to a required `recipes` list — that rename is a 26.3 schema change, not a fix). The two halves that *were* taken are version-independent: top-level `show_toast`/`announce_to_chat` are ignored by `Advancement.CODEC` on every modern line, and a child `display.background` is ignored here but is a **hard world-load error** in 26.3, so removing it makes this branch's data forward-compatible |
| 9 | **`gradlew` executable bit** (`2f4be58`) | `100644` → `100755` (`git ls-files -s gradlew`) | none — `compile-check.yml` calls `./gradlew` without a `chmod` step |
| 10 | **`.gitignore` python entries** | `__pycache__/`, `*.pyc` | none |
| 11 | **Dead `RenderPipeline` import** in `ChiseledEnchantingTableScreen` | removed | none — the import was unused on both lines (26.3 *had* to remove it because the package moved; here it is pure cleanup) |
| 12 | **Docs** | `docs/EXTENDING.md` (the `BookshelfBookFiller` two-halves entry-point split), `docs/DOCUMENTATION_INDEX.md`, `CHANGELOG.md`, `CURRENT_BASELINE.md`, `NEXT_TASK.md`, `README.md`, `docs/reports/VIEWER_INTEGRATION_JEI_REI.md`, `docs/reports/WORLDGEN_BOOKSHELF_DEADLOCK_REPORT.md`, this report | rewritten for this line instead of copied: the 26.3 texts describe 26.3 APIs and a 26.3 toolchain |

**Not synced:** `docs/patches/*` (this branch is that patch's *target*, so shipping the patch here
would be dead weight — it is applied), and `docs/reports/PORTING_26_2_REPORT.md` /
`PORTING_26_3_REPORT.md` (they document other Minecraft lines and would read as stale "current"
documents here; both are referenced from this report and from `docs/ci/README.md` instead).

## 3. What was deliberately NOT synced (26.3-only)

Every one of these is a *port to a newer Minecraft*, not a fix. Taking any of them would break this
branch's compile or its data loading. Left-hand side = the 26.3 branch, right-hand side = what stays
here.

| Area | 26.3 | 26.1.2 (kept) |
| --- | --- | --- |
| Block codec | per-block `MapCodec` removed; `Block.simpleCodec` gone | `CODEC` + `codec()` override stay |
| Item drops | `LivingEntity#drop(ItemStack, boolean, Prediction)` | `drop(ItemStack, boolean)` — both call sites (`InventoryUtils.insertItem`, the menu's overflow path) |
| Renderer rotation | `PoseStack#rotate(Axis, rad)` / `rotateDegrees(Axis, deg)` | `PoseStack#mulPose(Quaternionf)` |
| Renderer submit | 9-arg `submitModel` + separate `order(1).submitCrumblingOverlay(...)` | 11-arg `submitModel(..., breakProgress)` |
| Renderer import | `OverlayTexture` imported, dead `com.mojang.blaze3d.pipeline.RenderPipeline` gone (package moved to `com.mojang.renderpearl.api.pipeline`) | import cleanup taken, package move N/A |
| Loot builders | `ContextIntProviders.exactly`, `UniformContainerBase.Builder` | `ConstantValue.exactly`, `LootPoolSingletonContainer.Builder` |
| Conditional loot functions | `LootItemConditionalFunction(Optional<Holder<LootItemCondition>>)` | `(List<LootItemCondition>)` |
| Networking | `FriendlyByteBuf` collection helpers removed → hand-rolled VarInt loops in `ChiseledEnchantingMenuPayloads` | `writeCollection` / `readCollection` / `readList` still exist and are still used |
| Structure processors | `StructureProcessor` is an **interface** with `codec()`; `StructureProcessorType` lost its generic + `register(String, MapCodec)`; registration goes through `Registry.register(BuiltInRegistries.STRUCTURE_PROCESSOR, …)` | abstract class + `getType()` + `StructureProcessorType.register(...)` (both `BookshelfReplacerProcessor` and `StructureProcessorRegistry`) |
| Chunk post-processing mark | `ChunkAccess#markPosForPostProcessing` | `ChunkAccess#markPosForPostprocessing` |
| `processBlockInfos` clipping | clipped to the generating chunk | not clipped → the facing pass skips out-of-region positions |
| Block loot table schema | singular `condition` / `modifier`, dispatched on `type` | plural `conditions[]` / `functions[]`, dispatched on `condition` / `function` |
| `recipe_crafted` criteria | required `recipes` list | `recipe_id` string |
| Toolchain | Loom `1.17-SNAPSHOT`, Gradle wrapper 9.5.1, loader 0.19.5, Fabric API `0.162.0+26.3` | Loom `1.16-SNAPSHOT`, wrapper 9.4.1, loader 0.19.3, Fabric API `0.151.0+26.1.2` — the toolchain the 1.0.2 build was verified with. Nothing in this sync needs a newer one, and the dev sandbox has no maven access, so a blind bump would trade a verified build for an unverified one |
| `fabric.mod.json` | 26.3 identity strings, `depends.fabricloader >=0.19.5`, 2-space indentation | 26.1.2 identity strings, `>=0.19.3`, existing tab indentation (only the two entrypoints + `suggests` + `port_notes` were touched, to keep this branch's diff reviewable) |

## 4. Where this branch goes beyond a straight copy

### 4.1 The lore stamp existed twice; the 26.3 fix only converted one

`f55cc8f` converted `BookshelfBookFiller.LORE` (structure-generated books) to a translatable
component but left `mechanics/loot/EnchantedBookLootInjector.LORE` — the *chest-loot* copy of the
exact same stamp — as `Component.literal("Chiseled Enchanting Table")`. Both are the same string with
the same purpose, so a chest-generated book would still read in English on a non-English client.
Converted here as well. Safe: `RemoveEnchantedBooksFunction` recognises "our" books by *having* a lore
line, not by matching its text, so the switch is behaviour-preserving. (The two further copies under
`modifyChestLootTable/**` and `utils/ChiseledBookshelfLootTable` are excluded from compilation on
both branches — legacy, untouched.)

### 4.2 Lang parity gate now covers all 11 languages

The 26.3 `ci_static_checks.py` compared `en_us` against `zh_cn` only, because that was all the branch
shipped when the gate was written; the same branch then added nine more languages without widening
it. This branch syncs the gate *and* the languages, so the gate checks every `lang/*.json` against
`en_us` (missing key, extra/stale key, empty value). Verified: 15 keys × 11 files, parity exact.

### 4.3 `build.yml` gained a `no-viewers` job

`-Penable_jei=false -Penable_rei=false` is the documented escape hatch for a machine that cannot
reach `maven.blamejared.com` / `maven.shedaniel.me`. On the 26.3 branch nothing ever compiles that
configuration, so the escape hatch can rot silently. Here it is a CI job on every push.

### 4.4 `compat_check.py` gates the *inverse* schema

The 26.3 gates exist because a stale 26.2-shaped file silently loses its contents under 26.3. The
same failure mode applies in the other direction on this branch (a 26.3-shaped file silently loses
its `condition`/`modifier` here, and a `recipes` list makes the advancement fail to load), which is
exactly the risk created by keeping three Minecraft lines in one repository. So the two new Tier-1
gates enforce the 26.1.2 schema and name the 26.3 form in their messages. Negative-tested both ways:
pointed at this branch's pre-fix `craft.json` → 3 failures; pointed at the 26.3 branch's `craft.json`
+ block loot table → 3 failures; against the fixed files → PASS.

### 4.5 `consistency.yml` also watches `docs/ci/publish.yml`

and runs `docs/ci/install.sh --check` as a **warn-only** step. On this branch the reference copies are
first-class (the sandbox token has no `workflows: write`), so drift between `docs/ci/` and
`.github/workflows/` should be reported — but a missing live copy is a maintainer/permission action,
not a code defect, and must not turn the version gate red.

### 4.6 `ci_static_checks.py` gained a warn-only dangling-link check (Tier D)

Docs in this repository reference each other heavily, and a snapshot-style branch can lose a file
without anything noticing. Scanning all 34 committed `*.md` files found three references to
documents that are not in the tree on **any** branch: `docs/COMPATIBILITY.md` (twice) and
`docs/reports/PORTING_26_1_2_P19_CROSS_MOD_COMPATIBILITY_REPORT.md`. The check reports them as
warnings — a missing *document* is never a build defect — and the three references were reworded so
they now say plainly that the file is absent and where the same content lives instead
(`EXTENDING.md` §1 + `compat_check.py`'s `check_default_enchantment_tags_well_formed()` for the
cross-mod tag behaviour, `CHANGELOG_HISTORY.md` for the P19 round). Result: the check now reports
`markdown links ok (34 files scanned)`, so the *next* dangling link is visible.

While rewording those, one real doc/data drift surfaced and was fixed: `EXTENDING.md` documented the
default unlock tag as `#minecraft:non_treasure`, while
`data/.../default_enchantments.json` has shipped `#minecraft:in_enchanting_table` since the P19
round (a pure superset — see the JSON's own `comment` field). Both the example block and the prose
now match the data. Same drift exists on the 26.3 branch and is **not** fixed there.

## 5. Version and release-identity decision

`mod_version` **1.0.2 → 1.0.3** on this branch, matching the 26.3 branch's `1.0.3`. Rationale: the
sync adds features (two viewer integrations, cost sync, nine languages) and a crash-class fix
(worldgen deadlock), which is a minor release under the unofficial-port SemVer in
`CURRENT_BASELINE.md`; and keeping the same number on both lines makes "which feature set is this"
answerable from the version alone across the three branches (the Minecraft line is carried by the
branch/`minecraft_version`, not by `mod_version`). All five sources of truth that
`scripts/check_release_consistency.sh` knows about were updated in the same commit:
`gradle.properties`, `README.md`, `src/main/resources/fabric.mod.json` (`${version}` + identity
strings), `.github/workflows/publish.yml`, `docs/ci/publish.yml`.

## 6. Change inventory

New files: `.githooks/pre-commit`, `.github/workflows/{build,compile-check,consistency,publish}.yml`,
`docs/ci/{README.md,build.yml,compile-check.yml,consistency.yml,install.sh,publish.yml}`,
`docs/reports/{SYNC_FROM_26_3_REPORT.md,VIEWER_INTEGRATION_JEI_REI.md,WORLDGEN_BOOKSHELF_DEADLOCK_REPORT.md}`,
`scripts/{check_release_consistency.sh,ci_static_checks.py,install-hooks.sh}`,
`src/main/java/.../compat/{EnchantQueryIndex,SyncedEnchantmentCosts}.java`,
`src/main/java/.../menu/{EnchantCostSync,EnchantCostTablePayload}.java`,
`src/client/java/.../compat/jei/{ChiseledEnchantingJeiPlugin,EnchantingRecipe,EnchantingRecipeCategory}.java`,
`src/client/java/.../compat/rei/{ChiseledEnchantingReiPlugin,ReiEnchantingCategory,ReiEnchantingDisplay}.java`,
nine `lang/*.json` files.

Modified: `build.gradle`, `gradle.properties`, `gradlew` (mode), `.gitignore`,
`scripts/compat_check.py`, `src/main/resources/fabric.mod.json`,
`src/main/java/.../ChiseledEnchantingTable.java`,
`.../mechanics/loot/EnchantedBookLootInjector.java`,
`.../mechanics/structure/{BookshelfBookFiller,BookshelfReplacerProcessor}.java`,
`.../menu/ChiseledEnchantingTableScreenHandler.java`,
`.../mixin/structure/LevelChunkPostProcessMixin.java`,
`src/client/java/.../ChiseledEnchantingTableClient.java`,
`.../gui/ChiseledEnchantingTableScreen.java`, `lang/{en_us,zh_cn}.json`,
`data/.../advancement/{craft,root}.json`, `CHANGELOG.md`, `CURRENT_BASELINE.md`, `NEXT_TASK.md`,
`README.md`, `docs/DOCUMENTATION_INDEX.md`, `docs/EXTENDING.md`.

Unchanged on purpose (although the 26.3 branch differs): `scripts/import_upstream_loot.py` — its
comment says "the actual 26.1.2 loot table ids" here while the 26.3 copy still says "26.2", i.e.
ours is the correct one; everything in §3, plus the mixin config (the deadlock fix adds no mixin) and
all six data-driven content tables under
`data/chiseled_enchanting_table/chiseled_enchanting_table/`.

## 7. Verification state

Run in the dev sandbox (no JDK, no Gradle, no maven access — `java` is not installed and
`maven.fabricmc.net` is unreachable, so `./gradlew` cannot even resolve dependencies here):

| Gate | Result |
| --- | --- |
| `git apply --check -p1 docs/patches/26.1.2-worldgen-bookshelf-deadlock.patch` | clean against base `79717b5`; the applied `BookshelfBookFiller` / `LevelChunkPostProcessMixin` then differ from the 26.3 branch only by the intended 26.1.2 API adaptations + the lore change |
| `python3 scripts/ci_static_checks.py` | PASS (4 check groups, 0 warnings) — 17 mixin class references resolve, no registered mixin is excluded by `build.gradle`, lang parity 15 keys × 11 files, relative markdown links ok (34 files scanned) |
| `python3 scripts/compat_check.py` | PASS (2 Tier-2 warnings: no MC jar available), including the two new 26.1.2-schema gates, negative-tested in both directions (§4.4) |
| `bash scripts/check_release_consistency.sh --strict` | PASS |
| `bash docs/ci/install.sh --check` | ok for all four workflows (`docs/ci/` ≡ `.github/workflows/`) |
| YAML parse of all eight workflow files | ok (`build.yml` jobs `static`/`build`/`no-viewers`; `compile-check.yml` job `compile`; `consistency.yml` job `check`; `publish.yml` job `build`) |
| all changed/added JSON | parse-clean |
| JEI/REI API drift vs the viewers' own `26.1` branches | none (§3.1 / §4.1 of the viewer report) — this is what makes the byte-identical adapter port defensible without a local compiler |

### 7.1 CI result (2026-10-21) — green after two fixes

Only GitHub Actions can resolve `maven.fabricmc.net`, `maven.blamejared.com`, `maven.shedaniel.me`
and `maven.architectury.dev`, so the compile verdict came from there. `compile-check.yml` writes
`build-reports/compile-java.log` back to this `arena/**` branch; read it with
`gh api repos/q14433686-arch/chiseled-enchanting-table-26.1.2-source/contents/build-reports/compile-java.log?ref=<branch> --jq .content | base64 -d | tail -80`.

| Commit | compile-check | build.yml | What it proved |
| --- | --- | --- | --- |
| `5df4b57` (workflows only) | failure `39666332527` | failure `39666332547` | the write-back channel works (a `ci-log:` commit landed), but every job died at `./gradlew: Permission denied` — `gradlew` was still `100644` |
| `c0c0dc2` (full sync) | failure `39666575031` | failure `39666575035` | static gates + `no-viewers` green; compiling **with** JEI/REI failed in the *main* source set: `cannot access EntityFluidExtension` (§8 finding 7) |
| `18b851a` (viewer exclusions) | success `39667707099` | success `39667707142` | both source sets compile with the viewers on; `remapJar` + sources jar produced |
| `315fb6e` (log hardening) | success `39668301472` | success `39668301133` | `BUILD SUCCESSFUL in 1m 12s`, 2 warnings (JEI deprecations); resolved coordinates recorded — `jei-26.1.2-fabric:29.47.0.111`, `RoughlyEnoughItems-api-fabric:26.1.819`, `basic-math:0.6.1`, `architectury-fabric:20.0.6`, `fabric-api:0.151.0+26.1.2` unchanged |

`consistency.yml` is green as well (run `39666575001`, `12 passed, 0 warned, 0 failed`); it only
triggers on version-relevant paths, so it did not re-run for the last two commits.

So: **the whole 1.0.3 sync compiles on the 26.1.2 line**, including both viewer adapters, and the
optional-dependency escape hatch compiles too. Details and the upgrade notes for the pinned viewer
coordinates: `docs/reports/VIEWER_INTEGRATION_JEI_REI.md` §6.

Open, and why:

1. **In-game** — the deadlock fix needs a fresh world with the affected structure chunks (the
   reporter's case: a mesa village with Distant Horizons' `FEATURES` LOD generator); the shelves must
   come out *filled* and correctly facing; JEI/REI need the §5 checklist in the viewer report; the
   cost sync needs a dedicated server with a modified `enchantment_costs.json`.
2. **`publish.yml` is manual** (`workflow_dispatch`) and needs the `PUBLISH_*` secrets; nothing in
   this sync can be validated by running it. Its `build` job is the same Gradle invocation that
   `build.yml` just proved green, so the risk is confined to the release/secret plumbing.

## 8. Findings worth a follow-up (discovered while syncing, not fixed here)

1. **The advancement guide is not actually localised.** `CURRENT_BASELINE.md` (1.0.2) states
   "Advancement JSON display text was moved from hardcoded English to `translate` components" and
   `NEXT_TASK.md` asks a tester to "confirm English and Simplified Chinese strings display". Neither
   is true on this branch *or* on the 26.3 branch: all eight files under
   `data/chiseled_enchanting_table/advancement/` carry literal English `title`/`description`, and no
   `advancements.*` key exists in any `lang/*.json`. Fixing it means 8 advancements × 2 strings ×
   11 languages, i.e. real translation work — deliberately kept out of this sync (it is not a 26.3
   change, and a partial en_us/zh_cn-only fix would break the widened lang-parity gate, §4.2).
   `CURRENT_BASELINE.md` now says what the tree actually contains.
2. **`NEXT_TASK.md` item B (multi-slot cost panel) is still open** and is now *more* visible: with the
   cost table synced to the client, the GUI can show the true multi-item cost while the container
   still only has `ENCHANTABLE_SLOT` + `COST_SLOT` and silently consumes the rest from the player's
   inventory.
3. **Patch debris is committed**: `*.java.orig` / `*.java.rej` under `src/client/java/.../gui/` and
   `src/main/java/.../registry/`, plus root-level `fix*.patch` / `fix_*.py` / `adapted_handler.java` /
   `Dummy.class`. Present on all three branches; none is referenced by the build. Deleting them is a
   pure hygiene commit that would touch every branch, so it was left out of a sync that is meant to
   be diffable against `26.3`.
4. **Three documents referenced by the index do not exist** (`docs/COMPATIBILITY.md`,
   `docs/reports/PORTING_26_1_2_P19_CROSS_MOD_COMPATIBILITY_REPORT.md`) on any branch. Handled in
   §4.6: the references now say so explicitly and a warn-only CI check keeps the next one visible.
   Restoring the P19 write-up itself would need the round's original notes, which are not in the
   repository.
5. **`docs/marketing/*` still describes 1.0.2** (Modrinth / CurseForge / MC百科 listing copy). Not
   touched here: those files are release copy for a published listing, and this port is not
   published (`README.md` says so). They need a pass before any `publish.yml` run.
6. **`compile-check.yml` on the 26.3 branch has dead status-capture code.** GitHub Actions runs
   `run:` steps with `bash -e`, so

   ```bash
   ./gradlew compileJava … >> "$LOG" 2>&1
   status=$?          # never reached when Gradle fails
   ```

   aborts at the failing command: the log this branch got back for `c0c0dc2` contains no
   `gradle exit code` line at all. Anything placed after that line for the failure case — here, the
   dependency-report write-back — never runs exactly when it is needed. Fixed on this branch with
   `status=0` + `|| status=$?` (commit `315fb6e`); the 26.3 copy still has the bug.
7. **A viewer's POM can displace the pinned Fabric API and break the *main* source set.** This
   branch needed `exclude group: 'net.fabricmc.fabric-api' / 'net.fabricmc' / 'com.mojang'` on all
   four viewer `compileOnly` dependencies; without them `compileJava` fails with
   `cannot access EntityFluidExtension`, because Loom builds the Minecraft jar javac sees by applying
   Fabric API's declared interface injections, and a transitively-upgraded Fabric API changes what
   gets injected. Full analysis: `docs/reports/VIEWER_INTEGRATION_JEI_REI.md` §6.1. The 26.3 branch
   compiles green *without* the exclusions only because its `fabric-api 0.162.0+26.3` already wins
   its own version conflict — porting the exclusions back would make it robust against the next
   viewer release. This is the one divergence in §4 that is worth pushing upstream to the sister
   branch.
8. **Floating `compileOnly` coordinates.** Both branches shipped `jei_version=latest.release` and a
   `+`-suffixed `rei_version`, i.e. a build that can break with no commit in this repository. JEI has
   already deleted `IIngredientAcceptor#addItemStack` on its `26.1` branch — the pinned
   `29.47.0.111` still has it as `@Deprecated(forRemoval = true)`, which is the two warnings in the
   green log. Pinned here (29.47.0.111 / 26.1.819) with the migration path documented; `compile-check`
   now records the resolved coordinates in the log so a future bump is a two-line change with
   evidence. The 26.3 branch still floats.

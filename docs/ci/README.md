# CI triad — 26.1.2 line

Synced from the `26.3` branch of this repository (which adapted it from
[`q14433686-arch/TaCZ_Refabricated_Unofficial`](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/tree/26.3/.github/workflows)),
then re-adapted to Minecraft **26.1.2** / mod **1.0.3**. See
[`../reports/SYNC_FROM_26_3_REPORT.md`](../reports/SYNC_FROM_26_3_REPORT.md) §5 for the full list of
CI-side adaptations.

The files in this directory are **byte-identical reference copies** of `.github/workflows/`, kept for
two reasons:

1. A GitHub App installation token without `workflows: write` cannot create or update workflow
   files — the push is rejected outright:

   ```
   ! [remote rejected] (refusing to allow a GitHub App to create or update workflow
     `.github/workflows/build.yml` without `workflows` permission)
   ```

   A `PUT` through the Contents API fails the same way (`403 Resource not accessible by integration`).
   That is what the 26.3 branch hit, which is why it keeps this directory. **Re-tested for this
   branch on 2026-10-21: the sandbox token *did* have the permission** — commit `5df4b57` pushed all
   four workflow files normally, and the runs started immediately. So here `docs/ci/` is not a
   workaround but a reviewable source of truth with a drift check; if a future token lacks the
   permission again, `bash docs/ci/install.sh` from a permitted checkout is still the recovery path.
2. `scripts/check_release_consistency.sh` reads `docs/ci/publish.yml` as the version source of truth
   when the live copy cannot be updated, so the two must be able to agree.

## Status on this branch (2026-10-21)

Green, on commit `da77188` of `arena/3ae35b5e-chiseled-enchanting-table-26-1` — `compile-check` run
`39670181582` (`BUILD SUCCESSFUL in 48s`), `build` run `39670181557` (all three jobs),
`consistency` run `39670181572`. That commit pins the two viewer coordinates, so this run is also
the proof that `jei-26.1.2-fabric:29.47.0.111` and `RoughlyEnoughItems-api-fabric:26.1.819` exist
and resolve. The run before it, on `315fb6e`, is the one that produced the numbers:

| Workflow | Run | Result |
| --- | --- | --- |
| `compile-check.yml` | `39668301472` | success — `compileJava` + `compileClientJava`, `BUILD SUCCESSFUL in 1m 12s`, 2 warnings (JEI `addItemStack` deprecated-for-removal) |
| `build.yml` · static checks | `39668301133` | success — all three offline gates |
| `build.yml` · gradle build | `39668301133` | success — `remapJar` + sources jar uploaded as an Actions artifact |
| `build.yml` · no-viewers | `39668301133` | success — `-Penable_jei=false -Penable_rei=false` |
| `consistency.yml` | `39666575001` | success — `12 passed, 0 warned, 0 failed` |

The two runs before that are instructive and are the reason this file documents the failures as well
as the passes:

* `5df4b57` (workflows only, no source changes yet) — everything failed at `./gradlew: Permission
  denied`, because `gradlew` was still committed `100644` at that point. Fixed by committing the mode
  (`git update-index --chmod=+x gradlew`) *and* by `chmod +x ./gradlew` inside the workflows, so the
  jobs no longer depend on the mode surviving a checkout.
* `c0c0dc2` (full sync) — `no-viewers` and the static gates were green, but compiling **with** the
  viewers failed in the *main* source set:
  `cannot access EntityFluidExtension` at `ChiseledEnchantingTableBlock.java:101`. Cause: the viewer
  dependencies' POMs displaced the pinned Fabric API, and Loom's interface injections (which rewrite
  the Minecraft classes javac compiles against) followed the displaced one —
  `fabric-content-registries-v0` injects `net.fabricmc.fabric.api.registry.fluid.EntityFluidExtension`
  into `net.minecraft.world.entity.Entity`, so any `player.…` call needs that class file. Fixed in
  `18b851a` by excluding `net.fabricmc.fabric-api`, `net.fabricmc` and `com.mojang` from all four
  viewer `compileOnly` dependencies. The same log also proved a latent bug in the workflow itself:
  Actions runs `run:` steps with `bash -e`, so `status=$?` after a failing `./gradlew` is dead code —
  the failure log had no `gradle exit code` line, and the dependency-report block would never have
  run when it was needed. Now `|| status=$?`.

## Drift check and re-apply

```bash
bash docs/ci/install.sh --check   # MISSING / DRIFT per file; exit 1 without writing anything
bash docs/ci/install.sh           # copy the four files into .github/workflows/ and show the diff
```

| reference copy | target |
|---|---|
| `docs/ci/compile-check.yml` | `.github/workflows/compile-check.yml` |
| `docs/ci/build.yml` | `.github/workflows/build.yml` |
| `docs/ci/consistency.yml` | `.github/workflows/consistency.yml` |
| `docs/ci/publish.yml` | `.github/workflows/publish.yml` (`26.1.2` / `1.0.3`) |

**After changing CI, edit the copy here first, then run `install.sh`.** `consistency.yml` runs
`install.sh --check` as a *warn-only* step, so drift is reported on every relevant push but can never
turn the gate red on its own — a missing `.github/workflows/` copy is a maintainer/permission action,
not a code defect.

## What each workflow does

* **`compile-check.yml`** — compiles **both** source sets (`compileJava compileClientJava`; this repo
  splits `main` and `client`, so compiling only `main` silently skips the entire GUI/renderer half
  *and* both recipe-viewer adapters) and pushes the log back to `arena/**` branches as
  `build-reports/compile-java.log` through the Contents API. That is the point: Actions has network
  access to `maven.fabricmc.net` / Mojang's maven / `maven.blamejared.com` / `maven.shedaniel.me`,
  the development sandbox has none of them, and Actions *log blobs* are not reachable from the
  sandbox either — so the log has to be committed back to the branch to be readable. Also inherited
  from TACZR: `paths-ignore: build-reports/**` (stops the log commit re-triggering the workflow) and
  "only `arena/**` gets log commits" (release branches stay clean). The log header now also records
  the `enable_jei` / `enable_rei` toggles and their versions, because a viewer-artifact resolution
  failure is a *dependency* failure and must be distinguishable from a source failure at a glance.
  When the compile fails it also commits back `build-reports/compile-deps.log` — `dependencyInsight`
  for `net.fabricmc.fabric-api` on both compile classpaths plus the first 400 lines of each full
  dependency tree. That file exists because the first 1.0.3 run failed with
  `cannot access EntityFluidExtension`: a *viewer's* transitive fabric-api had displaced the pinned
  one, and Loom's interface injections (which rewrite the Minecraft classes javac sees) followed it.
  Without a committed dependency graph that class of failure is undiagnosable from the sandbox.
* **`build.yml`** — three offline static gates (`ci_static_checks.py`, `compat_check.py`,
  `check_release_consistency.sh --strict`) in a fast `static` job, then a `gradle build` job that
  uploads the jar as a 14-day artifact.
* **`build.yml` → `no-viewers` job (new on this line)** — the same compile with
  `-Penable_jei=false -Penable_rei=false`. JEI and REI are compile-only *optional* dependencies
  resolved from third-party mavens; the documented escape hatch for a machine that cannot reach them
  is only real if CI proves it still compiles. The 26.3 branch has no equivalent job.
* **`consistency.yml`** — the version gate on its own trigger set, because `build.yml`
  `paths-ignore`s `*.md` and would never see a README-only version drift. It also watches
  `docs/ci/publish.yml`, which on this branch is a first-class copy rather than a fallback.
* **`publish.yml`** — unchanged apart from the version fields (`MINECRAFT_VERSION: 26.1.2`,
  `VERSION: 1.0.3`, `RELEASE_NAME: Chiseled Enchanting Table 1.0.3 (Unofficial 26.1.2 Port)`);
  still manual (`workflow_dispatch`), so it can never fire by accident.

### Branch filters

All three automatic workflows fire on `arena/**`, `chiseled-enchanting-table-26.1.2-source` and
`main`. The 26.3 branch's copies additionally list `26.**`; that entry is meaningless here (each
branch runs *its own* copy of the workflow file), so it was dropped instead of copied.

## Reading CI results from the sandbox

The Actions **log blob** domain (`results-receiver.actions.githubusercontent.com`) is unreachable
from the sandbox — `gh run view --log-failed` and `gh api .../runs/{id}/logs` both die with `EOF`.
Two channels do work:

```bash
# 1. the compile log the workflow commits back (primary — full javac/Gradle output)
gh api "repos/q14433686-arch/chiseled-enchanting-table-26.1.2-source/contents/build-reports/compile-java.log?ref=<branch>" \
    --jq '.content' | base64 -d | tail -60

# 1b. on a compile failure, the dependency graph that goes with it (only committed when it fails)
gh api "repos/q14433686-arch/chiseled-enchanting-table-26.1.2-source/contents/build-reports/compile-deps.log?ref=<branch>" \
    --jq '.content' | base64 -d | head -80

# 2. step-level results, for "which step failed and how long did it take"
gh api "repos/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/runs/<run id>/jobs" \
    --jq '.jobs[0].steps[] | "\(.name): \(.conclusion)"'
```

The checks/annotations API is a third option, but it only carries what a problem matcher or an
explicit `::error` produced.

### `./gradlew: Permission denied`

`compile-check.yml` invokes `./gradlew` directly, without the `chmod +x` step that `build.yml` has.
`gradlew` is therefore committed executable (`100755`) on this branch too — it was `100644` in the
1.0.2 snapshot, which only ever worked because `build.yml` chmods first. If that error comes back,
check `git ls-files -s gradlew` before suspecting the workflow.

## The static gates run anywhere

No Gradle, no network, no JDK:

```bash
python3 scripts/ci_static_checks.py
python3 scripts/compat_check.py
bash scripts/check_release_consistency.sh --strict
bash docs/ci/install.sh --check
```

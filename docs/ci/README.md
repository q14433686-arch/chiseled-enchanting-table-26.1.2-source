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
   The development sandbox runs with such a token, so the intended CI state has to be reviewable and
   re-appliable from somewhere that is *not* `.github/workflows/`. This is exactly why the 26.3
   branch keeps the same directory.
2. `scripts/check_release_consistency.sh` reads `docs/ci/publish.yml` as the version source of truth
   when the live copy cannot be updated, so the two must be able to agree.

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

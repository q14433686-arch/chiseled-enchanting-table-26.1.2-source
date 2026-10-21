# CI triad — 26.2 line

Synced from the `26.3` and `26.1.2` branches of this repository (which adapted it from
[`q14433686-arch/TaCZ_Refabricated_Unofficial`](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/tree/26.3/.github/workflows)),
then adapted to Minecraft **26.2** / mod **1.0.3**.

The files in this directory are **byte-identical reference copies** of `.github/workflows/`, kept for
two reasons:

1. A GitHub App installation token without `workflows: write` cannot create or update workflow
   files. The current sandbox token *does* have the `workflows` permission, so all four workflow
   files are installed directly into `.github/workflows/` and verified via
   `bash docs/ci/install.sh --check`.
2. `scripts/check_release_consistency.sh` reads both `.github/workflows/publish.yml` and
   `docs/ci/publish.yml` to verify five-place version agreement.

## Status on this branch (2026-10-22)

Green on commit `61b2431` of `arena/dd589d1c-chiseled-enchanting-table-26-1`:

| Workflow | Check Run | Result |
| --- | --- | --- |
| `compile-check.yml` | `119488382287` | success — `compileJava` + `compileClientJava`, `BUILD SUCCESSFUL in 52s`, 2 warnings (JEI `addItemStack` deprecated-for-removal); resolved `jei-26.2-fabric:30.47.0.239` and `RoughlyEnoughItems-api-fabric:26.2.821`, with `fabric-api:0.155.2+26.2` unchanged |
| `build.yml` · static checks | `119488381220` | success — all three offline gates (`ci_static_checks.py`, `compat_check.py`, `check_release_consistency.sh --strict`) |
| `build.yml` · gradle build | `119488433821` | success — `remapJar` + sources jar uploaded as an Actions artifact |
| `build.yml` · no-viewers | `119488433668` | success — `-Penable_jei=false -Penable_rei=false` |
| `consistency.yml` | `119488380920` | success — `12 passed, 0 warned, 0 failed` + `docs/ci and .github/workflows agree` |

## Drift check and re-apply

```bash
bash docs/ci/install.sh --check   # MISSING / DRIFT per file; exit 1 without writing anything
bash docs/ci/install.sh           # copy the four files into .github/workflows/ and show the diff
```

| reference copy | target | status |
|---|---|---|
| `docs/ci/compile-check.yml` | `.github/workflows/compile-check.yml` | installed (`ok`) |
| `docs/ci/build.yml` | `.github/workflows/build.yml` | installed (`ok`) |
| `docs/ci/consistency.yml` | `.github/workflows/consistency.yml` | installed (`ok`) |
| `docs/ci/publish.yml` | `.github/workflows/publish.yml` | installed (`ok`, `26.2` / `1.0.3`) |

## What each workflow does

* **`compile-check.yml`** — compiles **both** source sets (`compileJava compileClientJava`), records
  the resolved coordinates for `mezz.jei`, `me.shedaniel`, `dev.architectury`, `net.fabricmc`, and
  `com.mojang` across `compileClasspath` and `clientCompileClasspath`, and pushes
  `build-reports/compile-java.log` (plus `build-reports/compile-deps.log` on failure) back to
  `arena/**` branches through the GitHub Contents API. Uses `status=0` + `./gradlew ... || status=$?`
  so `bash -e` does not abort before recording the exit code and failure diagnostics.
* **`build.yml`** — three offline static gates (`ci_static_checks.py`, `compat_check.py`,
  `check_release_consistency.sh --strict`) in a fast `static` job, then a `gradle build` job that
  uploads the jar as a 14-day artifact, plus a `no-viewers` job (`-Penable_jei=false -Penable_rei=false`)
  verifying the optional-dependency escape hatch.
* **`consistency.yml`** — the five-place version gate and `docs/ci/install.sh --check` drift check on
  its own trigger set, because `build.yml` `paths-ignore`s `*.md`.
* **`publish.yml`** — manual (`workflow_dispatch`) release workflow targeting Minecraft `26.2` /
  mod version `1.0.3`.

## Reading CI results from the sandbox

```bash
# 1. the compile log the workflow commits back (primary — full javac/Gradle output + resolved coords)
gh api "repos/q14433686-arch/chiseled-enchanting-table-26.1.2-source/contents/build-reports/compile-java.log?ref=<branch>" \
    --jq '.content' | base64 -d | tail -80

# 2. step-level results
gh api "repos/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/runs/<run id>/jobs" \
    --jq '.jobs[] | "\(.name): \(.conclusion)"'
```

## The static gates run anywhere

No Gradle, no network:

```bash
python3 scripts/ci_static_checks.py
python3 scripts/compat_check.py
bash scripts/check_release_consistency.sh --strict
bash docs/ci/install.sh --check
```

# CI triad — installed; these are the reference copies

**Status (2026-10-14): all four workflows are live in `.github/workflows/`.** `bash
docs/ci/install.sh --check` reports `ok` for every one of them.

The files in this directory are **byte-identical reference copies**, kept for two reasons:

1. The development sandbox still cannot write workflow files — its GitHub App installation token has
   no `workflows: write`, so any push touching `.github/workflows/` is rejected:

   ```
   ! [remote rejected] (refusing to allow a GitHub App to create or update workflow
     `.github/workflows/build.yml` without `workflows` permission)
   ```

   A `PUT` through the Contents API fails the same way (`403 Resource not accessible by
   integration`). Only a maintainer can change CI, so the intended state has to be reviewable and
   re-appliable from somewhere that is not `.github/workflows/`.
2. `scripts/check_release_consistency.sh` reads `docs/ci/publish.yml` as the version source of truth
   when the live copy cannot be updated, so the two must be able to agree.

## Drift check and re-apply

```bash
bash docs/ci/install.sh --check   # MISSING / DRIFT per file; exit 1 without writing anything
bash docs/ci/install.sh           # copy the four files into .github/workflows/ and show the diff
```

| reference copy | target | status |
|---|---|---|
| `docs/ci/compile-check.yml` | `.github/workflows/compile-check.yml` | installed, identical |
| `docs/ci/build.yml` | `.github/workflows/build.yml` | installed, identical |
| `docs/ci/consistency.yml` | `.github/workflows/consistency.yml` | installed, identical |
| `docs/ci/publish.yml` | `.github/workflows/publish.yml` | installed, identical (`26.3` / `1.1.0`) |

**After changing CI, edit the copy here first, then have a maintainer apply it** — otherwise
`--check` will report drift on the next run and the sandbox has no way to reconcile it.

## What each workflow does

Design, comments and lessons are ported from the sister repository
[`q14433686-arch/TaCZ_Refabricated_Unofficial`](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial/tree/26.3/.github/workflows).

* **`compile-check.yml`** — compiles **both** source sets (`compileJava compileClientJava`; this
  repo splits `main` and `client`, so compiling only `main` silently skips the entire GUI/renderer
  half) and pushes the log back to `arena/**` branches as `build-reports/compile-java.log` through
  the Contents API. That is the point: Actions has network access to
  `maven.fabricmc.net`/Mojang's maven, the development sandbox does not, and Actions *log blobs*
  are not reachable from the sandbox either — so the log has to be committed back to the branch to
  be readable. Also inherited from TACZR: `paths-ignore: build-reports/**` (stops the log commit
  re-triggering the workflow) and "only `arena/**` gets log commits" (main branches stay clean).
* **`build.yml`** — three offline static gates (`ci_static_checks.py`, `compat_check.py`,
  `check_release_consistency.sh --strict`) in a fast `static` job, then a `gradle build` job that
  uploads the jar as a 14-day artifact.
* **`consistency.yml`** — the version gate on its own trigger set, because `build.yml`
  `paths-ignore`s `*.md` and would never see a README-only version drift.
* **`publish.yml`** — unchanged apart from the version bump; still manual (`workflow_dispatch`).

## Reading CI results from the sandbox

The Actions **log blob** domain (`results-receiver.actions.githubusercontent.com`) is unreachable
from the sandbox — `gh run view --log-failed` and `gh api .../runs/{id}/logs` both die with `EOF`.
Two channels do work:

```bash
# 1. the compile log the workflow commits back (primary — full javac/Gradle output)
gh api "repos/q14433686-arch/chiseled-enchanting-table-26.1.2-source/contents/build-reports/compile-java.log?ref=<branch>" \
    --jq '.content' | base64 -d | tail -60

# 2. step-level results, for "which step failed and how long did it take"
gh api "repos/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/runs/<run id>/jobs" \
    --jq '.jobs[0].steps[] | "\(.name): \(.conclusion)"'
```

The checks/annotations API is a third option, but it only carries what a problem matcher or an
explicit `::error` produced. A temporary `gradlew` bridge used to emit those (it is what surfaced
the two 26.3 `:compileJava` breaks); it was **removed** once this workflow's log write-back proved
itself, so `gradlew` is plain again.

### `./gradlew: Permission denied`

`compile-check.yml` invokes `./gradlew` directly, without the `chmod +x` step that `build.yml` has.
`gradlew` is therefore committed executable (`100755`) — it had been `100644` since the 26.2
baseline, which only ever worked because `build.yml` chmods first. If that error comes back, check
`git ls-files -s gradlew` before suspecting the workflow.

## The static gates run anywhere

No Gradle, no network:

```bash
python3 scripts/ci_static_checks.py
python3 scripts/compat_check.py
bash scripts/check_release_consistency.sh --strict
```

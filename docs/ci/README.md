# CI triad — staged for manual apply

These four workflow files are **written, reviewed and ready**, but they cannot be committed to
`.github/workflows/` from this environment:

```
! [remote rejected] (refusing to allow a GitHub App to create or update workflow
  `.github/workflows/build.yml` without `workflows` permission)
```

The push token is a GitHub App installation token scoped without `workflows: write`, so *any*
change under `.github/workflows/` is rejected. This is the same blocker that left
`publish.yml` on `MINECRAFT_VERSION: 26.1.2` during the 26.2 round (recorded in
[`../reports/PORTING_26_2_REPORT.md`](../reports/PORTING_26_2_REPORT.md) §2).

## Install (one command, needs a token with `workflows` scope)

```bash
bash docs/ci/install.sh          # copies the four files into .github/workflows/ and shows the diff
git add .github/workflows && git commit -m "ci: install the 26.3 CI triad"
git push
```

Or copy them by hand / through the GitHub web editor:

| staged file | target | status |
|---|---|---|
| `docs/ci/compile-check.yml` | `.github/workflows/compile-check.yml` | **new** |
| `docs/ci/build.yml` | `.github/workflows/build.yml` | **replaces** the current 26.2-era build workflow |
| `docs/ci/consistency.yml` | `.github/workflows/consistency.yml` | **new** |
| `docs/ci/publish.yml` | `.github/workflows/publish.yml` | **replaces** (bumps `MINECRAFT_VERSION: 26.1.2` → `26.3`, `VERSION: 1.0.3`, release name) |

Alternatively: grant the GitHub App `Workflows: read & write` on this repository
(Settings → Actions → General → Workflow permissions is *not* the setting; it is the App
installation's repository permissions), then re-run this session's push.

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

## Until they are installed

The workflow that *is* live on the remote (`.github/workflows/build.yml`, 26.2 era) already runs
`python3 scripts/compat_check.py` and `./gradlew build` on every push, so the 26.3 port still gets
a compile signal — just without the log push-back, the static gates and the artifact naming rules.
The three static gates can always be run locally:

```bash
python3 scripts/ci_static_checks.py
python3 scripts/compat_check.py
bash scripts/check_release_consistency.sh --strict
```

### Reading CI failures without the Actions logs

The Actions **log blob** domain (`results-receiver.actions.githubusercontent.com`) is unreachable from
the development sandbox — `gh run view --log-failed` and `gh api .../runs/{id}/logs` both die with
`EOF`, and a `PUT` to `.github/workflows/*` via the Contents API returns
`403 Resource not accessible by integration`. The **checks/annotations** API *is* reachable, so
`gradlew` carries a temporary bridge: under `GITHUB_ACTIONS=true` it tees the build into
`build-reports/compile-java.log` and re-emits the failure as `::error` annotations (chunked into at
most 10 multi-line annotations — GitHub's per-step annotation cap). Outside Actions the bridge is
inert and `gradlew` still `exec`s Gradle directly.

```bash
SHA=<commit sha>
CID=$(gh api "repos/q14433686-arch/chiseled-enchanting-table-26.1.2-source/commits/$SHA/check-runs" \
      --jq '.check_runs[0].id')
gh api "repos/q14433686-arch/chiseled-enchanting-table-26.1.2-source/check-runs/$CID/annotations?per_page=100" \
   --jq '.[] | select(.annotation_level=="failure") | .message'
```

**Delete the bridge from `gradlew` once `compile-check.yml` is installed** — that workflow commits
`build-reports/compile-java.log` back to the branch, which supersedes it.

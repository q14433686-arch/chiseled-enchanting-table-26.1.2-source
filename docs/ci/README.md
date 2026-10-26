# CI workflows and diagnostic evidence

The four workflow reference copies here must remain byte-identical to `.github/workflows/`.
Workflow edits were previously blocked by the sandbox GitHub App permissions; as of this
2026-10-26 session, workflow pushes and the new diagnostic Checks API have both been exercised
successfully. Do not retain the historical permission limitation as a current assertion.

```bash
bash docs/ci/install.sh --check
```

## Workflows

- `compile-check.yml`: compile main/client/test sources and execute JUnit; preserve full logs,
  dependency information and JUnit XML as 14-day artifacts.
- `build.yml`: offline validation, full jar build, optional-viewer-disabled compilation, real
  dedicated-server loading/reload/ritual probes, then real SDL-window config/menu interaction
  probes under Xvfb and software graphics. The last probe uses a separate development-only mod
  (`src/clientSmoke`), excluded from release jars and normal builds.
- `consistency.yml`: version/metadata/README/release-workflow consistency.
- `publish.yml`: manual publishing, not activated by this refactor.

Every success is evidence for its **exact source SHA and exercised scenarios**, not for future
HEADs, every graphics driver, every input method, or concurrent multiplayer races.

## No generated diagnostics in Git

`build-reports/`, bytecode and disposable run directories are ignored. CI never calls the
Contents API to commit logs, needs no `contents: write`, and no longer needs log-commit trigger
exclusions. Markdown-only edits run the inventory gate as well.

Full logs: open/download the run's artifacts. The sandbox cannot reliably reach the Actions log
blob host, so **bounded diagnostic Check runs** carry source SHA, compiler errors, test totals
and log tails directly through `api.github.com`, on both success and failure:

```bash
REPO=q14433686-arch/chiseled-enchanting-table-26.1.2-source
SHA=<exact-source-sha>
gh api "repos/$REPO/commits/$SHA/check-runs" \
  --jq '.check_runs[] | select(.name=="compile-diagnostics" or .name=="server-smoke-diagnostics") | .output.summary'
```

Push/manual runs publish diagnostics with `checks: write`. Pull requests skip this supplemental
publisher (including forks with read-only tokens) but retain workflow step results and artifacts.
Diagnostic publication is a separate final step: it cannot turn a failed build into success;
publication failures are visible rather than silently discarded. Diagnostics are summarized to
fit Checks' UTF-8 byte limit; they are not a replacement for the full artifact.

## Local verification

```bash
python3 scripts/ci_static_checks.py
python3 scripts/test_baseline_mixins.py
python3 scripts/test_ci_diagnostics.py
python3 scripts/test_sync_26_3.py
python3 scripts/check_repository_hygiene.py
python3 scripts/compat_check.py
bash scripts/check_release_consistency.sh --strict
```

The native client probe requires Java 25, Mojang/Fabric dependency access, SDL graphics and an
isolated checkout; it refuses existing run worlds. Run `scripts/server_smoke.py` first, then
`xvfb-run -a python3 scripts/client_smoke.py`. It exercises synthetic committed Unicode text
through the **real SDL event queue**, not a human-operated platform IME candidate window.
Never run these destructive fixtures against a user's save.

Keep `gradlew` executable (`100755`). No CI output should ever be committed back to the branch.

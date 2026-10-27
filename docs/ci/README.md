# CI workflows and diagnostic evidence — 26.2 line

The four workflow reference copies here must remain byte-identical to `.github/workflows/`
(verified by `bash docs/ci/install.sh --check`). This line's CI is **Contents-API writeback**:
the network-restricted sandbox cannot reliably reach the GitHub Actions log-blob host, so
`compile-check.yml` and `build.yml` deliberately write `build-reports/*.log` back to the branch
via the GitHub Contents API on `arena/**` pushes. That writeback is this line's **only real Java
compile-verification channel** (the sandbox has no JDK/Maven). A sibling line (26.1.2/26.3) uses an
artifacts-only + Checks-API model with an opt-in `clientSmoke` source set; that model is NOT used
here — do not reintroduce `client_smoke.py` / `post_ci_diagnostics.py` / check-run publishers as
required gates on this line.

```bash
bash docs/ci/install.sh --check
```

## Workflows (this line)

- `compile-check.yml`: `./gradlew compileJava compileClientJava test` on real MC 26.2 / JDK 25,
  writes `build-reports/compile-java.log` (+ `compile-deps.log` on failure) back via Contents API.
  Read it back in the sandbox with:
  ```bash
  gh api "repos/q14433686-arch/chiseled-enchanting-table-26.1.2-source/contents/build-reports/compile-java.log?ref=<branch>" --jq .content | base64 -d
  ```
- `build.yml`: offline validation (3 gates — `ci_static_checks.py`, `compat_check.py`,
  `check_release_consistency.sh`), full `gradle build` (jar artifact), the no-viewers compile
  escape hatch (JEI/REI off), and the real dedicated-server loading + `/reload` smoke
  (`scripts/server_smoke.py` writes `build-reports/server-smoke.log` back).
- `consistency.yml`: version/metadata/release-workflow consistency.
- `publish.yml`: manual publishing (dry-run by default). `paths-ignore: build-reports/**` prevents
  the writeback commits from re-triggering CI loops.

Every success is evidence for its **exact source SHA and exercised scenarios**, not for future
HEADs. `build-reports/*.log` are intentionally tracked on this line (unlike the sibling lines):
`scripts/check_repository_hygiene.py` is adapted to allow them, while still rejecting stray
bytecode / ad-hoc logs and disposable run artifacts.

## Offline gates (no network / JDK needed)

```bash
python3 scripts/ci_static_checks.py          # mixin inventory, lang parity, single-source-of-truth, BMP, Locale.ROOT, BundledDefaults, CURRENT_BASELINE mixin list
python3 scripts/compat_check.py              # datapack schema / ids / tags / loot-function codecs
python3 scripts/gen_bundled_defaults.py --check
python3 scripts/test_sync_26_2.py          # 26.2 API boundary contracts (ChatFormatting shim, gui.setScreen, StructureProcessor, protocol 7, seven-path fixes)
python3 scripts/test_baseline_mixins.py
python3 scripts/test_architecture_boundaries.py
python3 scripts/test_ui_readability.py
python3 scripts/test_ci_diagnostics.py
python3 scripts/check_repository_hygiene.py
bash scripts/check_release_consistency.sh --strict
```

Keep `gradlew` executable (`100755`).

## Not covered by headless CI (manual / real client required)

Real client GUI interaction, SDL/IME text input (this line bridges it version-adaptively — see
`NativeTextInputFocus`), mouse drag / scaled layouts, JEI/REI screen switching, LAN authority,
multiplayer contention, hopper/automation against a running ritual, cross-dimension same-coord
menus, hard restart / chunk unload, third-party trade lifecycles, large-library performance.
Do not present headless smoke as these having been manually verified. See
`docs/reports/FULL_PORT_26_3_REV12_SEVEN_PATH_AUDIT_2026_10_27.md` §5.

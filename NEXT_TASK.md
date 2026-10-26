# Current state — UI readability and six engineering splits verified (2026-10-26)

Current session branch: `arena/089dab6b-chiseled-enchanting-table-26-1`.
Keep all work on the session's actual Arena branch; historical reports naming other branches are not instructions to switch.

The full source synchronization through `9f0e13bd75e31f7d1d8a569302d0f57abd2ce840` and the engineering
cleanup are implemented. Version remains **1.1.0 / MC 26.3 / wire revision 11**. Do not re-run one-shot
port scripts or restore older source/GUI/input conventions from historical reports.

## Current UI validation — 2026-10-26 (latest)

Verified source **`805cd2fce042a6958a739b2d7e2242b273e08d42`**, release **1.1.0 / MC 26.3 / revision 11**.

- [compile-check 40254410646](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/runs/40254410646): **123 JUnit, zero failures/errors/skips**.
- [build 40254410608](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/runs/40254410608): all four business jobs and both diagnostics successful, including real server and Chinese SDL client.
- Natural enchantment terminology; user-requested lock emoji; explicit ordinary/total bookshelf labels and separate milestone counters; disjoint header/tab geometry; all six tab textures unified.
- Every owned tooltip uses one bounded, wrapped summary/Shift-details policy; compact bookshelf summary replaces the default mechanics wall. Python UI contracts **7/7**, new tooltip JUnit contracts **6/6**.
- Final Checks contain **UI BOUNDARY PASS**, **CLIENT SMOKE PASS**, economic and candidate boundary PASS markers; screenshot `run-client-smoke/screenshots/table-ui-zh_cn.png` saved in [smoke artifact](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/runs/40254410608/artifacts/12621464381). Actual game screenshot not manually inspected: sandbox artifact download is network-restricted. Automated Linux evidence is not a full human visual/platform-IME matrix.
- [UI implementation, verification and cross-branch notes](docs/reports/UI_READABILITY_2026_10_26.md). Prior six-area split below remains implemented; its 117-test evidence is historical, not the latest UI result. No PR/merge authorized or performed.

## Previous verified engineering implementation

Source **`3a3b0076bf56abf70ef9a29b2eb3ab173505d87f`**:

- compile-check **40250562480**, **117 JUnit / zero failures, errors or skips**.
- build **40250562244**, all four jobs successful, including real dedicated-server probes and a
  real SDL window with an integrated server/player.
- Automated client: committed CJK search text through SDL, preset clicks, server save/ack and reopen,
  actual menu quick-move, config-overlay forwarding/focus release, persistent target after close.
- Docs inventory, log hygiene, architecture ratchets and deterministic editor boundaries are CI gates.
- Full logs are artifacts; sandbox-readable diagnostics are Check runs, not committed `build-reports/` files.

All six requested responsibility groups are complete, not deferred to another architecture round:
quotes/payment, actual table panels/tooltips, config option/trace/drag controls, immutable candidate
models and read-only list presentation, config/policy schema/presets, queue serialization/recovery.
Original public DTO/codec identities and legacy spelling delegates are preserved. Read-only list
queries expose detached row data rather than Widget instances. Native economic/candidate probes
passed; current-source Check diagnostics include both boundary PASS markers.

[Current report and cross-branch migration notes](docs/reports/ENGINEERING_SPLIT_COMPLETION_2026_10_26.md).
The user delegated architecture decisions; do not restart an architecture-choice questionnaire.
No PR or merge is authorized for this round. Only open/merge one if subsequently requested.

## Evidence limits / broader release validation backlog

These are not to be mislabeled as completed by the automated client probe:

1. Human-operated Windows/macOS IME composition/candidate windows, physical GPU/driver combinations,
   slider dragging and the full localization/visual matrix. Linux synthetic SDL committed-text input
   is now tested; the old blanket claim that no real client was ever started is obsolete.
2. Real dedicated/LAN multi-client owner/guest/op permissions and concurrent/late/disconnected saves.
   Rejected/stale/timeout states are executed in headless interaction tests; they are not a two-client network stress test.
3. Per-player discovery in already-open table and JEI/REI GUIs. One player's unlock must not unlock another catalog.
4. Full-inventory overflow, two players sharing shelves, old-world migration after backup, hopper,
   creative/explosion and restart/unload lifecycle matrices beyond the recorded probes.
5. Mod Menu/JEI/REI actual installed GUI permutations; successful viewer-enabled/disabled compilation
   is not universal runtime interoperability proof.
6. Local Tier-2 registry/tag enumeration needs the pinned MC/extra jars; the network-constrained
   sandbox reports those checks as skipped. Do not rewrite that as zero warnings or full Tier-2 success.
7. Natural generation and delayed structures, layered modpacks/trade lifecycles and long-duration performance.
   Flat-world smoke intentionally disables structures.

## Reproducible gates

```sh
bash scripts/check_release_consistency.sh --strict
bash docs/ci/install.sh --check
python3 scripts/ci_static_checks.py
python3 scripts/test_sync_26_3.py
python3 scripts/test_baseline_mixins.py
python3 scripts/test_ci_diagnostics.py
python3 scripts/test_architecture_boundaries.py
python3 scripts/check_repository_hygiene.py
python3 scripts/check_architecture_boundaries.py
python3 scripts/compat_check.py
# JDK 25 with pinned dependency-repository access:
./gradlew build
./gradlew -Penable_jei=false -Penable_rei=false compileJava compileClientJava
# Disposable CI checkout only, not a user's save:
python3 scripts/server_smoke.py
xvfb-run -a python3 scripts/client_smoke.py
```

Preserve version-specific SDL named constants and native focus ownership, the Fabric metadata-ID/
resource-namespace distinction, fail-closed policy and persist-before-publish. Internal Data model
Java names moved to top-level types: downstream Java extensions need import migration/recompilation;
this is not a wire/JSON format change. Book-stack bootstrap settings still require restart.

# Current state — research prerequisite foundation in progress (2026-10-26)

Current session branch: `arena/65a61f8b-chiseled-enchanting-table-26-1`.
Keep all work on the session's actual Arena branch; historical reports naming other branches are not instructions to switch.

The full source synchronization through `9f0e13bd75e31f7d1d8a569302d0f57abd2ce840` and the engineering
cleanup are implemented. Version remains **1.1.0 / MC 26.3**; the current compatibility gate is **wire revision 12**. Do not re-run one-shot
port scripts or restore older source/GUI/input conventions from historical reports.

## Research roadmap and live workspace — 2026-10-27

- [Authoritative gameplay and architecture plan](docs/research/MASTER_PLAN.md): four loops, player stages, additions/removals, old-save migration, R0–R6 acceptance. Research remains the prerequisite, not advanced enchantment effects.
- Source **`6bb5276`** adds real authorized table-menu scanning, loaded-only offsets, distinct enchantment breadth and best quality. No wear/generation/lock filtering or book mutation. No tick scanner.
- [compile-check 40364870519](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/runs/40364870519): **186 JUnit, zero failures/errors/skips**. [build 40364870525](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/runs/40364870525): static/build/no-viewer jobs succeeded; new native workspace probe still pending at documentation time.
- Previous foundation native run **40302066671** is now confirmed successful, including `RESEARCH FOUNDATION PASS`; this does not validate the new workspace probe.
- Next: complete R1 native verification, implement R2 bounded research view/actions, then R3 one usable trial/evidence/conclusion/reward loop. No production research topic/UI or old-maintenance migration yet. **1.1.0 / MC26.3 / revision12** unchanged.

## Previous research foundation — 2026-10-26

Source **`2278fc053c6e2bf32d6a06214ccafbd3afd00ffa`** adds the prerequisite research core, not advanced enchantment content. **1.1.0 / MC26.3 / revision12** unchanged; no payload, shared registry state or Mixin added.

- Implemented: immutable validated topic graph, conclusion prerequisites, distinct knowledge breadth/quality, hypothesis selection, unique server-verified evidence, retained contrary observations, field observation separated from workspace conclusion, cancellation, schema/revision checks and world-local UUID journals.
- Registered datapack loading and save lifecycle; catalog publication waits for overall successful reload. Corrupt/unknown-schema journals disable research rather than overwrite player knowledge. Domain/persistence/runtime boundaries have architecture gates.
- [compile-check 40302066798](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/runs/40302066798): **181 JUnit, zero failures/errors/skips**, including 26 new research tests. Build, static checks and no-viewer compilation succeeded in [build 40302066671](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/runs/40302066671).
- Native client validation is **not yet confirmed**: the job is still installing virtual-display dependencies. The test-only catalog/probe is present but its `RESEARCH FOUNDATION PASS` marker must actually run before claiming native success.
- **Not player-ready:** no production topics, research UI/network, real table scan adapter, gameplay evidence providers, trial abilities or unlock enforcement yet. Default workspace access denies all; test fixtures are development-only. Existing wear/generation/lock rules have **not** been migrated or removed.
- Next: workspace scan/authorization → bounded research UI/actions → one meaningful end-to-end introductory research experiment → unlock/book integration and old-rule migration → advanced enchantment content. Do not skip the research loop to implement Sharpness first.
- [Detailed contracts, save format and limitations](docs/research/FOUNDATION.md). No new PR or merge performed.

## Previous rainbow-item validation — 2026-10-26

Verified source **`0a43bd3a7e4284beef49a21dbffa34bdc5e513f7`**; **1.1.0 / MC 26.3 / `26_3_revision_12`** unchanged. This client presentation extension adds no component, registered state, payload or Mixin.

- Rainbow items show a purple localized **〔彩虹〕 / [Rainbow]** suffix in their tooltip header and two short explanatory lines. Player custom names and stored name/lore/data components are not rewritten; hotbar hover-name text remains the original name.
- Fabric's item-model before-bake hook replaces only the table's primary dye tint. Rainbow GUI/held models use the same vanilla sheep clock as placed cloth, with an explicit animated flag to prevent GUI atlas freezing. Ordinary tables still delegate to the original Dye source. Non-cuboid/no-primary-Dye resource-pack models are preserved with a warning, not forcibly replaced.
- [compile-check 40267243846](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/runs/40267243846): **155 JUnit, zero failures/errors/skips**. [build 40267243828](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/runs/40267243828): all jobs successful, including no-viewer compilation and real server/SDL client smoke.
- Diagnostics include **RAINBOW ITEM PASS** and **RAINBOW ITEM RELOAD PASS**: actual server loot, baked GUI colour changes across live frames, animated-cache flag, held colour parity, static ordinary dye control, actual tooltip callback/custom-name safety, unchanged custom data, and actual resource reload. Existing **RAINBOW CLOTH PASS**, **CATALOGUE WORLD PASS**, **CLIENT SMOKE PASS** remain passing.
- [Installable build including item animation](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/runs/40267243828/artifacts/12626602664); [native evidence including rainbow-items-a.png and rainbow-items-b.png](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/runs/40267243828/artifacts/12626638940). These are two captured frames, not a manually reviewed animation recording or a claim of all shader/resource-pack compatibility.
- [Implementation and limits](docs/reports/RAINBOW_CLOTH_2026_10_26.md). No PR/merge authorized or performed.

## Previous placed rainbow-cloth validation — 2026-10-26

Verified source **`871b696532df3882559057670dcb5ed0a255570e`**; release **1.1.0 / MC 26.3**, compatibility channel **`26_3_revision_12`**. Update client and server together: the new `rainbow_cloth` registered block state is not compatible with prior revision11 builds, even though business payload layouts and release label are unchanged.

- Sneak-use an exactly named **`jeb_`** name tag on the table to enable a reusable cosmetic key. No tag consumption or table rename. Dyeing exits the mode and retains ordinary colour mixing/consumption; drops/placement preserve the flag alongside dye and freeze metadata.
- Early Fabric use-block callback handles vanilla's sneaking bypass; spectator/build/vanilla interaction guards retained. No new Mixin or colour payload. Normal static block/item model unchanged; only rainbow states render five dynamic cloth faces, using vanilla 26.3 sheep `ColorLerper` and a bounded world-time/partial-tick phase, not per-frame chunk rebuilds.
- [compile-check 40263622160](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/runs/40263622160): **155 JUnit, zero failures/errors/skips**. [build 40263622260](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/runs/40263622260): all four business jobs and both diagnostics successful; real server/Chinese SDL client and no-viewer compile pass.
- Final diagnostics contain **RAINBOW CLOTH PASS**, **CATALOGUE WORLD PASS**, **CLIENT SMOKE PASS**. Server callback, real loot/component/placement-state restoration, ordinary dye exit/consumption and reactivation tested; not a claim of human input, full right-click network-path testing or all shader/resource-pack geometry combinations.
- [Installable build](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/runs/40263622260/artifacts/12625302256); [native logs/menu screenshots](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/runs/40263622260/artifacts/12625970306). Dedicated animated visual recording not produced/manually inspected. Only 26.3 has been validated; other versions require renderer/API adaptation.
- [Implementation, persistence and cross-version limits](docs/reports/RAINBOW_CLOTH_2026_10_26.md); [protocol transition](docs/PROTOCOL_HISTORY.md). Prior UI/engineering work remains implemented. No PR/merge authorized or performed.

## Previous framed-icon UI validation — 2026-10-26

Verified source **`3bd875ccfe0ce0b8001e404b33f193aa09953f9d`**; **1.1.0 / MC 26.3 / revision 11** unchanged.

- [compile-check 40261676215](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/runs/40261676215): **148 JUnit, zero failures/errors/skips**. Ten new geometry contracts cover safe insets, non-overlap, icon/text ink, popup containment, stable list bottom and lock-panel bounds.
- [build 40261676234](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/runs/40261676234): all four business jobs and both diagnostics successful. Real server and Chinese SDL client pass through actual select-visible/permanent-lock icon hit regions and authoritative owner sync.
- Second image round, first candidate selected after both first-round candidates were rejected. Shared gold/purple/teal nine-slice skin for filter fields, icon actions and dropdown frames; no flat hover/disabled rectangles covering decorative borders.
- Sort direction/reset/select-visible/clear and five lock actions are icon-only. Current filter values/counts remain text; action names, costs and batch warnings stay in tooltips. Count is now read-only; independent select-visible icon replaces its old click action.
- Pure `TomeDirectoryLayout` is shared by painting, clipping, hit tests, panel origins, list offset and client probes. Four-pixel panel safe area and three-pixel button ink inset; popup capped to five visible rows; list bottom unchanged, with slightly fewer book rows to allow real padding.
- Local UI Python **11/11**, static9 groups, architecture6, sync17, mixin4, diagnostics3 and hygiene pass. Eleven locales contain **1016 keys**. Previous catalogue functionality and six engineering splits retained.
- [Installable build](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/runs/40261676234/artifacts/12623854370); [Chinese screenshot/logs](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/runs/40261676234/artifacts/12625020403). Final material/geometry preview inspected, actual CI game screenshot not manually inspected; automated evidence does not cover all resource-pack fonts/DPI/mod GUI combinations.
- [Root cause, design and evidence](docs/reports/CATALOGUE_ICON_FRAME_2026_10_26.md). No PR/merge authorized or performed.

## Previous tome catalogue validation — 2026-10-26

Verified source **`ba1cb429f63d38904a2844a88402cfc83b757082`**; **1.1.0 / MC 26.3 / revision 11** unchanged.

- [compile-check 40257006923](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/runs/40257006923): **138 JUnit, zero failures/errors/skips**, including 15 new catalogue/queue contracts.
- [build 40257006935](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/runs/40257006935): all four business jobs successful; compile/server diagnostics successful. Real server and Chinese SDL client pass.
- Shift shows all enchantments with bounded Shift-wheel pages and shelf/slot identity. Intersecting enchantment/remaining/total/lock filters, scrollable choices and bidirectional sorting; stable selection, Shift multi-select, select visible and clear.
- Confirmed, paced multi-book lock/unlock reuses the authoritative per-book protocol. Hidden/removed selections are pruned; observable replacements are skipped. Costs/permissions remain per book, with partial completion possible; not an atomic transaction or new persistent item-identity system.
- Read final **CATALOGUE BOUNDARY PASS**, **CATALOGUE WORLD PASS** and **CLIENT SMOKE PASS** markers: actual shelf books, table-tab/selection/button clicks, paced permanent locks and owner/live-state sync.
- User selected first of two AI toolbar candidates; selected texture is integrated. Eleven locales contain **1013 keys**. Previous UI and six-area engineering splits remain in place.
- [Build artifact](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/runs/40257006935/artifacts/12623425899); [smoke artifact with `run-client-smoke/screenshots/tome-catalogue-zh_cn.png`](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/runs/40257006935/artifacts/12622388969). Actual game screenshot not manually inspected; network-restricted artifact download and broader human visual/multiplayer matrices remain evidence limits.
- [Catalogue design, ownership, CI and limitations](docs/reports/TOME_CATALOGUE_2026_10_26.md). No PR/merge authorized or performed.

## Previous UI validation — 2026-10-26

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

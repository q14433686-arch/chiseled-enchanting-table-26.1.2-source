# Current Baseline

**Date:** 2026-10-26
**Minecraft Version:** 26.3
**Fabric Loader:** 0.19.5
**Fabric API:** 0.162.0+26.3
**Gradle:** 9.5.1
**Loom:** 1.17-SNAPSHOT
**Java:** 25
**Mod Version:** `1.1.0`

This is an **unofficial community port** of Sacramentix's MIT-licensed mod, not affiliated
with or endorsed by the original author. The repository name contains `26.1.2` for historical
reasons; this branch targets **26.3**.

## Research foundation in progress — 2026-10-26

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

## Previous engineering validation — six-area split, 2026-10-26

Verified implementation SHA: **`3a3b0076bf56abf70ef9a29b2eb3ab173505d87f`**.
Release **1.1.0**, MC **26.3**, wire **`26_3_revision_11`**.

- [compile-check 40250562480](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/runs/40250562480): **117 JUnit tests, 0 failures/errors/skips**, including ten new engineering split contracts.
- [build 40250562244](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/runs/40250562244): all four business jobs and both diagnostics successful; full jar, optional-viewer-disabled compilation, real dedicated server and real SDL-window/integrated-client interactions.
- All six requested responsibility splits are implemented: economic quotes/payment, table panels/tooltips, config controls/trace views/drag state, inscription/cleanse candidate models, config/policy JSON and presets, queue encoding/recovery.
- Real-client coverage retains SDL committed CJK text, preset/save-ACK/reopen, quick-move, overlay/focus and persistent target. Added real inventory/XP/receipt and registry-backed candidate tests; their **ECONOMIC BOUNDARY PASS** and **CANDIDATE BOUNDARY PASS** markers have been read from Checks diagnostics.
- Helpers consume explicit capabilities or detached row data, not entire Screen/Widget instances. New owners as well as coordinators have growth/dependency gates; six positive/negative architecture tests are in CI.
- CI logs remain artifacts plus bounded API-readable diagnostic Checks, **never Git write-back**. Mixin inventory below remains generated from registered configs.
- [Six-area completion, exact ownership/API decisions, final CI evidence and cross-branch feedback](docs/reports/ENGINEERING_SPLIT_COMPLETION_2026_10_26.md). [Previous engineering round (historical 107-test evidence)](docs/reports/ENGINEERING_BOUNDARIES_2026_10_26.md).
- This is automated Linux/Xvfb evidence, **not** human platform-IME candidate-window or multiplayer concurrency validation. Documentation-only follow-ups do not change the verified code SHA. No PR/merge was authorized or performed in this round.

## Latest synchronization — 2026-10-26 (supersedes historical rounds below)

- Source locked to **`9f0e13bd75e31f7d1d8a569302d0f57abd2ce840`**, compared against previous source **`3e61f5de3ba7679ccbc92b73443dead149c96840`** and target **`1f2dd036ff35ea72712240b312a9c9b7e4e249bf`**.
- Release remains **1.1.0 / MC 26.3**; wire is **`26_3_revision_11`**, not the source branch's revision_17. Update both ends.
- Full tome/page/preset/data refactors integrated with 26.3 APIs preserved. Additional fixes cover six silent datapack overrides, external default-pinning packs, isolated loot-build inputs and failed reloads, hard wire-byte bounds, Creative shelf extraction, particle lifecycle and Iris F3+T invalidation.
- Earlier input/preset-fix evidence (superseded by engineering validation above), source **`aa898aa`**: compile-check **40243203302**, **97 JUnit, zero failed/skipped**; build **40243203274**, all four jobs successful (full artifact, optional viewers disabled, real server load/reload/ritual probes). Follow-up fixes start/release SDL search input and distinguish Fabric metadata id `chiseled-enchanting-table` from resource namespace `chiseled_enchanting_table`, restoring bundled-option/preset editability without bypassing external policy.
- [Seven-path audit, cross-branch feedback and validation boundaries](docs/reports/SYNC_9F0E13B_SEVEN_PATH_AUDIT_2026_10_26.md). That earlier synchronization evidence is recorded there; current engineering evidence is linked above; historical run counts below are not evidence for this synchronization.
- Work is on the current Arena branch; no new PR or merge has been requested/performed for this round.

## Historical integrated baseline — option 3, source 3e61f5d (superseded)

The **entire** 26.1.2 gameplay/resource delta through **`3e61f5de3ba7679ccbc92b73443dead149c96840`** (PR #17/#18) is integrated on the current development branch, with architecture-first hardening. It is no longer a foundation-only change.

- Release remains **1.1.0 / MC 26.3**; wire is **`26_3_revision_10`** (2026-10-24 design-audit package: generation fade, essence scapegoat pricing, data-driven wait curve, wear-immune tag, single-source `BundledDefaults` codegen, bounded god-class splits; see `docs/reports/AUDIT_2026_10_24_DESIGN_PACKAGE.md`. Earlier revision_9 audit fixes: ritual completion revalidates against the actual item, whole-book combine pricing, fail-closed cost resolution with explicit `material_free`/`xp_only` datapack declarations, cost-table byte budget + `costTableComplete`, ritual load references synced, all business channels revision-suffixed). Update both ends even if the visible version label is unchanged.
- One effective `RuntimeFrame`, stored-preference/effective/draft separation, strict policy/reload and persist-before-publish; startup components remain restart-only.
- Shared shelf recovery is wired to one per-world-session 20-tick scheduler; no legacy absolute-time credit.
- Persistent inventory and bounded owner/session/target-bound rituals; XP/rush/Infinity slip; registry failures quarantine instead of deleting orders.
- Depleted-tome chest loot/weathering/librarian commissions, weighted epiphany, new advancements, Grimoire and 11 locales.
- Source defaults: regen **24,000 / 12,000 / 3,600 seconds**, **38/42** shelves; new configs datapack-first, creative wear retained. Back up old worlds; explicit old preferences are preserved.

[Full architecture/cross-branch report and validation boundaries](docs/reports/CROSS_BRANCH_3E61F5D_ARCHITECTURE_REVIEW.md). Final game source `9243790` passed build **40007636762** (all four jobs) and compile-check **40007636849**, **52 JUnit**, plus real persisted-ritual/completion/quarantine/stored-input-break probes. Later changes are CI log returns, documentation and explanatory locale text. GUI/LAN/multiplayer/performance are not claimed as manually tested. No PR has been opened/merged in this round.

## Historical merged gameplay baseline — previous option 1

User selected faithful source defaults plus necessary hardening. The complete increment through **`3e081f31f262dc13f6a92f0a50313e12f6af28c0`** is merged; release **1.1.0**, target **MC 26.3**, protocol **`26_3_revision_5`**. This supersedes the previous “audit only / awaiting choice” state.

[Current implementation and evidence](docs/reports/SYNC_26_1_2_3E081F3_OPTION1.md): source **181e996** passed build **39904722889** (four jobs), compile-check **39904722983**, **29 JUnit**, **15 Python**, and the real no-player server/reload/component/loot smoke. Real GUI and multiplayer interaction remain unverified. Older 1.1.1 records are historical, not current release artifacts.

Back up old worlds: defaults include mother-tome wear, external/unmarked refund scaling and configuration-over-datapack priority. There is no retroactive provenance inference. Update both client and server to revision 5 even though the displayed release label is 1.1.0.

## Historical source boundary — 3e081f3 (superseded)

All gameplay, assets, languages and viewer functionality from the 26.1.2 branch through
`3e081f31f262dc13f6a92f0a50313e12f6af28c0` (1.1.0) are synced, with 26.3 adaptations.
See the [historical port report](docs/reports/SYNC_26_1_2_3E081F3_OPTION1.md) for the exact boundary, migration notes and verification.
[Player Wiki](docs/WIKI.md) describes resonance, Treasure Epiphany/freezing, XP, material
alternatives, book upgrades, Blank Overwrite and transformations.

- Dual-track 7×5×7 library resonance; Treasure Epiphany with reroll and persisted freeze.
- Dual XP payment modes, yield-scaled costs, multi-material alternatives, Arcane Condensate,
  Depleted Tome and Blank Overwrite.
- Painted GUI and XP animations, converging bookshelf particles, dyeable table appearance,
  11 locales, expanded advancements/recipes.
- Server-authoritative enchantment/upgrade/transformation costs pushed on join and reload;
  JEI and REI provide both enchantment and transformation lookup in both directions.
- Flat anvil prior-work penalty; same-level book merge blocking; stacked-book remainder fix.
- JSON-driven chest/archaeology loot, depth-based mineshaft Efficiency, weighted structure
  bookshelf replacement/fill with the worldgen-safe NBT pipeline (no live chunk access from workers).
- The generated Registered Mixin inventory at the end of this document is authoritative; source existence/compilation checks and documentation inventory checks are separate gates.

## Target-specific contracts (must not regress on future syncs)

- StructureProcessor is an interface, codecs register into STRUCTURE_PROCESSOR;
  `processBlock` takes template BlockPos; `markPosForPostProcessing` uses capital P.
- Renderer uses rotate/rotateDegrees, nine-argument submitModel and separate crumbling overlay.
- Loot builders use ContextIntProviders/UniformContainerBase; conditional functions take
  Optional<Holder<LootItemCondition>>; LootContext uses getOptional.
- Payload collections use explicit VarInt count + elements; overflow drops supply Prediction.
- Loot tables use condition/modifier with type dispatch; recipe_crafted uses recipes lists.
- Keep Minecraft, Loader, Fabric API, Loom, Gradle and Architectury on their **26.3** versions.
  Verified 26.3 pins: JEI 31.16.0.89, REI 26.3.824. Do not reuse 26.1.2 viewer pins.

## Validation and release

`bash scripts/check_release_consistency.sh --strict`, `python3 scripts/ci_static_checks.py`,
`python3 scripts/compat_check.py` and `python3 scripts/test_sync_26_3.py` are offline gates.
GitHub Actions is the authoritative Java compilation check: the sandbox cannot reach the
Fabric/Mojang Maven repositories. A green older 1.0.3 build is not evidence for this sync.
Real dedicated-server and selected real-client interactions have passed for the SHA above. Remaining platform-specific/manual and multiplayer coverage limits are in NEXT_TASK.md.

Version declarations must agree in gradle.properties, README.md, fabric.mod.json,
publish workflow and its docs/ci copy. Do not copy historical compile reports from 26.1.2.

## Historical builds and earlier scope

The sections below predate the current option-1 port. Their pending-runtime notes, versions and test counts are historical, not current status.

### Verified sync build

- `compile-check` run **39824070708**, source commit `7eb9827`: success for both main and client.
- `build` run **39824070633**, same source: static checks, full Gradle build and the
  no-JEI/no-REI compile job all succeeded. Artifact is available from that run.
- JEI **31.16.0.89**, REI **26.3.824** resolved in that compile and are now pinned here;
  the final pin-only build is independently checked by CI.
- Offline: 12 release consistency checks, five sync regression tests, nine mixin references,
  98 translation keys in each of 11 locales, Markdown links and Tier-1 compatibility all pass.
- Tier-2 registry/tag existence checks need the Minecraft jar (unavailable in sandbox);
  gameplay/runtime smoke tests have **not** been performed.

Final pinned-dependency verification (source commit `a0412e9`):
`compile-check` **39824564921** succeeded; `build` **39824565189** completed
all three jobs successfully (static checks, full Gradle build, no-viewers compilation).
The historical `build-reports/compile-java.log` (now replaced by artifacts/Checks diagnostics) recorded both exact pinned viewer versions and
`BUILD SUCCESSFUL`. This confirms compilation/packaging, not in-game runtime behaviour.

## 1.1.1 world-load hotfix (2026-10-23)

User runtime log exposed two missed data-codec changes: recipe_unlocked needs recipes,
and loot condition takes a single predicate holder (not an array). Both bundled files are
fixed, preserving all copy-components fields including freeze data. The offline gates now
cover both exact failures. Historical green compilation above did not validate datapack codecs.
See docs/reports/WORLD_LOAD_1_1_1_HOTFIX.md. Runtime retest is still required.

## Incremental 26.1.2 sync — 2026-10-23 (version remains 1.1.1)

Source advanced from `0304f9d` to `b78b244` (PR #13). Includes disenchanting/cleansing,
configurable shelf progression and live sync, illustrated Grimoire, new recipes/tags/assets,
viewer extensions, 307 translation keys per locale, headless economy/config tests and
stronger static checks. New 26.3 adaptations: Minecraft.gui screen navigation and explicit
VarInt collection codecs for LiveShelfStatePayload. Every new recipe advancement is converted
to the 26.3 recipes list; previous world-entry hotfixes remain intact. Source-branch tooling,
version declarations and historical build logs are not copied. Current verification and
runtime caveats: docs/reports/SYNC_FROM_26_1_2_INCREMENTAL_REPORT.md.

Incremental source `aadcecc` verified: full build **39888562095** (including imported JUnit
suite and no-viewers compile) and main/client compile-check **39888562097** are successful.
The sync now has 11 Python regression tests; all 307 keys in each of 11 locales and 297
code-referenced translation keys pass the updated gates. Version remains **1.1.1**.

<!-- BEGIN GENERATED MIXIN INVENTORY -->
## Registered Mixin inventory (generated)

**Registered Mixin declarations: 14.**

Source: `fabric.mod.json` registered configs, including common/client/server sections.
Regenerate with `python3 scripts/check_baseline_mixins.py --write`.

| Config | Section | Class |
| --- | --- | --- |
| `chiseled-enchanting-table-port.mixins.json` | `mixins` | `chiseled_enchanting_table.mixin.anvil.RemoveEnchantMerge` |
| `chiseled-enchanting-table-port.mixins.json` | `mixins` | `chiseled_enchanting_table.mixin.anvil.RemoveXpCostScaling` |
| `chiseled-enchanting-table-port.mixins.json` | `mixins` | `chiseled_enchanting_table.mixin.bookshelf.AddRandomTick` |
| `chiseled-enchanting-table-port.mixins.json` | `mixins` | `chiseled_enchanting_table.mixin.bookshelf.BookshelfCreativeExtractionMixin` |
| `chiseled-enchanting-table-port.mixins.json` | `mixins` | `chiseled_enchanting_table.mixin.bookshelf.LockedBookContainerMixin` |
| `chiseled-enchanting-table-port.mixins.json` | `mixins` | `chiseled_enchanting_table.mixin.enchanting_table.DisableBookEnchanting` |
| `chiseled-enchanting-table-port.mixins.json` | `mixins` | `chiseled_enchanting_table.mixin.grindstone.GrindstoneBookDepleteMixin` |
| `chiseled-enchanting-table-port.mixins.json` | `mixins` | `chiseled_enchanting_table.mixin.loot.ReloadableServerRegistriesMixin` |
| `chiseled-enchanting-table-port.mixins.json` | `mixins` | `chiseled_enchanting_table.mixin.structure.LevelChunkPostProcessMixin` |
| `chiseled-enchanting-table-port.mixins.json` | `mixins` | `chiseled_enchanting_table.mixin.structure.StructurePieceAccessor` |
| `chiseled-enchanting-table-port.mixins.json` | `mixins` | `chiseled_enchanting_table.mixin.structure.StructurePieceMarkMixin` |
| `chiseled-enchanting-table-port.mixins.json` | `mixins` | `chiseled_enchanting_table.mixin.structure.StructurePlaceSettingsMixin` |
| `chiseled-enchanting-table-port.mixins.json` | `mixins` | `chiseled_enchanting_table.mixin.structure.StructureTemplatePoolProjectionMixin` |
| `chiseled-enchanting-table-port.mixins.json` | `mixins` | `chiseled_enchanting_table.mixin.villager.VillagerInfinitySanitizeMixin` |
<!-- END GENERATED MIXIN INVENTORY -->

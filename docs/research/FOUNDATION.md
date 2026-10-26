# Research foundation — implementation boundary (2026-10-26)

[Gameplay loops, mechanism removals and implementation roadmap](MASTER_PLAN.md) are the authoritative plan.

## Direction, not a new enchantment-effect patch

The research system is the prerequisite architecture. Sharpness, Protection, maximum-durability
cultivation, loot and display-surface processing are downstream content/adapters, not its owners.

Agreed direction:
- breadth of distinct knowledge opens stages; quality of relevant knowledge supports specialization;
- choose a hypothesis, use an experimental ability to solve a real problem, retain an explicit result;
- no kill/block counters, paid-material-only tasks, timer gates or anti-automation arms race;
- stable personal understanding plus transferable books; some advanced specializations require practice;
- routine production must not destroy learned knowledge; no new routine mother-tome wear;
- exploration concerns phenomena, not repeated chest rolls;
- precise disenchantment can supply relevant evidence, not an infinitely farmable global stat.

## Implemented in this change

`research/` is an additive foundation, registered from the mod initializer:

| Owner | Responsibility |
|---|---|
| `ResearchCatalog` | immutable topic graph, alternatives, conclusion prerequisites, bounds and cycle detection |
| `ResearchEngine` | preview, choose hypothesis, record observation, conclude, cancel; deterministic transitions |
| `ResearchJournal` | immutable UUID-owned state payload; completed conclusions and unfinished attempts |
| `ResearchJson` | explicit schema-1 catalog/journal validation, stable save ordering |
| `ResearchStore` | world-local UUID storage, parse-before-publication, bounded files, temp/atomic replacement |
| `ResearchRuntime` | server-thread gate, authorized workspace adapter, registered server evidence verifiers, resource/save lifecycle |

There is no enchantment identifier, price formula, item mutation, random roll or timer in the engine.
Conditions distinguish shelf count, **distinct** knowledge breadth and best quality per knowledge id.
Duplicate books cannot inflate breadth. Named prerequisite **conclusions**, not collection sizes, form
stage nodes. No final production-stage thresholds are chosen yet.

### Lifecycle

1. Preview reports missing prerequisites, breadth, shelves and knowledge depth together.
2. Begin checks live authorized workspace conditions and selects a defined hypothesis.
3. A registered server adapter validates actual evidence; an unknown/false verifier cannot advance.
4. Field observation does **not** require keeping the table menu open. Relevant contrary results are
   retained too; switching hypothesis at the same definition revision preserves observations.
5. Conclude explicitly rechecks the live workspace, prerequisites, revision and required evidence.
6. Completion stores a stable conclusion id. Leaving/rebuilding the library does not unlearn it.
7. Cancellation discards the unfinished attempt only. Completed conclusions survive.

Observations are unique semantic facts, not repeatable counters. Replayed evidence is idempotent.
A hypothesis is a supported investigation route with its own evidence and resulting conclusion;
it is not yet a procedural puzzle generator. A contrary observation can support another route,
but cannot satisfy the chosen route unless its requirements actually match.

### Extension contracts

Production catalog path: `data/<namespace>/chiseled_enchanting_table/research.json`; currently the
runtime reads `chiseled_enchanting_table` namespace as one overrideable catalog. It does **not** yet
merge arbitrary namespace topic files. Root fields: `schema: 1`, `topics: [...]`.
See the **test-only** catalog at `src/clientSmoke/resources/data/chiseled_enchanting_table/chiseled_enchanting_table/research.json`
for an exact executable schema example. It is not included in the released jar.

Each topic has `id`, positive `revision`, `title`, `question`, `prerequisites` (conclusion ids),
`breadth`, `shelves`, `depth` (knowledge id -> required quality) and `hypotheses`.
Each hypothesis has `id`, unique `evidence` ids and a stable `conclusion` id.
All keys are namespaced. Title/question keys are content references; rendering/localization is not
implemented by this foundation. One topic completes through one alternative; independent research
outcomes should be separate topics, not alternatives that must all be collected.

- `setLibraryAccess`: a server table adapter must authorize the player/menu/table/range/dimension,
  scan loaded positions only and return an immutable library snapshot. The production default is `ResearchWorkspace.resolve`, which denies absent/invalid menus and scans the authorized workspace on demand.
- `registerEvidence`: a trusted server event adapter must verify the actual event/context. A string
  from a client is **not** evidence. The gateway has no completion/grant packet or command.
- `observe`: invoked by that event adapter, not a generic client request. Replay is harmless.
- `conclude`: invoked at the workspace after observations; does not consume items or grant effects.
- Downstream unlock checks must consume completed conclusions, not repeat the research logic.

Definitions are staged and published on overall successful datapack reload (or initial server
startup). A changed topic revision cannot finish an old attempt; explicit restart adopts the new
revision. Removed definitions do not erase saved conclusions/attempts. Content authors must bump
revision whenever evidence semantics change and use new conclusion ids for genuinely new learning.

### Save and failure behavior

World file: `data/chiseled_enchanting_table_research.json`, schema 1, player UUID keys. This is not
account-global: different saves have different understanding. No player entity NBT or existing
book components are rewritten. Save on world-save and stopping; no per-observation disk write.

Malformed/oversized/unknown-schema saves disable research for that server instance and preserve the
original file; they never become an empty writable replacement. Save errors retain dirty state
for a later retry during the running session. Atomic rename falls back only when unsupported.
This is ordinary save-cycle durability, **not** crash-proof per-action journaling or automatic backup recovery.

## Deliberately NOT done yet

This is a registered and tested **foundation**, not a playable full research release:
- no production topics, research tab, client synchronization or study-book distribution;
- the live table library adapter is now implemented; real gameplay evidence providers are still absent;
- no experimental-ability issuance, revocation or result feedback yet;
- no application of research unlocks to existing production or loot;
- no removal/migration of existing tome wear, generations, regeneration or economic locks;
- no higher enchantment caps, Sharpness damage/status effects or Protection specializations.

The existing game remains playable under its previous rules. Claiming the new design has replaced
those rules would be incorrect. The test-only topic/verifier exercise the gateway, not a real
research task and not player-facing content. No protocol bump is needed until client payloads or
registered shared state actually change; release remains 1.1.0 / MC 26.3 / revision12.

## Next dependency order

1. Implemented: bind an authorized, loaded-chunk-only table workspace and scan distinct books/quality; finish native verification.
2. Add read-only research views and server-authoritative selection/conclusion actions, with explicit
   protocol revision and bounded synchronization when those payloads are introduced.
3. Implement an end-to-end introductory **research** task: hypothesis, bounded experimental capability,
   a meaningful problem in the world, evidence, feedback, conclusion; not an advanced damage effect.
4. Validate that this task is intelligible and not a repetition/material tax before expanding topics.
5. Wire production unlocks and stable study books, then migrate superseded maintenance rules.
6. Only then fill the framework with advanced enchantment specializations.

## Verification scope

JUnit covers graph/conditions, one-shelf starting conditions, distinct breadth, evidence replay,
contrary results, workspace recheck, cancellation, definition revision, immutable snapshots,
versioned roundtrips, UUID separation, save failures and corrupt-file preservation.
Native smoke exercises the real reload listener and player gateway with a test-only catalog and
controlled verifier. Its marker is `RESEARCH FOUNDATION PASS`. This proves the integration contract,
not the quality/completion of a gameplay research task.

### Current CI evidence

Functional source `2278fc053c6e2bf32d6a06214ccafbd3afd00ffa`:
- compile-check 40302066798: success, **181 tests / 0 failures / 0 errors / 0 skipped** (26 new research tests).
- build 40302066671: static, Gradle build and no-viewer compile succeeded.
- Native build 40302066671 subsequently succeeded; diagnostics contain `RESEARCH FOUNDATION PASS` and `CLIENT SMOKE PASS`. This closes the previous pending result, not the new workspace probe.
- Local static9, architecture7, sync17, UI11, Mixin4, diagnostics3, release12 and diff checks passed.

### R1 live workspace adapter

`ResearchWorkspace` authorizes the current table menu/world/range, spectator/build and vanilla
interaction checks, then uses configured loaded-only scan offsets, breathing room and optional LOS.
Normal and chiseled bookshelf blocks each count as one shelf. Stored enchanted books contribute
independent namespaced enchantment ids at their highest quality, not stack sizes or sums.
No wear/generation/lock filter removes knowledge; no item is modified. `ResearchKnowledge` is the
pure bounded aggregator. This adapter does not claim universal third-party territory compatibility.

Source `6bb5276`: compile-check 40364870519 succeeded with **186 tests, zero failures/errors/skips**;
build 40364870525 has passed static, full build and no-viewer compile. Native workspace verification
is still pending at documentation time. No production topic/UI/unlock enforcement has been added.

### Outcomes and human review (2026-10-27)

[Concrete human-reviewed decisions](reviews/TARGET_FIRST_REVIEW_2026_10_27.md) now constrain content.
Optional root `outcomes` entries contain `conclusion`, `enchantment`, `branch`, `max_level`;
`sample_analyses` entries contain `topic`, `hypothesis`, `evidence`, `enchantment`, `minimum_level`.
Validation rejects unknown conclusions, duplicate grants/routes and fabricated evidence mappings.
Manufacturing maxima are separate from registry maxima and pricing. No existing production gate
is switched yet. `ResearchGoalView` resolves target-first routes and next actions, not GUI widgets.

Correction: workspace run40364870525 failed at the wooden-shelf assertion. This turn adds explicit
vanilla bookshelf recognition and same-height fixture placement with diagnostics; final native
verification must be read from the new run, not inferred from pure tests.

The patched workspace/outcome source976aae1 subsequently passed the full native run40372748679,
including `RESEARCH WORKSPACE PASS`. Source46836b9 adds the target-first model and passes207JUnit
in compile40374462742; its full native run40374462984 remains pending at documentation time.

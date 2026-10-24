# Sync Report — `26.3` line → `26.2` line (2026-10-23, second pass 2026-10-24)

> **Scope:** port everything that landed on the sister `26.3` branch after this line's last sync
> (`6a8f371`, the "provenance & live configuration" batch plus the runtime-audit hardening), onto
> **this** 26.2 branch, keeping every 26.2 toolchain pin and every 26.2-only API call intact.
> **Version:** unchanged — `1.1.0`. The user's standing instruction on this pass was
> `版本号不变`, and `1.1.0` had not been released from this branch yet, so the imported feature batch
> is folded into the same release (see `CHANGELOG.md`).
> **Second pass (2026-10-24):** the `26.3` branch then advanced `6a8f371..d2abe21` (source
> `3e61f5d`, PR #17/#18) adding the rule architecture, persistent table inventory, queued rituals,
> tome regeneration, depleted-tome world content and wire revision **6**. That increment is ported
> too — see **§9**, which supersedes the per-file numbers in §1/§2 for the current tree.
> **Verdict:** the feature batch is complete and green locally. What was deliberately **not** taken
> is the 26.3 *toolchain* (Minecraft 26.3 / loader 0.19.5 / FAPI 0.162.0 / JEI 31.x / REI 26.3.x),
> the 26.3-only API rewrites (§4), and the 26.3 build scratch artifacts (§6). The branch diff is
> 67 files, +8,350 / −553.

---

## 1. How the port was done

The three mod branches share no merge-base inside the shallow clone, so `git merge`/`rebase`/
`cherry-pick` across them is not available and was not attempted. Instead:

* a three-way **content port** (`git merge-file`, base = the 26.1.2 tip `b78b244`, ours = this
  branch's `HEAD`, theirs = `origin/26.3`) over the 60 paths that both lines touch, and
* `git show origin/26.3:<path>` for the files that only exist on 26.3 (new code), and
* hand resolution for the five files the merge could not reconcile
  (`build.gradle`, `docs/WIKI.md`, `fabric.mod.json`, `gui/ChiseledEnchantingTableScreen.java`,
  `scripts/compat_check.py`), plus targeted fixes for every 26.3-only API that had leaked in.

Every 26.3-only API leak is listed in §4 — that table exists because the port is written against a
different Minecraft line and *will* re-introduce them if it is ever replayed.

## 2. What was ported (change-by-change)

### 2.1 Provenance & fair refunds

| 26.3 change | 26.2 landing |
| --- | --- |
| `mechanics/enchanting/EnchantmentProvenance` — per-level, per-source (paid / looted / creative) attribution stored on the stack, so a disenchant only refunds what was actually paid | new file, unchanged logic |
| `XpCostConversion` / `DisenchantmentApplication` refund paths read provenance instead of assuming every level was purchased | `DisenchantmentApplication`, `XpCostConversion`, `BookUpgradeApplication`, `EnchantmentApplication` |
| external-refund scale (refund for levels that came from outside this table) | `disenchanting.json` `external_refund_scale: 0.25`, new payload field `disenchantExternalRefundScale` |
| anti-arbitrage potential table kept strictly telescoping when provenance splits a level | `EconomyAndConfigVerificationTest` covers the split cases (now 16 tests) |

### 2.2 Master Tome wear

* `mechanics/resonance/MasterTomeWear` (new): a Master Tome has `max_tome_uses = 16` charges
  (`library_resonance.json`), each use costs `4` copies (`cost_per_use`), draws one charge
  (`step_down`), and can be consumed on the last use; charge count is rendered as lore and a
  depleted tome is replaced rather than left as a blank. Line-of-sight filtering of the shelf scan
  (`optional_los_filter`) is honoured by the charge accounting.
* Applies to `BookUpgradeApplication`, `EnchantmentApplication` and the transformation path.

### 2.3 Live, per-player unlock catalogs

* `ChiseledEnchantingMenuPayloads`: `LiveShelfStatePayload.container_id` + `targets(containerId)`
  so a menu only reacts to its own container, `applyTransformationSnapshot(...)` for incremental
  updates, and `AvailableEnchantmentPayload.buildTransformationDisplays(player)`.
* `EnchantCostSync`: a `ServerTickEvents.END_SERVER_TICK` hook (every 20 ticks) diffs each player's
  transformation displays against a `WeakHashMap<ServerPlayer, …> LAST_DISPLAYS` and pushes only on
  change; the map is cleared on `SERVER_STOPPED` and per-player on `DISCONNECT`.
* The cost payload now carries `transformations` alongside `costs`; an **empty server catalog is
  authoritative** (`SyncedEnchantmentCosts.transformationDisplays()` / `hasServerData()`), so a
  server that ships no transformations no longer leaves the client showing its local file values.
* On `END_DATA_PACK_RELOAD` and on a config change, every open table menu for this mod is closed
  (vanilla returns the input stacks) instead of keeping a stale snapshot of IDs/inputs/thresholds.

### 2.4 Protocol capability channel & payload hardening

* `menu/EnchantingProtocol` (new): revision string `26_2_revision_5`, a zero-byte
  `Capability` payload registered in **both** directions, and `incompatible(peerHasMod,
  peerHasRevision)`. A peer that speaks any cost channel but not this revision is disconnected at
  `ServerPlayConnectionEvents.JOIN` with `MISMATCH_MESSAGE`.
* `menu/PayloadLimits` (new): `readCount(buf)` rejects negative or `> 65_536` collection counts
  before any allocation/iteration. Applied to every length-prefixed collection in
  `EnchantCostTablePayload` and `ChiseledEnchantingMenuPayloads`.
* Cost channel renamed to `enchant_cost_table_26_2_revision_5`. The **old** `enchant_cost_table`
  id is kept as a *query-only* `LEGACY_COST_TYPE` — deliberately never registered for decoding, so
  an older server cannot make this client parse its join-time packet before the JOIN callback can
  disconnect. Because 26.2 shipped exactly one previous wire layout, there is no
  `..._26_3_revision_4`-style second legacy probe (26.3 carries one; it never existed here).
  `NetworkCompatibilityTest` asserts the two ids differ.
* Config channel ids (`sync_runtime_config_*`, `update_runtime_config_*`) carry the same revision
  suffix; the config save path acks a `requestId`, and rejects unknown option types.

### 2.5 In-game configuration UI

* `config/ModRuntimeConfig` (new): one record for the whole runtime configuration, persisted to
  `config/chiseled_enchanting_table.json` (the file `ModBootstrapConfig` already owned), with
  `override_datapacks` (default **true**), clamping/`normalizeAfterEdit` invariants, atomic
  temp-file replace, corrupt-JSON rejection (log + defaults), and log-instead-of-swallow on every
  failure path. `ModBootstrapConfig` is now a thin delegating wrapper.
* `config/ConfigCatalog` (new): declarative schema — 5 categories, session/authority modes, parent→
  child `activeWhen` locking with localized reasons, effect scopes (live / new-chunk / restart).
* `config/ModConfigNetworking` (new): bidirectional sync + `/cet config`, `/cet reload-config`,
  `/cet reset-config`; edits are gated by `mayManage(dedicated, owner, permitted)` — a LAN host or
  an operator with `chiseled_enchanting_table:config/manage` (GAMEMASTERS) — and an authenticated
  dedicated server is never editable by a non-permitted client. `ConfigurationSafetyTest` pins the
  four allowed/denied combinations.
* `gui/config/ChiseledEnchantingConfigScreen` (new, client): categorized list with search,
  sliders, toggles and parent/child dimming; buttons built from `InputConstants` (not GLFW
  constants) so 26.2's input ids are used.
* Entry points: `CET` button in the table screen (`gui/btn_config.png`), a grimoire entry, and a
  Mod Menu plugin (`compat/modmenu/ChiseledEnchantingModMenuPlugin`).

### 2.6 Defaults mirrored from 26.3

`override_datapacks=true`; Master Tome `wear` → 16 uses / `cost_per_use` 4 / `step_down`; external
refund `0.25`; shelf gates `12` / `32` / `38`; batch max `64`; vanilla table mode `books`.

### 2.7 Bonus: a real 26.2-line bug the ported smoke test found

The first CI run of the new `server-smoke` job failed, and it failed on a genuine defect that the
26.2 line had shipped since `1.0.2`:

`RemoveEnchantedBooksFunction` and `DepthBasedEfficiencyFunction` built their `MapCodec` with
`MapCodec.unit(...)`. A unit codec ignores every unknown field, including the `conditions` array, so
a data pack that wrapped either function in an always-false condition had that condition **silently
dropped** and the function ran anyway — `remove_enchanted_books` ate books it was told to leave
alone. Nothing in the bundled data uses per-function conditions, which is why no earlier gate caught
it. The 26.3 line had already fixed it by routing the codec through `commonFields(...)`; the same
fix was applied here against the 26.2 signature (`List<LootItemCondition>` instead of a single
`Optional<Holder<LootItemCondition>>`):

```java
public static final MapCodec<RemoveEnchantedBooksFunction> MAP_CODEC =
    RecordCodecBuilder.mapCodec(i -> commonFields(i).apply(i, RemoveEnchantedBooksFunction::new));
```

`scripts/compat_check.py` now has a Tier-1 guard
(`check_loot_function_codecs_keep_conditions`) that rejects `MapCodec.unit(...)` (or a missing
`commonFields(...)`) in any `LootItemConditionalFunction` subclass, so datapack-facing condition
support cannot regress silently again.

### 2.8 Tests & CI

* `src/test/java/.../ConfigurationSafetyTest` (new, 4 tests): authority matrix + config round-trip
  safety.
* `src/test/java/.../NetworkCompatibilityTest` (new, 9 tests): revision/id separation, legacy
  channel never decodable, `readCount` bounds, cost payload round-trip including
  `transformations`.
* `EconomyAndConfigVerificationTest` extended (16 tests) — 29 JUnit tests after the first pass;
  the second pass (§9) adds `FullPortRuntimeTest` (6) and `RuleArchitectureTest` (13) for **52**.
* `scripts/server_smoke.py` (new, +x): boots a disposable flat world through `runServer`, issues
  `reload`, spawns named/dyed/frozen table loot and two always-false conditional loot-function
  fixtures, asserts the inspected NBT, verifies clean shutdown, and refuses to run against an
  existing world.
* `.github/workflows/compile-check.yml` now runs `compileJava compileClientJava **test**`.
* `.github/workflows/build.yml` gains the `server-smoke` job (`needs: static`, JDK 25, artifact
  upload, Contents-API write-back of `build-reports/server-smoke.log` on `arena/**`). The
  26.2-only `no-viewers` job is kept.
* `docs/ci/*` reference copies re-synced; `bash docs/ci/install.sh --check` is clean.

## 3. Protocol / compatibility consequences

* A 26.2 server and a 26.2 client from **before** this commit will refuse each other (expected: the
  wire layout changed), with an explicit message instead of a decode crash.
* The mod id, data-pack paths, item ids and world saves are unchanged, so existing worlds load.
* The legacy `enchant_cost_table` id remains registered for **sending** detection only.

## 4. 26.2 adaptations (why the port is not a copy)

| 26.3 code | 26.2 requirement here |
| --- | --- |
| `Minecraft.gui.setScreen(...)` / `#screen()` | screen lives on `Gui` (`gui.setScreen`, `gui.screen()`) |
| block `codec()` removed, `MapCodec` gone | the per-block `MapCodec`/`codec()` is **required** on 26.2 and was restored |
| structure processor as a record + rewritten codec | interface `implements StructureProcessor` + `codec()`, registered in `BuiltInRegistries.STRUCTURE_PROCESSOR` |
| `markPosForPostProcessing` renamed | capital-P `ChunkAccess#markPosForPostProcessing` (identical in all three lines) |
| `drop(stack, false, Prediction.PREDICTED)` | two-argument `LivingEntity#drop(stack, false)` |
| loot `modifier`/`condition` singular schema | plural `functions[]`/`conditions[]` on `function`/`condition` keys (bundled data **and** the smoke fixtures) |
| `ContextIntProviders` / `UniformContainerBase` | 26.2 loot provider classes kept |
| `ChatFormatting` absent on 26.2 | every styled line uses `Style#withColor(ARGB)` — 0 remaining references |
| `writeCollection`/`readList` removed upstream | keep the explicit `PayloadLimits.readCount` + per-element loop; never reintroduce a client-side list helper |
| SDL/GLFW mouse ids 1/3 | 26.2 GLFW numbering (0/1) in the custom screens |
| `scripts/test_sync_26_3.py` offline contracts | **not** ported: it asserts 26.3-shaped APIs (`markPosForPostProcessing` renames, no `MapCodec`, singular loot schema) which are exactly what this line must not do |

## 5. Discretionary "特色修复" — adopted vs skipped

Adopted (version-neutral, strictly safer on this line):

1. `PayloadLimits` bounds on every collection read.
2. Protocol capability channel + mismatch disconnect instead of a decode crash.
3. Empty-server-catalog-is-authoritative in `SyncedEnchantmentCosts`.
4. Live per-player unlock diff push (1 Hz) instead of full-table spam.
5. Close an open table menu on config change / datapack reload.
6. Master-Tome wear with charge lore; line-of-sight shelf filter.
7. Config authority gating (LAN host / permitted operator only), request-id ack, atomic save,
   corrupt-JSON rejection.
8. Unknown config option-type rejection; log-instead-of-swallow in `SyncedEnchantmentCosts`.
9. Both new JUnit suites and the server smoke test.

Skipped (26.3-specific, would break or mislead this line):

* the whole 26.3 toolchain and `gradle.properties`, its `-Xmx2G` JVM args, `build-reports/*.log`
  snapshots, and `scripts/test_sync_26_3.py` (§4);
* every 26.3-only API rewrite in §4 (applying them compiles nowhere on 26.2);
* the *relaxed* `compat_check.py` variants 26.3 carries for its singular schema — they would accept
  26.3-shaped data here and stop guarding the 26.2 schema.

## 6. Scratch artifacts deliberately not imported

`Dummy.*`, `fix*.patch`, `fix*.py`, `*.orig`, `*.rej`, `net/minecraft/**/*.class`,
`abstract_container_screen.txt`, `append.py`, `adapt_handler.py`, `adapted_handler.java`. Only the
three reviewed point-in-time patches in `docs/patches/` were copied, as documentation.

## 7. Verification

Local gates, all green on the ported tree:

| Gate | Result |
| --- | --- |
| `python3 scripts/compat_check.py` | `PASS (2 warning(s))` — both warnings Tier-2 (no MC jar in the sandbox) |
| `python3 scripts/ci_static_checks.py` | `PASS (5 check group(s), 0 warning(s))` — 9 mixins, 493 lang keys × 11 locales, 355 referenced keys, 36 markdown files |
| `bash scripts/check_release_consistency.sh --strict` | `12 passed, 0 warned, 0 failed` |
| `bash docs/ci/install.sh --check` | all four workflow copies identical |
| `grep -r ChatFormatting src` | 0 hits |
| conflict markers | 0 |

Java compile and the JUnit suites run in CI (`compile-check.yml`, now with `test`), and
`server-smoke` covers world load + datapack reload; neither can run in the sandbox (no Maven/
Mojang network).

## 8. Remaining known deltas

Everything left between this branch and `origin/26.3` is either the Minecraft version difference
itself (APIs listed in §4), the toolchain pins this line must keep (`minecraft_version=26.2`,
`loader_version=0.19.3`, `fabric_version=0.155.2+26.2`, `jei_version=30.47.0.239`,
`rei_version=26.2.821`, `rei_architectury_version=21.0.4`, `rei_math_version=0.6.1`,
`mod_version=1.1.0`), or 26.3-only branch documentation.

---

## 9. Second pass — 2026-10-24: `6a8f371..d2abe21` (source `3e61f5d`, wire revision 6)

The `26.3` branch gained 43 non-log changes after the first pass: 16 new files, 44 modified, plus
docs. That increment is described in the source branch's own
[`CROSS_BRANCH_3E61F5D_ARCHITECTURE_REVIEW.md`](CROSS_BRANCH_3E61F5D_ARCHITECTURE_REVIEW.md), which
also lists the R1-R8 / N1-N7 findings and explicitly marks them as **migration gates for 26.2**
rather than defects this line already had. Every gate was checked against the ported tree (§9.5).

### 9.1 How it was ported

Same method as the first pass — the branches still share no merge-base, so `git merge-file`
(base `6a8f371`, ours `HEAD`, theirs `d2abe21`) over the 60 non-log paths, with
`git show d2abe21:<path>` for the 16 new files. 21 files merged cleanly, 7 conflicted and were
resolved by hand, 16 lang files were taken from the source because this line's copies were
byte-identical to the merge base.

Conflicts resolved (all by taking the 26.3 content and re-applying the 26.2 adaptation):

| File | Conflict | Resolution |
| --- | --- | --- |
| `menu/EnchantingProtocol` | revision string + legacy probes | `26_2_revision_6` + `PREVIOUS_COST_TYPE = enchant_cost_table_26_2_revision_5` (two probes, not three — `..._26_3_revision_4` never existed here) |
| `menu/EnchantCostSync` | join-time probe list | both 26.2 legacy probes, current revision last |
| `ChiseledEnchantingTableClient` | tome-charge colour + new regen hint | theirs, colours converted |
| `gui/ChiseledEnchantingTableScreen` | 3 × (tome wear panel, resonance breakdown, regen rate) | theirs, colours converted |
| `gui/config/ChiseledEnchantingConfigScreen` | scope badge | theirs, `switch` returns ARGB ints |
| `block/ChiseledEnchantingTableBlockEntity` | frozen-lore colour | theirs (`withItalic(false)` + ARGB) |
| `mechanics/enchanting/EnchantmentApplication` | the old `applyShelfWear` block | deleted — 26.3 moved wear application to the top of the method; keeping ours would have double-charged wear |

### 9.2 What the increment adds

* **Rule architecture** — `config/rules/RuntimeRules` + `RuleBook`: preferences, effective values and
  the client draft are separated, ranges and cross-field invariants are normalised once in
  `prepare`, and `ChiseledEnchantingTableData.RuntimeFrame` publishes domain tables, costs, loot,
  policy and the effective DTO as one immutable generation. `ModRuntimeConfig.effective()` is what
  the anvil mixins and every gameplay consumer read now, so a locked/pack-governed row can no
  longer be bypassed by a direct `get()`.
* **`pack_policy.json`** — `enforce_datapack_priority`, `lock_in_game_config`, `locked_keys`;
  datapack-declared keys are resolved by *source pack* (final selected resource), not by guessing
  index 0, and the config screen shows `📦 datapack priority` / `🔒 modpack lock` reasons.
* **Persistent table inventory** — the two slots live in the block entity, survive closing the GUI,
  are dropped exactly once when the table is broken, and are guarded by an active-ritual target
  fingerprint.
* **Queued inscriptions** — `mechanics/ritual/InscriptionRitualMechanics` plus the block-entity
  state machine: owner/session/target-bound queue (max 256 steps / 32,000 serialized characters,
  ≤ 255 levels), deep-resonance trigger at `≥32` broad shelves **or** `≥10` stored tome
  enchantments, per-step wait capped at 5 minutes, XP-sacrifice skip (≤15 levels, no tome damage)
  and force-rush skip (0 XP, live degradation chance ≤50%, ≤70% with an Infinity tome, weakest tome
  first), `/cet cancel-ritual` for the owner, and quarantine (never silent deletion) when an
  enchantment id disappears or the target no longer matches.
* **Tome regeneration** — `RegenerationScheduler` + `WorldRegeneration`: one settlement per
  20-tick window per world session, aggregating all tables watching the same shelves so the fastest
  legal rate wins; no absolute-time catch-up, no inventory/cross-world credit, chunk-load verified,
  and nothing is written back to the item unless the value actually changed.
* **Depleted-tome world content** — `mechanics/loot/DepletedTomeWorldMechanics`: chest-loot
  injection per structure tier, wild chiseled-bookshelf weathering (three shelf tiers, ≤30% total
  replacement), librarian recycling / plain-book repair / level-gated commissioned enchanted-book
  rewrites, and the Infinity trade restriction (book results only, preserving uses/discounts/demand).
* **Gameplay balance** — weighted Treasure Epiphany, Infinity removed from the epiphany pool and the
  default inscription list, Infinity Tomes take zero wear, overloaded Infinity Tomes slip levels
  (≤20%), five new advancements, `pack_policy.json` governance.
* **UI / locale** — repaginated Grimoire with new diagrams (shelf taxonomy, wait/skip comparison,
  regeneration and Infinity, world remnants and librarian trades), 621 keys × 11 locales (+128 new,
  16 reworded), config screen effective-vs-draft display.
* **Wire** — revision 6 for every menu/cost/config payload, ritual payloads bound to
  container + session, `PayloadLimits` guarding every new collection read.

### 9.3 26.3-only APIs re-adapted (the port re-introduces them)

The same trap as the first pass: the source compiles against Minecraft 26.3, so every ported file
must be re-checked. This pass needed four categories:

| 26.3 usage | 26.2 fix applied | Occurrences |
| --- | --- | --- |
| `ChatFormatting` (removed in 26.2) | `Style#withColor(0xAARRGGBB)`, including `int` colour variables in charge/scope ternaries | 53 sites in 6 files, now **0** references repo-wide |
| `ContextIntProviders.exactly(...)` (provider namespace split in 26.3) | `ConstantValue.exactly(...)` — the same class this line's `EnchantedBookLootInjector` already uses | 2 in the new loot builder |
| `Optional<Holder<LootItemCondition>>` constructor (26.3 loot API) | `List<LootItemCondition>` + `commonFields(...)` codec, as this line requires | 0 leaked |
| singular `modifier`/`condition` loot JSON | plural `functions[]`/`conditions[]` in `scripts/server_smoke.py` fixtures | 1 fixture block |

Nothing else leaked: a token-level diff of every added line against the `26.1.2` tree (the oldest
sibling, so any identifier it also uses is stable across 26.2) leaves only project-local identifiers.

### 9.4 Kept as this line's own

`MapCodec`/`codec()` on the block, the interface `StructureProcessor` + `codec()` registration,
capital-P `markPosForPostProcessing`, the two-argument `LivingEntity#drop`, the plural loot schema
in bundled data **and** in the smoke fixtures, the `recipe_crafted`/`recipe_id` advancement
criterion, the JUnit-5 wiring, the 26.2 toolchain pins and the 26.2 GLFW mouse ids.

Not taken: 26.3's toolchain and JVM args, `build-reports/*.log`, `scripts/test_sync_26_3.py` (it
asserts 26.3-shaped APIs), and the 26.3-only rewrites listed in §4 (still absent, re-verified).

### 9.5 Regression gates from the source audit

Each item the source branch flagged for 26.2 was checked in the ported tree:

| Gate | Where it is satisfied |
| --- | --- |
| R1 effective rules reach direct consumers | `ModRuntimeConfig.effective()`; both anvil mixins read it |
| R2 preferences ≠ effective ≠ draft | `SyncRuntimeConfigPayload(json, policyJson, effectiveJson, canEdit, requestId, accepted)`; the client keeps a separate draft and rebuilds it from server preferences on ack |
| R3 no absolute-time regen credit | `WorldRegeneration` only settles observed, loaded windows; the old timer keys are no longer read |
| R4 shared shelves aggregate | `RegenerationScheduler` settles once per window at the fastest legal rate |
| R5 no per-second item writes | items are written back only when a charge actually changes; unworn/Infinity tomes never enter the queue |
| R6 datapack layer identity | resolution keys off the final selected resource + `sourcePackId` |
| R7 save authority | owner/operator check (`mayManage`), request-id ack, atomic temp-file replace, strict reload, corrupt-JSON rejection |
| R8 bounded reads | `PayloadLimits.readCount` on every collection; ritual payloads carry container + session |
| N1 inventory is an escrow, not a GUI lock | target fingerprint + owner/session checks in `setItem`/`removeItem`/`canTakeItem`, not only in the slot |
| N2 ownership is an authorisation boundary | `ownsRitual(uuid)` gates enqueue and skip; the owner is not just a reward recipient |
| N3 queue is bounded before payment | capacity/character/level/refund-budget pre-check before any payment; rejection has zero side effects |
| N4 completion is not "clear then fail" | invalid ids quarantine the target (`ritualBlocked`) and keep inputs; nothing is silently skipped |
| N5 menu routing includes the world | menu sync compares world + position + session |
| N6 villager offers stay owned by us | only enchanted-book results are touched and the offer object is mutated in place; commissioned offers are marked once |
| N7 loot/API + drop state | 26.2 `ConstantValue`, 26.2 schema, and the smoke test now covers stored-input break drops |

### 9.6 Verification

| Gate | Result |
| --- | --- |
| `python3 scripts/compat_check.py` | `PASS (2 warning(s))` — both Tier-2 (no MC jar in the sandbox) |
| `python3 scripts/ci_static_checks.py` | `PASS (5 groups)` — 9 mixins, **621** lang keys × 11 locales, 470 referenced keys, 40 markdown files |
| `bash scripts/check_release_consistency.sh --strict` | `12 passed, 0 warned, 0 failed` |
| `bash docs/ci/install.sh --check` | all four workflow copies identical |
| `grep -r ChatFormatting src` | 0 |
| conflict markers / unresolved project imports / duplicate classes | 0 / 0 / 0 |

Java compilation, the 52 JUnit tests (`compile-check` runs `test`) and the new `server-smoke`
phases (persisted ritual completion, registry-failure quarantine, stored-input break drop) run in
CI — the sandbox has no JDK and no Maven/Mojang access.

### 9.7 Version and release

`1.1.0`, unchanged (`版本号不变`). The wire revision moves to 6, so **both ends must be updated**;
the release label alone no longer identifies compatibility. No 26.3 toolchain, JVM argument or
build log was taken.

### 9.8 Still manual

GUI pagination/drag, LAN host vs. guest vs. operator authority, multiplayer contention on one table
or shared shelves, hopper/automation against a running ritual, cross-dimension same-coordinate
menus, hard restart / chunk unload, layered modpack resource stacks, third-party trade lifecycles
and large-library performance. Details in `NEXT_TASK.md` §A2.

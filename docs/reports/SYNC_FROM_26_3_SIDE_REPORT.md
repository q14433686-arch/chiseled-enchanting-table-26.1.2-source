# Sync Report — `26.3` line → `26.2` line (2026-10-23)

> **Scope:** port everything that landed on the sister `26.3` branch after this line's last sync
> (`6a8f371`, the "provenance & live configuration" batch plus the runtime-audit hardening), onto
> **this** 26.2 branch, keeping every 26.2 toolchain pin and every 26.2-only API call intact.
> **Version:** unchanged — `1.1.0`. The user's standing instruction on this pass was
> `版本号不变`, and `1.1.0` had not been released from this branch yet, so the imported feature batch
> is folded into the same release (see `CHANGELOG.md`).
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
* `EconomyAndConfigVerificationTest` extended (16 tests) — 29 JUnit tests in total.
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

# Active implementation — option 3 / 3e61f5d

User approved **3**, including latest 26.1.2 changes and feedback for 26.1.2/26.2. No fresh design-choice prompt is needed for this agreed scope. Current branch remains `arena/7a86fcca-chiseled-enchanting-table-26-1`; never work on or push other branches.

## Completed foundation, NOT full port

- Source pin: `3e61f5de3ba7679ccbc92b73443dead149c96840`; old synchronized source: `3e081f3`.
- `RuleBook`: immutable effective values/origins, startup boundary, policy precedence, prepare/persist/publish with stale/foreign/reentrant rejection.
- `RegenerationScheduler`: aggregate shared-shelf observations, fastest legal rate, no absent-window credit, identity/acknowledgement, eviction and linear batch acknowledgement.
- 13 new pure-core JUnit tests; final foundation source `200e468` passed 42 total JUnit, compile-check **40004583488**, and all four jobs of build **40004583434**. These checks do not validate the unintegrated new gameplay.
- [Cross-branch report](docs/reports/CROSS_BRANCH_3E61F5D_ARCHITECTURE_REVIEW.md) identifies R1–R8/N1–N7; runtime fixes remain pending adapter integration.

## Next implementation order

1. Merge the FULL `3e081f3..3e61f5d` gameplay/resource delta without overwriting 26.3 safeguards. Preserve version 1.1.0 and pinned 26.3 dependencies. New regen defaults are 24000/12000/3600 seconds.
2. Build candidate domain tables, policy and scalar rules before publication. Route all runtime consumers through one effective generation, while keeping stored preferences and UI drafts distinct. Actual startup components stay explicit restart-only values; world policy cannot retroactively control them.
3. Replace source per-table timestamp regeneration with one per-world-session aggregate scheduler. Validate loaded chunks and exact shelf slot/book identity before applying; preserve unrelated NBT/provenance. No missed-time/backpack/cross-world credit.
4. Integrate bounded ritual sessions, target escrow/fingerprint, ownership, preflight-before-payment, container/automation guards, recoverable completion and break/unload behavior. Do not copy the unbounded string queue or clear-before-complete sequence unchanged.
5. Upgrade wire independently (expected revision 6), including charges/policy/ritual messages. Preserve revision 4/5 query-only diagnostics, PayloadLimits, container/session targeting, request ACKs and LAN owner/op checks. Do not inherit source unknown-action fallback or old save code.
6. Integrate depleted-tome loot, wild-shelf decay, librarian trades/ranks, Infinity acquisition rules and the new Grimoire/UI/locales. Preserve 26.3 loot/provider APIs and safe structure processing. Separate third-party transaction ownership and test idempotency.
7. Run full CI/JUnit/server smoke plus new inventory/queue/policy/registry-stack probes, then actual GUI/multiplayer checks. Update the report's implemented/pending columns; no declaration of full port before integration evidence.

Scratch three-way candidate is OUTSIDE the repository at `/home/user/new-port-audit/candidate`; source/base archives are in `latest` / `base`, with `plan_merge.py` and `merge_plan.json` in the parent. These are comparison aids, not applied code or a publishable build. If unavailable, reconstruct from pinned git objects. Do not blindly copy conflict candidates: client receiver, block scanning, config networking, wear, payloads, handler and language files require deliberate integration.

## Existing gameplay manual-validation backlog (still relevant)



1. Singleplayer GUI: SDL left/right/drag, overlay/standalone pause, save/reject feedback and draft retention. Saving closes old table menus; check feedback if the overlay disappears before ACK rendering.
2. Real dedicated/LAN clients: owner/guest/op permissions, successful/rejected/failed saves, disconnects and config commands. Current smoke has no players and does not execute config commands.
3. Per-player discovery in an already-open table and JEI/REI. One player's unlock must not unlock another player's catalog.
4. Mother-tome wear/exhaustion, transfer/merge provenance, full-inventory batch overflow, two players sharing shelves and old-world upgrades after backup.
5. Install Mod Menu/JEI/REI and exercise actual GUIs; no-viewer compilation is not GUI validation.
6. Run Tier 2 compatibility with the pinned MC jar. Check config-over-datapack precedence, unmarked old items and restart-only book stack defaults on both client and server.
7. Delayed structure mixins and natural generation; the flat smoke intentionally disables structures and cannot cover these paths.

## Reproducible gates

```sh
bash scripts/check_release_consistency.sh --strict
python3 scripts/ci_static_checks.py
python3 scripts/compat_check.py
python3 scripts/test_sync_26_3.py
bash docs/ci/install.sh --check
# JDK 25 with access to pinned game/dependency repositories:
./gradlew build
./gradlew -Penable_jei=false -Penable_rei=false compileJava compileClientJava
```

Preserve source defaults chosen in option 1 and the 26.3 schema/mouse/protocol/catalog fixes. Use current-source CI, never substitute old green runs. Book stack limits require restart; runtime sync alone cannot update bootstrap item components.

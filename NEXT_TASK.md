# Active implementation — option 3 / 3e61f5d

User approved **3**, including latest 26.1.2 changes and feedback for 26.1.2/26.2. No fresh design-choice prompt is needed for this agreed scope. Current branch remains `arena/7a86fcca-chiseled-enchanting-table-26-1`; never work on or push other branches.

## Full integration implemented

Source pin: `3e61f5de3ba7679ccbc92b73443dead149c96840`, including PR #17/#18. Effective RuntimeRules/RuntimeFrame, aggregate WorldRegeneration, persistent inventories, bounded/owned ritual sessions, revision 6 wire, depleted-tome world/trade features and all new UI/locales are wired. Do not re-run the one-shot candidate merge scripts; they would overwrite reviewed fixes.

[Current implementation, cross-branch feedback, exact CI evidence and honest boundaries](docs/reports/CROSS_BRANCH_3E61F5D_ARCHITECTURE_REVIEW.md).

## Remaining acceptance / release work

1. Final game-source CI is green: `9243790`, build 40007636762 (four jobs), compile-check 40007636849, 52 JUnit, real ritual/quarantine/break probes. Preserve these regression gates; never substitute old foundation-only runs.
2. Complete the real GUI/LAN/multiplayer, hopper/creative/explosion, restart/unload, layered-pack/modpack, trade lifecycle and performance matrix listed in the report.
3. After the user requests it, open a PR from the existing Arena branch. Do not merge or change other branches without authorization. 26.1.2/26.2 receive the report/core adaptation recommendations, not direct pushes.

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

Preserve the latest 3e61f5d defaults and 26.3 schema/mouse/protocol/catalog fixes. Use current-source CI, never substitute old green runs. Book stack limits require restart; runtime sync alone cannot update bootstrap item components.

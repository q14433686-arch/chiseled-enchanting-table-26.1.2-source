# Next validation — option 1 port

The user selected **1**. The source increment through **3e081f3** is ported, retaining 26.3 fixes and release **1.1.0**. Wire is revision **5**. See [implementation, migration and evidence](docs/reports/SYNC_26_1_2_3E081F3_OPTION1.md).

Final functional source **181e996** passed build **39904722889** (all four jobs), compile-check **39904722983**, 29 JUnit, 15 Python and the real no-player server/reload/loot smoke. No further default-policy decision is pending.

## Remaining real-client checks

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

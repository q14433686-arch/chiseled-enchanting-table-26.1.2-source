#!/usr/bin/env python3
"""Boot a disposable Loom dedicated MC 26.1.2 server; require real datapack/mixin load + reload.

Only use on an isolated test checkout (CI). Never point this at a user's save.
Requires the pinned JDK and Maven/network access; offline static checks cannot replace it.
"""
import json
import os
from pathlib import Path
import queue
import shutil
import signal
import subprocess
import threading
import time

ROOT = Path(__file__).resolve().parents[1]
RUN = ROOT / 'run'
RUN.mkdir(exist_ok=True)
if (RUN / 'world').exists() or (RUN / 'audit-world').exists():
    raise SystemExit('Refusing to use an existing world; smoke test requires a fresh checkout')
(RUN / 'eula.txt').write_text('eula=true\n')
(RUN / 'server.properties').write_text(
    'online-mode=false\nserver-ip=127.0.0.1\nserver-port=0\n'
    'level-name=audit-world\nlevel-type=minecraft:flat\n'
    'generator-settings={"layers":[{"block":"minecraft:bedrock","height":1}],"biome":"minecraft:plains","lakes":false,"features":false}\n'
    'generate-structures=false\nview-distance=2\nsimulation-distance=2\nmax-tick-time=60000\n')

# CI-only resources: exercise conditional function decoding inside MC 26.1.2 (26.1.2 loot schema:
# "functions" array with "function" + "conditions" array with "condition"), never shipped in jars.
fixture_root = ROOT / 'src/main/resources/data/cet_audit_smoke'
if fixture_root.exists():
    raise SystemExit('Refusing to overwrite an existing audit fixture namespace')
fixture_tables = fixture_root / 'loot_table'
fixture_tables.mkdir(parents=True)
for name, function in (('remove_false', 'remove_enchanted_books'), ('depth_false', 'depth_based_efficiency')):
    table = {
        'type': 'minecraft:generic',
        'pools': [{
            'rolls': 1,
            'entries': [{
                'type': 'minecraft:item',
                'name': 'minecraft:enchanted_book',
                'functions': [{
                    'function': 'chiseled_enchanting_table:' + function,
                    'conditions': [{'condition': 'minecraft:random_chance', 'chance': 0.0}],
                }],
            }],
        }],
    }
    (fixture_tables / (name + '.json')).write_text(json.dumps(table))

fixture_functions = fixture_root / 'function'
fixture_functions.mkdir()
(fixture_functions / 'spawn_table.mcfunction').write_text(
    'setblock 0 65 0 chiseled_enchanting_table:chiseled_enchanting_table\n'
    'data merge block 0 65 0 {color:1122867,CustomName:"Audit Table",epiphanySeed:123456,pendingFreezeTicks:1200L,frozenCycleIndex:17}\n'
    'loot spawn 0 67 0 mine 0 65 0\n'
    'schedule function cet_audit_smoke:inspect 10t\n')
(fixture_functions / 'inspect.mcfunction').write_text('say CET_AUDIT_INSPECT_READY\n')
(fixture_functions / 'ritual.mcfunction').write_text(
    'setblock 1 65 0 chiseled_enchanting_table:chiseled_enchanting_table\n'
    'data merge block 1 65 0 {Items:[{Slot:0b,id:"minecraft:diamond_sword",count:1}],ritualTarget:{id:"minecraft:diamond_sword",count:1},'
    'ritualQueue:"minecraft:sharpness|1|0|0|0|0|0",ritualSession:123L,ritualTotalTicks:80L,ritualRemainingTicks:80L,'
    'ritualOwnerUuid:"00000000-0000-0000-0000-000000000001"}\n'
    'schedule function cet_audit_smoke:ritual_pending 10t\n')
(fixture_functions / 'ritual_pending.mcfunction').write_text('say CET_AUDIT_RITUAL_PENDING\n')
(fixture_functions / 'ritual_finished.mcfunction').write_text('say CET_AUDIT_RITUAL_FINISHED\n')
(fixture_functions / 'ritual_invalid.mcfunction').write_text(
    'setblock 1 65 0 chiseled_enchanting_table:chiseled_enchanting_table replace\n'
    'data merge block 1 65 0 {Items:[{Slot:0b,id:"minecraft:diamond_sword",count:1}],ritualTarget:{id:"minecraft:diamond_sword",count:1},'
    'ritualQueue:"cet_audit_smoke:removed_enchantment|1|0|0|0|0|0",ritualSession:456L,ritualTotalTicks:1L,ritualRemainingTicks:1L,'
    'ritualOwnerUuid:"00000000-0000-0000-0000-000000000001"}\n'
    'schedule function cet_audit_smoke:ritual_quarantined 10t\n')
(fixture_functions / 'ritual_quarantined.mcfunction').write_text('say CET_AUDIT_RITUAL_QUARANTINED\n')
(fixture_functions / 'ritual_broken.mcfunction').write_text('say CET_AUDIT_RITUAL_BROKEN\n')

log_path = ROOT / 'build-reports/server-smoke.log'
log_path.parent.mkdir(exist_ok=True)
process = subprocess.Popen(
    ['./gradlew', 'runServer', '--args=nogui', '--console=plain', '--no-daemon'],
    cwd=ROOT, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
    stderr=subprocess.STDOUT, text=True, bufsize=1, start_new_session=True)
lines = queue.Queue()


def pump():
    for line in process.stdout:
        lines.put(line)
    lines.put(None)


threading.Thread(target=pump, daemon=True).start()
ready = False
reloaded = False
drop_verified = False
remove_verified = False
depth_verified = False
ritual_pending_verified = False
ritual_finished_verified = False
ritual_quarantine_verified = False
ritual_break_verified = False
stopping = False
probe_phase = 'table'
failed = []
start = time.monotonic()
try:
    with log_path.open('w') as log:
        while time.monotonic() - start < 300:
            try:
                line = lines.get(timeout=1)
            except queue.Empty:
                continue
            if line is None:
                break
            log.write(line)
            log.flush()
            print(line, end='', flush=True)
            if '/ERROR]' in line or any(marker in line for marker in (
                    'Registry loading errors', 'Failed to load registries',
                    'Mixin apply failed', 'MixinTransformerError', 'Failed to load datapacks',
                    "Couldn't parse", 'Failed to parse', 'Error loading', 'Failed to validate',
                    'Failed to send the enchanting cost table', 'No entity was found')):
                failed.append(line.strip())
            if not ready and 'Done (' in line:
                ready = True
                process.stdin.write('reload\n')
                process.stdin.flush()
            elif ready and ('Loaded ' in line and ' advancements' in line):
                reloaded = True
                process.stdin.write('forceload add 0 0\nschedule function cet_audit_smoke:spawn_table 20t\n')
                process.stdin.flush()
            elif reloaded and 'CET_AUDIT_INSPECT_READY' in line:
                process.stdin.write('data get entity @e[type=minecraft:item,limit=1]\n')
                process.stdin.flush()
            elif reloaded and all(token in line for token in (
                'Audit Table', '"minecraft:dyed_color": 1122867', 'pendingFreezeTicks: 1200L',
                'epiphanySeed: 123456', 'frozenCycleIndex: 17', 'item.chiseled_enchanting_table.frozen_lore')):
                drop_verified = True
                probe_phase = 'remove'
                process.stdin.write('kill @e[type=minecraft:item]\n'
                                    'loot spawn 0 67 0 loot cet_audit_smoke:remove_false\n'
                                    'schedule function cet_audit_smoke:inspect 10t\n')
                process.stdin.flush()
            elif reloaded and probe_phase == 'remove' and 'has the following entity data:' in line:
                remove_verified = 'minecraft:enchanted_book' in line
                if not remove_verified:
                    failed.append('Conditional removal fixture did not preserve its enchanted book')
                probe_phase = 'depth'
                process.stdin.write('kill @e[type=minecraft:item]\n'
                                    'loot spawn 0 67 0 loot cet_audit_smoke:depth_false\n'
                                    'schedule function cet_audit_smoke:inspect 10t\n')
                process.stdin.flush()
            elif reloaded and probe_phase == 'depth' and 'has the following entity data:' in line:
                depth_verified = 'minecraft:enchanted_book' in line and 'minecraft:stored_enchantments' not in line
                if not depth_verified:
                    failed.append('Depth function executed despite its always-false condition')
                probe_phase = 'ritual_start'
                process.stdin.write('function cet_audit_smoke:ritual\n')
                process.stdin.flush()
            elif reloaded and 'CET_AUDIT_RITUAL_PENDING' in line:
                probe_phase = 'ritual_pending'
                process.stdin.write('data get block 1 65 0\n')
                process.stdin.flush()
            elif probe_phase == 'ritual_pending' and 'has the following block data:' in line:
                ritual_pending_verified = all(token in line for token in (
                    'minecraft:diamond_sword', 'ritualSession: 123L', 'minecraft:sharpness|1|0|0|0|0|0'))
                if not ritual_pending_verified:
                    failed.append('Pending ritual lost its persisted input/session/queue')
                probe_phase = 'ritual_wait'
                process.stdin.write('save-all\nschedule function cet_audit_smoke:ritual_finished 100t\n')
                process.stdin.flush()
            elif reloaded and 'CET_AUDIT_RITUAL_FINISHED' in line:
                probe_phase = 'ritual_finished'
                process.stdin.write('data get block 1 65 0\n')
                process.stdin.flush()
            elif probe_phase == 'ritual_finished' and 'has the following block data:' in line:
                ritual_finished_verified = all(token in line for token in (
                    'minecraft:diamond_sword', 'minecraft:enchantments', 'minecraft:sharpness', 'ritualQueue: ""'))
                if not ritual_finished_verified:
                    failed.append('Real ritual ticker did not commit its enchantment and clear its queue')
                probe_phase = 'ritual_invalid'
                process.stdin.write('function cet_audit_smoke:ritual_invalid\n')
                process.stdin.flush()
            elif reloaded and 'CET_AUDIT_RITUAL_QUARANTINED' in line:
                probe_phase = 'ritual_quarantine'
                process.stdin.write('data get block 1 65 0\n')
                process.stdin.flush()
            elif probe_phase == 'ritual_quarantine' and 'has the following block data:' in line:
                ritual_quarantine_verified = all(token in line for token in (
                    'minecraft:diamond_sword', 'cet_audit_smoke:removed_enchantment|1|0|0|0|0|0', 'ritualBlocked: 1b'))
                if not ritual_quarantine_verified:
                    failed.append('Invalid registry ritual did not preserve its input and quarantined queue')
                probe_phase = 'ritual_break'
                process.stdin.write('kill @e[type=minecraft:item]\nsetblock 1 65 0 air destroy\nschedule function cet_audit_smoke:ritual_broken 10t\n')
                process.stdin.flush()
            elif reloaded and 'CET_AUDIT_RITUAL_BROKEN' in line:
                process.stdin.write('data get entity @e[type=minecraft:item,nbt={Item:{id:"minecraft:diamond_sword"}},limit=1]\n')
                process.stdin.flush()
            elif probe_phase == 'ritual_break' and 'has the following entity data:' in line:
                ritual_break_verified = 'minecraft:diamond_sword' in line and 'count: 1' in line
                if not ritual_break_verified:
                    failed.append('Breaking the quarantined table did not return its single stored sword')
            if ready and not stopping and (failed or (
                    drop_verified and remove_verified and depth_verified and ritual_pending_verified
                    and ritual_finished_verified and ritual_quarantine_verified and ritual_break_verified)):
                stopping = True
                process.stdin.write('stop\n')
                process.stdin.flush()
        if (not ready or not reloaded or not drop_verified or not remove_verified or not depth_verified
                or not ritual_pending_verified or not ritual_finished_verified
                or not ritual_quarantine_verified or not ritual_break_verified or failed):
            raise RuntimeError(
                f'Smoke test failed: ready={ready}, reloaded={reloaded}, drop_verified={drop_verified}, '
                f'remove_verified={remove_verified}, depth_verified={depth_verified}, '
                f'ritual_pending={ritual_pending_verified}, ritual_finished={ritual_finished_verified}, '
                f'ritual_quarantine={ritual_quarantine_verified}, ritual_break={ritual_break_verified}, '
                f'errors={failed}')
    code = process.wait(timeout=45)
    if code:
        raise RuntimeError(f'Server/Gradle exited with {code}')
    print('SERVER SMOKE PASS: fresh world + reload + named/dyed/frozen table loot + conditional loot functions + persisted ritual input/session + natural ritual completion + registry failure quarantine + stored-input break drop + clean shutdown')
finally:
    shutil.rmtree(fixture_root)
    if process.poll() is None:
        os.killpg(process.pid, signal.SIGTERM)
        try:
            process.wait(timeout=15)
        except subprocess.TimeoutExpired:
            os.killpg(process.pid, signal.SIGKILL)

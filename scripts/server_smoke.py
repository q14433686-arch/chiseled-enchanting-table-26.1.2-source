#!/usr/bin/env python3
"""Boot a disposable Loom dedicated server; require real datapack/mixin load + reload.

Only use on an isolated test checkout (CI). Never point this at a user's save.
Requires the pinned JDK and Maven/network access; offline static checks cannot replace it.
"""
import os
import json
import shutil
from pathlib import Path
import queue
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
# CI-only resources: exercise condition decoding inside the real game, never ship these tables.
fixture_root = ROOT / 'src/main/resources/data/cet_audit_smoke'
if fixture_root.exists():
    raise SystemExit('Refusing to overwrite an existing audit fixture namespace')
fixture_tables = fixture_root / 'loot_table'
fixture_tables.mkdir(parents=True)
for name, function in (('remove_false', 'remove_enchanted_books'), ('depth_false', 'depth_based_efficiency')):
    table = {'type': 'minecraft:generic', 'pools': [{'rolls': 1, 'entries': [{
        'type': 'minecraft:item', 'name': 'minecraft:enchanted_book',
        'modifier': {'type': 'chiseled_enchanting_table:' + function,
                     'condition': {'type': 'minecraft:random_chance', 'chance': 0.0}}
    }]}]}
    (fixture_tables / (name + '.json')).write_text(json.dumps(table))
log_path = ROOT / 'build-reports/server-smoke.log'
log_path.parent.mkdir(exist_ok=True)
process = subprocess.Popen(['./gradlew', 'runServer', '--args=nogui', '--console=plain', '--no-daemon'],
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
stopping = False
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
            if '/ERROR]' in line or any(marker in line for marker in ('Registry loading errors', 'Failed to load registries',
                    'Mixin apply failed', 'MixinTransformerError', 'Failed to load datapacks',
                    'Couldn\'t parse', 'Failed to parse', 'Error loading', 'Failed to validate',
                    'Failed to send the enchanting cost table')):
                failed.append(line.strip())
            if not ready and 'Done (' in line:
                ready = True
                process.stdin.write('reload\n')
                process.stdin.flush()
            elif ready and ('Loaded ' in line and ' advancements' in line):
                reloaded = True
                # Exercise the actual 26.3 block-entity -> loot component path, not just JSON parsing.
                process.stdin.write(
                    'forceload add 0 0\n'
                    'setblock 0 65 0 chiseled_enchanting_table:chiseled_enchanting_table\n'
                    'data merge block 0 65 0 {color:1122867,CustomName:"Audit Table",epiphanySeed:123456,pendingFreezeTicks:1200L,frozenCycleIndex:17}\n'
                    'loot spawn 0 67 0 mine 0 65 0\n'
                    'data get entity @e[type=minecraft:item,limit=1]\n'
                    'loot spawn 3 70 0 loot cet_audit_smoke:remove_false\n'
                    'loot spawn 5 70 0 loot cet_audit_smoke:depth_false\n'
                    'tag @e[type=minecraft:item,x=5,y=70,z=0,distance=..1,limit=1] add cet_audit_depth_probe\n'
                    'data get entity @e[tag=cet_audit_depth_probe,limit=1]\n')
                process.stdin.flush()
            elif reloaded and all(token in line for token in (
                'Audit Table', '"minecraft:dyed_color": 1122867', 'pendingFreezeTicks: 1200L',
                'epiphanySeed: 123456', 'frozenCycleIndex: 17', 'item.chiseled_enchanting_table.frozen_lore')):
                drop_verified = True
            elif reloaded and 'cet_audit_smoke:remove_false' in line and 'Dropped 1 ' in line:
                remove_verified = True
            elif reloaded and 'cet_audit_depth_probe' in line and 'has the following entity data:' in line:
                depth_verified = 'minecraft:enchanted_book' in line and 'minecraft:stored_enchantments' not in line
                if not depth_verified:
                    failed.append('Depth function executed despite its always-false condition')
            if ready and not stopping and (failed or (drop_verified and remove_verified and depth_verified)):
                stopping = True
                process.stdin.write('stop\n')
                process.stdin.flush()
        if not ready or not reloaded or not drop_verified or not remove_verified or not depth_verified or failed:
            raise RuntimeError(f'Smoke test failed: ready={ready}, reloaded={reloaded}, drop_verified={drop_verified}, remove_verified={remove_verified}, depth_verified={depth_verified}, errors={failed}')
    code = process.wait(timeout=45)
    if code:
        raise RuntimeError(f'Server/Gradle exited with {code}')
    print('SERVER SMOKE PASS: fresh world + reload + named/dyed/frozen table loot + conditional loot functions + clean shutdown')
finally:
    shutil.rmtree(fixture_root)
    if process.poll() is None:
        os.killpg(process.pid, signal.SIGTERM)
        try:
            process.wait(timeout=15)
        except subprocess.TimeoutExpired:
            os.killpg(process.pid, signal.SIGKILL)

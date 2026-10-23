#!/usr/bin/env python3
"""Boot a disposable Loom dedicated server; require real datapack/mixin load + reload.

Only use on an isolated test checkout (CI). Never point this at a user's save.
Requires the pinned JDK and Maven/network access; offline static checks cannot replace it.
"""
import os
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
    'generate-structures=false\nview-distance=2\nsimulation-distance=2\nmax-tick-time=60000\n')
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
            if any(marker in line for marker in ('Registry loading errors', 'Failed to load registries',
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
                process.stdin.write('stop\n')
                process.stdin.flush()
        if not ready or not reloaded or failed:
            raise RuntimeError(f'Smoke test failed: ready={ready}, reloaded={reloaded}, errors={failed}')
    code = process.wait(timeout=45)
    if code:
        raise RuntimeError(f'Server/Gradle exited with {code}')
    print('SERVER SMOKE PASS: fresh world + reload + clean shutdown')
finally:
    if process.poll() is None:
        os.killpg(process.pid, signal.SIGTERM)
        try:
            process.wait(timeout=15)
        except subprocess.TimeoutExpired:
            os.killpg(process.pid, signal.SIGKILL)

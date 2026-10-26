#!/usr/bin/env python3
"""CI-only real client probe. Requires server_smoke.py's disposable saved world first."""
from pathlib import Path
import os
import shutil
import signal
import subprocess

root = Path(__file__).resolve().parents[1]
run = root / 'run-client-smoke'
world = root / 'run/audit-world'
if run.exists() or not world.is_dir():
    raise SystemExit('Refusing existing client run directory or missing disposable server-smoke world')
shutil.copytree(world, run / 'saves/audit-world')
(run / 'options.txt').write_text('onboardAccessibility:false\nlang:zh_cn\nguiScale:2\nmaxFps:60\nenableVsync:false\n')
log = root / 'build-reports/client-smoke.log'
log.parent.mkdir(exist_ok=True)
with log.open('w') as output:
    process = subprocess.Popen(['./gradlew', 'runClientSmoke', '-Pclient_smoke=true',
                                '-Penable_jei=false', '-Penable_rei=false', '--console=plain', '--no-daemon'],
                               cwd=root, stdout=output, stderr=subprocess.STDOUT, start_new_session=True)
    try:
        code = process.wait(timeout=600)
    finally:
        if process.poll() is None:
            os.killpg(process.pid, signal.SIGTERM)
            try:
                process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL)
print('\n'.join(log.read_text(errors='replace').splitlines()[-100:]))
result = run / 'client-smoke-result.txt'
if code != 0 or not result.exists() or not result.read_text().startswith('CLIENT SMOKE PASS:'):
    raise SystemExit('Real client smoke did not pass; inspect client-smoke.log')
print(result.read_text())

if not (run / 'screenshots/table-ui-zh_cn.png').is_file():
    raise SystemExit('Missing actual Chinese UI screenshot')
print('UI BOUNDARY PASS: actual Chinese menu screenshot saved to artifacts')

if not (run / 'screenshots/tome-catalogue-zh_cn.png').is_file():
    raise SystemExit('Missing actual Chinese tome catalogue screenshot')
print('CATALOGUE WORLD PASS: Chinese catalogue screenshot saved to artifacts')

for filename in ('rainbow-items-a.png', 'rainbow-items-b.png'):
    if not (run / 'screenshots' / filename).is_file():
        raise SystemExit('Missing rainbow item animation screenshot: ' + filename)
print('RAINBOW ITEM PASS: two live-frame inventory screenshots saved to artifacts')

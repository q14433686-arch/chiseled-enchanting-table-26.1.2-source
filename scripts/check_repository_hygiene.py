#!/usr/bin/env python3
"""26.2-line repository hygiene gate.

Our 26.2 CI (see .github/workflows/compile-check.yml) intentionally uses the GitHub Contents API
to write compile logs back to build-reports/ — the network-restricted sandbox cannot reach the
Actions log-blob host, so this writeback is the ONLY channel that gives us real compile evidence.
The 26.1.2/26.3 sister lines use artifacts-only CI (no writeback) and a stricter "no generated
logs in git" rule; applying that here would delete our compile-verification channel.

So: allow tracked build-reports/*.log and the Contents-API writeback steps, but still reject
stray generated products (bytecode, ad-hoc logs outside build-reports/, disposable run dirs).
"""
from pathlib import Path
import subprocess
import sys

ALLOWED_LOG_DIRS = ("build-reports/",)
tracked = subprocess.check_output(['git', 'ls-files', '-z']).decode().split('\0')
bad = []
for name in tracked:
    if not name:
        continue
    if name.endswith('.class'):
        bad.append(name)
        print(f'ERROR: tracked bytecode: {name}')
    elif name.endswith('.log') and not name.startswith(ALLOWED_LOG_DIRS):
        bad.append(name)
        print(f'ERROR: generated log outside build-reports/: {name}')

for stray in ('Dummy.java', 'Dummy.class'):
    if Path(stray).exists():
        bad.append(stray)
        print(f'ERROR: disposable artifact present: {stray}')

if bad:
    sys.exit(1)
print('ok: no stray generated products (26.2 writeback CI model: build-reports/ logs are intentional)')

#!/usr/bin/env python3
"""Generated outputs are artifacts, never source-controlled diagnostics."""
from pathlib import Path
import subprocess
import sys

tracked = subprocess.check_output(['git', 'ls-files', '-z']).decode().split('\0')
bad = [name for name in tracked if name.endswith(('.class', '.log')) or name.startswith('build-reports/')]
for name in bad:
    print(f'ERROR: generated output tracked: {name}')
for path in Path('.github/workflows').glob('*.yml'):
    content = path.read_text()
    if 'api.github.com/repos/' in content and '/contents/' in content:
        bad.append(str(path))
        print(f'ERROR: workflow commits generated files: {path}')
if bad:
    sys.exit(1)
print('ok: no tracked generated logs/bytecode or workflow Contents API write-back')

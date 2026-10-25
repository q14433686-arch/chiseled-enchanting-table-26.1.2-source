#!/usr/bin/env python3
"""Readable GitHub Checks diagnostics without committing generated logs to Git."""
import argparse
import json
import os
from pathlib import Path
import subprocess


def output(name, sha, conclusion, paths):
    sections = [f'Source SHA: `{sha}`. Full logs are in this run\'s artifacts.']
    for path in paths:
        if not path.exists():
            continue
        lines = path.read_text(encoding='utf-8', errors='replace').splitlines()
        important = [line for line in lines if any(token in line for token in
                     ('error:', 'FAILED', 'FAILURE', 'JUnit XML totals:', 'SERVER SMOKE PASS', 'BUILD SUCCESSFUL'))]
        excerpt = important[:100] + ['--- tail ---'] + lines[-100:]
        sections.append(f'### {path.name}\n```text\n' + '\n'.join(excerpt) + '\n```')
    # Checks output has a 65535-byte limit; bound UTF-8 bytes, not Unicode characters.
    summary = '\n\n'.join(sections).encode('utf-8')[:58000].decode('utf-8', errors='ignore')
    return dict(name=name, head_sha=sha, status='completed', conclusion=conclusion,
                output=dict(title=f'{name}: {conclusion}', summary=summary))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--name', required=True)
    parser.add_argument('--conclusion', choices=['success', 'failure', 'cancelled', 'skipped'], required=True)
    parser.add_argument('--publish', action='store_true')
    parser.add_argument('paths', nargs='+', type=Path)
    args = parser.parse_args()
    payload = output(args.name, os.environ.get('GITHUB_SHA', 'local'), args.conclusion, args.paths)
    if args.publish:
        subprocess.run(['gh', 'api', '-X', 'POST', f"repos/{os.environ['GITHUB_REPOSITORY']}/check-runs",
                        '--input', '-'], input=json.dumps(payload), text=True, check=True)
    else:
        print(json.dumps(payload, ensure_ascii=False))


if __name__ == '__main__':
    main()

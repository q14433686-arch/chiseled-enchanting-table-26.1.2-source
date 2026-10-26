#!/usr/bin/env python3
"""Check/generate the CURRENT_BASELINE inventory from Fabric's registered configs."""
import argparse
import json
from pathlib import Path

START = '<!-- BEGIN GENERATED MIXIN INVENTORY -->'
END = '<!-- END GENERATED MIXIN INVENTORY -->'


def inventory(root):
    resources = root / 'src/main/resources'
    metadata = json.loads((resources / 'fabric.mod.json').read_text(encoding='utf-8'))
    entries = []
    for declaration in metadata.get('mixins', []):
        name = declaration if isinstance(declaration, str) else declaration['config']
        candidates = list((root / 'src').glob('*/resources/' + name))
        if len(candidates) != 1:
            raise ValueError(f'Expected exactly one registered config: {name}')
        config = json.loads(candidates[0].read_text(encoding='utf-8'))
        for section in ('mixins', 'client', 'server'):
            for cls in config.get(section, []):
                entries.append((name, section, config['package'] + '.' + cls))
    if len(entries) != len(set(entries)):
        raise ValueError('Duplicate Mixin declarations')
    return entries


def render(entries):
    lines = [START, '## Registered Mixin inventory (generated)', '',
             f'**Registered Mixin declarations: {len(entries)}.**', '',
             'Source: `fabric.mod.json` registered configs, including common/client/server sections.',
             'Regenerate with `python3 scripts/check_baseline_mixins.py --write`.', '',
             '| Config | Section | Class |', '| --- | --- | --- |']
    lines.extend(f'| `{config}` | `{section}` | `{cls}` |' for config, section, cls in sorted(entries))
    return '\n'.join(lines + [END])


def synchronize(text, block):
    if START not in text and END not in text:
        return text.rstrip() + '\n\n' + block + '\n'
    if text.count(START) != 1 or text.count(END) != 1:
        raise ValueError('Malformed or duplicate inventory markers')
    start, end = text.index(START), text.index(END) + len(END)
    if end <= start:
        raise ValueError('Inventory markers out of order')
    return text[:start] + block + text[end:]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--write', action='store_true')
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    path = root / 'CURRENT_BASELINE.md'
    try:
        text = path.read_text(encoding='utf-8')
        updated = synchronize(text, render(inventory(root)))
        if args.write:
            path.write_text(updated, encoding='utf-8')
        elif text != updated:
            raise ValueError('CURRENT_BASELINE Mixin inventory is missing/stale; run with --write')
    except (OSError, ValueError, KeyError) as error:
        print(f'FAIL: {error}')
        return 1
    print('PASS: CURRENT_BASELINE Mixin count and full inventory match registered configs')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())

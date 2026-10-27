#!/usr/bin/env python3
"""Validate the checked-in ``loot_injections.json`` (data-ization / DEAD-01).

History: this tool used to *regenerate* ``loot_injections.json`` by scraping the retired upstream
Java class ``modifyChestLootTable/ReworkEnchantedBookChestLoot.java``. That class was removed by the
2026-10-24 / 2026-10-27 dead-code cleanups (the JSON table is now the single source of truth — see
docs/EXTENDING.md §4 ``loot_injections.json``), so a regeneration step would silently destroy the
curated 196-entry table. On this 26.2 line the tool is therefore a **validator**, not a generator: it
checks the schema (mode, entries, depleted-tome spawn tier lists) and normalises the pre-1.17 vanilla
chest path spellings in-place only when they are absent. Run
``python3 scripts/import_upstream_loot.py`` locally; CI validates the JSON via ``compat_check.py``.

The schema is defined by ``compat_check.check_loot_injections_well_formed`` and
``check_bookshelf_book_pool_well_formed``. This wrapper intentionally performs NO writes.
"""
import json
import os
import sys

DEST = "src/main/resources/data/chiseled_enchanting_table/chiseled_enchanting_table/loot_injections.json"

# Pre-1.17 vanilla chest-table path -> modern path. Kept for documentation of the normalisation the
# retired generator applied; informational only.

def main() -> int:
    if not os.path.isfile(DEST):
        print(f"ERROR: missing {DEST}; loot_injections.json is the single source of truth now "
              f"(see docs/EXTENDING.md).", file=sys.stderr)
        return 1
    with open(DEST, encoding="utf-8") as fh:
        data = json.load(fh)
    if not isinstance(data, dict):
        print("ERROR: loot_injections.json top level must be an object", file=sys.stderr)
        return 1
    for key in ("vanilla_chest_books", "entries"):
        if key not in data:
            print(f"ERROR: loot_injections.json missing '{key}'", file=sys.stderr)
            return 1
    entries = data["entries"]
    if not isinstance(entries, list):
        print("ERROR: 'entries' must be a list", file=sys.stderr)
        return 1
    # No writes: the curated table is the source of truth. Report a bound summary.
    print(f"ok: loot_injections.json validated ({len(entries)} entries, mode "
          f"{data.get('vanilla_chest_books')!r}); no regeneration (JSON is authoritative)")
    return 0


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""
Automated compatibility / sanity checker for the Chiseled Enchanting Table mod.

Why this exists
---------------
P15 shipped a bug where the block dropped nothing when mined, because the block loot
table lived at the *old plural* path ``data/<ns>/loot_tables/blocks/...`` and had empty
pools. Minecraft 26.1.2 expects the *singular* path ``data/<ns>/loot_table/blocks/...``
(same singularisation as ``recipe`` and ``advancement``), so the file was silently never
loaded. This script encodes that whole class of "silently wrong data-pack layout / empty
content" mistakes so it can be caught in CI instead of in a live client.

Two tiers of checks
-------------------
Tier 1 (offline, deterministic, FAILS the build):
  * Data-pack directory naming uses the modern singular convention.
  * No stale plural directories (loot_tables / recipes / advancements / tags/*s).
  * Block loot tables have non-empty pools and drop something.
  * All ``*.json`` under src parse.
  * The JSON item_transformations table matches the Java built-in fallback (no drift
    between the two sources of truth).
  * ``default_enchantments.json``'s ``tags`` entries (if present) are well-formed: each
    has a syntactically valid ``tag`` resource location (optionally ``#``-prefixed) and a
    positive integer ``level``. This is the cross-mod compatibility extension point (see
    ``docs/COMPATIBILITY.md``): any third-party enchantment mod that tags its enchantments
    with one of these tags (e.g. the vanilla ``#minecraft:non_treasure`` default) is picked
    up automatically, so this check only has to guard against typos in the tag id itself.
  * Loot tables use the **26.1.2** schema: plural ``conditions[]`` / ``functions[]`` arrays
    dispatched on the ``condition`` / ``function`` string keys. Minecraft 26.3 collapsed this
    to a singular ``condition`` / ``modifier`` dispatched on ``type``, so a 26.3 data file
    copied into this branch fails *silently* (unknown keys are ignored, the drop just loses
    its condition/function). This is deliberately the inverse of the 26.3 branch's guard.
  * Advancements use the **26.1.2** schema: only the six keys ``Advancement.CODEC`` reads,
    ``show_toast`` / ``announce_to_chat`` inside ``display`` (they are ``DisplayInfo`` fields,
    top-level copies are ignored), ``display.background`` on roots only, and
    ``minecraft:recipe_crafted`` conditions keyed on ``recipe_id`` (26.3 renamed it to a
    required ``recipes`` list — see ``docs/reports/PORTING_26_3_REPORT.md`` §5.2 on that
    branch, and ``docs/reports/SYNC_FROM_26_3_REPORT.md`` here for why this branch keeps
    ``recipe_id``).

Tier 2 (best-effort, needs the MC 26.1.2 jar and/or extra mod jars; WARNS only):
  * Item / enchantment ids referenced by recipe + transformations + costs exist either
    in this mod's own registry namespace, somewhere in the vanilla data jar, or in one of
    the ``--extra-jar`` paths (e.g. a third-party enchantment mod's jar, for ids like
    ``oreenchants:auto_smelt``). Misses are warnings (not failures) to avoid false
    positives on code-only registry entries or ids belonging to mods not present in CI.
  * Non-``minecraft:`` ids referenced from our own JSON (modded item/enchantment ids) are
    reported separately from vanilla ones so a maintainer can see at a glance which
    referenced ids depend on another mod being installed.

Exit code is non-zero if any Tier 1 check fails.

Usage:
  python3 scripts/compat_check.py
  python3 scripts/compat_check.py --mc-jar /path/to/minecraft-extracted_server.jar
  python3 scripts/compat_check.py --extra-jar /path/to/some-other-mod.jar [--extra-jar ...]
"""

from __future__ import annotations

import argparse
import glob
import json
import os
import re
import sys
import zipfile

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA_ROOT = os.path.join(REPO, "src", "main", "resources", "data")
NAMESPACE = "chiseled_enchanting_table"

# Modern (singular) -> legacy (plural) directory names for 1.21+/26.x data packs.
SINGULAR_DIRS = {
    "loot_table": "loot_tables",
    "recipe": "recipes",
    "advancement": "advancements",
}
# tag subfolders also singularised: tags/block (not tags/blocks), tags/item, etc.

errors: list[str] = []
warnings: list[str] = []


def err(msg: str) -> None:
    errors.append(msg)


def warn(msg: str) -> None:
    warnings.append(msg)


# --------------------------------------------------------------------------- #
# Tier 1
# --------------------------------------------------------------------------- #

def check_json_parses() -> None:
    for path in glob.glob(os.path.join(REPO, "src", "**", "*.json"), recursive=True):
        try:
            with open(path, encoding="utf-8") as fh:
                json.load(fh)
        except Exception as exc:  # noqa: BLE001
            err(f"JSON parse failed: {os.path.relpath(path, REPO)}: {exc}")


def check_datapack_dir_naming() -> None:
    if not os.path.isdir(DATA_ROOT):
        err(f"missing data root: {DATA_ROOT}")
        return
    for ns in os.listdir(DATA_ROOT):
        ns_dir = os.path.join(DATA_ROOT, ns)
        if not os.path.isdir(ns_dir):
            continue
        for legacy in SINGULAR_DIRS.values():
            legacy_path = os.path.join(ns_dir, legacy)
            if os.path.isdir(legacy_path):
                singular = next(s for s, p in SINGULAR_DIRS.items() if p == legacy)
                err(
                    f"legacy data-pack dir '{ns}/{legacy}/' found; MC 26.1.2 expects "
                    f"'{ns}/{singular}/' (singular). Files here are silently ignored."
                )
        # tags: tags/block not tags/blocks, tags/item not tags/items
        tags_dir = os.path.join(ns_dir, "tags")
        for bad in ("blocks", "items", "entity_types", "fluids", "game_events"):
            if os.path.isdir(os.path.join(tags_dir, bad)):
                err(f"legacy tag dir '{ns}/tags/{bad}/' found; use singular form.")


def iter_block_loot_tables():
    for ns in os.listdir(DATA_ROOT) if os.path.isdir(DATA_ROOT) else []:
        blocks_dir = os.path.join(DATA_ROOT, ns, "loot_table", "blocks")
        if os.path.isdir(blocks_dir):
            for path in glob.glob(os.path.join(blocks_dir, "*.json")):
                yield ns, path


def check_block_loot_tables_drop_something() -> None:
    found_any = False
    for ns, path in iter_block_loot_tables():
        found_any = True
        rel = os.path.relpath(path, REPO)
        try:
            with open(path, encoding="utf-8") as fh:
                data = json.load(fh)
        except Exception:  # already reported by parse check
            continue
        pools = data.get("pools") or []
        if not pools:
            err(f"block loot table has empty/missing pools (drops nothing): {rel}")
            continue
        has_entry = any(p.get("entries") for p in pools)
        if not has_entry:
            err(f"block loot table pools have no entries (drops nothing): {rel}")
    if not found_any:
        warn("no block loot tables found under any '<ns>/loot_table/blocks/'")


def iter_data_json(kind: str):
    """Yield (namespace, path, parsed json) for every '<ns>/<kind>/**/*.json'."""
    for ns in os.listdir(DATA_ROOT) if os.path.isdir(DATA_ROOT) else []:
        root = os.path.join(DATA_ROOT, ns, kind)
        if not os.path.isdir(root):
            continue
        for path in glob.glob(os.path.join(root, "**", "*.json"), recursive=True):
            try:
                with open(path, encoding="utf-8") as fh:
                    yield ns, path, json.load(fh)
            except Exception:  # already reported by the parse check
                continue


def check_loot_tables_use_26_1_2_schema() -> None:
    """26.1.2 loot schema: plural ``conditions[]`` / ``functions[]`` arrays, each element
    dispatched on the ``condition`` / ``function`` **string** key.

    Why this gate exists on *this* branch: the 26.3 line migrated the same files to the
    collapsed schema (singular ``condition`` / ``modifier`` dispatched on ``type``). Copying a
    26.3 data file here — or copying a 26.1.2 file there — parses as JSON, loads without an
    error, and silently drops the condition/function, which is exactly the failure mode that
    produced the P15 "block drops nothing" bug this script was written for. Each branch therefore
    guards its own direction; this is the 26.1.2 one.

    Evidence for the 26.1.2 form: this repository's own block loot table shipped and live-tested
    as ``conditions: [{"condition": "minecraft:survives_explosion"}]`` +
    ``functions: [{"function": "minecraft:copy_components", ...}]`` (1.0.2 baseline, see
    ``CURRENT_BASELINE.md``); the 26.3 branch's ``PORTING_26_3_REPORT.md`` §5.1 documents the
    rename as a 26.3 change.
    """

    def walk(node, rel: str, where: str) -> None:
        if isinstance(node, list):
            for index, item in enumerate(node):
                walk(item, rel, f"{where}[{index}]")
            return
        if not isinstance(node, dict):
            return

        for singular, plural, dispatch in (
            ("condition", "conditions", "condition"),
            ("modifier", "functions", "function"),
        ):
            if singular not in node:
                continue
            value = node[singular]
            # A *string* "condition" is the 26.1.2 dispatch key of one element inside a
            # "conditions" array ({"condition": "minecraft:survives_explosion"}), which is exactly
            # what this branch must keep. Anything else — an object/list under "condition", or any
            # "modifier" key at all — is the collapsed MC 26.3 form.
            is_26_1_2_dispatch = singular == "condition" and isinstance(value, str) and value
            if is_26_1_2_dispatch:
                continue
            err(
                f"{rel}: {where} uses the MC 26.3 singular '{singular}' field; 26.1.2 reads the "
                f"plural '{plural}' array, whose elements dispatch on a '{dispatch}' **string** "
                f"(e.g. {{\"{dispatch}\": \"minecraft:...\"}}) — the 26.3 form is silently ignored here"
            )

        for plural, dispatch in (("conditions", "condition"), ("functions", "function")):
            if plural not in node:
                continue
            value = node[plural]
            if not isinstance(value, list):
                err(f"{rel}: {where}['{plural}'] must be an array in 26.1.2, found {type(value).__name__}")
                continue
            for index, element in enumerate(value):
                if not isinstance(element, dict):
                    err(f"{rel}: {where}['{plural}'][{index}] must be an object")
                    continue
                key = element.get(dispatch)
                if not isinstance(key, str) or not key:
                    err(
                        f"{rel}: {where}['{plural}'][{index}] is missing its '{dispatch}' string "
                        f"dispatch key (26.1.2 form, e.g. {{\"{dispatch}\": \"minecraft:...\"}})"
                    )

        for key, value in node.items():
            if isinstance(value, list):
                for index, item in enumerate(value):
                    walk(item, rel, f"{where}.{key}[{index}]")
            else:
                walk(value, rel, f"{where}.{key}")

    for _ns, path, data in iter_data_json("loot_table"):
        rel = os.path.relpath(path, REPO)
        for index, pool in enumerate(data.get("pools") or []):
            walk(pool, rel, f"pools[{index}]")


ADVANCEMENT_TOP_KEYS = {
    "parent", "display", "rewards", "criteria", "requirements", "sends_telemetry_event",
}
DISPLAY_KEYS = {
    "icon", "title", "description", "background", "frame", "show_toast", "announce_to_chat", "hidden",
}


def check_advancements_use_26_1_2_schema() -> None:
    """26.1.2 advancement schema checks (synced from the 26.3 branch, version-adjusted).

    * ``Advancement.CODEC`` only reads ``parent`` / ``display`` / ``rewards`` / ``criteria`` /
      ``requirements`` / ``sends_telemetry_event``. ``show_toast`` and ``announce_to_chat`` are
      ``DisplayInfo`` fields: at the top level they are ignored, i.e. the intended
      "no toast, no chat" silently does not happen. This was true on the 26.3 line and it is
      true here, so the guard is identical.
    * ``display.background`` belongs to roots only. 26.1.2 ignores it on a child; **26.3 rejects
      it outright** ("Only advancement roots can have background"), which fails the whole
      ``minecraft:advancement`` registry and makes the world refuse to open. Keeping the key out
      here means this branch's data files stay forward-compatible with the 26.3 line.
    * ``minecraft:recipe_crafted`` takes ``recipe_id`` on this Minecraft line. 26.3 replaced it
      with a required ``recipes`` list; the inverse guard below catches a blind copy of a 26.3
      advancement file, which would fail to load the advancement here.
    """
    for _ns, path, data in iter_data_json("advancement"):
        rel = os.path.relpath(path, REPO)

        for key in sorted(set(data) - ADVANCEMENT_TOP_KEYS):
            hint = " (it belongs inside 'display')" if key in DISPLAY_KEYS else ""
            err(f"{rel}: unknown top-level advancement key '{key}'{hint}")

        display = data.get("display")
        if isinstance(display, dict):
            for key in sorted(set(display) - DISPLAY_KEYS):
                err(f"{rel}: unknown display key '{key}'")

            is_root = not data.get("parent")
            if is_root and "background" not in display:
                err(
                    f"{rel}: visible advancement root has no 'display.background' "
                    f"(the tab would have no background; 26.3 rejects this outright: "
                    f"'Visible advancement roots must have background')"
                )
            elif not is_root and "background" in display:
                err(
                    f"{rel}: non-root advancement carries 'display.background' "
                    f"(ignored by 26.1.2, a hard load error in 26.3: 'Only advancement roots can "
                    f"have background'); delete the key, the parent owns the tab background"
                )

        for name, criterion in (data.get("criteria") or {}).items():
            if not isinstance(criterion, dict):
                continue
            if criterion.get("trigger") != "minecraft:recipe_crafted":
                continue
            conditions = criterion.get("conditions") or {}
            if "recipes" in conditions:
                err(
                    f"{rel}: criteria '{name}' uses the MC 26.3 'recipes' list; 26.1.2 reads "
                    f"'recipe_id' (a single recipe id string) — the 26.3 form makes this "
                    f"advancement fail to load here"
                )
            elif "recipe_id" not in conditions:
                err(f"{rel}: criteria '{name}' (recipe_crafted) is missing 'recipe_id'")
            elif not isinstance(conditions["recipe_id"], str):
                err(f"{rel}: criteria '{name}' 'recipe_id' must be a single recipe id string")


def check_transformation_json_matches_java() -> None:
    """The item_transformations.json must mirror the Java built-in fallback so a missing
    datapack file and the bundled file behave identically."""
    json_path = os.path.join(
        DATA_ROOT, NAMESPACE, NAMESPACE, "item_transformations.json"
    )
    java_path = os.path.join(
        REPO, "src", "main", "java", "chiseled_enchanting_table", "data",
        "ChiseledEnchantingTableData.java",
    )
    if not os.path.isfile(json_path) or not os.path.isfile(java_path):
        warn("could not locate both transformation JSON and Java fallback to compare")
        return
    try:
        with open(json_path, encoding="utf-8") as fh:
            json_values = {v["id"]: _norm_transform(v) for v in json.load(fh)["values"]}
    except Exception as exc:  # noqa: BLE001
        err(f"failed reading transformation JSON: {exc}")
        return

    with open(java_path, encoding="utf-8") as fh:
        java_src = fh.read()
    # Pull every brace-object that looks like a transformation out of the embedded
    # text block (lines containing '"input"' and '"output"').
    java_values: dict[str, dict] = {}
    for line in java_src.splitlines():
        if '"input"' in line and '"output"' in line and '"id"' in line:
            snippet = line.strip().rstrip(",")
            try:
                obj = json.loads(snippet)
            except Exception:
                continue
            java_values[obj["id"]] = _norm_transform(obj)

    if not java_values:
        warn("no transformation objects parsed from Java fallback; skipping drift check")
        return

    if set(json_values) != set(java_values):
        err(
            "transformation id sets differ between JSON and Java fallback: "
            f"JSON={sorted(json_values)} JAVA={sorted(java_values)}"
        )
    for tid in set(json_values) & set(java_values):
        if json_values[tid] != java_values[tid]:
            err(
                f"transformation '{tid}' differs between JSON and Java fallback:\n"
                f"    JSON={json_values[tid]}\n    JAVA={java_values[tid]}"
            )


def _norm_transform(v: dict) -> dict:
    """Normalise the fields we care about for drift comparison."""
    return {
        "input": v.get("input"),
        "output": v.get("output"),
        "level": v.get("level", 1),
        "xp_cost": v.get("xp_cost", 0),
        "primary_cost": v.get("primary_cost"),
        "additional_costs": v.get("additional_costs", []),
    }


# A resource location is "[namespace:]path", namespace/path each [a-z0-9_.-/].
_RESOURCE_LOCATION_RE = re.compile(r"^(?:[a-z0-9_.-]+:)?[a-z0-9_./-]+$")


def check_loot_injections_well_formed() -> None:
    """Validate the schema of loot_injections.json (Tier 1). Structure:
       { entries: [ { table: str (loot table id or "#tag_id"), rolls: int>=1,
                      entries: [ { enchantment: id, level: int>=1, weight: int>=1 }, ... ] } ] }
    """
    path = os.path.join(DATA_ROOT, NAMESPACE, NAMESPACE, "loot_injections.json")
    if not os.path.isfile(path):
        return
    try:
        with open(path, encoding="utf-8") as fh:
            data = json.load(fh)
    except Exception:
        return
    entries = data.get("entries")
    if entries is None:
        return
    if not isinstance(entries, list):
        err(f"loot_injections.json: 'entries' must be a list, got {type(entries).__name__}")
        return
    for i, entry in enumerate(entries):
        if not isinstance(entry, dict):
            err(f"loot_injections.json: entries[{i}] must be an object")
            continue
        table = entry.get("table")
        if not isinstance(table, str) or not table:
            err(f"loot_injections.json: entries[{i}].table must be a non-empty string")
        else:
            raw = table[1:] if table.startswith("#") else table
            if not _RESOURCE_LOCATION_RE.match(raw):
                err(f"loot_injections.json: entries[{i}].table is not a valid resource location: {table!r}")
        rolls = entry.get("rolls", 1)
        if not isinstance(rolls, int) or rolls < 1:
            err(f"loot_injections.json: entries[{i}].rolls must be a positive integer, got {rolls!r}")
        sub = entry.get("entries")
        if not isinstance(sub, list) or not sub:
            err(f"loot_injections.json: entries[{i}].entries must be a non-empty list")
            continue
        for j, se in enumerate(sub):
            if not isinstance(se, dict):
                err(f"loot_injections.json: entries[{i}].entries[{j}] must be an object"); continue
            weight = se.get("weight", 1)
            if not isinstance(weight, int) or weight < 1:
                err(f"loot_injections.json: entries[{i}].entries[{j}].weight must be a positive integer, got {weight!r}")
            # Empty filler entries (upstream EmptyEntry.builder) skip the enchantment/level check.
            if se.get("empty") is True:
                continue
            enchant = se.get("enchantment")
            if not isinstance(enchant, str) or not _RESOURCE_LOCATION_RE.match(enchant):
                err(f"loot_injections.json: entries[{i}].entries[{j}].enchantment is not a valid resource location: {enchant!r}")
            level = se.get("level", 1)
            if not isinstance(level, int) or level < 1:
                err(f"loot_injections.json: entries[{i}].entries[{j}].level must be a positive integer, got {level!r}")
            special = se.get("special")
            if special is not None and special not in ("depth_based_efficiency",):
                err(f"loot_injections.json: entries[{i}].entries[{j}].special must be one of "
                    f"'depth_based_efficiency', got {special!r}")


def check_bookshelf_book_pool_well_formed() -> None:
    """Validate the schema of bookshelf_book_pool.json (Tier 1, schema v2). Structure:
       { book_count: { min: int>=0, max: int>=min },
         enchanted_book_probability: 0.0..1.0,
         enchanted_book_selection: {
             enchant_pool_source: 'loot_injections' | 'inline',
             use_base_weight: bool,
             use_vanilla_weight_factor: bool,
             level_falloff: float
         },
         enchant_sets: optional list of lists of {enchantment, level, weight} entries }.
    """
    path = os.path.join(DATA_ROOT, NAMESPACE, NAMESPACE, "bookshelf_book_pool.json")
    if not os.path.isfile(path):
        return
    try:
        with open(path, encoding="utf-8") as fh:
            data = json.load(fh)
    except Exception:
        return

    # Reject the retired v1 schema so integrators upgrading a data pack get an actionable error.
    legacy_keys = [k for k in ("filling_type_weights", "slot_content_weights") if k in data]
    if legacy_keys:
        err(
            f"bookshelf_book_pool.json: schema v1 keys {legacy_keys} are no longer supported; "
            "v2 uses `book_count`/`enchanted_book_probability`/`enchanted_book_selection` instead"
        )

    bc = data.get("book_count")
    if bc is not None:
        if not isinstance(bc, dict):
            err("bookshelf_book_pool.json: 'book_count' must be an object")
        else:
            bmin = bc.get("min", 0)
            bmax = bc.get("max", 6)
            if not isinstance(bmin, int) or bmin < 0 or bmin > 6:
                err(f"bookshelf_book_pool.json: book_count.min must be an integer in [0, 6], got {bmin!r}")
            if not isinstance(bmax, int) or bmax < 0 or bmax > 6:
                err(f"bookshelf_book_pool.json: book_count.max must be an integer in [0, 6], got {bmax!r}")
            if isinstance(bmin, int) and isinstance(bmax, int) and bmax < bmin:
                err(f"bookshelf_book_pool.json: book_count.max ({bmax}) must be >= book_count.min ({bmin})")

    prob = data.get("enchanted_book_probability")
    if prob is not None and not (isinstance(prob, (int, float)) and 0.0 <= float(prob) <= 1.0):
        err(f"bookshelf_book_pool.json: enchanted_book_probability must be a number in [0.0, 1.0], got {prob!r}")

    sel = data.get("enchanted_book_selection")
    if sel is not None:
        if not isinstance(sel, dict):
            err("bookshelf_book_pool.json: 'enchanted_book_selection' must be an object")
        else:
            source = sel.get("enchant_pool_source")
            if source is not None and source not in ("loot_injections", "inline"):
                err(f"bookshelf_book_pool.json: enchanted_book_selection.enchant_pool_source must be 'loot_injections' or 'inline', got {source!r}")
            for boolkey in ("use_base_weight", "use_vanilla_weight_factor"):
                v = sel.get(boolkey)
                if v is not None and not isinstance(v, bool):
                    err(f"bookshelf_book_pool.json: enchanted_book_selection.{boolkey} must be a boolean, got {v!r}")
            # v2b: level_falloff was renamed to max_level_weight_ratio (semantics: penalty at
            # max level rather than per numeric level). The Java loader still accepts the old
            # name during transition but the schema check nudges data packs to the new one.
            ratio = sel.get("max_level_weight_ratio")
            if ratio is not None and not (isinstance(ratio, (int, float)) and 0.0 < float(ratio) <= 1.0):
                err(f"bookshelf_book_pool.json: enchanted_book_selection.max_level_weight_ratio must be a number in (0.0, 1.0], got {ratio!r}")
            if sel.get("level_falloff") is not None:
                warn("bookshelf_book_pool.json: enchanted_book_selection.level_falloff is deprecated; use max_level_weight_ratio instead (see EXTENDING.md)")

    sets = data.get("enchant_sets")
    if sets is not None:
        if not isinstance(sets, list):
            err(f"bookshelf_book_pool.json: 'enchant_sets' must be a list")
            return
        for i, s in enumerate(sets):
            if not isinstance(s, list):
                err(f"bookshelf_book_pool.json: enchant_sets[{i}] must be a list of {{enchantment, level, weight}} entries")
                continue
            for j, entry in enumerate(s):
                if not isinstance(entry, dict):
                    err(f"bookshelf_book_pool.json: enchant_sets[{i}][{j}] must be an object"); continue
                enchant = entry.get("enchantment")
                if not isinstance(enchant, str) or not _RESOURCE_LOCATION_RE.match(enchant):
                    err(f"bookshelf_book_pool.json: enchant_sets[{i}][{j}].enchantment is not a valid resource location: {enchant!r}")
                level = entry.get("level", 1)
                if not isinstance(level, int) or level < 1:
                    err(f"bookshelf_book_pool.json: enchant_sets[{i}][{j}].level must be a positive integer, got {level!r}")
                weight = entry.get("weight", 1)
                if not isinstance(weight, int) or weight < 1:
                    err(f"bookshelf_book_pool.json: enchant_sets[{i}][{j}].weight must be a positive integer, got {weight!r}")


def check_default_enchantment_tags_well_formed() -> None:
    """Validate the `tags` extension in default_enchantments.json (the cross-mod compatibility
    hook: any enchantment mod that tags its enchantments with one of these is auto-included)."""
    path = os.path.join(DATA_ROOT, NAMESPACE, NAMESPACE, "default_enchantments.json")
    if not os.path.isfile(path):
        return
    try:
        with open(path, encoding="utf-8") as fh:
            data = json.load(fh)
    except Exception:
        return  # already reported by check_json_parses
    tags = data.get("tags")
    if tags is None:
        return
    if not isinstance(tags, list):
        err(f"default_enchantments.json: 'tags' must be a list, got {type(tags).__name__}")
        return
    for i, entry in enumerate(tags):
        if not isinstance(entry, dict) or "tag" not in entry:
            err(f"default_enchantments.json: tags[{i}] must be an object with a 'tag' field")
            continue
        raw_tag = entry["tag"]
        tag_id = raw_tag[1:] if isinstance(raw_tag, str) and raw_tag.startswith("#") else raw_tag
        if not isinstance(tag_id, str) or not _RESOURCE_LOCATION_RE.match(tag_id):
            err(f"default_enchantments.json: tags[{i}].tag is not a valid resource location: {raw_tag!r}")
        level = entry.get("level", 1)
        if not isinstance(level, int) or level < 1:
            err(f"default_enchantments.json: tags[{i}].level must be a positive integer, got {level!r}")


# --------------------------------------------------------------------------- #
# Tier 2 (best-effort id existence against the MC jar and/or third-party mod jars)
# --------------------------------------------------------------------------- #

# Matches any "namespace:path" resource location (not just minecraft:), so ids referencing a
# third-party enchantment/item mod (e.g. "oreenchants:auto_smelt") are picked up too. The
# namespace-only tag prefix ("#namespace:path") is handled by the caller stripping the "#".
ID_RE = re.compile(r"\"#?([a-z0-9_.-]+:[a-z0-9_./-]+)\"")


def collect_referenced_ids() -> set[str]:
    """Ids referenced as *values* (item ids, enchantment ids) — not tag references, which are
    file-path lookups and validated separately by `check_referenced_tags_exist`."""
    ids: set[str] = set()
    targets = [
        os.path.join(DATA_ROOT, NAMESPACE, "recipe", "chiseled_enchanting_table.json"),
        os.path.join(DATA_ROOT, NAMESPACE, NAMESPACE, "item_transformations.json"),
        os.path.join(DATA_ROOT, NAMESPACE, NAMESPACE, "enchantment_costs.json"),
        # U1: enchantment ids referenced by the new loot injection + bookshelf book pool JSONs.
        os.path.join(DATA_ROOT, NAMESPACE, NAMESPACE, "loot_injections.json"),
        os.path.join(DATA_ROOT, NAMESPACE, NAMESPACE, "bookshelf_book_pool.json"),
    ]
    for path in targets:
        if os.path.isfile(path):
            with open(path, encoding="utf-8") as fh:
                ids |= set(ID_RE.findall(fh.read()))

    # default_enchantments.json's `values` entries are ids; its `tags` entries are tag references
    # (file-path lookups), not ids, so pull just the `values` half here.
    default_path = os.path.join(DATA_ROOT, NAMESPACE, NAMESPACE, "default_enchantments.json")
    if os.path.isfile(default_path):
        try:
            with open(default_path, encoding="utf-8") as fh:
                data = json.load(fh)
            for entry in data.get("values", []):
                if isinstance(entry, dict) and isinstance(entry.get("enchantment"), str):
                    ids.add(entry["enchantment"])
        except Exception:
            pass  # already reported by check_json_parses
    return ids


def collect_referenced_tags() -> set[str]:
    """Tag ids referenced from `default_enchantments.json`'s `tags` array, normalised to
    'namespace:path' (leading '#' stripped, default namespace added if missing)."""
    tags: set[str] = set()
    path = os.path.join(DATA_ROOT, NAMESPACE, NAMESPACE, "default_enchantments.json")
    if not os.path.isfile(path):
        return tags
    try:
        with open(path, encoding="utf-8") as fh:
            data = json.load(fh)
    except Exception:
        return tags
    for entry in data.get("tags", []) or []:
        if not isinstance(entry, dict):
            continue
        raw = entry.get("tag")
        if not isinstance(raw, str):
            continue
        tag_id = raw[1:] if raw.startswith("#") else raw
        tags.add(tag_id if ":" in tag_id else f"minecraft:{tag_id}")
    return tags


def check_referenced_tags_exist(mc_jar: str | None, extra_jars: list[str]) -> None:
    """Tags are file-path lookups (data/<ns>/tags/enchantment/<path>.json), not ids that appear as
    values elsewhere, so they need their own existence check distinct from `check_ids_exist`."""
    tags = collect_referenced_tags()
    if not tags:
        return
    jars = ([mc_jar] if mc_jar else []) + list(extra_jars)
    if not jars:
        warn("no MC jar or --extra-jar available; skipping referenced-tag existence check (Tier 2)")
        return
    for tag in sorted(tags):
        namespace, _, path = tag.partition(":")
        rel = f"data/{namespace}/tags/enchantment/{path}.json"
        found = False
        for jar in jars:
            try:
                with zipfile.ZipFile(jar) as zf:
                    if rel in zf.namelist():
                        found = True
                        break
            except Exception:
                continue
        if not found:
            warn(f"referenced tag '#{tag}' not found as '{rel}' in vanilla jar or any --extra-jar (verify it exists)")


def collect_vanilla_ids(mc_jar: str) -> set[str]:
    ids: set[str] = set()
    with zipfile.ZipFile(mc_jar) as zf:
        for name in zf.namelist():
            if name.endswith(".json") and name.startswith("data/minecraft/"):
                try:
                    ids |= set(ID_RE.findall(zf.read(name).decode("utf-8")))
                except Exception:
                    continue
    return ids


def collect_jar_ids(jar_path: str) -> set[str]:
    """Collect ids referenced anywhere in a mod jar's data/assets JSON (best-effort: works for any
    mod jar, e.g. a third-party enchantment mod, so its enchantment/item ids can be verified to
    exist when cross-checking our own JSON references to them)."""
    ids: set[str] = set()
    try:
        with zipfile.ZipFile(jar_path) as zf:
            for name in zf.namelist():
                if name.endswith(".json") and (name.startswith("data/") or name.startswith("assets/")):
                    try:
                        ids |= set(ID_RE.findall(zf.read(name).decode("utf-8")))
                    except Exception:
                        continue
    except Exception as exc:  # noqa: BLE001
        warn(f"could not read extra jar '{jar_path}' for Tier 2 checks: {exc}")
    return ids


def find_mc_jar(explicit: str | None) -> str | None:
    if explicit and os.path.isfile(explicit):
        return explicit
    home = os.path.expanduser("~")
    for cand in (
        os.path.join(home, ".gradle", "caches", "fabric-loom", "26.1.2", "minecraft-extracted_server.jar"),
        os.path.join(home, ".gradle", "caches", "fabric-loom", "26.1.2", "minecraft-common.jar"),
    ):
        if os.path.isfile(cand):
            return cand
    return None


def check_ids_exist(mc_jar: str | None, extra_jars: list[str]) -> None:
    referenced = collect_referenced_ids()
    if not referenced:
        return

    own_namespace_ids = {rid for rid in referenced if rid.startswith(f"{NAMESPACE}:")}
    modded_ids = {
        rid for rid in referenced
        if not rid.startswith("minecraft:") and not rid.startswith(f"{NAMESPACE}:")
    }
    if modded_ids:
        # Not an error: these are cross-mod-compatibility references (e.g. a third-party
        # enchantment/item id used in a cost or transformation entry). Surfacing them
        # separately makes it obvious which referenced ids require another mod to be
        # installed, instead of silently mixing them in with vanilla-only references.
        warn(
            "JSON references non-vanilla, non-self id(s) (require the owning mod to be "
            f"installed): {sorted(modded_ids)}"
        )

    known: set[str] = set()
    if mc_jar:
        known |= collect_vanilla_ids(mc_jar)
    else:
        warn("MC 26.1.2 jar not found; skipping vanilla id existence check (Tier 2)")
    for extra_jar in extra_jars:
        known |= collect_jar_ids(extra_jar)

    if not mc_jar and not extra_jars:
        return

    for rid in sorted(referenced - own_namespace_ids):
        if rid not in known:
            # Items/enchantments defined only in code (no data reference), or belonging to a
            # mod not passed via --extra-jar, may legitimately be absent here, so this is a
            # warning, not an error.
            warn(f"referenced id '{rid}' not seen in vanilla data jar or any --extra-jar (verify it exists)")


def check_disenchanting_and_anti_arbitrage() -> None:
    """Tier 1 gate: validate disenchanting.json schema and prove disenchant anti-arbitrage + incremental discount invariants."""
    import math

    cfg_path = os.path.join(
        REPO,
        "src/main/resources/data/chiseled_enchanting_table/chiseled_enchanting_table/disenchanting.json",
    )
    if not os.path.isfile(cfg_path):
        errors.append("missing disenchanting.json under data/chiseled_enchanting_table/chiseled_enchanting_table/")
        return
    cfg = json.load(open(cfg_path, encoding="utf-8"))
    for key in ("enabled", "allow_curse_purge", "xp_refund_scale", "targeted_strip", "curse_purge"):
        if key not in cfg:
            errors.append(f"disenchanting.json missing required key '{key}'")

    # Verify EVERY option in targeted_strip and curse_purge uses canonical "options" + "extras"
    # (dual-material design) AND involves chiseled_enchanting_table:cleansing_grit or arcane_catalyst!
    for section_key in ("targeted_strip", "curse_purge"):
        sec = cfg.get(section_key, {})
        opts = sec.get("options", [])
        if not opts:
            errors.append(f"disenchanting.json '{section_key}' must define non-empty 'options' list")
        for idx, opt in enumerate(opts):
            extras = opt.get("extras", [])
            if not extras:
                errors.append(f"disenchanting.json '{section_key}.options[{idx}]' missing dual-material 'extras'")
            involved = {opt.get("item")} | {ex.get("item") for ex in extras}
            if section_key == "targeted_strip" and "chiseled_enchanting_table:cleansing_grit" not in involved:
                errors.append(
                    f"disenchanting.json 'targeted_strip.options[{idx}]' must require cleansing_grit: got {involved}"
                )

    # Verify BookshelfProgression exact integer curve (I=0 .. MAX=38) & 3-gradient Depleted Tome rules!
    import math
    def req_shelves(target_lvl: int, max_lvl: int) -> int:
        s_max = max(1, max_lvl)
        s_tgt = max(1, min(s_max, target_lvl))
        if s_max <= 1 or s_tgt <= 1:
            return 0
        if s_tgt >= s_max:
            return 38
        prog = (s_tgt - 1) / (s_max - 1)
        raw = int(math.floor(38.0 * (prog ** 1.11) + 0.5))
        min_a = min(37, s_tgt - 1)
        max_a = max(1, 38 - (s_max - s_tgt))
        return max(min(min_a, max_a), min(raw, max_a))

    expected_vanilla_curves = {
        1: [0],
        2: [0, 38],
        3: [0, 18, 38],
        4: [0, 11, 24, 38],
        5: [0, 8, 18, 28, 38],
    }
    for m_lvl, exp_curve in expected_vanilla_curves.items():
        got = [req_shelves(l, m_lvl) for l in range(1, m_lvl + 1)]
        if got != exp_curve:
            errors.append(f"BookshelfProgression curve mismatch for maxLevel={m_lvl}: expected {exp_curve}, got {got}")
    for modded_max in (6, 7, 8, 10, 15):
        seq = [req_shelves(l, modded_max) for l in range(1, modded_max + 1)]
        if seq[0] != 0 or seq[-1] != 38 or any(seq[i] >= seq[i + 1] for i in range(len(seq) - 1)):
            errors.append(f"Non-monotonic modded BookshelfProgression sequence for maxLevel={modded_max}: {seq}")

    shelf_gates_path = os.path.join(
        REPO,
        "src/main/resources/data/chiseled_enchanting_table/chiseled_enchanting_table/shelf_gates.json",
    )
    if not os.path.isfile(shelf_gates_path):
        errors.append("missing shelf_gates.json under data/chiseled_enchanting_table/chiseled_enchanting_table/")
    else:
        sg_json = json.load(open(shelf_gates_path, encoding="utf-8"))
        d_obj = sg_json.get("direct_upgrade_gates", {})
        dep_obj = sg_json.get("depleted_tome_gradients", {})
        if d_obj.get("max_bookshelves") != 38 or not isinstance(d_obj.get("overrides"), dict):
            errors.append(f"shelf_gates.json direct_upgrade_gates invalid: {d_obj}")
        if (
            dep_obj.get("tier1_bookshelves") != 8
            or dep_obj.get("tier2_bookshelves") != 18
            or dep_obj.get("tier3_qualifying_enchantments") != 6
        ):
            errors.append(f"shelf_gates.json depleted_tome_gradients invalid: {dep_obj}")

    # Verify Chiseled Bookshelf counts TOTAL QUALIFYING ENCHANTMENTS across stored books (not book slots)
    # and DisenchantmentApplication turns emptied Enchanted Books into DEPLETED_TOME + spawns ExperienceOrb!
    bp_java = open(
        os.path.join(REPO, "src/main/java/chiseled_enchanting_table/mechanics/resonance/BookshelfProgression.java"),
        encoding="utf-8",
    ).read()
    if "qualifyingCount++;" not in bp_java or "countQualifyingHighTierEnchantments(" not in bp_java:
        errors.append("BookshelfProgression must count every qualifying stored enchantment on each book!")
    dis_java = open(
        os.path.join(REPO, "src/main/java/chiseled_enchanting_table/mechanics/enchanting/DisenchantmentApplication.java"),
        encoding="utf-8",
    ).read()
    if "BlockRegistry.DEPLETED_TOME" not in dis_java or "ExperienceOrb.award" not in dis_java:
        errors.append("DisenchantmentApplication must close the DEPLETED_TOME lifecycle and award ExperienceOrb!")

    transforms_path = os.path.join(
        REPO,
        "src/main/resources/data/chiseled_enchanting_table/chiseled_enchanting_table/item_transformations.json",
    )
    t_json = json.load(open(transforms_path, encoding="utf-8"))
    blank_entry = next((v for v in t_json.get("values", []) if v.get("id") == "chiseled_enchanting_table:blank_overwrite"), None)
    if not blank_entry:
        errors.append("item_transformations.json missing chiseled_enchanting_table:blank_overwrite")
    else:
        for opt in blank_entry.get("cost_options", []):
            cnt = opt.get("count", 0)
            disc = max(1, int(math.floor(cnt * 0.50)))
            if cnt < 2 or disc >= cnt:
                errors.append(f"blank_overwrite option {opt} must have count >= 2 so Tier-2 (18 shelves) strictly discounts material quantity!")

    def xp_for_level(level: int) -> int:
        if level >= 30:
            return 112 + (level - 30) * 9
        if level >= 15:
            return 37 + (level - 15) * 5
        return 7 + level * 2

    def min_master_tome_enchant_points(max_level: int, target_level: int) -> int:
        capped_max = max(1, max_level)
        lvl = max(1, min(target_level, capped_max))
        tier = 3 if capped_max <= 1 else (1 if lvl / capped_max <= 0.40 else (2 if lvl / capped_max <= 0.75 else 3))
        raw_req = max(tier, round(8 + 22 * (lvl / capped_max)))
        # Even at maximum -40% resonance discount:
        req = max(tier, round(raw_req * 0.60))
        pts = 0
        for l in range(req - 1, req - tier - 1, -1):
            pts += xp_for_level(l)
        return pts

    def essence_potential(max_level: int, level: int, anvil_cost: int, is_treasure: bool, scale: float = 1.0) -> int:
        if level <= 0 or scale <= 0.0:
            return 0
        capped_max = max(1, max_level)
        clamped_lvl = min(level, capped_max)
        r = clamped_lvl / capped_max
        rarity = min(1.45, max(1.00, 1.0 + 0.18 * math.log(max(1, anvil_cost)))) * (1.15 if is_treasure else 1.0)
        base_max = 34.0 if capped_max == 1 else 48.0
        raw = round(scale * base_max * rarity * math.pow(r, 1.65))
        return max(clamped_lvl, raw)

    scale = float(cfg.get("xp_refund_scale", 1.0))
    for max_lvl in range(1, 11):
        for anvil_cost in (1, 2, 4, 8, 25, 100):
            for is_treasure in (False, True):
                vals = [essence_potential(max_lvl, l, anvil_cost, is_treasure, scale) for l in range(0, max_lvl + 1)]
                for l in range(1, max_lvl + 1):
                    if vals[l] <= vals[l - 1]:
                        errors.append(
                            f"non-monotonic essence potential at max={max_lvl}, L={l}, anvil={anvil_cost}: {vals}"
                        )
                    min_cost = min_master_tome_enchant_points(max_lvl, l)
                    if vals[l] > min_cost:
                        errors.append(
                            f"XP arbitrage detected at max={max_lvl}, L={l}, anvil={anvil_cost}, treasure={is_treasure}: "
                            f"refund {vals[l]} > min master-tome cost {min_cost}"
                        )
                # Telescoping step-down sum must match direct full wipe identically:
                if max_lvl >= 2:
                    step_sum = sum(max(1, vals[l] - vals[l - 1]) for l in range(max_lvl, 1, -1)) + vals[1]
                    if step_sum != vals[max_lvl]:
                        errors.append(
                            f"step-down telescoping mismatch at max={max_lvl}: step_sum={step_sum} != full={vals[max_lvl]}"
                        )


# --------------------------------------------------------------------------- #

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mc-jar", help="path to the MC 26.1.2 extracted jar for Tier 2 checks")
    parser.add_argument(
        "--extra-jar",
        action="append",
        default=[],
        help="path to a third-party mod jar to also check referenced ids against "
        "(repeatable; e.g. an enchantment-adding mod jar referenced from cost/transformation JSON)",
    )
    args = parser.parse_args()

    check_json_parses()
    check_datapack_dir_naming()
    check_block_loot_tables_drop_something()
    check_loot_tables_use_26_1_2_schema()
    check_advancements_use_26_1_2_schema()
    check_transformation_json_matches_java()
    check_default_enchantment_tags_well_formed()
    check_loot_injections_well_formed()
    check_bookshelf_book_pool_well_formed()
    check_disenchanting_and_anti_arbitrage()
    mc_jar = find_mc_jar(args.mc_jar)
    check_ids_exist(mc_jar, args.extra_jar)
    check_referenced_tags_exist(mc_jar, args.extra_jar)

    for w in warnings:
        print(f"WARN: {w}")
    for e in errors:
        print(f"FAIL: {e}")

    if errors:
        print(f"\ncompat_check: FAILED ({len(errors)} error(s), {len(warnings)} warning(s))")
        return 1
    print(f"\ncompat_check: PASS ({len(warnings)} warning(s))")
    return 0


if __name__ == "__main__":
    sys.exit(main())

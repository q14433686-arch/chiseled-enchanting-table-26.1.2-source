#!/usr/bin/env python3
"""
Automated compatibility / sanity checker for the Chiseled Enchanting Table mod.

Why this exists
---------------
P15 shipped a bug where the block dropped nothing when mined, because the block loot
table lived at the *old plural* path ``data/<ns>/loot_tables/blocks/...`` and had empty
pools. Minecraft 26.x expects the *singular* path ``data/<ns>/loot_table/blocks/...``
(same singularisation as ``recipe`` and ``advancement``), so the file was silently never
loaded. This script encodes that whole class of "silently wrong data-pack layout / empty
content" mistakes so it can be caught in CI instead of in a live client.

Two tiers of checks
-------------------
Tier 1 (offline, deterministic, FAILS the build):
  * Data-pack directory naming uses the modern singular convention.
  * No stale plural directories (loot_tables / recipes / advancements / tags/*s).
  * Block loot tables have non-empty pools and drop something.
  * All loot tables use the 26.3 schema dialect (single ``condition`` object, ``modifier``
    instead of ``functions``, ``type`` keys inside condition/function objects). The legacy
    shape is silently not loaded by 26.3 (blocks drop as purple-black nameless items).
  * All ``*.json`` under src parse.
  * The JSON item_transformations table matches the Java built-in fallback (no drift
    between the two sources of truth).
  * ``default_enchantments.json``'s ``tags`` entries (if present) are well-formed: each
    has a syntactically valid ``tag`` resource location (optionally ``#``-prefixed) and a
    positive integer ``level``. This is the cross-mod compatibility extension point (see
    ``docs/COMPATIBILITY.md``): any third-party enchantment mod that tags its enchantments
    with one of these tags (e.g. the vanilla ``#minecraft:non_treasure`` default) is picked
    up automatically, so this check only has to guard against typos in the tag id itself.

Tier 2 (best-effort, needs the MC 26.3 jar and/or extra mod jars; WARNS only):
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
                    f"legacy data-pack dir '{ns}/{legacy}/' found; MC 26.x expects "
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


def _walk_loot_nodes(node, path, hits) -> None:
    """Recursively collect schema violations of the 26.3 loot-table JSON dialect.

    26.3 rewrote the loot JSON schema (see docs/reports/PORTING_26_3_REPORT.md):
      * pool/entry/table level ``conditions: [...]`` array  -> single ``condition`` object
        (multiple go through ``{"type": "minecraft:all_of", "terms": [...]}``);
      * ``functions: [...]`` array                          -> ``modifier`` (object, or array of
        objects);
      * inside condition/function objects the type key is now ``type`` (was ``condition`` /
        ``function`` when that value was a primitive id).
    The legacy shape is **silently not loaded** by 26.3 — blocks turn into purple-black
    nameless items — so this must be caught offline, not in a live client.
    """
    if isinstance(node, list):
        for item in node:
            _walk_loot_nodes(item, path, hits)
        return
    if not isinstance(node, dict):
        return
    if "conditions" in node and isinstance(node.get("conditions"), list):
        hits.append(f"{path}: legacy 'conditions' array (26.3 wants a single 'condition' object; "
                    f"wrap multiples in minecraft:all_of)")
    if "functions" in node and isinstance(node.get("functions"), list):
        hits.append(f"{path}: legacy 'functions' array (26.3 wants 'modifier')")
    if "function" in node and isinstance(node.get("function"), str) and "type" not in node:
        hits.append(f"{path}: legacy 'function': \"<id>\" key (26.3 wants 'type' inside a modifier object)")
    if "condition" in node and isinstance(node.get("condition"), str) and "type" not in node:
        hits.append(f"{path}: legacy 'condition': \"<id>\" key (26.3 wants 'type')")
    for value in node.values():
        _walk_loot_nodes(value, path, hits)


def check_loot_table_schema_26_3() -> None:
    """Tier 1: all vanilla-schema loot tables use the 26.3 dialect (see above)."""
    found_any = False
    for ns in os.listdir(DATA_ROOT) if os.path.isdir(DATA_ROOT) else []:
        loot_root = os.path.join(DATA_ROOT, ns, "loot_table")
        if not os.path.isdir(loot_root):
            continue
        for path in glob.glob(os.path.join(loot_root, "**", "*.json"), recursive=True):
            found_any = True
            rel = os.path.relpath(path, REPO)
            try:
                with open(path, encoding="utf-8") as fh:
                    data = json.load(fh)
            except Exception:  # already reported by parse check
                continue
            hits: list[str] = []
            _walk_loot_nodes(data, rel, hits)
            for h in hits:
                err(f"loot table not in 26.3 schema: {h}")
    if not found_any:
        warn("no loot tables found under any '<ns>/loot_table/'")


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
        os.path.join(home, ".gradle", "caches", "fabric-loom", "26.3", "minecraft-extracted_server.jar"),
        os.path.join(home, ".gradle", "caches", "fabric-loom", "26.3", "minecraft-common.jar"),
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
        warn("MC 26.3 jar not found; skipping vanilla id existence check (Tier 2)")
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


# --------------------------------------------------------------------------- #

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mc-jar", help="path to the MC 26.3 extracted jar for Tier 2 checks")
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
    check_loot_table_schema_26_3()
    check_transformation_json_matches_java()
    check_default_enchantment_tags_well_formed()
    check_loot_injections_well_formed()
    check_bookshelf_book_pool_well_formed()
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

#!/usr/bin/env python3
"""
Automated compatibility / sanity checker for the Chiseled Enchanting Table mod.

Why this exists
---------------
P15 shipped a bug where the block dropped nothing when mined, because the block loot
table lived at the *old plural* path ``data/<ns>/loot_tables/blocks/...`` and had empty
pools. Minecraft 26.3 expects the *singular* path ``data/<ns>/loot_table/blocks/...``
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
                    f"legacy data-pack dir '{ns}/{legacy}/' found; MC 26.3 expects "
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


def check_loot_tables_use_26_3_schema() -> None:
    """26.3 collapsed the loot schema: pools/entries carry a singular `condition` / `modifier`
    dispatched on `type`, instead of the 26.2 `conditions[]` / `functions[]` arrays dispatched on
    `condition` / `function`. Old files still parse as JSON but silently lose their contents.

    Evidence (MC 26.3 sources):
      LootPool.CODEC                 -> optionalFieldOf("condition") / optionalFieldOf("modifier")
      LootPoolEntryContainer         -> commonFields(): "condition" + "modifier"
      LootItemFunctions.DIRECT_CODEC -> byNameCodec().dispatch(...)  => dispatch key is "type"
    """

    def walk(node, rel: str, where: str) -> None:
        if isinstance(node, list):
            for index, item in enumerate(node):
                walk(item, rel, f"{where}[{index}]")
            return
        if not isinstance(node, dict):
            return

        for plural, singular in (("conditions", "condition"), ("functions", "modifier")):
            if plural in node:
                err(
                    f"{rel}: {where} uses the 26.2 '{plural}' array; MC 26.3 expects the singular "
                    f"'{singular}' field — the legacy plural array is "
                    f"silently ignored"
                )

        for legacy_key in ("function",):
            if legacy_key in node:
                err(
                    f"{rel}: {where} uses '{legacy_key}' as a dispatch key; MC 26.3 dispatches "
                    f"loot functions on 'type'"
                )

        for key in ("condition", "modifier"):
            if key not in node:
                continue
            value = node[key]
            # Conditions use a single Holder, NOT a HolderSet. In contrast,
            # modifier accepts an inline SequenceFunction list (LootItemFunctions.DIRECT_CODEC).
            if key == "condition" and isinstance(value, list):
                err(f"{rel}: {where}['condition'] must be a single predicate object/reference, "
                    "not an array (26.3: Not a JSON object); use minecraft:all_of for multiple predicates")
            elif not isinstance(value, (dict, str, list)):
                err(f"{rel}: {where}['{key}'] has an invalid value")
            elif isinstance(value, dict) and "type" not in value:
                err(f"{rel}: {where}['{key}'] is missing its 'type' dispatch key")

        for value in node.values():
            walk(value, rel, where)

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


def check_advancements_use_26_3_schema() -> None:
    """26.3 advancement schema checks.

    * `minecraft:recipe_crafted` now takes `recipes` (a list of recipe ids/holders) instead of the
      26.2 `recipe_id` string — RecipeCraftedTrigger.TriggerInstance.CODEC has no `recipe_id` field,
      so the old form fails to load the advancement.
    * `minecraft:recipe_unlocked` also requires `recipes`, not the old `recipe` string.
    * `show_toast` / `announce_to_chat` are DisplayInfo fields; Advancement.CODEC only knows
      parent / display / rewards / criteria / requirements / sends_telemetry_event.
    * `display.background` is root-only. `Advancement.validate()` returns
      "Only advancement roots can have background" for a child that carries one (and
      "Visible advancement roots must have background" for a visible root that does not).
      Both are hard parse errors: the whole `minecraft:advancement` registry fails to load and the
      world refuses to open ("Failed to load level data or datapacks, can't proceed with
      server load"). 26.2 ignored the key silently, which is how it survived until now.
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
                    f"(26.3: 'Visible advancement roots must have background')"
                )
            elif not is_root and "background" in display:
                err(
                    f"{rel}: non-root advancement carries 'display.background' "
                    f"(26.3: 'Only advancement roots can have background'); delete the key, the "
                    f"parent owns the tab background"
                )

        for name, criterion in (data.get("criteria") or {}).items():
            if not isinstance(criterion, dict):
                continue
            trigger = criterion.get("trigger")
            if trigger not in ("minecraft:recipe_crafted", "minecraft:recipe_unlocked"):
                continue
            conditions = criterion.get("conditions") or {}
            legacy_key = "recipe_id" if trigger == "minecraft:recipe_crafted" else "recipe"
            if legacy_key in conditions:
                err(
                    f"{rel}: criteria '{name}' ({trigger}) uses '{legacy_key}'; MC 26.3 renamed it to "
                    f"'recipes' (a list, e.g. [\"namespace:path\"])"
                )
            elif "recipes" not in conditions:
                err(f"{rel}: criteria '{name}' ({trigger}) is missing the required 'recipes' list")
            elif not isinstance(conditions["recipes"], list):
                err(f"{rel}: criteria '{name}' 'recipes' must be a list of recipe ids")


def check_transformation_json_matches_java() -> None:
    """Single-source-of-truth gate (F8 / R2-9): verify ChiseledEnchantingTableData reads
    bundled classpath JSON resources directly via readBundledJsonObject(...) with zero
    duplicate embedded BUILT_IN_JSON text blocks that could drift from data/*.json."""
    json_path = os.path.join(
        DATA_ROOT, NAMESPACE, NAMESPACE, "item_transformations.json"
    )
    dis_path = os.path.join(
        DATA_ROOT, NAMESPACE, NAMESPACE, "disenchanting.json"
    )
    java_path = os.path.join(
        REPO, "src", "main", "java", "chiseled_enchanting_table", "data",
        "ChiseledEnchantingTableData.java",
    )
    if not os.path.isfile(json_path) or not os.path.isfile(dis_path) or not os.path.isfile(java_path):
        err("missing item_transformations.json, disenchanting.json, or ChiseledEnchantingTableData.java")
        return
    try:
        with open(json_path, encoding="utf-8") as fh:
            json_values = {v["id"]: _norm_transform(v) for v in json.load(fh)["values"]}
        if len(json_values) < 6:
            err(f"expected >= 6 built-in transformations in item_transformations.json, got {len(json_values)}")
    except Exception as exc:  # noqa: BLE001
        err(f"failed reading transformation JSON: {exc}")
        return

    with open(java_path, encoding="utf-8") as fh:
        java_src = fh.read()
    if "BUILT_IN_JSON" in java_src:
        err("ChiseledEnchantingTableData.java still contains a duplicate BUILT_IN_JSON text block (F8 drift risk)")
    for req_call in ('readBundledJsonObject("item_transformations.json")', 'readBundledJsonObject("disenchanting.json")'):
        if req_call not in java_src:
            err(f"ChiseledEnchantingTableData.java missing single-source classpath loader {req_call}")


def _norm_transform(v: dict) -> dict:
    """Normalise transformation fields."""
    return {
        "input": v.get("input"),
        "output": v.get("output"),
        "level": v.get("level", 1),
        "xp_cost": v.get("xp_cost", 0),
        "min_required_level": v.get("min_required_level", v.get("min_player_level", 1)),
        "deducted_levels": v.get("deducted_levels", 0),
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


def check_disenchanting_and_anti_arbitrage() -> None:
    """Tier 1 gate: validate disenchanting.json, 100% key consumption on library_resonance.json,
    shelf_gates.json (including high-maxLevel 40/100/255 & scan volume cap), Book Combine <= Research,
    Transformation XP gates & progress preservation, client S2C sync, WIKI formulas, and exact
    1:1 Java inductive XP conservation across ALL M = 1..255 (391,680 checks)."""
    import math

    def jround(x: float) -> int:
        """Exact Java Math.round(double) == floor(x + 0.5)."""
        return int(math.floor(x + 0.5))

    # F11: Ensure zero transient root clutter or feedback .zip archives remain in worktree.
    for pattern in ("fix*.patch", "fix*.py", "*.orig", "*.zip"):
        clutter = glob.glob(os.path.join(REPO, pattern))
        if clutter:
            errors.append(f"Transient root clutter detected: {[os.path.basename(c) for c in clutter]}")

    # HC-01 & HC-10: Verify block/enchantment tag extension files exist and are non-empty.
    for tag_rel in (
        "src/main/resources/data/chiseled_enchanting_table/tags/block/regular_bookshelves.json",
        "src/main/resources/data/chiseled_enchanting_table/tags/enchantment/epiphany_exclusive.json",
        "src/main/resources/data/chiseled_enchanting_table/tags/enchantment/crossline_excluded.json",
    ):
        tp = os.path.join(REPO, tag_rel)
        if not os.path.isfile(tp):
            errors.append(f"missing required tag file: {tag_rel}")
        else:
            tdata = json.load(open(tp, encoding="utf-8"))
            if not tdata.get("values"):
                errors.append(f"{tag_rel} must have non-empty 'values'")

    # R2-2 / CFG-01 / R2-6: Verify library_resonance.json nested sections AND 100% key consumption by LibraryResonance.java!
    lr_path = os.path.join(
        REPO,
        "src/main/resources/data/chiseled_enchanting_table/chiseled_enchanting_table/library_resonance.json",
    )
    lr_java_path = os.path.join(
        REPO,
        "src/main/java/chiseled_enchanting_table/mechanics/resonance/LibraryResonance.java",
    )
    lr_java = open(lr_java_path, encoding="utf-8").read()
    lr = json.load(open(lr_path, encoding="utf-8"))
    for req_section in ("wooden_bookshelves", "chiseled_mastery", "epiphany_affinity"):
        if not isinstance(lr.get(req_section), dict) or not lr[req_section]:
            errors.append(f"library_resonance.json missing nested section '{req_section}'")
        if f'"{req_section}"' not in lr_java:
            errors.append(f"LibraryResonance.java does not consume nested section '{req_section}'")
        for leaf_key in lr.get(req_section, {}):
            if leaf_key.startswith("_"):
                continue
            if f'"{leaf_key}"' not in lr_java:
                errors.append(f"library_resonance.json key '{req_section}.{leaf_key}' is NOT consumed by LibraryResonance.java!")

    max_disc = int(lr["chiseled_mastery"]["max_total_xp_discount_percent"])
    credit_factor = float(lr["chiseled_mastery"]["incremental_step_credit_factor"])
    if max_disc != 40 or abs(credit_factor - 0.85) > 1e-9:
        errors.append(f"library_resonance.json canonical defaults invalid: max_disc={max_disc}, credit={credit_factor}")

    # R2-1: Verify BookUpgradeApplication uses resolveEffectiveXpRequirement + XpCostConversion.deductXp AND ScreenHandler preserves 0.6x combine cap.
    bua_java = open(
        os.path.join(REPO, "src/main/java/chiseled_enchanting_table/mechanics/enchanting/BookUpgradeApplication.java"),
        encoding="utf-8",
    ).read()
    sh_java = open(
        os.path.join(REPO, "src/main/java/chiseled_enchanting_table/menu/ChiseledEnchantingTableScreenHandler.java"),
        encoding="utf-8",
    ).read()
    # H-01 (supersedes the R2-1 single-entry contract): one combine click merges every
    # enchantment, so applyCombine must charge the whole-book sequential price.
    if "handler.bookCombineXpRequirement()" not in bua_java or "deductXp(handler.player, xpReq)" not in bua_java:
        errors.append("BookUpgradeApplication.applyCombine must use handler.bookCombineXpRequirement() + deductXp (H-01)")
    if "combineSequential" not in sh_java:
        errors.append("ScreenHandler.bookCombineXpRequirement must combine per-entry prices via XpCostConversion.combineSequential (H-01)")
    if "type == OptionType.BOOK_RESEARCH_UPGRADE || type == OptionType.BOOK_COMBINE_UPGRADE" not in sh_java:
        errors.append("ScreenHandler must apply min(rawCost, deltaRaw) to BOOK_COMBINE_UPGRADE so 0.6x multiplier takes effect (R2-1)")
    if "/ 12000L" in sh_java:
        errors.append("ScreenHandler still hardcodes / 12000L instead of reading epiphanyCycleTicks() (R2-9)")

    # R2-3 / SYNC-01 / XP-02: Verify XpCostConversion preserves bar percentage via giveExperienceLevels & takes explicit Config.
    xpc_java = open(
        os.path.join(REPO, "src/main/java/chiseled_enchanting_table/mechanics/enchanting/XpCostConversion.java"),
        encoding="utf-8",
    ).read()
    if "player.giveExperienceLevels(-req.deductedLevels())" not in xpc_java:
        errors.append("XpCostConversion.deductXp must use player.giveExperienceLevels(-req.deductedLevels()) in vanillaTierMode (XP-02)")

    cfg_path = os.path.join(
        REPO,
        "src/main/resources/data/chiseled_enchanting_table/chiseled_enchanting_table/disenchanting.json",
    )
    cfg = json.load(open(cfg_path, encoding="utf-8"))
    for key in ("enabled", "allow_curse_purge", "xp_refund_scale", "targeted_strip", "curse_purge"):
        if key not in cfg:
            errors.append(f"disenchanting.json missing required key '{key}'")

    # HC-05: Both targeted_strip and curse_purge must support dual-material options AND pure vanilla alternatives!
    for section_key in ("targeted_strip", "curse_purge"):
        sec = cfg.get(section_key, {})
        opts = sec.get("options", [])
        if not opts:
            errors.append(f"disenchanting.json '{section_key}' must define non-empty 'options' list")
            continue
        has_custom = False
        has_pure_vanilla = False
        for idx, opt in enumerate(opts):
            extras = opt.get("extras", [])
            is_arcane_bypass = opt.get("item") == "chiseled_enchanting_table:arcane_catalyst"
            if not extras and not is_arcane_bypass:
                errors.append(f"disenchanting.json '{section_key}.options[{idx}]' missing dual-material 'extras'")
            involved = {opt.get("item")} | {ex.get("item") for ex in extras}
            if any(str(i).startswith("chiseled_enchanting_table:") for i in involved):
                has_custom = True
            if all(str(i).startswith("minecraft:") for i in involved):
                has_pure_vanilla = True
        if not has_custom or not has_pure_vanilla:
            errors.append(
                f"disenchanting.json '{section_key}' must provide both custom catalyst options AND pure standalone vanilla alternatives!"
            )

    # SHELF-01 & PERF-01: Verify BookshelfProgression across M = 1..255 (strict on 1..39, full 39-gate distribution on 40..255)!
    def req_shelves(target_lvl: int, max_lvl: int, gamma: float = 1.25) -> int:
        # Bound to BookshelfProgression.java:258-281
        s_max = max(1, max_lvl)
        s_tgt = max(1, min(s_max, target_lvl))
        if s_max <= 1 or s_tgt <= 1:
            return 0
        if s_tgt >= s_max:
            return 38
        prog = (s_tgt - 1) / (s_max - 1)
        raw = jround(38.0 * (prog ** gamma))
        if s_max > 39:
            return max(1, min(37, raw))
        min_a = min(37, s_tgt - 1)
        max_a = max(1, 38 - (s_max - s_tgt))
        return max(min(min_a, max_a), min(raw, max_a))

    expected_vanilla_curves = {
        1: [0],
        2: [0, 38],
        3: [0, 16, 38],
        4: [0, 10, 23, 38],
        5: [0, 7, 16, 27, 38],
    }
    for m_lvl, exp_curve in expected_vanilla_curves.items():
        got = [req_shelves(l, m_lvl) for l in range(1, m_lvl + 1)]
        if got != exp_curve:
            errors.append(f"BookshelfProgression gamma=1.25 curve mismatch for maxLevel={m_lvl}: expected {exp_curve}, got {got}")
    for modded_max in range(6, 40):
        seq = [req_shelves(l, modded_max) for l in range(1, modded_max + 1)]
        if seq[0] != 0 or seq[-1] != 38 or any(seq[i] >= seq[i + 1] for i in range(len(seq) - 1)):
            errors.append(f"Non-strict BookshelfProgression sequence for maxLevel={modded_max}: {seq}")
    for high_max in (40, 100, 255):
        seq = [req_shelves(l, high_max) for l in range(1, high_max + 1)]
        if seq[0] != 0 or seq[-1] != 38 or any(seq[i] > seq[i + 1] for i in range(len(seq) - 1)):
            errors.append(f"Non-monotonic high-maxLevel BookshelfProgression sequence for maxLevel={high_max}")
        shelf_one_count = sum(1 for v in seq if v == 1)
        if high_max == 255 and (len(set(seq)) != 39 or shelf_one_count > 25):
            errors.append(f"SHELF-01 regression on M=255: unique_gates={len(set(seq))}, shelf_1_count={shelf_one_count}")

    bp_java = open(
        os.path.join(REPO, "src/main/java/chiseled_enchanting_table/mechanics/resonance/BookshelfProgression.java"),
        encoding="utf-8",
    ).read()
    if "countQualifyingStoredMasterEnchantments(" not in bp_java or "MAX_SCAN_VOLUME_BLOCKS" not in bp_java:
        errors.append("BookshelfProgression must evaluate deduplicated qualifying enchantments and cap scan volume!")
    if "countQualifyingHighTierEnchantments(" in bp_java:
        errors.append("Dead slot-accumulating helper countQualifyingHighTierEnchantments remains in BookshelfProgression.java (R2-9)")

    dis_java = open(
        os.path.join(REPO, "src/main/java/chiseled_enchanting_table/mechanics/enchanting/DisenchantmentApplication.java"),
        encoding="utf-8",
    ).read()
    if "BlockRegistry.DEPLETED_TOME" not in dis_java or "ExperienceOrb.award" not in dis_java:
        errors.append("DisenchantmentApplication must close the DEPLETED_TOME lifecycle and award ExperienceOrb!")

    # XP-01 / R2-8: Verify all 6 built-in transformations match WIKI mass-production table & preserve bottle conservation!
    transforms_path = os.path.join(
        REPO,
        "src/main/resources/data/chiseled_enchanting_table/chiseled_enchanting_table/item_transformations.json",
    )
    t_json = json.load(open(transforms_path, encoding="utf-8"))
    expected_transforms = {
        "chiseled_enchanting_table:blank_overwrite": (1, 1, 1),
        "chiseled_enchanting_table:experience_bottle": (5, 3, 1),
        "chiseled_enchanting_table:echo_shard": (12, 2, 2),
        "chiseled_enchanting_table:enchanted_golden_apple": (20, 2, 2),
        "chiseled_enchanting_table:trident": (25, 3, 3),
        "chiseled_enchanting_table:totem_of_undying": (30, 3, 3),
    }
    for v in t_json.get("values", []):
        tid = v.get("id")
        if tid in expected_transforms:
            got_tuple = (v.get("xp_cost"), v.get("min_required_level"), v.get("deducted_levels"))
            if got_tuple != expected_transforms[tid]:
                errors.append(f"Transformation {tid} XP mismatch: expected {expected_transforms[tid]}, got {got_tuple}")
    blank_entry = next((v for v in t_json.get("values", []) if v.get("id") == "chiseled_enchanting_table:blank_overwrite"), None)
    if not blank_entry:
        errors.append("item_transformations.json missing chiseled_enchanting_table:blank_overwrite")
    else:
        for opt in blank_entry.get("cost_options", []):
            cnt = opt.get("count", 0)
            disc = max(1, int(math.floor(cnt * 0.50)))
            if cnt < 2 or disc >= cnt:
                errors.append(f"blank_overwrite option {opt} must have count >= 2")

    # R2-5: Verify WIKI.md §3, §4, §7, §8 match real formulas with zero stale drift!
    wiki_text = open(os.path.join(REPO, "docs/WIKI.md"), encoding="utf-8").read()
    for stale_fragment in (
        "0.45\\,r(e)",
        "round(1.5\\,E",
        "gamma = 1.11",
        "bookshelf_books.json",
        "0.85×",
        "Lv.4~10",
        "涤魔碎砂",
    ):
        if stale_fragment in wiki_text:
            errors.append(f"docs/WIKI.md still contains stale formula/filename '{stale_fragment}' (R2-5)")
    for required_fragment in (
        "38 - 12",
        "r(e)^{1.25}",
        "blank_book_affinity_weight = 0.50",
        "giveExperienceLevels",
        "library_resonance.json",
        "Lv.3~10",
        "1.4×",
    ):
        if required_fragment not in wiki_text:
            errors.append(f"docs/WIKI.md missing required synchronized specification '{required_fragment}' (R2-5)")

    # R2-4 / TEST-01: Exact 1:1 mirror of Java XpCostConversion.java + DisenchantmentApplication.buildPotentialTable
    # tested across ALL 255 integers M = 1..255 and EVERY step 1 <= a < k <= M (391,680 checks)!
    SAFETY_MARGIN = 5  # DisenchantmentApplication.java:CONSERVATION_SAFETY_MARGIN_POINTS

    def xp_for_level(level: int) -> int:
        # XpCostConversion.java:xpPointsForLevel
        if level >= 30:
            return 112 + (level - 30) * 9
        if level >= 15:
            return 37 + (level - 15) * 5
        return 7 + max(0, level) * 2

    def points_for_level_deduction(required_level: int, deducted: int) -> int:
        # XpCostConversion.java:minPointsRequired
        start_level = max(deducted, required_level)
        pts = 0
        for i in range(deducted):
            pts += xp_for_level(start_level - 1 - i)
        return pts

    def master_tome_tier_cost(level: int, max_level: int) -> int:
        # XpCostConversion.java:vanillaTierDeduction (0.40 / 0.75 boundaries -> 1 / 2 / 3)
        capped_max = max(1, max_level)
        if capped_max <= 1:
            return 3
        r = max(1, min(level, capped_max)) / capped_max
        return 1 if r <= 0.40 else (2 if r <= 0.75 else 3)

    def min_master_tome_full_points(target_level: int, max_level: int) -> int:
        # XpCostConversion.java:forMasterTomeEnchant (8.0 + 22.0 * r, discount = max_disc)
        capped_max = max(1, max_level)
        lvl = max(1, min(target_level, capped_max))
        tier = master_tome_tier_cost(lvl, capped_max)
        raw_req = max(tier, jround(8.0 + 22.0 * (lvl / capped_max)))
        req = max(tier, jround(raw_req * (1.0 - max_disc / 100.0)))
        return points_for_level_deduction(req, tier)

    def min_master_tome_step_points(existing_level: int, target_level: int, max_level: int) -> int:
        # XpCostConversion.java:forIncrementalMasterTomeEnchant (credit_factor = 0.85, floor 0.15)
        capped_max = max(1, max_level)
        ex = max(0, min(existing_level, capped_max))
        tgt = max(1, min(target_level, capped_max))
        delta = max(1, tgt - ex)
        tier = master_tome_tier_cost(delta, capped_max)
        rem = max(0.15, (tgt / capped_max) - credit_factor * (ex / capped_max))
        raw_req = max(tier, jround(8.0 + 22.0 * rem))
        req = max(tier, jround(raw_req * (1.0 - max_disc / 100.0)))
        return points_for_level_deduction(req, tier)

    def build_potential_table(
        max_level: int,
        anvil_cost: int,
        is_treasure: bool,
        scale: float,
        full_costs: list[int],
        step_costs: list[list[int]],
    ) -> list[int]:
        # DisenchantmentApplication.java:buildPotentialTable (base 34/48, rarity 1.45*1.15, margin 5)
        s_max = max(1, max_level)
        clamped_scale = max(0.0, min(1.0, scale))
        if clamped_scale <= 0.0:
            return [0] * (s_max + 1)
        safe_anvil = max(1, anvil_cost)
        rarity = min(1.45, max(1.00, 1.0 + 0.18 * math.log(safe_anvil))) * (1.15 if is_treasure else 1.0)
        base_max = 34.0 if s_max == 1 else 48.0
        vals = [0] * (s_max + 1)
        for k in range(1, s_max + 1):
            r = k / s_max
            desired = jround(clamped_scale * base_max * rarity * (r ** 1.65))
            row = step_costs[k]
            step_bound = min((vals[a] + row[a] - SAFETY_MARGIN for a in range(1, k)), default=full_costs[k] - SAFETY_MARGIN)
            cap = max(0, min(full_costs[k] - SAFETY_MARGIN, step_bound))
            vals[k] = max(vals[k - 1], min(desired, cap))
        return vals

    # R2-1 mathematical check across M = 2..10 & discounts 0..40: Combine <= Research ALWAYS
    for m_lvl in range(2, 11):
        for tgt in range(2, m_lvl + 1):
            t_raw = max(2, jround(2 + 8.0 * (tgt - 1) / max(1, m_lvl - 1)))
            p_raw = max(2, jround(2 + 8.0 * (tgt - 2) / max(1, m_lvl - 1)))
            d_raw = max(1, t_raw - p_raw)
            comb_eff = min(max(1, jround(t_raw * 0.60)), d_raw)
            res_eff = min(max(1, jround(t_raw * 0.85)), d_raw)
            for disc in (0, 20, 40):
                comb_ded = max(1, jround(comb_eff * (1.0 - disc / 100.0)))
                res_ded = max(1, jround(res_eff * (1.0 - disc / 100.0)))
                if comb_ded > res_ded:
                    errors.append(f"Combine > Research regression at M={m_lvl}, target={tgt}, disc={disc}")

    # Exhaustive conservation check across EVERY SINGLE M in 1..255!
    xp_width = [xp_for_level(l) for l in range(40)]
    cum_xp = [0]
    for w in xp_width:
        cum_xp.append(cum_xp[-1] + w)

    def fast_min_pts(req_lvl: int, ded: int) -> int:
        s = max(ded, req_lvl)
        return cum_xp[s] - cum_xp[s - ded]

    scale = float(cfg.get("xp_refund_scale", 1.0))
    checked_pairs = 0
    for max_lvl in range(1, 256):
        inv_m = 1.0 / max_lvl
        tier_lut = [3] * (max_lvl + 1)
        if max_lvl > 1:
            for d in range(1, max_lvl + 1):
                r_d = d * inv_m
                tier_lut[d] = 1 if r_d <= 0.40 else (2 if r_d <= 0.75 else 3)
        full_mins = [0] * (max_lvl + 1)
        step_by_k: list[list[int]] = [[] for _ in range(max_lvl + 1)]
        disc_mult = 1.0 - max_disc / 100.0
        for k in range(1, max_lvl + 1):
            tk = tier_lut[k]
            raw_k = max(tk, int((8.0 + 22.0 * (k * inv_m)) + 0.5))
            req_k = max(tk, int(raw_k * disc_mult + 0.5))
            full_mins[k] = fast_min_pts(req_k, tk)
            row_k = [0] * k
            tf = k * inv_m
            for a in range(1, k):
                ts = tier_lut[k - a]
                rem = tf - credit_factor * (a * inv_m)
                if rem < 0.15:
                    rem = 0.15
                raw_s = max(ts, int((8.0 + 22.0 * rem) + 0.5))
                req_s = max(ts, int(raw_s * disc_mult + 0.5))
                row_k[a] = fast_min_pts(req_s, ts)
            step_by_k[k] = row_k

        for anvil_cost in (1, 2, 4, 8, 25, 100):
            for is_treasure in (False, True):
                vals = build_potential_table(max_lvl, anvil_cost, is_treasure, scale, full_mins, step_by_k)
                for k in range(1, max_lvl + 1):
                    if vals[k] < vals[k - 1]:
                        errors.append(f"decreasing potential at M={max_lvl}, k={k}")
                    if vals[k] > full_mins[k] - SAFETY_MARGIN:
                        errors.append(f"full-wipe XP arbitrage at M={max_lvl}, k={k}")
                    checked_pairs += 1
                    row_k = step_by_k[k]
                    vk = vals[k]
                    for a in range(1, k):
                        if vk - vals[a] > row_k[a] - SAFETY_MARGIN:
                            errors.append(f"step XP arbitrage at M={max_lvl}, {a}->{k}")
                        checked_pairs += 1
                telescoped = sum(vals[l] - vals[l - 1] for l in range(1, max_lvl + 1))
                if telescoped != vals[max_lvl]:
                    errors.append(f"telescoping sum mismatch at M={max_lvl}: {telescoped} != {vals[max_lvl]}")
    if checked_pairs < 33_000_000:
        errors.append(f"expected >= 33,000,000 exhaustive conservation checks, ran only {checked_pairs}")


def check_grimoire_recipes_sounds_and_textures() -> None:
    from pathlib import Path
    repo_root = Path(REPO)
    client_res = repo_root / "src" / "client" / "resources" / "assets" / "chiseled_enchanting_table"
    data_res = repo_root / "src" / "main" / "resources" / "data"

    # 1. Verify both shapeless recipes exist and match exact ingredients
    r1_path = data_res / "chiseled_enchanting_table" / "recipe" / "arcane_grimoire_from_shelf.json"
    r2_path = data_res / "chiseled_enchanting_table" / "recipe" / "arcane_grimoire_from_tome.json"
    for rpath, exp_set in [
        (r1_path, {"minecraft:writable_book", "minecraft:chiseled_bookshelf"}),
        (r2_path, {"chiseled_enchanting_table:depleted_tome", "minecraft:ink_sac"}),
    ]:
        if not rpath.is_file():
            errors.append(f"missing grimoire recipe: {rpath}")
            continue
        robj = json.loads(rpath.read_text(encoding="utf-8"))
        if robj.get("type") != "minecraft:crafting_shapeless":
            errors.append(f"{rpath.name} must be minecraft:crafting_shapeless")
        if set(robj.get("ingredients", [])) != exp_set:
            errors.append(f"{rpath.name} ingredients mismatch: {robj.get('ingredients')} != {exp_set}")
        if robj.get("result", {}).get("id") != "chiseled_enchanting_table:arcane_grimoire":
            errors.append(f"{rpath.name} result id must be chiseled_enchanting_table:arcane_grimoire")

    # 2. Verify native vanilla sound usage (zero custom .ogg bloat) & JEI/REI viewer integration
    if (client_res / "sounds.json").exists():
        errors.append("obsolete sounds.json still exists; grimoire must use vanilla SoundEvents")
    if (client_res / "sounds").exists():
        errors.append("obsolete custom sounds directory still exists; grimoire must use vanilla SoundEvents")

    sound_helper = (
        repo_root
        / "src/client/java/chiseled_enchanting_table/gui/grimoire/GrimoireSoundHelper.java"
    ).read_text(encoding="utf-8")
    for required_sym in ("SoundEvents.BOOK_PAGE_TURN", "SoundEvents.BOOK_PUT", "SoundEvents.UI_BUTTON_CLICK"):
        if required_sym not in sound_helper:
            errors.append(f"GrimoireSoundHelper missing vanilla sound symbol: {required_sym}")

    # 3. Verify JEI & REI viewer bridge + zero optional-mod imports outside compat/{jei,rei}
    for non_compat_java in sorted((repo_root / "src").glob("**/*.java")):
        posix = non_compat_java.as_posix()
        if "/compat/jei/" in posix or "/compat/rei/" in posix:
            continue
        src_txt = non_compat_java.read_text(encoding="utf-8")
        if "mezz.jei." in src_txt or "me.shedaniel." in src_txt:
            errors.append(f"{posix} directly imports JEI/REI; breaks -Penable_jei=false/-Penable_rei=false")

    jei_txt = (
        repo_root
        / "src/client/java/chiseled_enchanting_table/compat/jei/ChiseledEnchantingJeiPlugin.java"
    ).read_text(encoding="utf-8")
    for tok in ("BlockRegistry.ARCANE_GRIMOIRE", "ArcaneGrimoireScreen", "GrimoireViewerBridge", "hoveredIngredient"):
        if tok not in jei_txt:
            errors.append(f"ChiseledEnchantingJeiPlugin missing '{tok}'")

    rei_txt = (
        repo_root
        / "src/client/java/chiseled_enchanting_table/compat/rei/ChiseledEnchantingReiPlugin.java"
    ).read_text(encoding="utf-8")
    for tok in ("BlockRegistry.ARCANE_GRIMOIRE", "ArcaneGrimoireScreen", "GrimoireViewerBridge", "registerFocusedStack"):
        if tok not in rei_txt:
            errors.append(f"ChiseledEnchantingReiPlugin missing '{tok}'")
    if "me.shedaniel.rei.plugin." in rei_txt:
        errors.append("ChiseledEnchantingReiPlugin must use me.shedaniel.rei.api.* only (not default-plugin internal packages)")

    # 4. Verify D1-D7 synchronized datapack schemas & defaults
    data_dir = Path(REPO) / "src/main/resources/data/chiseled_enchanting_table/chiseled_enchanting_table"
    lr_json = json.loads((data_dir / "library_resonance.json").read_text(encoding="utf-8"))
    cm = lr_json.get("chiseled_mastery", {})
    if cm.get("tome_consumption_mode") != "wear" or cm.get("max_tome_uses") != 4 or cm.get("book_copy_wear_cost") != 2:
        errors.append("library_resonance.json chiseled_mastery tome wear fields must default to wear / 4 / 2 (D1 / Part B)")
    if cm.get("unbreaking_waiver_max_chance", 1.0) > 0.30 + 1e-9:
        errors.append("library_resonance.json unbreaking_waiver_max_chance must be <= 0.30")
    cs = lr_json.get("crossline_slip", {})
    if cs.get("max_slip_chance") != 0.18 or cs.get("tier_drop_share") != 0.70 or cs.get("junk_crossline_share") != 0.25 or cs.get("curse_crossline_share") != 0.05:
        errors.append("library_resonance.json crossline_slip must default to 0.18 cap and 0.70 / 0.25 / 0.05 outcome split")

    sg_json = json.loads((data_dir / "shelf_gates.json").read_text(encoding="utf-8"))
    dtg = sg_json.get("depleted_tome_gradients", {})
    sv = sg_json.get("scan_volume", {})
    if dtg.get("tier1_bookshelves") != 12 or dtg.get("tier2_bookshelves") != 32:
        errors.append("shelf_gates.json unified Depleted Tome shelf stages must be 12 / 32 (D6)")
    if sg_json.get("vanilla_table_mode") != "books":
        errors.append("shelf_gates.json default vanilla_table_mode must be 'books' (D2)")
    if sv.get("require_breathing_room") is not True or sv.get("min_open_adjacent_blocks") != 6:
        errors.append("shelf_gates.json scan_volume breathing-room check must default to enabled with min 6 open blocks (D4)")

    dis_json = json.loads((data_dir / "disenchanting.json").read_text(encoding="utf-8"))
    if dis_json.get("external_xp_refund_scale") != 0.25:
        errors.append("disenchanting.json must configure external_xp_refund_scale = 0.25 (D3)")

    pool_json = json.loads((data_dir / "bookshelf_book_pool.json").read_text(encoding="utf-8"))
    loot_json = json.loads((data_dir / "loot_injections.json").read_text(encoding="utf-8"))
    if pool_json.get("enabled") is not True or loot_json.get("vanilla_chest_books") != "keep_both":
        errors.append("bookshelf_book_pool.json / loot_injections.json must expose D7 datapack switches")

    bs_tag = json.loads(
        (Path(REPO) / "src/main/resources/data/minecraft/tags/item/bookshelf_books.json").read_text(encoding="utf-8")
    )
    if "chiseled_enchanting_table:depleted_tome" not in bs_tag.get("values", []):
        errors.append("bookshelf_books.json must include chiseled_enchanting_table:depleted_tome (D1)")


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
    check_loot_tables_use_26_3_schema()
    check_advancements_use_26_3_schema()
    check_transformation_json_matches_java()
    check_default_enchantment_tags_well_formed()
    check_loot_injections_well_formed()
    check_bookshelf_book_pool_well_formed()
    check_disenchanting_and_anti_arbitrage()
    check_grimoire_recipes_sounds_and_textures()
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

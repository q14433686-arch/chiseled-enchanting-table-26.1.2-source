#!/usr/bin/env python3
"""Datapack & JSON-Java schema sanity check for Enchantment Fusion (MC 26.1.2)."""
from __future__ import annotations

import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]

VALID_TRIGGERS = {
    "ON_MELEE_STRIKE",
    "ON_SWEEP_OR_CLEAVE",
    "ON_PROJECTILE_IMPACT",
    "ON_TRIDENT_CHANNEL",
    "ON_BLOCK_HARVEST",
    "ON_DAMAGE_TAKEN",
    "ON_MOVEMENT_OR_LAND",
    "ON_UTILITY_CYCLE",
}

VALID_PAYLOADS = {
    "PYRO",
    "CRYO",
    "VOLT",
    "GALE",
    "TIDE",
    "RETURN",
    "PIERCE",
    "SCATTER",
    "GRAVITY",
    "EDGE",
    "BANE",
    "FORTUNE",
    "AEGIS",
    "MEND",
}


def check_matrix_json() -> None:
    mp = ROOT / "src/main/resources/data/enchantment_fusion/enchantment_fusion/matrix_profiles.json"
    rp = ROOT / "src/main/resources/data/enchantment_fusion/enchantment_fusion/repulsive_pairs.json"
    matrix = json.loads(mp.read_text(encoding="utf-8"))
    repulsive = json.loads(rp.read_text(encoding="utf-8"))

    profiles = matrix.get("profiles", {})
    if len(profiles) < 38:
        raise AssertionError(f"Expected >= 38 enchantment profiles, found {len(profiles)}")

    seen_t, seen_p = set(), set()
    for ench_id, entry in profiles.items():
        if ":" not in ench_id:
            raise AssertionError(f"Invalid namespaced enchantment ID: {ench_id}")
        trig = entry.get("trigger")
        pay = entry.get("payload")
        if trig not in VALID_TRIGGERS:
            raise AssertionError(f"{ench_id} has unknown TriggerType {trig}")
        if pay not in VALID_PAYLOADS:
            raise AssertionError(f"{ench_id} has unknown PayloadType {pay}")
        seen_t.add(trig)
        seen_p.add(pay)

    if seen_t != VALID_TRIGGERS:
        raise AssertionError(f"Unused TriggerTypes: {VALID_TRIGGERS - seen_t}")
    if seen_p != VALID_PAYLOADS:
        raise AssertionError(f"Unused PayloadTypes: {VALID_PAYLOADS - seen_p}")

    groups = repulsive.get("exclusive_groups", [])
    if len(groups) < 6:
        raise AssertionError("Expected >= 6 vanilla exclusive groups in repulsive_pairs.json")
    chance = repulsive.get("base_instability_chance", -1)
    if not (0.0 <= chance <= 1.0):
        raise AssertionError(f"Invalid base_instability_chance: {chance}")


def check_recipes_and_loot() -> None:
    for rname in ("enchantment_fusion_table", "resonance_stabilizer"):
        rp = ROOT / f"src/main/resources/data/enchantment_fusion/recipe/{rname}.json"
        recipe = json.loads(rp.read_text(encoding="utf-8"))
        rtype = recipe["type"]
        assert rtype in ("minecraft:crafting_shaped", "minecraft:crafting_shapeless"), f"Unexpected recipe type in {rp}"
        if rtype == "minecraft:crafting_shaped":
            for k, v in recipe["key"].items():
                assert isinstance(v, str), f"26.1.2 recipe key values must be plain item/#tag strings in {rp}:{k}"
        else:
            for item in recipe["ingredients"]:
                assert isinstance(item, str), f"26.1.2 shapeless ingredients must be plain item/#tag strings in {rp}"
        assert recipe["result"]["id"].startswith("enchantment_fusion:")

    lp = ROOT / "src/main/resources/data/enchantment_fusion/loot_table/blocks/enchantment_fusion_table.json"
    loot = json.loads(lp.read_text(encoding="utf-8"))
    assert loot["type"] == "minecraft:block"
    assert len(loot["pools"]) == 1


def main() -> int:
    check_matrix_json()
    check_recipes_and_loot()
    print("ok: compat_check Tier 1 passed (8x14 matrix schema + 26.1.2 recipes/loot/tags)")
    return 0


if __name__ == "__main__":
    sys.exit(main())

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


def check_economy_json() -> None:
    ep = ROOT / "src/main/resources/data/enchantment_fusion/enchantment_fusion/economy.json"
    econ = json.loads(ep.read_text(encoding="utf-8"))
    if econ.get("schema_version") != 2:
        raise AssertionError(f"Expected economy.json schema_version=2, found {econ.get('schema_version')}")
    ceiling_pairs = (
        ("low_fail_chance", "fail_ceiling_low"),
        ("mid_fail_chance", "fail_ceiling_mid"),
        ("high_fail_chance", "fail_ceiling_high"),
        ("repulsive_fail_chance", "fail_ceiling_repulsive"),
        ("aberrant_socket_fail_chance", "fail_ceiling_socket"),
    )
    for base_k, ceil_k in ceiling_pairs:
        base_v = float(econ[base_k])
        ceil_v = float(econ[ceil_k])
        if not (0.0 <= base_v <= ceil_v <= 1.0):
            raise AssertionError(f"Invalid economy ceiling pair {base_k}={base_v} vs {ceil_k}={ceil_v}")
    if not (0.0 <= float(econ["repulsive_min_floor"]) <= float(econ["repulsive_fail_chance"])):
        raise AssertionError("repulsive_min_floor must be <= repulsive_fail_chance")
    if not (0.0 <= float(econ["fail_ceiling_conversion"]) <= 1.0):
        raise AssertionError("fail_ceiling_conversion out of [0, 1] range")
    cooldowns = econ.get("trigger_cooldowns", {})
    if set(cooldowns.keys()) != VALID_TRIGGERS:
        raise AssertionError(f"trigger_cooldowns keys mismatch: {set(cooldowns.keys())} vs {VALID_TRIGGERS}")
    if int(econ.get("purify_aberrant_stabilizers", 0)) < 1:
        raise AssertionError("purify_aberrant_stabilizers must be >= 1")


def check_signatures_and_transmutations_json() -> None:
    sp = ROOT / "src/main/resources/data/enchantment_fusion/enchantment_fusion/signatures.json"
    tp = ROOT / "src/main/resources/data/enchantment_fusion/enchantment_fusion/transmutations.json"
    sigs = json.loads(sp.read_text(encoding="utf-8"))
    trans = json.loads(tp.read_text(encoding="utf-8"))
    if sigs.get("schema_version") != 1 or len(sigs.get("signatures", {})) < 140:
        raise AssertionError("signatures.json schema_version=1 & >= 140 curated signatures required")
    if trans.get("schema_version") != 1 or len(trans.get("variants", {})) < 110:
        raise AssertionError("transmutations.json schema_version=1 & >= 110 curated variants required")

    for sub in (
        ROOT / "src/main/java/enchantment_fusion/mechanics/signatures",
        ROOT / "src/main/java/enchantment_fusion/mechanics/transmute",
    ):
        for jf in sorted(sub.glob("*.java")):
            n = len(jf.read_text(encoding="utf-8").splitlines())
            if n >= 500:
                raise AssertionError(f"Domain handler exceeds 500-line budget ({n}): {jf}")
    for facade in (
        ROOT / "src/main/java/enchantment_fusion/mechanics/SignaturePairs.java",
        ROOT / "src/main/java/enchantment_fusion/mechanics/CrossDomainTransmutations.java",
    ):
        n = len(facade.read_text(encoding="utf-8").splitlines())
        if n >= 500:
            raise AssertionError(f"Facade exceeds 500-line budget ({n}): {facade}")


def main() -> int:
    check_matrix_json()
    check_economy_json()
    check_signatures_and_transmutations_json()
    check_recipes_and_loot()
    print("ok: compat_check Tier 1 passed (8x14 matrix + economy v2 + curated signatures/transmutations + <500L handlers)")
    return 0


if __name__ == "__main__":
    sys.exit(main())

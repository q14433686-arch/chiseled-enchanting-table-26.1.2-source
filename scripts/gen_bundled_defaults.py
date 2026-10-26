#!/usr/bin/env python3
"""Generate BundledDefaults.java from the bundled datapack JSON files.

Single source of truth (audit P1): the JSON files under
``src/main/resources/data/chiseled_enchanting_table/chiseled_enchanting_table/``
are the only place where bundled balance defaults are typed by hand.
This script renders them as javac compile-time constants in
``src/main/java/chiseled_enchanting_table/BundledDefaults.java``; every
``Config.DEFAULT`` in the mechanics layer reads from that generated class.
``scripts/ci_static_checks.py`` re-renders and byte-compares, so editing a
JSON default without regenerating (or hand-editing the generated file)
fails CI instead of silently forking the two truths.

Usage:
    python3 scripts/gen_bundled_defaults.py           # rewrite the .java file
    python3 scripts/gen_bundled_defaults.py --check   # exit 1 if stale
"""

from __future__ import annotations

import json
import os
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA_DIR = os.path.join(
    REPO, "src", "main", "resources", "data",
    "chiseled_enchanting_table", "chiseled_enchanting_table",
)
OUT_PATH = os.path.join(
    REPO, "src", "main", "java", "chiseled_enchanting_table", "BundledDefaults.java"
)

# (section comment, java name, java type, json file key, json path)
# Types: int | double | boolean | String | intlist
FIELDS: list[tuple[str | None, str, str, str, tuple[str, ...]]] = [
    ("library_resonance.json / wooden_bookshelves", "MIN_DISCOUNT_BOOKSHELVES", "int", "lr", ("wooden_bookshelves", "min_discount_bookshelves")),
    (None, "MAX_DISCOUNT_BOOKSHELVES", "int", "lr", ("wooden_bookshelves", "max_discount_bookshelves")),
    (None, "MAX_WOODEN_DISCOUNT_PERCENT", "int", "lr", ("wooden_bookshelves", "max_wooden_discount_percent")),
    (None, "SHELF_UPGRADE_MATERIAL_MULTIPLIER", "double", "lr", ("wooden_bookshelves", "shelf_upgrade_material_multiplier")),
    ("library_resonance.json / chiseled_mastery", "MASTERY_UNIT_EXPONENT", "double", "lr", ("chiseled_mastery", "mastery_unit_exponent")),
    (None, "GLOBAL_MASTERY_MULTIPLIER", "double", "lr", ("chiseled_mastery", "global_mastery_multiplier")),
    (None, "MAX_GLOBAL_MASTERY_DISCOUNT_PERCENT", "int", "lr", ("chiseled_mastery", "max_global_mastery_discount_percent")),
    (None, "MAX_BASE_XP_DISCOUNT_PERCENT", "int", "lr", ("chiseled_mastery", "max_base_xp_discount_percent")),
    (None, "MAX_SPECIFIC_ENCHANT_BONUS_PERCENT", "int", "lr", ("chiseled_mastery", "max_specific_enchant_bonus_percent")),
    (None, "SPECIFIC_ENCHANT_EXPONENT", "double", "lr", ("chiseled_mastery", "specific_enchant_exponent")),
    (None, "MAX_TOTAL_XP_DISCOUNT_PERCENT", "int", "lr", ("chiseled_mastery", "max_total_xp_discount_percent")),
    (None, "MASTER_TOME_UPGRADE_MATERIAL_MULTIPLIER", "double", "lr", ("chiseled_mastery", "master_tome_upgrade_material_multiplier")),
    (None, "INCREMENTAL_STEP_CREDIT_FACTOR", "double", "lr", ("chiseled_mastery", "incremental_step_credit_factor")),
    (None, "TOME_CONSUMPTION_MODE", "String", "lr", ("chiseled_mastery", "tome_consumption_mode")),
    (None, "MAX_TOME_USES", "int", "lr", ("chiseled_mastery", "max_tome_uses")),
    (None, "BOOK_COPY_WEAR_COST", "int", "lr", ("chiseled_mastery", "book_copy_wear_cost")),
    (None, "UNBREAKING_WAIVER_PER_LEVEL", "double", "lr", ("chiseled_mastery", "unbreaking_waiver_per_level")),
    (None, "UNBREAKING_WAIVER_MAX_CHANCE", "double", "lr", ("chiseled_mastery", "unbreaking_waiver_max_chance")),
    (None, "MAX_MASTERY_UNITS_PER_SLOT", "double", "lr", ("chiseled_mastery", "max_mastery_units_per_slot")),
    (None, "WEAR_IMMUNE_MAX_GENERATION", "int", "lr", ("chiseled_mastery", "wear_immune_max_generation")),
    (None, "WEAR_IMMUNE_FULL_WAIVER_MAX_ENCHANTS", "int", "lr", ("chiseled_mastery", "wear_immune_full_waiver_max_enchants")),
    (None, "WEAR_IMMUNE_OVERLOAD_WAIVER_DECAY", "double", "lr", ("chiseled_mastery", "wear_immune_overload_waiver_decay")),
    (None, "TOME_DEPLETE_BEHAVIOR", "String", "lr", ("chiseled_mastery", "tome_deplete_behavior")),
    (None, "TOME_REGEN_ENABLED", "boolean", "lr", ("chiseled_mastery", "tome_regen_enabled")),
    (None, "TOME_REGEN_BASE_SECONDS", "int", "lr", ("chiseled_mastery", "tome_regen_base_seconds")),
    (None, "TOME_REGEN_TIER1_SHELVES", "int", "lr", ("chiseled_mastery", "tome_regen_tier1_shelves")),
    (None, "TOME_REGEN_TIER1_SECONDS", "int", "lr", ("chiseled_mastery", "tome_regen_tier1_seconds")),
    (None, "TOME_REGEN_TIER2_SHELVES", "int", "lr", ("chiseled_mastery", "tome_regen_tier2_shelves")),
    (None, "TOME_REGEN_TIER2_SECONDS", "int", "lr", ("chiseled_mastery", "tome_regen_tier2_seconds")),
    (None, "SCAPEGOAT_PRICING", "String", "lr", ("chiseled_mastery", "scapegoat_pricing")),
    ("library_resonance.json / tome_pages", "LOCKED_SHELF_BREAKABLE", "boolean", "lr", ("tome_pages", "locked_shelf_breakable")),
    (None, "LOCKED_BOOK_EXTRACTION_BLOCKED", "boolean", "lr", ("tome_pages", "locked_book_extraction_blocked")),
    (None, "EARLY_UNLOCK_XP_LEVELS", "int", "lr", ("tome_pages", "early_unlock_xp_levels")),
    (None, "EARLY_UNLOCK_GRIT_COUNT", "int", "lr", ("tome_pages", "early_unlock_grit_count")),
    (None, "SACRIFICE_REQUIRED", "int", "lr", ("tome_pages", "sacrifice_required")),
    (None, "TOME_PAGE_REQUIRED_SHELVES", "int", "lr", ("tome_pages", "tome_page_required_shelves")),
    ("library_resonance.json / wild_book_wear", "WILD_BOOK_WEAR_ENABLED", "boolean", "lr", ("wild_book_wear", "enabled")),
    (None, "WILD_REMAINING_WEIGHTS", "intlist", "lr", ("wild_book_wear", "remaining_durability_weights")),
    ("library_resonance.json / epiphany_affinity", "EPIPHANY_PROBABILITY_GLOBAL_WEIGHT", "double", "lr", ("epiphany_affinity", "epiphany_probability_global_weight")),
    (None, "EPIPHANY_PROBABILITY_AFFINITY_WEIGHT", "double", "lr", ("epiphany_affinity", "epiphany_probability_affinity_weight")),
    (None, "BLANK_BOOK_AFFINITY_WEIGHT", "double", "lr", ("epiphany_affinity", "blank_book_affinity_weight")),
    (None, "EPIPHANY_SHELF_SCORE_FACTOR", "double", "lr", ("epiphany_affinity", "epiphany_shelf_score_factor")),
    (None, "EPIPHANY_GLOBAL_MASTERY_SCORE_FACTOR", "double", "lr", ("epiphany_affinity", "epiphany_global_mastery_score_factor")),
    (None, "EPIPHANY_AFFINITY_MASTERY_SCORE_FACTOR", "double", "lr", ("epiphany_affinity", "epiphany_affinity_mastery_score_factor")),
    (None, "EPIPHANY_TIER2_MAX_PROB", "double", "lr", ("epiphany_affinity", "epiphany_tier2_max_prob")),
    (None, "EPIPHANY_TIER2_SCORE_DIVISOR", "double", "lr", ("epiphany_affinity", "epiphany_tier2_score_divisor")),
    (None, "EPIPHANY_TIER2_MASTERY_BONUS", "double", "lr", ("epiphany_affinity", "epiphany_tier2_mastery_bonus")),
    (None, "EPIPHANY_TIER_PLUS_MAX_PROB", "double", "lr", ("epiphany_affinity", "epiphany_tier_plus_max_prob")),
    (None, "EPIPHANY_TIER_PLUS_SCORE_DIVISOR", "double", "lr", ("epiphany_affinity", "epiphany_tier_plus_score_divisor")),
    (None, "EPIPHANY_TIER_PLUS_MASTERY_BONUS", "double", "lr", ("epiphany_affinity", "epiphany_tier_plus_mastery_bonus")),
    ("library_resonance.json / essence_valuation", "ESSENCE_RARITY_MIN_MULTIPLIER", "double", "lr", ("essence_valuation", "rarity_min_multiplier")),
    (None, "ESSENCE_RARITY_MAX_MULTIPLIER", "double", "lr", ("essence_valuation", "rarity_max_multiplier")),
    (None, "ESSENCE_RARITY_LOG_SLOPE", "double", "lr", ("essence_valuation", "rarity_log_slope")),
    (None, "ESSENCE_TREASURE_MULTIPLIER", "double", "lr", ("essence_valuation", "treasure_multiplier")),
    (None, "CONSERVATION_SAFETY_MARGIN_POINTS", "int", "lr", ("essence_valuation", "conservation_safety_margin_points")),
    ("library_resonance.json / ritual", "MAX_SINGLE_WAIT_SECONDS", "int", "lr", ("ritual", "max_single_wait_seconds")),
    (None, "MIN_TOME_ENCHANTS_FOR_WAIT", "int", "lr", ("ritual", "min_tome_enchants_for_wait")),
    (None, "MAX_SACRIFICE_XP_LEVELS", "int", "lr", ("ritual", "max_sacrifice_xp_levels")),
    (None, "SACRIFICE_EXTRA_XP_PER_QUEUED_STEP", "int", "lr", ("ritual", "sacrifice_extra_xp_per_queued_step")),
    (None, "SACRIFICE_XP_TIME_CONSTANT_SECONDS", "double", "lr", ("ritual", "sacrifice_xp_time_constant_seconds")),
    (None, "MAX_RUSH_DEGRADE_NORMAL", "double", "lr", ("ritual", "max_rush_degrade_normal")),
    (None, "MAX_RUSH_DEGRADE_INFINITY", "double", "lr", ("ritual", "max_rush_degrade_infinity")),
    (None, "RUSH_DEGRADE_TIME_CONSTANT_SECONDS", "double", "lr", ("ritual", "rush_degrade_time_constant_seconds")),
    (None, "MAX_TRANSCRIPTION_SLIP_CHANCE", "double", "lr", ("ritual", "max_transcription_slip_chance")),
    (None, "SHELF_LOAD_REFERENCE", "double", "lr", ("ritual", "shelf_load_reference")),
    (None, "TOME_LOAD_REFERENCE", "double", "lr", ("ritual", "tome_load_reference")),
    ("library_resonance.json / ritual.wait_curve", "WAIT_CURVE_SHELF_WEIGHT", "double", "lr", ("ritual", "wait_curve", "shelf_weight")),
    (None, "WAIT_CURVE_TOME_WEIGHT", "double", "lr", ("ritual", "wait_curve", "tome_weight")),
    (None, "WAIT_CURVE_LEVEL_FLOOR", "double", "lr", ("ritual", "wait_curve", "level_floor")),
    (None, "WAIT_CURVE_LEVEL_SPAN", "double", "lr", ("ritual", "wait_curve", "level_span")),
    (None, "WAIT_CURVE_LEVEL_EXPONENT", "double", "lr", ("ritual", "wait_curve", "level_exponent")),
    (None, "WAIT_CURVE_SATURATION", "double", "lr", ("ritual", "wait_curve", "saturation")),
    (None, "WAIT_CURVE_MIN_TICKS", "int", "lr", ("ritual", "wait_curve", "min_ticks")),
    ("library_resonance.json / ritual.applicability", "APPLICABILITY_ITEM_BREADTH_REFERENCE", "double", "lr", ("ritual", "applicability", "item_breadth_reference")),
    (None, "APPLICABILITY_BREADTH_WEIGHT", "double", "lr", ("ritual", "applicability", "breadth_weight")),
    (None, "APPLICABILITY_KEYSTONE_MAJOR_MULTIPLIER", "double", "lr", ("ritual", "applicability", "keystone_major_multiplier")),
    (None, "APPLICABILITY_KEYSTONE_MINOR_MULTIPLIER", "double", "lr", ("ritual", "applicability", "keystone_minor_multiplier")),
    (None, "APPLICABILITY_BROAD_MULTIPLIER", "double", "lr", ("ritual", "applicability", "broad_multiplier")),
    (None, "APPLICABILITY_RARITY_WEIGHT_1_MULTIPLIER", "double", "lr", ("ritual", "applicability", "rarity_weight_1_multiplier")),
    (None, "APPLICABILITY_RARITY_WEIGHT_2_MULTIPLIER", "double", "lr", ("ritual", "applicability", "rarity_weight_2_multiplier")),
    (None, "APPLICABILITY_RARITY_WEIGHT_5_MULTIPLIER", "double", "lr", ("ritual", "applicability", "rarity_weight_5_multiplier")),
    (None, "APPLICABILITY_MIN_FACTOR", "double", "lr", ("ritual", "applicability", "min_factor")),
    (None, "APPLICABILITY_MAX_FACTOR", "double", "lr", ("ritual", "applicability", "max_factor")),
    ("library_resonance.json / crossline_slip", "CROSSLINE_MIN_LOAD_THRESHOLD", "double", "lr", ("crossline_slip", "min_load_threshold")),
    (None, "CROSSLINE_PER_LOAD_SLOPE", "double", "lr", ("crossline_slip", "per_load_slope")),
    (None, "CROSSLINE_MAX_SLIP_CHANCE", "double", "lr", ("crossline_slip", "max_slip_chance")),
    (None, "CROSSLINE_SHELF_TIER1_COUNT", "int", "lr", ("crossline_slip", "shelf_tier1_count")),
    (None, "CROSSLINE_SHELF_TIER1_BONUS", "double", "lr", ("crossline_slip", "shelf_tier1_bonus")),
    (None, "CROSSLINE_SHELF_TIER2_COUNT", "int", "lr", ("crossline_slip", "shelf_tier2_count")),
    (None, "CROSSLINE_SHELF_TIER2_BONUS", "double", "lr", ("crossline_slip", "shelf_tier2_bonus")),
    (None, "CROSSLINE_SHELF_TIER3_COUNT", "int", "lr", ("crossline_slip", "shelf_tier3_count")),
    (None, "CROSSLINE_SHELF_TIER3_BONUS", "double", "lr", ("crossline_slip", "shelf_tier3_bonus")),
    (None, "CROSSLINE_TREASURE_SCORE", "double", "lr", ("crossline_slip", "treasure_score")),
    (None, "CROSSLINE_SINGLE_TREASURE_MIN_LOAD", "double", "lr", ("crossline_slip", "single_treasure_min_load")),
    (None, "CROSSLINE_HIGH_RARITY_MAX_WEIGHT", "int", "lr", ("crossline_slip", "high_rarity_max_weight")),
    (None, "CROSSLINE_HIGH_RARITY_SCORE", "double", "lr", ("crossline_slip", "high_rarity_score")),
    (None, "CROSSLINE_MID_RARITY_MAX_WEIGHT", "int", "lr", ("crossline_slip", "mid_rarity_max_weight")),
    (None, "CROSSLINE_MID_RARITY_SCORE", "double", "lr", ("crossline_slip", "mid_rarity_score")),
    (None, "CROSSLINE_BROAD_APPLICABILITY_MIN_ITEMS", "int", "lr", ("crossline_slip", "broad_applicability_min_items")),
    (None, "CROSSLINE_BROAD_APPLICABILITY_SCORE", "double", "lr", ("crossline_slip", "broad_applicability_score")),
    (None, "CROSSLINE_RELATIVE_LEVEL_MIN_RATIO", "double", "lr", ("crossline_slip", "relative_level_min_ratio")),
    (None, "CROSSLINE_RELATIVE_LEVEL_SCORE", "double", "lr", ("crossline_slip", "relative_level_score")),
    (None, "CROSSLINE_UNBREAKING_HIGH_MIN_LEVEL", "int", "lr", ("crossline_slip", "unbreaking_high_min_level")),
    (None, "CROSSLINE_UNBREAKING_HIGH_SCORE", "double", "lr", ("crossline_slip", "unbreaking_high_score")),
    (None, "CROSSLINE_TIER_DROP_SHARE", "double", "lr", ("crossline_slip", "tier_drop_share")),
    (None, "CROSSLINE_JUNK_SHARE", "double", "lr", ("crossline_slip", "junk_crossline_share")),
    (None, "CROSSLINE_CURSE_SHARE", "double", "lr", ("crossline_slip", "curse_crossline_share")),
    (None, "CROSSLINE_POLLUTION_REFUND_RATIO", "double", "lr", ("crossline_slip", "pollution_refund_ratio")),
    ("shelf_gates.json", "VANILLA_TABLE_MODE", "String", "sg", ("vanilla_table_mode",)),
    (None, "SCAN_HORIZONTAL_RADIUS", "int", "sg", ("scan_volume", "horizontal_radius")),
    (None, "SCAN_VERTICAL_RADIUS", "int", "sg", ("scan_volume", "vertical_radius")),
    (None, "REQUIRE_BREATHING_ROOM", "boolean", "sg", ("scan_volume", "require_breathing_room")),
    (None, "MIN_OPEN_ADJACENT_BLOCKS", "int", "sg", ("scan_volume", "min_open_adjacent_blocks")),
    (None, "REQUIRE_LINE_OF_SIGHT", "boolean", "sg", ("scan_volume", "require_line_of_sight")),
    (None, "DIRECT_GATES_ENABLED", "boolean", "sg", ("direct_upgrade_gates", "enabled")),
    (None, "MAX_DIRECT_BOOKSHELVES", "int", "sg", ("direct_upgrade_gates", "max_bookshelves")),
    (None, "TWO_LEVEL_MAX_BOOKSHELVES", "int", "sg", ("direct_upgrade_gates", "two_level_max_bookshelves")),
    (None, "DIRECT_TIER_GAMMA", "double", "sg", ("direct_upgrade_gates", "gamma")),
    (None, "BLANK_MAX_TIER_MODE", "String", "sg", ("direct_upgrade_gates", "blank_max_tier_mode")),
    (None, "DEPLETED_TIER1_BOOKSHELVES", "int", "sg", ("depleted_tome_gradients", "tier1_bookshelves")),
    (None, "DEPLETED_TIER2_BOOKSHELVES", "int", "sg", ("depleted_tome_gradients", "tier2_bookshelves")),
    (None, "DEPLETED_TIER2_MATERIAL_MULTIPLIER", "double", "sg", ("depleted_tome_gradients", "tier2_material_multiplier")),
    (None, "DEPLETED_TIER3_QUALIFYING_ENCHANTMENTS", "int", "sg", ("depleted_tome_gradients", "tier3_qualifying_enchantments")),
    (None, "DEPLETED_BASE_BATCH_SIZE", "int", "sg", ("depleted_tome_gradients", "base_batch_size")),
    (None, "DEPLETED_TIER3_BATCH_SIZE", "int", "sg", ("depleted_tome_gradients", "tier3_batch_size")),
]


def _load(name: str) -> dict:
    with open(os.path.join(DATA_DIR, name), encoding="utf-8") as fh:
        return json.load(fh)


def _dig(doc: dict, path: tuple[str, ...]):
    cur = doc
    for key in path:
        if not isinstance(cur, dict) or key not in cur:
            raise KeyError("missing JSON key: " + ".".join(path))
        cur = cur[key]
    return cur


def _literal(java_type: str, value) -> str:
    if java_type == "int":
        if isinstance(value, bool) or not float(value).is_integer():
            raise TypeError(f"expected int, got {value!r}")
        return str(int(value))
    if java_type == "double":
        text = repr(float(value))
        return text
    if java_type == "boolean":
        return "true" if value else "false"
    if java_type == "String":
        return json.dumps(str(value))
    if java_type == "intlist":
        return "java.util.List.of(" + ", ".join(str(int(v)) for v in value) + ")"
    raise TypeError(java_type)


def _decl(java_type: str) -> str:
    return "java.util.List<Integer>" if java_type == "intlist" else java_type


def render() -> str:
    docs = {"lr": _load("library_resonance.json"), "sg": _load("shelf_gates.json")}
    lines: list[str] = [
        "package chiseled_enchanting_table;",
        "",
        "/**",
        " * GENERATED bundled-datapack defaults — do not edit by hand.",
        " *",
        " * <p>Rendered by {@code scripts/gen_bundled_defaults.py} from",
        " * {@code library_resonance.json} and {@code shelf_gates.json}, which are the",
        " * single source of truth for every default below (audit P1: the Java",
        " * {@code DEFAULT} records used to retype the same numbers and could drift",
        " * silently). {@code ci_static_checks.py} re-renders this file and fails on",
        " * any byte difference. To change a default, edit the JSON and re-run the",
        " * generator ({@code ./gradlew genBundledDefaults} or the script directly).</p>",
        " */",
        "public final class BundledDefaults {",
        "    private BundledDefaults() {}",
    ]
    for section, name, java_type, doc_key, path in FIELDS:
        if section is not None:
            lines.append("")
            lines.append(f"    // ---- {section} ----")
        value = _dig(docs[doc_key], path)
        lines.append(
            f"    public static final {_decl(java_type)} {name} = {_literal(java_type, value)};"
        )
    lines.append("}")
    lines.append("")
    return "\n".join(lines)


def main(argv: list[str]) -> int:
    rendered = render()
    if "--check" in argv:
        try:
            with open(OUT_PATH, encoding="utf-8") as fh:
                current = fh.read()
        except OSError:
            print("BundledDefaults.java is missing", file=sys.stderr)
            return 1
        if current != rendered:
            print(
                "BundledDefaults.java is stale — run: python3 scripts/gen_bundled_defaults.py",
                file=sys.stderr,
            )
            return 1
        print("BundledDefaults.java is current")
        return 0
    with open(OUT_PATH, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(rendered)
    print(f"wrote {os.path.relpath(OUT_PATH, REPO)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))

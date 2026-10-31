#!/usr/bin/env python3
"""Check design-document coverage and traceability, not Minecraft behavior.

Run from anywhere: python3 scripts/verify_fusion_designs.py
To compare the 53rd batch with its pre-batch snapshot, add
--baseline-ref e564a5e (requires that commit in the local Git history).
Update the expected totals and promoted pairs whenever the design advances.
"""

from __future__ import annotations

import argparse
from collections import Counter, defaultdict
import json
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent
DOCS = ROOT / "docs"
COVERAGE = "FUSION_MATRIX_COVERAGE.md"
FALLBACK = "FUSION_FALLBACK_DRAFT.md"
INDEX = "FUSION_FAMILY_INDEX.md"
FAMILY = "FUSION_FAMILY_PLAN.md"
DRAFT = "FUSION_AUTONOMOUS_DRAFT.md"
TRANSMUTATION = "TRANSMUTATION_DRAFT.md"
REDESIGN = "FUSION_REDESIGN.md"
GLOBAL = "FUSION_GLOBAL_DESIGN.md"
SEMANTIC = "FUSION_SEMANTIC_COMBINATION.md"
DOC_FILES = [COVERAGE, FALLBACK, INDEX, FAMILY, DRAFT, TRANSMUTATION, REDESIGN, GLOBAL, SEMANTIC]

EXPECTED = Counter({"专属": 184, "副位通配": 124, "已选模板": 124, "代设计": 402, "候选推广": 0, "总设初稿": 972})
FAMILIES = {
    "M": "sharpness smite bane_of_arthropods fire_aspect knockback breach density lunge sweeping_edge wind_burst",
    "R": "power punch flame infinity multishot piercing quick_charge",
    "T": "loyalty riptide channeling impaling",
    "H": "efficiency silk_touch fortune",
    "D": "protection fire_protection blast_protection projectile_protection thorns",
    "V": "soul_speed swift_sneak feather_falling depth_strider frost_walker",
    "W": "respiration aqua_affinity",
    "S": "looting mending unbreaking",
    "F": "luck_of_the_sea lure",
    "C": "binding_curse vanishing_curse",
}
FAMILY_OF = {item: family for family, items in FAMILIES.items() for item in items.split()}
FAMILY_RANK = {family: n for n, family in enumerate("MRTHDVWSF")}

# Each tuple is (53.xx, first main, second main, old G in that order, old reverse G).
BATCH53 = [
    (1, "sharpness", "luck_of_the_sea", 14, 984),
    (2, "power", "luck_of_the_sea", 252, 994),
    (3, "efficiency", "luck_of_the_sea", 523, 1004),
    (4, "protection", "luck_of_the_sea", 584, 1007),
    (5, "soul_speed", "luck_of_the_sea", 745, 1012),
    (6, "respiration", "luck_of_the_sea", 877, 1017),
    (7, "loyalty", "channeling", 431, 488),
    (8, "multishot", "riptide", 352, 458),
    (9, "fire_protection", "depth_strider", 611, 819),
    (10, "blast_protection", "frost_walker", 644, 844),
    (11, "projectile_protection", "swift_sneak", 674, 760),
    (12, "smite", "punch", 20, 254),
    (13, "breach", "punch", 109, 257),
    (14, "density", "power", 131, 231),
    (15, "soul_speed", "unbreaking", 744, 976),
    (16, "respiration", "looting", 874, 935),
    (17, "silk_touch", "thorns", 532, 702),
    (18, "blast_protection", "feather_falling", 642, 792),
    (19, "fire_protection", "mending", 614, 950),
    (20, "piercing", "thorns", 383, 697),
    (21, "channeling", "swift_sneak", 497, 753),
    (22, "blast_protection", "unbreaking", 649, 973),
    (23, "soul_speed", "looting", 742, 930),
    (24, "riptide", "efficiency", 461, 508),
]

ROW = re.compile(r"^\| ([^|\n]+)（`([^`]+)`） \| ([^|\n]+) \| ([^|\n]+) \| ([^|\n]+) \|$", re.M)
G_STATE = re.compile(r"\[总设初稿 · G(\d{4})\]\((?:FUSION_FAMILY_INDEX|FUSION_FALLBACK_DRAFT)\.md#g\1\)（未逐项核验）")
FALLBACK_ROW = re.compile(
    r'^\| ([^|]+) · <a id="g(\d{4})"></a>G\2（`([^`]+)` → `([^`]+)`）'
    r'(?:（已由 53\.(\d{2}) 取代）)? \| ([^|]+) \| ([^|]+) \| ([^|]+) \| ([^|]+) \|$', re.M
)
INDEX_ROW = re.compile(
    r'^\| <a id="g(\d{4})"></a>\[([^]]+) · G\1\]\(FUSION_FALLBACK_DRAFT\.md#g\1\)'
    r' \| <a id="g(\d{4})"></a>\[([^]]+) · G\3\]\(FUSION_FALLBACK_DRAFT\.md#g\3\) \|$'
)


def check(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def category(state: str) -> str:
    return "总设初稿" if state.startswith("[总设初稿") else state.split(" · ")[0]


def read_doc(name: str) -> str:
    return (DOCS / name).read_text(encoding="utf-8")


def parse_coverage(text: str) -> tuple[dict, dict]:
    parts = re.split(r"^## ([^（\n]+)（`([^`]+)`）\n", text, flags=re.M)
    check(len(parts) == 1 + 43 * 3, f"coverage: expected 43 main headings, got {(len(parts)-1)//3}")
    entries: dict[tuple[str, str], tuple[str, str]] = {}
    names = {}
    for index in range(1, len(parts), 3):
        name, main, body = parts[index:index + 3]
        check(main not in names, f"duplicate main heading: {main}")
        names[main] = name
        rows = ROW.findall(body)
        check(len(rows) == 42, f"coverage {main}: expected 42 directions, got {len(rows)}")
        for sub_name, sub, _meaning, state, scope in rows:
            check(main != sub and (main, sub) not in entries, f"duplicate/self pair: {main}, {sub}")
            check(bool(sub_name and state and scope), f"empty field: {main}, {sub}")
            entries[main, sub] = (state, scope)
    ids = set(names)
    check(len(entries) == 1806 and set(entries) == {(a, b) for a in ids for b in ids if a != b}, "coverage is not the full 43 × 42 matrix")
    return entries, names


def pair_family(a: str, b: str) -> str:
    check(a in FAMILY_OF and b in FAMILY_OF, f"missing family: {a}, {b}")
    families = (FAMILY_OF[a], FAMILY_OF[b])
    check(all(f in FAMILY_RANK for f in families), f"unexpected curse in remaining family: {a}, {b}")
    return "".join(sorted(families, key=FAMILY_RANK.__getitem__))


def verify_overview(text: str, entries: dict, names: dict) -> None:
    section = text.split("## 主位覆盖概览", 1)[1].split("## 43 种附魔", 1)[0]
    rows = re.findall(r"^\| ([^|]+) \| (\d+) \| (\d+) \| (\d+) \| (\d+) \| (\d+) \| (\d+) \|$", section, re.M)
    by_name = {row[0]: list(map(int, row[1:])) for row in rows}
    check(len(by_name) == 43, "coverage overview must have 43 unique rows")
    order = ["专属", "副位通配", "已选模板", "代设计", "候选推广", "总设初稿"]
    for a, name in names.items():
        count = Counter(category(state) for (main, _), (state, _scope) in entries.items() if main == a)
        check(by_name.get(name) == [count[c] for c in order], f"overview is stale for {a}: {by_name.get(name)} vs {count}")


def verify_transmutations(text: str) -> None:
    path = ROOT / "src/main/resources/data/enchantment_fusion/enchantment_fusion/transmutations.json"
    variants = json.loads(path.read_text(encoding="utf-8"))["variants"]
    roles = {
        "SWORD": "剑", "SPEAR": "矛", "AXE": "斧", "MACE": "重锤", "TRIDENT": "三叉戟",
        "BOW": "弓", "CROSSBOW": "弩", "DIG_TOOL": "工具（详见第 4 节）", "SHIELD": "盾",
        "HEAD_ARMOR": "头部", "CHEST_ARMOR": "胸部", "LEG_ARMOR": "腿部", "FOOT_ARMOR": "脚部",
        "ELYTRA": "鞘翅/滑翔物", "FISHING_ROD": "钓竿", "TOTEM": "图腾", "GLASS_BOTTLE": "空玻璃瓶",
        "CONSUMABLE": "消耗品/投掷物",
    }
    section = text.split("## 3. 默认 116 个变体：逐项草案", 1)[1].split("## 4. `DIG_TOOL`", 1)[0]
    rows = {}
    for match in re.finditer(r"^\| [^|]+（`([^`]+)`） \| ([^|]+) \| ([^|]+) \| ([^|]+) \|$", section, re.M):
        variant, source, present_roles, _description = match.groups()
        check(variant not in rows, f"duplicate transmutation {variant}")
        rows[variant] = (source, present_roles)
    check(set(rows) == set(variants) and len(rows) == 116, "116 transmutation ids do not match the data")
    bindings = []
    alias_count = 0
    for variant, data in variants.items():
        source, present_roles = rows[variant]
        expected_roles = "、".join(roles[r] for r in data["roles"])
        if data.get("require_damageable"):
            expected_roles += "；仅可损坏"
        check(present_roles == expected_roles, f"{variant}: roles or damageable flag differ")
        check(f"`{data['source'].split(':')[-1]}`" in source, f"{variant}: missing primary source")
        bindings.extend((variant, data["source"], role) for role in data["roles"])
        for alias in data.get("extra_sources", []):
            alias_roles = data.get("extra_source_roles", {}).get(alias, data["roles"])
            expected_alias = f"`{alias.split(':')[-1]}`：" + "、".join(roles[r] for r in alias_roles)
            check(expected_alias in source, f"{variant}: missing alias {alias}")
            alias_count += len(alias_roles)
            bindings.extend((variant, alias, role) for role in alias_roles)
    check(len(bindings) == len(set(bindings)) == 431 and alias_count == 23, "transmutation binding counts differ")
    dig = text.split("## 4. `DIG_TOOL`", 1)[1].split("## 5.", 1)[0]
    dig_rows = {}
    for match in re.finditer(r"^\| `([^`]+)` \| ([^|]+) \| ([^|]+) \| ([^|]+) \| ([^|]+) \| ([^|]+) \|$", dig, re.M):
        variant = match.group(1)
        check(variant not in dig_rows, f"duplicate DIG_TOOL variant {variant}")
        dig_rows[variant] = match.groups()[1:]
    expected_dig = {variant for variant, data in variants.items() if "DIG_TOOL" in data["roles"]}
    check(set(dig_rows) == expected_dig and len(dig_rows) == 28, "DIG_TOOL coverage is not 28 × 5")
    check(all(len(actions) == 5 and all(actions) for actions in dig_rows.values()), "DIG_TOOL has empty actions")


def verify_links_and_tables() -> None:
    for name in DOC_FILES:
        path = DOCS / name
        text = read_doc(name)
        columns = None
        for number, line in enumerate(text.splitlines(), 1):
            if line.startswith("|") and line.endswith("|"):
                count = line.count("|")
                if columns is None:
                    columns = count
                check(count == columns, f"{name}:{number}: inconsistent table columns")
            else:
                columns = None
        for href in re.findall(r"\]\(([^)]+)\)", text):
            if href.startswith(("http://", "https://", "#")):
                continue
            rel, _, fragment = href.partition("#")
            target = (path.parent / rel).resolve()
            check(target.is_file(), f"{name}: missing link {href}")
            if fragment.startswith(("g0", "g1", "family-")):
                target_text = target.read_text(encoding="utf-8")
                check(f'id="{fragment}"' in target_text, f"{name}: missing explicit anchor {href}")


def verify_baseline(ref: str, now: dict) -> None:
    old_text = subprocess.check_output(["git", "show", f"{ref}:docs/{COVERAGE}"], cwd=ROOT, text=True)
    before, _ = parse_coverage(old_text)
    promoted = {(a, b) for _, a, b, _g, _r in BATCH53} | {(b, a) for _, a, b, _g, _r in BATCH53}
    for key, (old_state, old_scope) in before.items():
        state, scope = now[key]
        if key in promoted:
            check(G_STATE.fullmatch(old_state) is not None and state.startswith("代设计 · 53."), f"baseline promotion mismatch {key}")
        elif category(old_state) == "总设初稿":
            old_g, new_g = G_STATE.fullmatch(old_state), G_STATE.fullmatch(state)
            check(old_g is not None and new_g is not None and old_g.group(1) == new_g.group(1) and old_scope == scope,
                  f"baseline pending identity/scope changed {key}")
        else:
            check((old_state, old_scope) == (state, scope), f"baseline previously selected/design scope changed {key}")
    old_redesign = subprocess.check_output(["git", "show", f"{ref}:docs/{REDESIGN}"], cwd=ROOT, text=True)
    new_redesign = read_doc(REDESIGN)
    check(old_redesign.split("## 助手代设计 47：", 1)[0] == new_redesign.split("## 助手代设计 47：", 1)[0],
          "baseline: user-selected sections 01–46 or their numeric/safety constraints changed")
    marker, end = "## 用户本轮集中决定", "## 助手总设计与跨界草案"
    check(old_redesign.split(marker, 1)[1].split(end, 1)[0] == new_redesign.split(marker, 1)[1].split(end, 1)[0],
          "baseline: three selected global decisions changed")
    old_draft = subprocess.check_output(["git", "show", f"{ref}:docs/{DRAFT}"], cwd=ROOT, text=True)
    check(old_draft.split("## 尚未完成", 1)[0] == read_doc(DRAFT).split("## 53：", 1)[0],
          "baseline: pre-53 autonomous proposals changed")
    old_transmutation = subprocess.check_output(["git", "show", f"{ref}:docs/{TRANSMUTATION}"], cwd=ROOT, text=True)
    check(old_transmutation == read_doc(TRANSMUTATION), "baseline: cross-domain transmutation design changed")
    print(f"PASS baseline {ref}: selected states, scopes, numeric/safety constraints and prior proposals preserved; exactly 48 pending directions promoted")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline-ref", help="optional Git ref for pre-53 coverage comparison")
    args = parser.parse_args()
    coverage, fallback, index, family, draft = map(read_doc, [COVERAGE, FALLBACK, INDEX, FAMILY, DRAFT])
    entries, names = parse_coverage(coverage)
    profile_path = ROOT / "src/main/resources/data/enchantment_fusion/enchantment_fusion/matrix_profiles.json"
    profiles = json.loads(profile_path.read_text(encoding="utf-8"))["profiles"]
    profile_ids = {item.removeprefix("minecraft:") for item in profiles}
    check(set(names) == set(FAMILY_OF) == profile_ids and len(profile_ids) == 43, "semantic families or matrix profile IDs differ")
    counts = Counter(category(state) for state, _ in entries.values())
    check(counts == EXPECTED, f"coverage categories: {counts}, expected {EXPECTED}")
    verify_overview(coverage, entries, names)

    old_g = {}
    replaced = {}
    for match in FALLBACK_ROW.finditer(fallback):
        label, raw_id, main, sub, marker, trigger, structure, modification, scope = match.groups()
        n = int(raw_id)
        check(n not in old_g and all([trigger.strip(), structure.strip(), modification.strip(), scope.strip()]), f"invalid historical G{n:04d}")
        check(label == f"{names[main]} ▷ {names[sub]}", f"historical G{n:04d} direction mismatch")
        old_g[n] = (main, sub)
        if marker:
            replaced[n] = int(marker)
    check(set(old_g) == set(range(1, 1021)) and len(set(old_g.values())) == 1020, "historical G rows are missing/duplicated")
    pending_g = {}
    for pair, (state, scope) in entries.items():
        if category(state) == "总设初稿":
            match = G_STATE.fullmatch(state)
            check(match is not None and "FUSION_FAMILY_INDEX.md" in state, f"invalid family index state {pair}: {state}")
            n = int(match.group(1))
            check(n not in pending_g and old_g[n] == pair, f"G{n:04d}: wrong pair or duplicate")
            check("建议" in scope and "待确认" in scope, f"pending scope presented as final: {pair}")
            pending_g[n] = pair
    check(len(pending_g) == 972 and set(pending_g).isdisjoint(replaced), "pending/replaced G rows overlap")
    check(set(pending_g) | set(replaced) == set(old_g) and len(replaced) == 48, "historical G partition must be 972 + 48")
    check(all((b, a) in entries and category(entries[b, a][0]) == "总设初稿" for a, b in pending_g.values()), "pending directions must be paired reversibly")

    section53 = draft.split("## 53：", 1)[1].split("## 尚未完成", 1)[0]
    headings53 = re.findall(r"^### 53\.(\d{2}) ([^\n]+) × ([^\n]+)$", section53, re.M)
    check(len(headings53) == 24, "53 batch must have 24 headings")
    seen_promoted = set()
    for n, a, b, g, reverse_g in BATCH53:
        check(any(int(num) == n and {first, second} == {names[a], names[b]}
                  for num, first, second in headings53), f"missing/mislabeled heading 53.{n:02d}")
        for main, sub, old_id in ((a, b, g), (b, a, reverse_g)):
            check(entries[main, sub][0] == f"代设计 · 53.{n:02d}（待审）", f"incorrect 53 mapping {main}, {sub}")
            check(old_g[old_id] == (main, sub) and replaced.get(old_id) == n, f"incorrect replaced marker G{old_id:04d}")
            check(f"**{names[main]} ▷ {names[sub]}｜" in section53, f"missing 53 proposal {main}, {sub}")
            seen_promoted.add((main, sub))
    check(len(seen_promoted) == 48 and set(replaced) == {g for _, _, _, a, b in BATCH53 for g in (a, b)}, "53 promotion differs")
    integration53 = section53.split("### 53 批与原版主位的衔接初稿", 1)[1]
    rows53 = re.findall(r"^\| 53\.(\d{2}) ([^|]+) \| ([^|]+) \| ([^|]+) \|$", integration53, re.M)
    check(len(rows53) == 24 and [int(row[0]) for row in rows53] == list(range(1, 25)),
          "53 batch must document vanilla-main handling for both directions of all 24 pairs")
    expected_titles = {number: f"{first} × {second}" for number, first, second in headings53}
    for number, title, forward, reverse in rows53:
        check(title == expected_titles[number] and forward.strip() and reverse.strip(), f"53.{number} main handling is mismatched")
    section52 = draft.split("## 52：", 1)[1].split("## 53：", 1)[0]
    headings52 = re.findall(r"^### 52\.(\d{2}) ([^\n]+) × ([^\n]+)$", section52, re.M)
    check(len(headings52) == 20 and [int(row[0]) for row in headings52] == list(range(1, 21)), "52 batch must have 20 headings")
    by_name = {name: item for item, name in names.items()}
    for n, first, second in headings52:
        a, b = by_name[first], by_name[second]
        for main, sub in ((a, b), (b, a)):
            check(entries[main, sub][0] == f"代设计 · 52.{n}（待审）", f"incorrect 52 mapping {main}, {sub}")
            check(f"**{names[main]} ▷ {names[sub]}｜" in section52, f"missing 52 proposal {main}, {sub}")

    for a, b in [("fire_aspect", "knockback"), ("knockback", "fire_aspect"), ("density", "breach"),
                 ("breach", "density"), ("loyalty", "protection"), ("protection", "loyalty")]:
        check(entries[a, b][0].startswith("专属"), f"selected exception no longer exclusive: {a}, {b}")
    check("39.1" in entries["density", "breach"][0] and "39.1" in entries["breach", "density"][0], "same-effect exception 39.1 changed")

    families = Counter()
    unordered = set()
    for a, b in pending_g.values():
        pair = frozenset((a, b))
        if pair not in unordered:
            unordered.add(pair)
            families[pair_family(a, b)] += 1
    check(len(unordered) == 486 and len(families) == 41, "expected 486 pairs across 41 families")
    declared = {}
    for match in re.finditer(r'<a id="family-([a-z]{2})"></a>([A-Z]{2}) / (\d+)', family):
        anchor, code, count = match.groups()
        check(anchor == code.lower() and code not in declared, f"bad family anchor {code}")
        declared[code] = int(count)
    check(Counter(declared) == families, f"family counts differ: {declared} vs {families}")
    semantic = family.split("## 三、43 种附魔各自贡献什么", 1)[1].split("## 四、", 1)[0]
    semantic_names = re.findall(r"^\| ([^|]+) \| [^|]+ \| [^|]+ \|$", semantic, re.M)[2:]
    check(len(semantic_names) == 43 and set(semantic_names) == set(names.values()), "semantic profile table is missing names")

    indexed = set()
    per_index_family = Counter()
    current_family = None
    heading = re.compile(r"^## \[([A-Z]{2}) 家族机制\]\(FUSION_FAMILY_PLAN\.md#family-([a-z]{2})\)（(\d+) 对）$")
    index_heading_counts = {}
    for line in index.splitlines():
        m = heading.match(line)
        if m:
            current_family, anchor, count = m.groups()
            check(current_family.lower() == anchor and current_family not in index_heading_counts, "duplicate/mismatched index family")
            index_heading_counts[current_family] = int(count)
            continue
        m = INDEX_ROW.match(line)
        if m:
            first, label1, second, label2 = m.groups()
            first, second = int(first), int(second)
            check(current_family is not None and first in pending_g and second in pending_g, "index has unknown G")
            a, b = pending_g[first]
            check(pending_g[second] == (b, a), f"index G{first:04d}/G{second:04d} are not reverses")
            check(label1 == f"{names[a]} ▷ {names[b]}" and label2 == f"{names[b]} ▷ {names[a]}", "index label mismatch")
            check(pair_family(a, b) == current_family, f"index wrong family for {a}/{b}")
            check(first not in indexed and second not in indexed, "index duplicated G")
            indexed.update((first, second))
            per_index_family[current_family] += 1
    check(indexed == set(pending_g) and per_index_family == families and Counter(index_heading_counts) == families,
          "index missing/duplicated pairs or incorrect family counts")

    # Presence guards only: these don't prove that the prose or any future implementation is safe.
    for text, required in [
        (family, ["正反同效", "旧装备不符合新范围时保留原数据和旧效果", "横刈额外范围只收成熟作物", "安全薄冰消失不生成水"]),
        (section53, ["玩家和真实武器都只有一份", "不生成额外箭", "仅可移除模组自己的临时薄冰", "不产水", "不换装备"]),
        (read_doc(TRANSMUTATION), ["经验照常获得，同时积累补给进度", "所有原水受保护", "岩浆永久变圆石"]),
        (read_doc(SEMANTIC), ["不是机械拼字", "未取代 53.10", "不需要先受到爆炸", "不破地形、不产水"]),
    ]:
        for phrase in required:
            check(phrase in text, f"missing safety/decision marker: {phrase}")
    verify_transmutations(read_doc(TRANSMUTATION))
    verify_links_and_tables()
    if args.baseline_ref:
        verify_baseline(args.baseline_ref, entries)
    print("PASS matrix: 1,806 unique directions; 184/124/124/402/0/972, including all 43 overview rows")
    print("PASS traceability: 24 × 2 batch-53 directions, 48 replaced G rows, 972 current G links, 486 reverse pairs, 41 family counts")
    print("PASS selected exceptions, 43 semantic profiles, 116 cross-domain variants and 28 × 5 tool adapters")
    print("PASS local links, explicit G/family anchors, table shapes, selected safety/decision markers")
    print("NOTE: static document checks do not verify playability, balance, implementation or in-game safety")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, KeyError, IndexError, OSError, subprocess.CalledProcessError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        sys.exit(1)

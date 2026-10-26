#!/usr/bin/env python3
"""One-shot importer: parse upstream ReworkEnchantedBookChestLoot.java and emit
loot_injections.json entries.

Kept as a script under scripts/ (not deleted after use) so anyone re-tracing the port can see
where the ~200 entries in loot_injections.json came from and re-derive them if the upstream Java
is ever pulled in fresh again.

Structure it parses:
    if (id.equals("minecraft:archaeology/trail_ruins_rare")) { <one pool> }
    ...
    if (chestType.equals("<X>")) { <one pool> }
    else if (chestType.equals("<Y>")) { <one pool> }

Each pool is a chain of
    ItemEntry.builder(Items.ENCHANTED_BOOK)
        .apply(fromChiseledEnchantingTableLore)
        .apply(enchantWithLevel.apply("<enchant>", <level>))
        .weight(<w>)
plus optional EmptyEntry.builder().weight(N) fillers.

One special case: `abandoned_mineshaft` uses `.apply(DepthBasedEfficiencyBuilder.INSTANCE)` for a
depth-computed Efficiency book. We emit it as an entry with a "special" tag so the Java loot
injector can honour it (or fall back to plain Efficiency I if the special path is disabled).
"""
import re, json, sys, os

SRC = "src/main/java/chiseled_enchanting_table/modifyChestLootTable/ReworkEnchantedBookChestLoot.java"
DEST = "src/main/resources/data/chiseled_enchanting_table/chiseled_enchanting_table/loot_injections.json"

with open(SRC, encoding="utf-8") as fh:
    text = fh.read()

# The archaeology special case at the top.
arch_block = re.search(
    r'if\s*\(id\.equals\("([^"]+)"\)\)\s*\{(.*?)\}\s*\n\s*\n?\s*if\s*\(!id\.startsWith',
    text, re.DOTALL,
)

# The chestType-equals chain.
chest_blocks = re.findall(
    r'chestType\.equals\("([^"]+)"\)\s*\)\s*\{(.*?)(?=\}\s*else\s+if\s*\(\s*chestType\.equals|\}\s*\n\s*\}\s*\n\s*\})',
    text, re.DOTALL,
)


def parse_block(body: str):
    entries = []
    for m in re.finditer(
        r'(?:ItemEntry\.builder\(Items\.ENCHANTED_BOOK\)(?:.*?)\.weight\(\d+\))'
        r'|(?:EmptyEntry\.builder\(\)\s*\.weight\(\d+\))',
        body, re.DOTALL,
    ):
        chunk = m.group(0)
        if 'EmptyEntry' in chunk:
            w = int(re.search(r'\.weight\((\d+)\)', chunk).group(1))
            entries.append({"empty": True, "weight": w})
            continue
        depth = 'DepthBasedEfficiencyBuilder.INSTANCE' in chunk
        w = int(re.search(r'\.weight\((\d+)\)', chunk).group(1))
        if depth:
            entries.append({
                "enchantment": "minecraft:efficiency",
                "level": 1,
                "weight": w,
                "special": "depth_based_efficiency",
            })
            continue
        ench_m = re.search(r'\.apply\(enchantWithLevel\.apply\("([^"]+)",\s*(\d+)\)\)', chunk)
        if not ench_m:
            continue
        ench, lvl = ench_m.group(1), int(ench_m.group(2))
        if ":" not in ench:
            ench = "minecraft:" + ench
        entries.append({"enchantment": ench, "level": lvl, "weight": w})
    return entries


existing_doc = {}
if os.path.exists(DEST):
    try:
        with open(DEST, encoding="utf-8") as prev_fh:
            existing_doc = json.load(prev_fh)
    except Exception:
        existing_doc = {}

out = {
    "_comment": existing_doc.get(
        "_comment",
        "Enchanted-book & Depleted Tome injections into vanilla/modded chest and archaeology loot tables.",
    ),
    "_schema_version": existing_doc.get("_schema_version", 2),
    "vanilla_chest_books": existing_doc.get("vanilla_chest_books", "keep_both"),
}
if "depleted_tome_chest_spawns" in existing_doc:
    out["depleted_tome_chest_spawns"] = existing_doc["depleted_tome_chest_spawns"]
out["entries"] = []

if arch_block:
    entries = parse_block(arch_block.group(2))
    if entries:
        out["entries"].append({
            "table": arch_block.group(1),
            "rolls": 1,
            "entries": entries,
        })

# Upstream still uses the pre-1.17 flat path "chests/village_armorer"; 1.17+ moved every villager
# workstation to the "village/village_<job>" sub-path. Normalise here so the exported JSON hits
# the actual 26.1.2 loot table ids.
UPSTREAM_ID_FIXES = {
    "village_armorer": "village/village_armorer",
}

for chest_type, body in chest_blocks:
    entries = parse_block(body)
    if not entries:
        continue
    normalised = UPSTREAM_ID_FIXES.get(chest_type, chest_type)
    out["entries"].append({
        "table": f"minecraft:chests/{normalised}",
        "rolls": 1,
        "entries": entries,
    })

os.makedirs(os.path.dirname(DEST), exist_ok=True)
with open(DEST, "w", encoding="utf-8") as fh:
    json.dump(out, fh, ensure_ascii=False, indent=2)
    fh.write("\n")

print(f"Wrote {DEST}")
print(f"  {len(out['entries'])} injection targets, "
      f"{sum(len(e['entries']) for e in out['entries'])} sub-entries total")
for e in out["entries"]:
    non_empty = [se for se in e["entries"] if not se.get("empty")]
    print(f"    {e['table']}: {len(e['entries'])} slots ({len(non_empty)} enchanted-book entries)")

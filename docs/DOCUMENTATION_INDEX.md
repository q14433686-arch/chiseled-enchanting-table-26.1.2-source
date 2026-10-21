# Documentation Index

> **Unofficial community port.** All documents indexed below describe the unofficial 26.2 port
> in this repository. It is not affiliated with or endorsed by the original author of
> [`chiseled-enchanting-table`](https://github.com/Sacramentix/chiseled-enchanting-table).
> Current version: **`1.0.2`** (Minecraft 26.2).

## Read these first

Roughly in the order a new contributor should walk them:

1. [`../README.md`](../README.md) — project one-pager: what the mod is, install, credits.
2. [`../CURRENT_BASELINE.md`](../CURRENT_BASELINE.md) — canonical source of truth for
   *currently-shipping* capabilities, build config, package layout, and active temporary
   boundaries. Update this whenever the code / data / build changes.
3. [`EXTENDING.md`](EXTENDING.md) — every data-driven extension point (all 5 JSON files under
   `data/<ns>/chiseled_enchanting_table/`), their schemas, their Java entry points, and how to
   add a new one. Also documents the singular-directory 26.x data-pack trap.
4. [`../NEXT_TASK.md`](../NEXT_TASK.md) — the open backlog. Every completed live-verification
   round graduates out of NEXT_TASK; only the still-open items stay.
5. [`COMPATIBILITY.md`](COMPATIBILITY.md) — cross-mod compatibility investigation for
   third-party enchantment / anvil mods (Ore Enchants-style, Universal Enchants-style, Merge
   Enchantments-style). Explains why the mod is designed the way it is.

## Release notes

- [`../CHANGELOG.md`](../CHANGELOG.md) — currently-shipping release and one previous release.
- [`CHANGELOG_HISTORY.md`](CHANGELOG_HISTORY.md) — archive of older internal porting notes, including the P1-P19 internal porting rounds (`0.2.3+26.1.2-pNN` labels).

## Tooling / scripts

- `scripts/compat_check.py` — offline data-pack sanity + JSON schema validators (Tier 1) +
  best-effort id existence (Tier 2). **This is the canonical schema definition** for every
  file under `data/<ns>/chiseled_enchanting_table/` — the `check_*_well_formed()` functions
  are the ground truth; the prose in `EXTENDING.md` documents them.
- `scripts/import_upstream_loot.py` — regenerates
  `data/<ns>/chiseled_enchanting_table/loot_injections.json` from upstream
  `ReworkEnchantedBookChestLoot.java`. Kept in-repo so the 196-entry loot table is auditable
  and re-derivable.

## Distribution / listing copy

Used when publishing an updated release; keeps every platform aligned.

- [`marketing/README.md`](marketing/README.md) — index + universal "unofficial port" disclaimer.
- [`marketing/modrinth.md`](marketing/modrinth.md) — Modrinth summary + full description.
- [`marketing/curseforge.md`](marketing/curseforge.md) — CurseForge summary + full description.
- [`marketing/mcmod.md`](marketing/mcmod.md) — MC百科 (mcmod.cn) 中文简介与介绍正文.

## Minecraft-line & feature reports

- [26.1.2 → 26.2 impact assessment & port report](reports/PORTING_26_2_REPORT.md)
- [Worldgen bookshelf fill deadlock fix (thread rules for `finalizeProcessing`)](reports/WORLDGEN_BOOKSHELF_DEADLOCK_REPORT.md)
- [Recipe-viewer integration: JEI and REI + S2C cost sync](reports/VIEWER_INTEGRATION_JEI_REI.md)
- [CI triad (reference copies + drift check)](ci/README.md)

## Historical porting reports

These document how the port was built up before the current `1.0.2` release line. They previously
corresponded to `0.2.3+26.1.2-pNN` internal `mod_version` values; those labels are kept inside the
report bodies as a historical record only, not as live version numbers.

**If you are trying to understand *current* behaviour, read `CURRENT_BASELINE.md` +
`EXTENDING.md` first**, not these. These are useful mostly for reconstructing "why is this
class shaped this way".

- [P1 Build-system migration](reports/PORTING_26_1_2_P1_REPORT.md)
- [P2 Core server compile slice](reports/PORTING_26_1_2_P2_REPORT.md)
- [P3 Client compile slice and test jar](reports/PORTING_26_1_2_P3_REPORT.md)
- [P5/P6 Runtime smoke boundary and data-driven mechanics](reports/PORTING_26_1_2_P5_P6_REPORT.md)
- [P7 User feedback fixes](reports/PORTING_26_1_2_P7_FIX_REPORT.md)
- [P8 Scroll and chiseled bookshelf level fixes](reports/PORTING_26_1_2_P8_FIX_REPORT.md)
- [P9 Data-driven balance pass](reports/PORTING_26_1_2_P9_DATA_DRIVEN_BALANCE_REPORT.md)
- [P10 balance rework](reports/PORTING_26_1_2_P10_BALANCE_REWORK_REPORT.md)
- [P11 enchantment coverage and recipe balance](reports/PORTING_26_1_2_P11_ENCHANTMENT_TABLE_COVERAGE_REPORT.md)
- [P12 multi-item costs](reports/PORTING_26_1_2_P12_MULTI_ITEM_COST_REPORT.md)
- [P13 item transformations and extensibility review](reports/PORTING_26_1_2_P13_ITEM_TRANSFORMATION_REPORT.md)
- [P14 transformation fix and payload decoupling](reports/PORTING_26_1_2_P14_TRANSFORMATION_FIX_AND_PAYLOAD_REPORT.md)
- [P15 package/UI decoupling](reports/PORTING_26_1_2_P15_DECOUPLING_REPORT.md)
- [P16 loot fix and compat tooling](reports/PORTING_26_1_2_P16_LOOT_FIX_AND_COMPAT_TOOLING_REPORT.md)
- [P17 retire legacy block package](reports/PORTING_26_1_2_P17_BLOCK_PACKAGE_REPORT.md)
- [P18 typed available-option model](reports/PORTING_26_1_2_P18_AVAILABLE_OPTION_MODEL_REPORT.md)
- [P19 cross-mod enchantment compatibility](reports/PORTING_26_1_2_P19_CROSS_MOD_COMPATIBILITY_REPORT.md)
- [Agent Execution Brief](reports/CURRENT_AGENT_BRIEF.md)

*(Note: `JSON_EXTENSION_SPEC_1_1_3.md` and `EXTENDING_HERBCRAFT.md` are not natively part of
this specific mod's repository and have been omitted; `EXTENDING.md` / `COMPATIBILITY.md`
above are this mod's equivalents.)*

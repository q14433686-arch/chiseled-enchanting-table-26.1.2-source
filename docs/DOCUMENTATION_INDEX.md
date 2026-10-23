# Documentation Index

> **Unofficial community port.** All documents indexed below describe the unofficial 26.3 port
> in this repository. It is not affiliated with or endorsed by the original author of
> [`chiseled-enchanting-table`](https://github.com/Sacramentix/chiseled-enchanting-table).
> Current version: **`1.1.0`** (Minecraft 26.3). Shipped gameplay is synchronized through source `3e081f3`; the architecture-first port through `3e61f5d` is in progress, not yet fully integrated.
> [Latest cross-branch audit and progress](reports/CROSS_BRANCH_3E61F5D_ARCHITECTURE_REVIEW.md).

## Read these first

Roughly in the order a new contributor should walk them:

1. [`../README.md`](../README.md) — project one-pager: what the mod is, install, credits.
2. [`../CURRENT_BASELINE.md`](../CURRENT_BASELINE.md) — canonical source of truth for
   *currently-shipping* capabilities, build config, package layout, and active temporary
   boundaries. Update this whenever the code / data / build changes.
3. [`EXTENDING.md`](EXTENDING.md) — every data-driven extension point (all 6 JSON files under
   `data/<ns>/chiseled_enchanting_table/`), their schemas, their Java entry points, and how to
   add a new one. Also documents the singular-directory 26.x data-pack trap and the
   worldgen-worker vs server-thread split in `BookshelfBookFiller`.
4. [`../NEXT_TASK.md`](../NEXT_TASK.md) — the open backlog. Every completed live-verification
   round graduates out of NEXT_TASK; only the still-open items stay.
5. [`ci/README.md`](ci/README.md) — the CI triad: what each workflow gates, why `docs/ci/`
   keeps byte-identical reference copies, and how to read a compile result from a sandbox with
   no maven access.

   *(The cross-mod compatibility write-up that older revisions of this index linked as
   `COMPATIBILITY.md` — third-party enchantment / anvil mods, Ore Enchants-style, Universal
   Enchants-style, Merge Enchantments-style — is **not present in this snapshot** on any branch
   of the repository; `scripts/ci_static_checks.py` reports the reference as a dangling link
   (warn-only) until the document is restored. The design conclusions it described are still
   live in the code: tag-driven default unlocks in `default_enchantments.json`, see
   [`EXTENDING.md`](EXTENDING.md) §1.)*

## Release notes

- [`../CHANGELOG.md`](../CHANGELOG.md) — currently-shipping release and one previous release.
- [`CHANGELOG_HISTORY.md`](CHANGELOG_HISTORY.md) — archive of older internal porting notes, including the P1-P19 internal porting rounds (`0.2.3+26.3-pNN` labels).

## Tooling / scripts

- `scripts/compat_check.py` — offline data-pack sanity + JSON schema validators (Tier 1) +
  best-effort id existence (Tier 2). **This is the canonical schema definition** for every
  file under `data/<ns>/chiseled_enchanting_table/` — the `check_*_well_formed()` functions
  are the ground truth; the prose in `EXTENDING.md` documents them. Since `1.0.3` it also
  gates the **26.3** loot-table and advancement schemas, i.e. it fails on 26.3-shaped data
  files (the inverse of the 26.3 branch's gate — see the sync report §4.4).
- `scripts/ci_static_checks.py` — cheap pre-Gradle gates: mixin configs resolve to real
  sources, a mixin registered in `fabric.mod.json` is never excluded by `build.gradle`,
  every `lang/*.json` has exactly the `en_us` key set, and (warn-only) relative markdown
  links resolve.
- `scripts/check_release_consistency.sh` — the five-place version gate
  (`gradle.properties` / `README.md` / `fabric.mod.json` / `publish.yml` / `docs/ci/publish.yml`).
- `scripts/install-hooks.sh` + `.githooks/pre-commit` — warn-only local reminder that runs the
  two scripts above when a version or data file is staged. Never blocks a commit.
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

## Current-round reports (1.0.3)

Read these before touching the structure/worldgen code, the recipe-viewer adapters, or CI.

- [**Sync from the 26.3 branch** — what was back-ported, what was adapted, and what was
  deliberately left behind because it is 26.3-only](reports/SYNC_FROM_26_1_2_REPORT.md)
- [Worldgen bookshelf fill deadlock](reports/WORLDGEN_BOOKSHELF_DEADLOCK_REPORT.md) — the
  integrated-server freeze fix ([issue #3](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/issues/3))
  and the threading rules for `finalizeProcessing` / `BookshelfBookFiller`
- [JEI + REI integration](reports/VIEWER_INTEGRATION_JEI_REI.md) — the shared query index, the
  server-authoritative cost sync, and the verified viewer API surface for the 26.1 line
- [CI triad](ci/README.md) — installed workflows, reference copies, drift check

The equivalent documents on the `26.3` branch (`reports/PORTING_26_3_REPORT.md`,
`reports/PORTING_26_2_REPORT.md`, `patches/README.md`) describe *other* Minecraft lines and are
deliberately not copied here; read them on that branch.

## Historical porting reports

These document how the port was built up before the current `1.0.3` release line. They previously
corresponded to `0.2.3+26.3-pNN` internal `mod_version` values; those labels are kept inside the
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
- P19 cross-mod enchantment compatibility — *report file not present in this snapshot*
  (`reports/PORTING_26_1_2_P19_CROSS_MOD_COMPATIBILITY_REPORT.md`); the round is summarised in
  [`CHANGELOG_HISTORY.md`](CHANGELOG_HISTORY.md)
- [Agent Execution Brief](reports/CURRENT_AGENT_BRIEF.md)

*(Note: `JSON_EXTENSION_SPEC_1_1_3.md` and `EXTENDING_HERBCRAFT.md` are not natively part of
this specific mod's repository and have been omitted; `EXTENDING.md` above is this mod's
equivalent.)*

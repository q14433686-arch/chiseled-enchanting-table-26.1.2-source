# Documentation Index

> **Unofficial community port.** All documents indexed below describe the unofficial `26.1.2` port
> in this repository. It is not affiliated with or endorsed by the original author of
> [`chiseled-enchanting-table`](https://github.com/Sacramentix/chiseled-enchanting-table).
> Current version: **`1.1.0`** (Minecraft `26.1.2`, wire protocol `26_1_2_revision_17`).

## Read these first

Roughly in the order a new contributor or modpack author should walk them:

1. [`../README.md`](../README.md) — project one-pager: concept summary, install, CI overview, credits.
2. [`WIKI.md`](WIKI.md) — comprehensive gameplay guide, mathematical formulas, 80-option configuration & Rule Trace reference, villager/loot mechanics, and upstream comparison table.
3. [`../CURRENT_BASELINE.md`](../CURRENT_BASELINE.md) — canonical source of truth for currently-shipping capabilities, build config, package layout, and active boundaries.
4. [`GLOSSARY.md`](GLOSSARY.md) — canonical domain and localization terminology (`en_us` / `zh_cn` / `zh_tw` / Java & datapack IDs) across all 11 shipped languages.
5. [`EXTENDING.md`](EXTENDING.md) — data-driven extension points (`data/<ns>/chiseled_enchanting_table/*.json` and enchantment/block tags), schemas, Java entry points, and the singular-directory `26.x` data-pack layout.
6. [`PROTOCOL_HISTORY.md`](PROTOCOL_HISTORY.md) — network wire revision ledger (`revision_1` through `26_1_2_revision_17`), `PayloadLimits` wire caps, per-UUID sync caching, and `/reload` container safety.
7. [`MIXIN_UPGRADE_CHECKLIST.md`](MIXIN_UPGRADE_CHECKLIST.md) — inventory of all 13 active mixins, config guards, optional client Iris guard, and Minecraft `26.x` upgrade smoke matrix.
8. [`../NEXT_TASK.md`](../NEXT_TASK.md) — post-`1.1.0` live-verification checklist and open cross-branch backlog.
9. [`ci/README.md`](ci/README.md) — the CI triad: what each workflow gates, why `docs/ci/` keeps byte-identical reference copies, and how to inspect `build-reports/compile-java.log`.

## Release notes & historical archives

- [`../CHANGELOG.md`](../CHANGELOG.md) — currently-shipping release (`1.1.0`) and previous release (`1.0.3`).
- [`CHANGELOG_HISTORY.md`](CHANGELOG_HISTORY.md) — archive of older internal porting notes (`0.2.3+26.1.2-pNN` rounds).
- [`upstream/TODO_ARCHIVE.md`](upstream/TODO_ARCHIVE.md) — archived historical `TODO.md` scratchpad from Sacramentix's original upstream repository.

## Tooling / scripts

- `scripts/compat_check.py` — offline data-pack sanity + JSON schema validators (Tier 1) + best-effort id existence (Tier 2), including `26.1.2` loot-table/advancement schema gates and Java collaborator invariants.
- `scripts/ci_static_checks.py` — pre-Gradle static gates: mixin config resolution and non-exclusion, 11-locale key parity, full `TranslatableContents` positional/sequential placeholder parity, BMP-safe glyph verification, untranslated-prose guard, `Locale.ROOT` formatting, markdown link verification, and `BundledDefaults.java` drift check.
- `scripts/gen_bundled_defaults.py` — generates `BundledDefaults.java` directly from shipped datapack JSON files (`--check` verifies zero drift).
- `scripts/check_release_consistency.sh` — the five-place version gate (`gradle.properties` / `README.md` / `fabric.mod.json` / `publish.yml` / `docs/ci/publish.yml`).
- `scripts/install-hooks.sh` + `.githooks/pre-commit` — warn-only local pre-commit reminder.
- `scripts/import_upstream_loot.py` — regenerates `loot_injections.json` (including `depleted_tome_chest_spawns` chest injection specs) deterministically.

## Distribution / listing copy

Used when publishing an updated release; keeps every platform aligned.

- [`marketing/README.md`](marketing/README.md) — index + universal "unofficial port" disclaimer.
- [`marketing/modrinth.md`](marketing/modrinth.md) — Modrinth summary + full description.
- [`marketing/curseforge.md`](marketing/curseforge.md) — CurseForge summary + full description.
- [`marketing/mcmod.md`](marketing/mcmod.md) — MC百科 (mcmod.cn) 中文简介与介绍正文.

## Architecture & sync reports

- [**26.3 → 26.1.2 回移植彻查报告 (2026-10-26)** — 七条审计路径逐文件差分分类、10 项缺陷回移植（含 2 项 26.1.2 独有）、母本目录功能移植与有意未移植清单](reports/BACKPORT_FROM_26_3_2026_10_26.md)
- [**Cross-branch porting & sync guide (`26.1.2` `revision_17` → `26.2` & `26.3`, 2026-10-26)** — Wave A–D delta breakdown, drop-in manifest, 3-version API/JSON difference matrix, and CI acceptance checklist](reports/SYNC_TO_26_2_AND_26_3_2026_10_26.md)
- [Sync from the 26.3 branch (1.1.0 runtime & architecture hardening)](reports/SYNC_FROM_26_3_ARCHITECTURE_BACKPORT.md)
- [Sync instruction from the 26.3 branch (2026-10-24)](reports/SYNC_TO_26_1_2_2026_10_24.md)
- [Design-audit max-package backport (2026-10-24)](reports/SYNC_FROM_26_3_DESIGN_AUDIT_BACKPORT_2026_10_24.md)
- [Tome page, page unlocks & tome locks (2026-10-24)](reports/TOME_PAGE_LOCKS_2026_10_24.md)
- [Sync from the 26.3 branch (1.0.3 initial sync)](reports/SYNC_FROM_26_3_REPORT.md)
- [Worldgen bookshelf fill deadlock](reports/WORLDGEN_BOOKSHELF_DEADLOCK_REPORT.md)
- [JEI + REI integration](reports/VIEWER_INTEGRATION_JEI_REI.md)
- [CI triad](ci/README.md)

## Historical porting reports (`P1` – `P18`)

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
- [Agent Execution Brief](reports/CURRENT_AGENT_BRIEF.md)

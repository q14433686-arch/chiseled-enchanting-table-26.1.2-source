# Chiseled Enchanting Table — Unofficial 26.1.2 Port

![Chiseled enchanting table banner](./banner.png)

> ⚠️ **Unofficial community port.** This repository is a community-maintained, unofficial port of
> [Sacramentix/chiseled-enchanting-table](https://github.com/Sacramentix/chiseled-enchanting-table)
> updated to **Minecraft 26.1.2** on Fabric. It is **not affiliated with, endorsed by, or
> maintained by the original author**. All credit for the original mod design, code, and assets
> belongs to Sacramentix and is used under the original [MIT License](LICENSE). Please direct
> bug reports about this port to **this** repository (`q14433686-arch/chiseled-enchanting-table-26.1.2-source`),
> not to the upstream project.

**Current version:** `1.1.0` *(unofficial-port SemVer; see `CURRENT_BASELINE.md` for details)*
**Target:** Minecraft `26.1.2`, Fabric Loader `>=0.19.3`, Fabric API `0.151.0+26.1.2`, Java `>=25`.

📖 **Full Gameplay Wiki & Upstream Comparison (中文机制详解与上游差异完整对照): [`docs/WIKI.md`](docs/WIKI.md)**

`1.1.0` builds on the `1.0.3` 26.3-sync baseline ([issue #3](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/issues/3)
worldgen deadlock fix, S2C enchantment costs, 11 languages, and CI triad — see
[`docs/reports/SYNC_FROM_26_3_REPORT.md`](docs/reports/SYNC_FROM_26_3_REPORT.md)) with a comprehensive
progression, balance, UI/UX, and recipe-viewer overhaul; user-visible changes are in
[`CHANGELOG.md`](CHANGELOG.md) and [`docs/WIKI.md`](docs/WIKI.md).

A Minecraft Fabric mod that enhances the enchanting experience.

## Concept

* **Smooth entry, Vanilla Upgrade & Illustrated In-Game Manual (`纂刻秘典` / `Chiseled Grimoire`)**: craft the table directly with `2 Diamonds + 1 Chiseled Bookshelf + 3 Crying Obsidian + 1 Book`, upgrade a vanilla Enchanting Table, craft `8× Crying Obsidian` from `8× Obsidian + 1× Ghast Tear`, and craft the full interactive illustrated manual via **`Book and Quill + Chiseled Bookshelf`** OR **`Depleted Tome + Ink Sac`** (or open it directly from the button above the table UI).
* **Three-track library progression, Live World Sync & Conservation-Verified Dual-Mode XP Formula (see [`docs/WIKI.md`](docs/WIKI.md))**:
  * **Regular Bookshelves (`#chiseled_enchanting_table:regular_bookshelves`, configurable `7×5×7`)** unlock blank inscriptions up to mid-tier (`ceil(maxLevel / 2)`), smoothly scale upgrades on already-inscribed gear/books up to `MAX` along `0..38` shelves (`gamma = 1.25`: `I(0) → II(7) → III(16) → IV(27) → V(38)`), grant XP reduction starting at `12+` shelves, and at **`32+` Bookshelves** awaken **Treasure Epiphany**.
  * **Chiseled Bookshelves (`7×5×7`)** turn stored Enchanted Books into permanent **Master Tomes** unlocking tiers `1..k` directly on blank items (including Treasures), counting deduplicated qualifying enchantments toward free Depleted Tome restoration, adding global & per-enchantment XP discounts (data-driven via `library_resonance.json`, up to **`-40%`**), and switching inscriptions into **Vanilla Mass-Production XP Mode (`1–3` levels deducted)**.
  * **Inductive XP Conservation & Standalone-Vanilla Cleanse Alternatives**: full wipes (`DISENCHANT_ALL`) and step-downs (`DOWNGRADE_ENCHANTMENT`) satisfy exact telescoping conservation (`V[k] - V[j] <= Cost_min(j -> k)` across all `maxLevel = 1..255`), while targeted trait stripping and curse purging support both custom catalysts (`Cleansing Grit`, `Arcane Synthesis Crystal`) and pure standalone vanilla item bundles (`disenchanting.json`).
  * **Bidirectional JEI & REI Support**: full recipe & usage lookup for both **Chiseled Enchanting** and **Transmutation & Cleansing** (`Enchanted Golden Apple`, `Trident`, `Totem of Undying`, `Echo Shard`, `Bottle o' Enchanting`, `Blank Overwrite`, `Cleansing Grit`, and Cleanse / Curse-Purge rituals).

## Installation

Grab the latest `chiseled-enchanting-table-1.1.0.jar` from the `build` workflow's artifact on the
branch you want (Actions → *build* → **Artifacts**, kept 14 days) or from
`release_output/chiseled_enchanting_table_1_1_0/` when a release is staged there, and drop it into
your Fabric `mods/` folder alongside Fabric API `0.151.0+26.1.2`.

To build it yourself: JDK 25 + network access to `maven.fabricmc.net` (the dev sandbox this port is
maintained in has neither, which is why CI does the compiling — see below):

```bash
chmod +x ./gradlew
python3 scripts/compat_check.py          # offline data-pack sanity, needs no JDK
./gradlew build                          # jars land in build/libs/
```

### Optional: JEI / REI

Neither viewer is required, bundled, or a runtime dependency — the mod ships one client-side adapter
for each and only loads it when the viewer is installed. Both are compile-only and can be switched
off (`-Penable_jei=false` / `-Penable_rei=false`, or the same keys in `gradle.properties`), which is
what CI's `no-viewers` job verifies on every push. With either viewer installed you get a
**Chiseled Enchanting** category that answers both directions of the mechanic: focus an enchanted
book to see every item it can enchant, focus an item to see every book that works on it, with the XP
cost, the material cost and the conflicts list on the row. Costs shown there are the ones *your
server* resolved for *you* (they are synced on join and after every `/reload`).

This unofficial port is **not** currently published to Modrinth or CurseForge under the original
mod's listing. If a release is published, it will be under the unofficial-port repository's own
release page, not the upstream listing.

## Usage

Once installed, the Chiseled Enchanting Table will be available in your game. Craft it using the new recipe and start enchanting!

In-game text ships in eleven languages: `de_de`, `en_us`, `es_es`, `fr_fr`, `it_it`, `ja_jp`,
`ko_kr`, `pt_br`, `ru_ru`, `zh_cn`, `zh_tw` (missing keys fall back to English per key, as in
vanilla). The lore line stamped on generated enchanted books is translatable too, so a book produced
by a dedicated server reads in each player's own language.

## Continuous integration

CI mirrors the triad used by the sister port
[`q14433686-arch/TaCZ_Refabricated_Unofficial`](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial)
and by this repository's own `26.3` branch, because this port is developed in a sandbox that cannot
reach `maven.fabricmc.net` (nor the JEI/REI mavens) and therefore cannot compile locally — the
compile has to happen on Actions and the log has to come back to the branch.

**Status: green.** On commit `da77188` (2026-10-21): `compile-check` run `39670181582`
(`BUILD SUCCESSFUL in 48s`, `compileJava` + `compileClientJava` with JEI and REI enabled, 2
deprecation warnings from JEI's API), `build.yml` run `39670181557` (static gates → full build + jar
artifact → `no-viewers`), `consistency.yml` run `39670181572`. `docs/ci/README.md` records the
history, including the two failed runs and what each one taught us.

| Workflow | What it does | Trigger |
|---|---|---|
| [`compile-check.yml`](.github/workflows/compile-check.yml) | `compileJava compileClientJava`, then **pushes the compile log back to `arena/**` branches** as `build-reports/compile-java.log` via the Contents API (Actions log blobs are unreachable from the sandbox). The log also records which artifact versions the floating viewer coordinates resolved to, and a failure additionally commits back `build-reports/compile-deps.log` with the dependency graph | push to `arena/**` / this branch, PRs |
| [`build.yml`](.github/workflows/build.yml) | static gates first (no network needed) → full `./gradlew build` + jar artifact (14 days), plus a **`no-viewers`** job that compiles with `-Penable_jei=false -Penable_rei=false` | push / PR |
| [`consistency.yml`](.github/workflows/consistency.yml) | version gate for "bumped one place, forgot the other four", plus a warn-only `docs/ci` drift check | changes to `gradle.properties`, `README.md`, `fabric.mod.json`, `publish.yml`, `docs/ci/publish.yml` |
| [`publish.yml`](.github/workflows/publish.yml) | manual release to GitHub / Modrinth (`MINECRAFT_VERSION: 26.1.2`, `VERSION: 1.1.0`) | `workflow_dispatch` only |

`docs/ci/` keeps byte-identical reference copies of all four, because a GitHub App token without
`workflows: write` cannot create or update workflow files; `bash docs/ci/install.sh --check` reports
drift and `bash docs/ci/install.sh` re-applies them. Details: [`docs/ci/README.md`](docs/ci/README.md).

The three static gates run in seconds and need no Gradle, no JDK and no network, so they also work
offline (and are what a contributor should run before pushing):

```bash
python3 scripts/ci_static_checks.py                 # mixin configs resolvable + registered mixins not excluded + lang parity across all 11 languages
python3 scripts/compat_check.py                     # data-pack schema / JSON-vs-Java drift (Tier 1) — including the 26.1.2 loot/advancement schema gates
bash scripts/check_release_consistency.sh --strict  # gradle.properties / README / fabric.mod.json / publish.yml agree
bash docs/ci/install.sh --check                     # docs/ci ≡ .github/workflows
```

Optional warn-only pre-commit reminder: `bash scripts/install-hooks.sh` (it never blocks a commit;
CI is the real gate).

## Forge (or other modloader) port

Not planned at the moment.
But if the mod get more popular, I might have a look at it!

## Contributing

Contributions are welcome! Please feel free to submit a pull request or open an issue.

## License

This project is licensed under the [MIT License](LICENSE).

## Inspiration

I've looked at RPG enchanting table to get the base concept done
https://github.com/TheRedBrain/rpg-enchanting
If you are looking for a more RPG approch be sure to check out this mod!

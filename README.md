# Chiseled Enchanting Table — Unofficial 26.2 Port

![Chiseled enchanting table banner](./banner.png)

> ⚠️ **Unofficial community port.** This repository is a community-maintained, unofficial port of
> [Sacramentix/chiseled-enchanting-table](https://github.com/Sacramentix/chiseled-enchanting-table)
> updated to **Minecraft 26.2** on Fabric. It is **not affiliated with, endorsed by, or
> maintained by the original author**. All credit for the original mod design, code, and assets
> belongs to Sacramentix and is used under the original [MIT License](LICENSE). Please direct
> bug reports about this port to **this** repository (`q14433686-arch/chiseled-enchanting-table-26.1.2-source`),
> not to the upstream project.

**Current version:** `1.1.0` *(unofficial-port SemVer; see `CURRENT_BASELINE.md` for details)*
**Target:** Minecraft `26.2`, Fabric Loader `>=0.19.3`, Fabric API `0.155.2+26.2`, Java `>=25`.

📖 **Full Gameplay Wiki & Upstream Comparison (中文机制详解与上游差异完整对照): [`docs/WIKI.md`](docs/WIKI.md)**

A Minecraft Fabric mod that enhances the enchanting experience.

## Concept

* Add a new chiseled enchanting table
* Unlock new enchantment for chiseled enchanting table by placing enchanted book in nearby chiseled bookshelves
* Explore your world to discover new enchanted books!

## What 1.1.0 ships on this line

`1.1.0` is the same feature set as the sister `26.1.2` and `26.3` branches, adapted to this
Minecraft line. On this `26.2` branch it combines two syncs:

1. the `26.3` back-port that landed on 2026-10-21 — worldgen bookshelf deadlock fix
   ([issue #3](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/issues/3)),
   server-authoritative enchantment costs, optional JEI + REI integration, 11 languages, CI triad;
2. the `26.1.2`-line progression/economy/UI overhaul that landed on 2026-10-22 — two-track library
   progression, Library Resonance, the dual-mode XP formula, yield-aware material curves, Treasure
   Epiphany, the Depleted Tome loop, item transformations in both viewers, and the painted UI.

What was taken, what was adapted and what was deliberately left out is recorded in
[`docs/reports/SYNC_FROM_26_1_2_SIDE_REPORT.md`](docs/reports/SYNC_FROM_26_1_2_SIDE_REPORT.md)
(the 2026-10-22 sync) and in [`docs/reports/PORTING_26_2_REPORT.md`](docs/reports/PORTING_26_2_REPORT.md)
§3.4 (the 26.3 back-port and the 26.2 port itself); user-visible changes are in
[`CHANGELOG.md`](CHANGELOG.md).

Highlights:

* **Smooth entry & vanilla upgrade**: craft directly with `2 Diamonds + 1 Book + 1 Chiseled
  Bookshelf + 3 Crying Obsidian`, upgrade a vanilla Enchanting Table (`1 Enchanting Table + 1
  Chiseled Bookshelf + 4 Amethyst Shard + 3 Crying Obsidian`), or craft `8× Crying Obsidian` from
  `8× Obsidian + 1× Ghast Tear`.
* **Two-track library progression & dual-mode XP formula** (full tables in [`docs/WIKI.md`](docs/WIKI.md)):
  * **Wooden Bookshelves** inside the `7×5×7` volume raise standard enchantments up to
    `ceil(maxLevel / 2)`, start granting XP discounts at `12+` shelves, and at **`32+` shelves**
    unlock **Treasure Epiphany** (countdown, persistent cryo-stasis freeze, manual reroll).
  * **Chiseled Bookshelves** turn stored enchanted books into permanent **Master Tomes**, unlocking
    levels `1..k` directly, adding global & per-enchantment XP discounts (up to `-40%`), and
    switching Master-Tome inscribing into **vanilla mass-production mode** (`1–3` levels deducted)
    while item transformations, base enchanting, upgrades and epiphanies keep the full XP curve.
  * **Yield-aware material curves** (`Lapis Lazuli > Emerald >> Amethyst Shard`) that adapt to any
    modded `maxLevel` through `base_count` / `max_level_count` / `level_exponent`.
* **Bidirectional JEI & REI support**: full recipe/usage lookup for both **Chiseled Enchanting**
  and **Item Transformations** (`Enchanted Golden Apple`, `Trident`, `Totem of Undying`, `Echo
  Shard`, `Bottle o' Enchanting`, `Blank Overwrite`).

## Installation

Grab the latest `chiseled-enchanting-table-1.1.0.jar` from the `build` workflow's artifact on the
branch you want (Actions → *build* → **Artifacts**, kept 14 days) or from
`release_output/chiseled_enchanting_table_1_1_0/` when a release is staged there, and drop it into
your Fabric `mods/` folder alongside Fabric API `0.155.2+26.2`.

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
what CI's `no-viewers` job verifies on every push. With either viewer installed you get two
categories — **Chiseled Enchanting** and **Chiseled Item Transformation** — that answer both
directions of each mechanic: focus an enchanted book to see every item it can enchant, focus an item
to see every book that works on it (and the same for transformations), with XP tier, material
costs, secondary materials and conflicts on the row. Costs shown there are the ones *your server*
resolved for *you* (they are synced on join and after every `/reload`).

On this line the viewers are pinned to `mezz.jei:jei-26.2-fabric:30.47.0.239` and
`me.shedaniel:RoughlyEnoughItems-api-fabric:26.2.821` (plus compile-only cloth `0.6.1` and
architectury `21.0.4`), all with `toolchainPins` exclusions so viewer POMs can never displace the
pinned Fabric API.

This unofficial port is **not** currently published to Modrinth or CurseForge under the original
mod's listing. If a release is published, it will be under the unofficial-port repository's own
release page, not the upstream listing.

## Usage

Once installed, the Chiseled Enchanting Table will be available in your game. Craft it using the new
recipe and start enchanting! Put the item to enchant in the top slot, the material in the bottom
slot, and click an entry in the list — no anvil-style "Too Expensive" penalty ever accumulates.

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

**Status: green** on `1.1.0` for Minecraft 26.2 — commit `9f2046c`, runs `39819932191`
(compile-check), `39819932163` (build: `static`, `gradle build`, `no-viewers`) and `39819931994`
(consistency); the feature-sync commit `902e947` was verified by runs `39818720349` /
`39818720326` / `39818720363`. The compiler log is committed back as
`build-reports/compile-java.log`. See `docs/ci/README.md` for the history.

| Workflow | What it does | Trigger |
|---|---|---|
| [`compile-check.yml`](.github/workflows/compile-check.yml) | `compileJava compileClientJava`, then **pushes the compile log back to `arena/**` branches** as `build-reports/compile-java.log` via the Contents API (Actions log blobs are unreachable from the sandbox). The log also records which artifact versions the floating viewer coordinates resolved to, and a failure additionally commits back `build-reports/compile-deps.log` with the dependency graph | push to `arena/**` / this branch, PRs |
| [`build.yml`](.github/workflows/build.yml) | static gates first (no network needed) → full `./gradlew build` + jar artifact (14 days), plus a **`no-viewers`** job that compiles with `-Penable_jei=false -Penable_rei=false` | push / PR |
| [`consistency.yml`](.github/workflows/consistency.yml) | version gate for "bumped one place, forgot the other four", plus a warn-only `docs/ci` drift check | changes to `gradle.properties`, `README.md`, `fabric.mod.json`, `publish.yml`, `docs/ci/publish.yml` |
| [`publish.yml`](.github/workflows/publish.yml) | manual release to GitHub / Modrinth (`MINECRAFT_VERSION: 26.2`, `VERSION: 1.1.0`) | `workflow_dispatch` only |

`docs/ci/` keeps byte-identical reference copies of all four, because a GitHub App token without
`workflows: write` cannot create or update workflow files; `bash docs/ci/install.sh --check` reports
drift and `bash docs/ci/install.sh` re-applies them. Details: [`docs/ci/README.md`](docs/ci/README.md).

The three static gates run in seconds and need no Gradle, no JDK and no network, so they also work
offline (and are what a contributor should run before pushing):

```bash
python3 scripts/ci_static_checks.py                 # mixin configs resolvable + registered mixins not excluded + lang parity across all 11 languages
python3 scripts/compat_check.py                     # data-pack schema / JSON-vs-Java drift (Tier 1) — including the 26.2 loot/advancement schema gates, book_upgrades.json and treasure_epiphany.json
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

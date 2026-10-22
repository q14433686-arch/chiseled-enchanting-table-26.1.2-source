# Chiseled Enchanting Table — Unofficial 26.1.2 Port

![Chiseled enchanting table banner](./banner.png)

> ⚠️ **Unofficial community port.** This repository is a community-maintained, unofficial port of
> [Sacramentix/chiseled-enchanting-table](https://github.com/Sacramentix/chiseled-enchanting-table)
> updated to **Minecraft 26.1.2** on Fabric. It is **not affiliated with, endorsed by, or
> maintained by the original author**. All credit for the original mod design, code, and assets
> belongs to Sacramentix and is used under the original [MIT License](LICENSE). Please direct
> bug reports about this port to **this** repository (`q14433686-arch/chiseled-enchanting-table-26.1.2-source`),
> not to the upstream project.

**Current version:** `1.0.3` *(unofficial-port SemVer; see `CURRENT_BASELINE.md` for details)*
**Target:** Minecraft `26.1.2`, Fabric Loader `>=0.19.3`, Fabric API `0.151.0+26.1.2`, Java `>=25`.

`1.0.3` on this branch is the back-port of the sister `26.3` branch's fixes and features — the
worldgen bookshelf deadlock fix ([issue #3](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/issues/3)),
optional JEI + REI integration, server-authoritative enchantment costs, 11 languages and the CI
triad — adapted to the 26.1.2 API and data schemas. What was taken, what was adapted and what was
deliberately left on the 26.3 branch is recorded in
[`docs/reports/SYNC_FROM_26_3_REPORT.md`](docs/reports/SYNC_FROM_26_3_REPORT.md); user-visible
changes are in [`CHANGELOG.md`](CHANGELOG.md).

A Minecraft Fabric mod that enhances the enchanting experience.

## Concept

* **Smooth entry & vanilla upgrade**: craft directly with `2 Diamonds + 1 Chiseled Bookshelf + 3 Crying Obsidian + 1 Book`, or upgrade an existing vanilla Enchanting Table with `4 Amethyst Shards + 1 Chiseled Bookshelf + 3 Crying Obsidian`.
* **Two-track library progression, Grand Library Epiphany & Library Resonance (`-35% XP` cap)**:
  * **Wooden Bookshelves** smoothly boost standard table enchantments (`#minecraft:in_enchanting_table`) up to mid-tier (`ceil(maxLevel / 2)`), grant `-1% XP` per shelf (up to `-10%`), and at **32+ Wooden Bookshelves** awaken **Treasure Epiphany** (`35%–65%` chance to manifest 1 item-matched Treasure Enchantment, refreshable at Dawn, after enchanting, or via the `↻` UI button).
  * **Chiseled Bookshelves** turn every stored Enchanted Book into a permanent **Master Tome** unlocking tiers `1..k` directly and granting `-2% XP` per stored book (up to `-35%` combined discount).
  * **Arcane Synthesis Crystal (`arcane_catalyst`)**: an expensive craftable universal catalyst accepted across all Treasure Epiphanies, Treasure inscriptions, and Master-Tome Research upgrades alongside thematic biome materials!
* **Dual economy & full player choice**: daily gear inscribing stays fast and affordable with your choice of arcane materials (`Lapis Lazuli`, `Amethyst Shard`, `Emerald`, or thematic catalysts — zero per-player lock-in), while one-time Master-Tome upgrades let you either combine books for free or research higher tiers across 3 biome-diverse material routes per enchantment!

## Installation

Grab the latest `chiseled-enchanting-table-1.0.3.jar` from the `build` workflow's artifact on the
branch you want (Actions → *build* → **Artifacts**, kept 14 days) or from
`release_output/chiseled_enchanting_table_1_0_3/` when a release is staged there, and drop it into
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
| [`publish.yml`](.github/workflows/publish.yml) | manual release to GitHub / Modrinth (`MINECRAFT_VERSION: 26.1.2`, `VERSION: 1.0.3`) | `workflow_dispatch` only |

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

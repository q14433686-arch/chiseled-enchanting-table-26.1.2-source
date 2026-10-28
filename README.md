> **2026-10-28 移植说明：** 26.1.2 `abb2af1` 的 revision 19–22 功能正在移植到 26.2，协议为 `26_2_revision_8`；客户端和服务端须同时更新。验证状态以 CI 为准。

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

> The `26.3`-line increment through `3e61f5d` (PR #17/#18 — itself the full `26.1.2` gameplay delta) is
> ported to this `26.2` line with the same architecture-first approach: effective-rule snapshots,
> one shared shelf-recovery scheduler, persistent table inventories, bounded owner/session-scoped
> rituals, depleted-tome world/trade content and updated Grimoire/locales. See the
> [port report](docs/reports/SYNC_FROM_26_3_SIDE_REPORT.md) and the source branch's
> [architecture review](docs/reports/CROSS_BRANCH_3E61F5D_ARCHITECTURE_REVIEW.md).
> Protocol revision is **6** despite the unchanged `1.1.0` release label: update both client and server.
> **Back up existing worlds:** defaults now include mother-tome wear (16 charges, copying costs 4),
> reduced refunds for unmarked/external items, datapack-first configuration, and
> 24,000 / 12,000 / 3,600-second recovery tiers (38/42 shelves). Existing explicit settings are
> preserved. 52 JUnit tests and the real dedicated-server probes pass in CI; GUI/multiplayer
> interaction still requires manual validation.
**Target:** Minecraft `26.2`, Fabric Loader `>=0.19.3`, Fabric API `0.155.2+26.2`, Java `>=25`.

📖 **Full Gameplay Wiki & Upstream Comparison (中文机制详解与上游差异完整对照): [`docs/WIKI.md`](docs/WIKI.md)**

A Minecraft Fabric mod that enhances the enchanting experience.

## Concept

* Add a new chiseled enchanting table
* Unlock new enchantment for chiseled enchanting table by placing enchanted book in nearby chiseled bookshelves
* Explore your world to discover new enchanted books!

## What 1.1.0 ships on this line

`1.1.0` is the same feature set as the sister `26.1.2` and `26.3` branches, adapted to this
Minecraft line. On this `26.2` branch it combines five passes:

1. the `26.3` back-port that landed on 2026-10-21 — worldgen bookshelf deadlock fix
   ([issue #3](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/issues/3)),
   server-authoritative enchantment costs, optional JEI + REI integration, 11 languages, CI triad;
2. the `26.1.2`-line progression/economy/UI overhaul that landed on 2026-10-22 — three-track library
   progression, Library Resonance, the dual-mode XP formula, yield-aware material curves, Treasure
   Epiphany, the Depleted Tome loop, item transformations in both viewers, and the painted UI;
3. the second `26.1.2`-line batch that landed on 2026-10-23 — the illustrated **Arcane Grimoire**
   manual, the dual-tab **Cleanse** system (free disenchant / downgrade / single-trait strip / curse
   purge, with `Cleansing Grit`), incremental upgrade discounting, and the anti-arbitrage economy
   hardening that is now proven by a compiled JUnit suite;
4. the `26.3`-line feature batch that landed on 2026-10-23 — enchantment provenance with fair
   refunds, Master-Tome wear with charge lore, the versioned protocol capability channel, the
   categorized in-game configuration screen, and live per-player unlock catalogs.
5. the `26.3`-line architecture/feature batch that landed on 2026-10-24 — one effective-rule
   snapshot (`RuntimeRules`/`RuleBook`) with datapack governance, persistent chest-style table
   inventories, bounded owner/session-scoped inscription rituals with XP-sacrifice and force-rush
   skips, Infinity acquisition limits with the overloaded-tome slip, weighted Treasure Epiphany,
   passive Master-Tome regeneration (`24,000 / 12,000 / 3,600` s at `38 / 42` broad shelves),
   depleted-tome chest loot / wild-shelf weathering / librarian trades, five new advancements and
   the repaginated illustrated Grimoire in 11 locales.

What was taken, what was adapted and what was deliberately left out is recorded in
[`docs/reports/SYNC_FROM_26_1_2_SIDE_REPORT.md`](docs/reports/SYNC_FROM_26_1_2_SIDE_REPORT.md)
(the 2026-10-22/23 syncs), [`docs/reports/SYNC_FROM_26_3_SIDE_REPORT.md`](docs/reports/SYNC_FROM_26_3_SIDE_REPORT.md)
(the 2026-10-23 `26.3` feature port) and in
[`docs/reports/PORTING_26_2_REPORT.md`](docs/reports/PORTING_26_2_REPORT.md) §3.4 (the 26.3
back-port and the 26.2 port itself); user-visible changes are in [`CHANGELOG.md`](CHANGELOG.md).

Highlights:

* **Smooth entry & vanilla upgrade**: craft directly with `2 Diamonds + 1 Book + 1 Chiseled
  Bookshelf + 3 Crying Obsidian`, upgrade a vanilla Enchanting Table (`1 Enchanting Table + 1
  Chiseled Bookshelf + 4 Amethyst Shard + 3 Crying Obsidian`), or craft `8× Crying Obsidian` from
  `8× Obsidian + 1× Ghast Tear`.
* **Three-track library progression & dual-mode XP formula** (full tables in [`docs/WIKI.md`](docs/WIKI.md)):
  * **Wooden Bookshelves** (`#chiseled_enchanting_table:regular_bookshelves`, configurable `7×5×7`
    scan) unlock blank inscriptions up to `ceil(maxLevel / 2)`, step already-enchanted gear/books up
    to `MAX` along the `0..38` shelf curve (`gamma = 1.25`: `I(0) → II(7) → III(16) → IV(27) →
    V(38)`), start granting XP discounts at `12+` shelves (up to `-15%` at `38`), and at **`32+`
    shelves** unlock **Treasure Epiphany** (countdown, persistent cryo-stasis freeze, manual reroll).
  * **Chiseled Bookshelves** turn stored enchanted books into permanent **Master Tomes**, unlocking
    levels `1..k` directly on blank items (treasures included), adding global & per-enchantment XP
    discounts (up to `-40%`, data-driven through `library_resonance.json`), and switching
    Master-Tome inscribing into **vanilla mass-production mode** (`1–3` levels deducted) while item
    transformations, base enchanting, upgrades and epiphanies keep the full XP curve.
  * **Yield-aware material curves** (`Lapis Lazuli > Emerald >> Amethyst Shard`) that adapt to any
    modded `maxLevel` through `base_count` / `max_level_count` / `level_exponent`.
  * **Live shelf sync**: a server-authoritative rescan every second and before every ritual, so an
    open menu reacts immediately to placed/broken shelves and stored/removed books.
* **Illustrated in-game manual (`纂刻秘典` / `arcane_grimoire`)**: craft it from
  `Book and Quill + Chiseled Bookshelf` or `Depleted Tome + Ink Sac`, store it on a shelf, or open it
  from the button above the table UI. Eight chapters of live, data-driven diagrams with JEI/REI
  lookup from every item slot; vanilla book/UI sounds only.
* **Dual-tab cleanse system (`清练`)**: `Inscribe · Upgrade` and `Cleanse · Refine` tabs. Full
  disenchants and level step-downs are free and *refund* essence XP (telescoping potentials, proven
  never to beat a Master-Tome re-enchant), `Cleansing Grit` strips a single unwanted trait, and curse
  purging removes `#minecraft:curse` entries. Every ritual keeps a pure-vanilla alternative
  (`disenchanting.json`).
* **Chest-style table storage & queued deep-resonance rituals**: the two table slots persist inside
  the block (close the GUI, walk away, reload the world — nothing drops and nothing duplicates), and
  a deep library lets you queue several inscriptions on one item. When the wait is too long you can
  **sacrifice XP** (safe, up to `15` levels) or **force the rush** (no XP, but a live chance of
  degrading the weakest Master Tome) — both with dedicated advancements.
* **Passive shelf recovery**: worn Master Tomes stored in Chiseled Bookshelves slowly heal, from
  `1` point per `20` game days at a bare table down to `1` per `3` game days with `42` broad
  shelves. Only time spent with the shelves loaded counts.
* **In-game configuration screen**: every rule (bookshelf gates and scan volume, Master-Tome
  consumption mode and charges, economy discounts and refunds, epiphany odds, cleanse/uninstall
  switches, worldgen loot policy) is editable live from `/cet config`, the table screen, the
  grimoire or Mod Menu — with search, sliders, parent/child locking and a read-only inspector mode
  for players without permission. Saves are atomic, acknowledged and broadcast to everyone online.
* **Enchantment provenance**: each level remembers whether it was paid for here, looted or
  creative-granted, so disenchant refunds return what was actually invested
  (`external_refund_scale` for levels that came from elsewhere) instead of a flat estimate.
* **Bidirectional JEI & REI support**: full recipe/usage lookup for both **Chiseled Enchanting**
  and **Transmutation & Cleansing** (`Enchanted Golden Apple`, `Trident`, `Totem of Undying`, `Echo
  Shard`, `Bottle o' Enchanting`, `Blank Overwrite`, `Cleansing Grit`, cleanse and curse-purge
  rituals).

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
categories — **Chiseled Enchanting** and **Transmutation & Cleansing** — that answer both
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

**Status: green** on `1.1.0` for Minecraft 26.2 — commit `6173b6f` (second sync), runs
`39889855557` (compile-check), `39889855537` (build: `static` + `gradle build` with the JUnit suite +
`no-viewers`) and `39889855610` (consistency); earlier passes are green at `9f2046c`
(`39819932191` / `39819932163` / `39819931994`) and `902e947` (`39818720349` / `39818720326` /
`39818720363`). The compiler log is committed back as `build-reports/compile-java.log`. See
`docs/ci/README.md` for the history.

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

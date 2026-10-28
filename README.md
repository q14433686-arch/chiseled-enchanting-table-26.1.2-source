# Chiseled Enchanting Table — Unofficial 26.3 Port

[![compile-check](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/workflows/compile-check.yml/badge.svg?branch=26.3)](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/workflows/compile-check.yml)
[![build](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/workflows/build.yml/badge.svg?branch=26.3)](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/workflows/build.yml)
[![consistency](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/workflows/consistency.yml/badge.svg?branch=26.3)](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/workflows/consistency.yml)

![Chiseled enchanting table banner](./banner.png)

> ⚠️ **Unofficial community port.** This repository is a community-maintained, unofficial port of
> [Sacramentix/chiseled-enchanting-table](https://github.com/Sacramentix/chiseled-enchanting-table)
> updated to **Minecraft 26.3** on Fabric. It is **not affiliated with, endorsed by, or
> maintained by the original author**. All credit for the original mod design, code, and assets
> belongs to Sacramentix and is used under the original [MIT License](LICENSE). Please direct
> bug reports about this port to **this** repository (`q14433686-arch/chiseled-enchanting-table-26.1.2-source`),
> not to the upstream project.

Targeted, deterministic enchanting for Fabric **26.3** — choose exact enchantments instead of rolling random offers, store Enchanted Books in Chiseled Bookshelves as reusable Template Books, and strip or downgrade unwanted traits anytime without losing your progress.

**Current version:** `1.1.0` *(unofficial-port SemVer; see `CURRENT_BASELINE.md` for details)*  
**Target:** Minecraft `26.3`, Fabric Loader `>=0.19.5`, Fabric API `0.162.0+26.3`, Java `>=25`

**Current compatibility:** `26_3_revision_13` — update both client and server to this build; older `1.1.0` jars use incompatible ritual/tome payloads and lack the new visual-event channel. [Compatibility history](docs/PROTOCOL_HISTORY.md).

**2026-10-28 full source sync:** 26.1.2 PR27 (revision22) → 26.3 revision13: smart ritual wait/abort/fuel, tome maintenance, Cleanse extraction and curse purge, custom 3D effects/floating targets, batch locate and cross-tab tome selection. [Scope and validation](docs/reports/FULL_PORT_PR27_TO_26_3_2026_10_28.md).

<details>
<summary>Optional cosmetic easter egg (spoiler)</summary>

Sneak-right-click the table with a name tag named exactly `jeb_` to enable a smooth vanilla-sheep rainbow tablecloth. The tag is reusable. Ordinary dyeing restores static colour mode; dropped tables retain the rainbow flag when placed again. Rainbow inventory and held-item icons animate smoothly too; a localized purple [Rainbow] tooltip badge identifies them without overwriting custom names. Ordinary dyed tables stay static. [Implementation and validation limits](docs/reports/RAINBOW_CLOTH_2026_10_26.md).

</details>

📖 **Full Gameplay Wiki & Upstream Comparison (中文机制详解与上游差异完整对照): [`docs/WIKI.md`](docs/WIKI.md)** · **Release History: [`CHANGELOG.md`](CHANGELOG.md)**

> ⚠️ **Upgrading existing worlds to `1.1.0` (network protocol `26_3_revision_13`):** Back up your world and update **both client and server** together.
> - **Template Book durability, `Unbreaking` ward & wild pre-wear:** Non-`Infinity` Enchanted Books in Chiseled Bookshelves hold **`4`** durability per tier (enchanting gear spends **`1`**, copying onto a book spends **`2`**; `Unbreaking` grants up to **`30%`** wear waiver; wild loot/bookshelf books spawn pre-worn with most at **`1–2`** charges left; `Infinity` books never wear out) and passively recharge in the shelf over **6 / 3 / 1 game days** (`0 / 38 / 42` total nearby shelves).
> - **Overloaded `Infinity` Tome slip & cross-line (`<= 18%`):** Copying from a heavily loaded `Infinity` Template Book (`L >= 3`) risks a single-roll miscopy (`70%` `-1` tier / `25%` useless low-rarity enchant / `5%` curse); cross-line pollution never deletes your existing enchantments, refunds **`50%`** XP & materials, and leaves an Ink Stain washed off by Cleanse.
> - **External disenchant refund:** Enchantments not inscribed at this table refund XP at `25%` by default to prevent mob-loot XP farming.
> - **Config & datapack priority:** Saved in-game config settings are preserved across updates while explicit datapack overrides take precedence (`docs/reports/CROSS_BRANCH_3E61F5D_ARCHITECTURE_REVIEW.md`, `docs/reports/SYNC_FROM_26_1_2_REPORT.md`).

---

## Core Features

### Getting Started & Illustrated Guide (`纂刻秘典` / `Chiseled Grimoire`)

* Craft the table directly (`1 Book + 2 Diamonds + 1 Chiseled Bookshelf + 3 Crying Obsidian`) or upgrade a vanilla Enchanting Table (`Enchanting Table + 1 Chiseled Bookshelf + 2 Crying Obsidian`).
* Craft `8× Crying Obsidian` from `8× Obsidian + 1× Ghast Tear`.
* Craft the illustrated in-game guide (`Chiseled Grimoire`) with **`Book and Quill + Chiseled Bookshelf`** or **`Depleted Tome + Ink Sac`** — or open it anytime from the book button above the table UI.

### How Bookshelves Work (`7×5×7` Scan Range)

| Shelf Type | Unlocks Tiers? | Cuts XP Cost? | Speeds Up Recharge & Timers? |
|---|---|---|---|
| **Wooden Bookshelf** (`#regular_bookshelves`) | Yes — blank gear starts up to mid-tier (`ceil(max / 2)`), and upgrades scale smoothly to max along `0..38` shelves (`I(0) → II(7) → III(16) → IV(27) → V(38)`). `32+` shelves unlock **Treasure Epiphany** | Yes (`12+` shelves, up to `-15%`) | Yes (counts toward total nearby shelves: `38 / 42` tiers) |
| **Chiseled Bookshelf (with Enchanted Books)** | Yes — stored books act as **Template Books**, unlocking their exact tiers (`1..k`, including Treasures) on blank items and switching XP cost to **Vanilla `1–3` Level Mode** | Yes (up to `-20%` global + `-15%` matching trait; total cap **`-40%`**) | Yes (counts toward total nearby shelves) |
| **Empty Chiseled Bookshelf / Depleted Tome** | No | No | Yes — counts toward total nearby shelves (`6 → 3 → 1` game-day recharge & shorter ritual timers) |

### Safe Disenchanting & Curse Removal

* **Clear All or Step Down 1 Level:** Zero material cost; refunds a conservation-verified XP floor so repeated enchanting and stripping can never create free XP.
* **Remove 1 Chosen Enchantment:** `Lv.3–10` XP + `Cleansing Grit` (or `Amethyst Shard + Glow Ink Sac` / `Arcane Synthesis Crystal`) removes one unwanted trait while keeping every other enchantment intact.
* **Purge Curses:** `Lv.12` XP + `Ghast Tear` (or `3 Golden Apples + 3 Cleansing Grit` / `1 Arcane Synthesis Crystal + 2 Cleansing Grit`), reduced by library XP discounts.

### Bidirectional JEI & REI Integration

* Press **Recipes (`R`)** on an Enchanted Book to see every item it can enchant, or **Uses (`U`)** on gear to see every compatible enchantment, material option, XP mode, and conflict rule.
* Dedicated **Transmutation & Cleansing** category covers batch transmutations (`Enchanted Golden Apple`, `Trident`, `Totem of Undying`, `Echo Shard`, `Bottle o' Enchanting`, free `Depleted Tome -> Book` restoration) and Cleanse / Curse-Purge recipes, synced live from your server's rules on join and `/reload`.

---

## Installation (Players & Servers)

1. Install **Minecraft `26.3`**, **Fabric Loader `>=0.19.5`**, and **Fabric API `0.162.0+26.3`** (requires **Java 25+**).
2. Download `chiseled-enchanting-table-1.1.0.jar` from this repository's [Releases](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/releases) or from the latest [`build` workflow artifact](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/actions/workflows/build.yml) (kept 14 days), and place it in `mods/` on **both client and server**.
3. *(Optional)* Install **JEI** or **REI** on the client for recipe and usage lookup — neither viewer is required at runtime.

> This unofficial port is **not** published under the original author's Modrinth or CurseForge pages. Any builds for this port are distributed exclusively through this repository.

## Localization

Ships with 11 built-in translations: `de_de`, `en_us`, `es_es`, `fr_fr`, `it_it`, `ja_jp`, `ko_kr`, `pt_br`, `ru_ru`, `zh_cn`, and `zh_tw` (missing keys fall back to `en_us`). Generated book lore components use server-neutral translation keys so items read naturally in each player's client language.

---

## Building from Source & CI

CI badges above track the **26.3 branch**, not arbitrary working-tree edits. A successful run
only verifies its recorded source SHA and tested scenarios; it does not certify GUI/IME or multiplayer interaction.

Building locally requires **JDK 25** and network access to `maven.fabricmc.net`:

```bash
chmod +x ./gradlew
./gradlew build   # output jars in build/libs/ (pass -Penable_jei=false -Penable_rei=false for viewer-free compile)
```

Offline static verification gates (no JDK or network required):

```bash
python3 scripts/ci_static_checks.py                 # mixin registration + 11-locale key parity
python3 scripts/compat_check.py                     # 26.3 datapack, loot & advancement schema checks
bash scripts/check_release_consistency.sh --strict  # gradle.properties / README / fabric.mod.json / publish.yml sync
bash docs/ci/install.sh --check                     # workflow reference mirror check (see docs/ci/README.md)
```

| Workflow | Purpose | Trigger |
|---|---|---|
| [`compile-check.yml`](.github/workflows/compile-check.yml) | Compiles main + client + test sources, runs JUnit verification suites, and publishes full logs as artifacts plus bounded API-readable Checks diagnostics | Push / PR |
| [`build.yml`](.github/workflows/build.yml) | Static checks → full `./gradlew build` + artifact upload → `no-viewers` compile gate → headless Minecraft `26.3` server smoke test | Push / PR |
| [`consistency.yml`](.github/workflows/consistency.yml) | Cross-checks version strings across `gradle.properties`, `README.md`, `fabric.mod.json`, and `publish.yml` | Version/doc edits |
| [`publish.yml`](.github/workflows/publish.yml) | Manual release pipeline (`MINECRAFT_VERSION: 26.3`, `VERSION: 1.1.0`) | `workflow_dispatch` |

## Other Mod Loaders (Forge / NeoForge)

This repository maintains a **Fabric-only** port for Minecraft `26.3`; Forge and NeoForge builds are not planned here.

## Contributing & Credits

Contributions, issue reports, and pull requests for the `26.3` port are welcome in this repository!

* **Original Mod Author:** [Sacramentix](https://github.com/Sacramentix/chiseled-enchanting-table) — original concept, code, and artwork ([MIT License](LICENSE)).
* **Upstream Concept Inspiration:** Sacramentix credited [TheRedBrain/rpg-enchanting](https://github.com/TheRedBrain/rpg-enchanting) as an early design inspiration for the original mod.

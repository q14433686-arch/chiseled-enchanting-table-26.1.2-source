# Modrinth listing — Chiseled Enchanting Table (Unofficial 26.1.2 Port) `1.0.2`

> Paste copy for the Modrinth project page. Modrinth supports CommonMark Markdown in the
> description editor; the "Summary" field is plain text and capped at ~256 characters.

---

## Project name (project title)

```
Chiseled Enchanting Table (Unofficial 26.1.2 Port)
```

## Summary (short, ≤256 chars — shown in search results)

```
Unofficial community port of Sacramentix's Chiseled Enchanting Table to Minecraft 26.1.2 (Fabric). No-RNG enchanting via a custom table that unlocks enchantments from nearby chiseled bookshelves. Not affiliated with the original author.
```

## Categories / tags

- **Categories:** Adventure, Game Mechanics, Utility
- **Environments:** Client *and* Server (both required)
- **Loaders:** Fabric
- **Game versions:** 26.1.2
- **License:** MIT

## Required dependencies

- [Fabric API](https://modrinth.com/mod/fabric-api) — `0.151.0+26.1.2` or compatible

(*Note for migrators from upstream:* this port has **dropped the Fabric Language Kotlin dependency**
that earlier upstream `0.2.x` builds listed. You only need Fabric API.)

## Optional / suggested dependencies (carried over from upstream's recommendation list)

- [More Chiseled Bookshelf Variants](https://modrinth.com/mod/more-chiseled-bookshelf-variants) — adds more chiseled bookshelf blocks recognised by the table
- [Bookshelf Inspector](https://modrinth.com/mod/bookshelf-inspector) — quickly see which book is in which chiseled-bookshelf slot
- [Enchanted Bookshelves](https://modrinth.com/mod/enchanted-bookshelves) — enchanted books in chiseled bookshelves get a glint
- [Colorful Books](https://modrinth.com/mod/colorful-books) — coloured books in chiseled bookshelves

*(These are mentioned as compatible recommendations; install them at your own discretion.
This port has only been verified against vanilla so far — see "Compatibility status" below.)*

## External links

- **Source code:** https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source
- **Issue tracker:** *(use the GitHub repo above — please **do not** open issues against the upstream project for this port)*
- **Upstream / original mod:** https://modrinth.com/mod/chiseled-enchanting-table

---

# Full description (paste into Modrinth's description editor)

> ⚠️ **Unofficial community port.** This is an **unofficial** community port of Sacramentix's
> *Chiseled Enchanting Table* to Minecraft **26.1.2** on Fabric. It is **not affiliated with,
> endorsed by, or maintained by the original author**. All original mod design, code, and assets
> © Sacramentix, used under the [MIT License](https://github.com/Sacramentix/chiseled-enchanting-table/blob/main/LICENSE).
> **Please direct bug reports about this 26.1.2 port to [this repository](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source), not to the upstream project.**

---

## What this mod does

Replaces vanilla enchanting RNG with a **deterministic, library-driven** workflow:

- Adds a new block, the **Chiseled Enchanting Table**.
- Place enchanted books into nearby **chiseled bookshelves** to **permanently unlock** those
  enchantments for the table.
- Open the table and **pick exactly the enchantment you want** from a scrollable list — no slot
  randomness, no re-rolling.
- The applied enchantment **consumes the matching enchanted book** (which is why building a
  bookshelf library matters), plus a small XP and material cost shown in the UI.
- Some enchantments are unlocked by default (the vanilla `#minecraft:non_treasure` tag, plus a
  small set of treasure-only enchantments the table chooses to also unlock); the rest you collect
  by exploring and looting enchanted books, just like vanilla — but you keep them forever.
- Includes a small set of **item transformations** that share the same table UI
  (e.g. Golden Apple → Enchanted Golden Apple, Glass Bottle → Bottle o' Enchanting).
- Anvil change inherited from upstream: you can **no longer combine two same-level books at an
  anvil to bump the level**. Use the bookshelf library + table instead.

## How to play (quickstart)

1. **Craft** the Chiseled Enchanting Table (vanilla-style recipe; see the in-game recipe book).
2. Build a small library of **chiseled bookshelves** next to it (vanilla bookshelves still give
   the baseline level boost).
3. Drop enchanted books into the chiseled-bookshelf slots — each book's enchantments become
   pickable at the table.
4. Open the table, put the item to enchant in the left slot, scroll the list, click the
   enchantment you want, pay the XP + materials it shows, and the enchantment is applied
   (consuming the matching book).
5. Use the table for the small set of built-in item transformations whenever they show up in the
   list with no item in the input slot.

## Why a port

The upstream project currently targets Minecraft `1.21.x` and depends on Fabric Language Kotlin.
This 26.1.2 port does the following:

1. **Updates the toolchain** to Minecraft 26.1.2 / Fabric Loader 0.19.3 / Fabric API 0.151.0+26.1.2 /
   Loom 1.16.3 / Gradle 9.4.1 / **Java 25**.
2. **Drops the Kotlin runtime dependency** — the only hard dependency is Fabric API.
3. **Hardens data-driven content**: default unlocks, enchantment costs, item transformations,
   the 41-vanilla-loot-table enchanted-book injection, and the structure bookshelf book pool
   are all loaded from JSON data tables (see [`docs/EXTENDING.md`](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/blob/main/docs/EXTENDING.md)).
   Rebalancing without recompiling is one datapack away.
4. **Cross-mod compatibility built in**: third-party enchantment mods that tag their enchantments
   into `#minecraft:non_treasure` are picked up automatically as default unlocks; Universal
   Enchants-style rewrites work without any port-side changes because item compatibility is read
   from the live registry each time.
5. **XP deduction now vanilla-anvil-style** (points, not raw levels) — high-level players pay
   proportionally more for the same enchant, matching vanilla's cost curve.

## Compatibility status

- **Vanilla 26.1.2 + Fabric API 0.151.0:** all core paths verified live — place block, open
  menu, scroll list, apply enchantment, apply transformation, apply from book, mining drop,
  floating book renderer, chest loot injection, structure bookshelf replacement in villages /
  mansions / ancient cities / stronghold libraries, XP deduction (vanilla-anvil-style points).
- **Third-party enchantment mods that add enchantments to `#minecraft:non_treasure`:** designed
  to be auto-picked-up by this table's default-unlock list — no per-mod configuration needed.
- **Universal Enchants** style item/exclusivity rewrites: work without any port-side changes
  because `Enchantment.canEnchant` / `areCompatible` is read from the live registry each time.
- **Merge Enchantments** style anvil mods: operate on the vanilla anvil, a separate interaction
  path from this table; the two coexist.

See [`docs/COMPATIBILITY.md`](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/blob/main/docs/COMPATIBILITY.md)
in the source repo for the full investigation and per-mod notes.

## Known port-side temporary boundaries

Carried over from the porting work and still parked in `build.gradle` (all others originally
listed here have been enabled through the 1.0.x cycle):

- **Legacy anvil mixins** (`RemoveEnchantMerge`, `RemoveXpCostScaling`) — vanilla anvil combining
  behaviour is currently unchanged from vanilla; upstream's "no more same-level enchant-book
  combining" balance change is not yet re-verified on 26.1.2.
- **Enchant particle / sound over a bookshelf holding an enchanted book** — the ambient effect
  from upstream's `ChiseledBookshelfTick` is not yet wired.
- **Third-party mod integration layer** (e.g. Colorful Books integration) — not yet ported.
- **Dedicated multi-slot cost panel** — additional-cost items are still consumed silently from
  the player's main inventory rather than shown in a dedicated menu panel. Tracked in the
  source repo's `NEXT_TASK.md` for the 1.0.2 line.

These are documented limitations, not bugs — please mention them on any feedback.

## Credits

- **Original mod:** [Sacramentix](https://modrinth.com/user/Sacramentix) — design, code, assets
  (MIT). The original mod is at <https://modrinth.com/mod/chiseled-enchanting-table>.
- **Unofficial 26.1.2 port:** `q14433686-arch`. Source:
  <https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source>.
- **License:** MIT, same as upstream.

If you like this mod, please go support the original author too.

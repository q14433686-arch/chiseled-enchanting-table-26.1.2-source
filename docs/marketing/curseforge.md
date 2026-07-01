# CurseForge listing — Chiseled Enchanting Table (Unofficial 26.1.2 Port) `1.0.2`

> Paste copy for the CurseForge project page. CurseForge's description editor accepts a Markdown-ish
> dialect but renders to HTML; BBCode-style tags do not work. Headings, bold, italics, lists, and
> links all render. The **Summary** field is plain text, capped at 255 characters.

---

## Project name

```
Chiseled Enchanting Table (Unofficial 26.1.2 Port)
```

## Summary (≤255 chars)

```
Unofficial community port of Sacramentix's Chiseled Enchanting Table to Minecraft 26.1.2 (Fabric). No-RNG enchanting via a custom table that unlocks enchantments from nearby chiseled bookshelves. Not affiliated with the original author.
```

## CurseForge metadata to set

- **Categories:** Adventure and RPG, Magic, Map and Information (pick the closest two CurseForge
  actually offers under Fabric — usually "Magic" + "Adventure and RPG").
- **Mod loaders:** Fabric
- **Game versions:** 26.1.2
- **License:** MIT
- **Client / Server:** Required on both
- **Source URL:** https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source
- **Issues URL:** https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/issues

## Dependencies (set in CurseForge "Relations")

| Slug | Type | Notes |
|---|---|---|
| `fabric-api` | Required | Tested against `0.151.0+26.1.2`. |
| `chiseled-enchanting-table` (Sacramentix) | "Required parent" / "Similar project" | Mark as the **original / upstream** this is ported from. |

*(This port no longer requires Fabric Language Kotlin even though upstream `0.2.x` did.)*

## Optional / suggested ("Optional" relation)

- `more-chiseled-bookshelf-variants`
- `bookshelf-inspector`
- `enchanted-bookshelves`
- `colorful-books`

---

# Full description (paste into CurseForge's description editor)

> ⚠️ **Unofficial community port.** This is an **unofficial** community port of Sacramentix's
> *Chiseled Enchanting Table* to Minecraft **26.1.2** on Fabric. It is **not affiliated with,
> endorsed by, or maintained by the original author**. All original mod design, code, and assets
> © Sacramentix, used under the MIT License.
>
> **Please direct bug reports about this 26.1.2 port to
> [this GitHub repository](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source),
> not to the upstream project page or to Sacramentix directly.**

---

## What it does

Replaces vanilla enchanting RNG with a **deterministic, library-driven** workflow:

- A new block, the **Chiseled Enchanting Table**.
- Place enchanted books into nearby **chiseled bookshelves** to **permanently unlock** those
  enchantments for the table.
- Open the table and **pick exactly the enchantment you want** from a scrollable list. No slot
  randomness, no re-rolling.
- Each applied enchantment consumes the matching enchanted book, plus a small XP and material
  cost shown directly in the UI.
- A baseline set of vanilla enchantments is unlocked by default (via the vanilla
  `#minecraft:non_treasure` tag plus a small treasure-only carve-out). Everything else, you go
  find — but you keep it forever.
- Built-in **item transformations** share the same table UI (e.g. Golden Apple → Enchanted Golden
  Apple, Glass Bottle → Bottle o' Enchanting).
- Anvil change inherited from upstream: combining two same-level enchanted books at an anvil to
  bump the level **no longer works**. Use the bookshelf library + this table instead.

## Quickstart

1. **Craft** the Chiseled Enchanting Table (vanilla-style recipe; see the in-game recipe book).
2. Place **chiseled bookshelves** around it; build up a small library. (Regular bookshelves still
   give the baseline level boost.)
3. Drop enchanted books into the chiseled-bookshelf slots — those enchantments become pickable.
4. Open the table, place the item to enchant in the input slot, scroll the list, click the
   enchantment you want, pay the cost the UI shows, and the enchantment is applied (consuming the
   matching book).
5. Some recipes have **no item input** — those are item transformations. Pay the listed cost and
   the table gives you the output item.

## What's different in this 26.1.2 port

Compared to upstream's `0.2.x` line:

- **Toolchain:** Minecraft `26.1.2`, Fabric Loader `0.19.3`, Fabric API `0.151.0+26.1.2`,
  Loom `1.16.3`, Gradle `9.4.1`, **Java 25** required.
- **Fabric Language Kotlin dependency dropped** — only Fabric API is required at runtime.
- **Fully data-driven content**: default unlocks, enchantment costs, item transformations, the
  41-vanilla-loot-table enchanted-book injection, and the structure bookshelf book pool are all
  loaded from JSON data tables under `data/chiseled_enchanting_table/`. Rebalancing without
  recompiling is one datapack away.
- **Cross-mod compatibility built in**: third-party enchantment mods that tag their enchantments
  into `#minecraft:non_treasure` are picked up automatically as default unlocks; Universal
  Enchants-style rewrites work without any port-side changes.
- **XP deduction now vanilla-anvil-style** (points, not raw levels).
- An offline data-pack / id-existence checker (`scripts/compat_check.py`) is wired into CI to
  catch regressions before they ship.

## Compatibility status

- **Vanilla 26.1.2 + Fabric API 0.151.0:** all core paths verified live — place block, open
  menu, scroll list, apply enchantment, apply transformation, apply from book, mining drop,
  floating book renderer, chest loot injection, structure bookshelf replacement in villages /
  mansions / ancient cities / stronghold libraries, XP deduction (vanilla-anvil-style points).
- **Third-party enchantment mods that add enchantments to `#minecraft:non_treasure`:** designed
  to be auto-picked-up by this table's default-unlock list — no per-mod configuration needed.
- **Universal Enchants** style item/exclusivity rewrites: work without any port-side changes
  (`Enchantment.canEnchant` / `areCompatible` is read from the live registry).
- **Merge Enchantments** style anvil mods: operate on the vanilla anvil, a separate interaction
  path from this table; the two coexist. See `docs/COMPATIBILITY.md` for details.

## Known port-side temporary boundaries

Small set carried over from the porting work and still parked pending 26.1.2 verification:

- **Legacy anvil mixins** (`RemoveEnchantMerge`, `RemoveXpCostScaling`) — vanilla anvil combining
  behaviour is currently unchanged from vanilla.
- **Enchant particle / sound over a bookshelf holding an enchanted book** — the ambient effect
  from upstream's `ChiseledBookshelfTick` is not yet wired.
- **Third-party mod integration layer** (e.g. Colorful Books integration) — not yet ported.
- **Dedicated multi-slot cost panel** — additional-cost items are still consumed silently from
  the player's main inventory. Tracked in the source repo's `NEXT_TASK.md` for the 1.0.2 line.

## Credits

- **Original mod:** Sacramentix — design, code, assets. MIT-licensed.
  Original page: [Modrinth](https://modrinth.com/mod/chiseled-enchanting-table) /
  [GitHub](https://github.com/Sacramentix/chiseled-enchanting-table). If you enjoy this mod,
  please go support the original author too.
- **Unofficial 26.1.2 port:** `q14433686-arch`.
  Source: <https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source>.
- **License:** MIT, same as upstream.

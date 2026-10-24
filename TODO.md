LIST OF THING! :

<!-- Upstream author's scratch list, kept for provenance. Statuses resynced with the
     actual delivery state of this fork on 2026-10-24 (audit follow-up: the board and
     CHANGELOG/docs had drifted into two versions of the truth). -->

- ✅ Create a new Chiseled enchanting table that work with chiseled bookshelf

- ✅ Chiseled enchanting table can enchant item with enchant present in book in nearby chiseled bookshelf

- ✅ Chiseled enchanting table have a infuse ingredient cost different for each enchant

- ✅ Chiseled enchanting table can override conflicting enchant and downgrade enchant level

- ✅ Chiseled enchanting table is dyable
 
- ✅ Disable anvil enchant level upgrade when fusioning 2 similar enchant of same level

- ✅ Disable classic enchanting table book enchanting

- ✅ structure spawn with chiseled bookshelves (it replace all classic bookshelves programmatically)
that can contain book and enchanted book

- ✅ Add crating recipe for chiseled enchanting table

- ✅ can consume enchanted book in infuse slot to enchant without xp cost

- ✅ rework chest loot table enchanted book
  (delivered: data-driven `loot_injections.json`, 41 structure/chest injections)

- ✅ add coherent loot table to chiseled bookshelf
  (delivered: `bookshelf_book_pool.json` weighted fill + weathering)

- 🚧 improve fishing loot table
  (not done: no fishing-specific injection exists yet)

- X Scaling enchant cost based on material tier (gold, iron, diamond, netherite) (might not be relevant because enchanting via book is free)

- ✅ Add configuration
  (delivered: in-game config screen + server sync)

- ✅ Add data driven configuration
  (delivered: 11 datapack JSON tables under data/chiseled_enchanting_table/)

- ✅ handle Chiseled enchanting table ui scrollbar click
  (delivered; later hardened to left-click-only widget input)

- add advancement :
    - ✅ Advancement for crating the table
    - ✅ Advancement for unlock enchant by placing them in nearby chiseled bookshelf
    - ✅ Advancement for having the table with all enchant unlock at once
    - ✅ Advancement for adding all enchant to a book using the table


- ✅ setup translation
  (delivered: 11 locales, CI-enforced key/placeholder parity)

- ✅ handle case book swap enchant level
  (covered by conflict-override + downgrade application paths)

- ❓ Chiseled enchanting table should work with lectern ?
  (open design question — undecided)

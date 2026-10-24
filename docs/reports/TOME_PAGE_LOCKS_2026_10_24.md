# Tome page, page unlocks & tome locks — 2026-10-24

## Player-facing behavior

- The Chiseled Enchanting Table gains a third top tab, **Tomes**. It lists every
  Enchanted Book on in-range Chiseled Bookshelves using the same scan scope as
  Master Tome charging, with remaining/effective charges, generation, ETA to
  +1, ETA to full, qualifying-shelf count, total library shelves, and the gap
  to the next regeneration tier.
- **Cleanse** and **Tomes** start locked per player. Hovering either locked tab
  shows: “未解锁该功能页，请前往纂刻秘典查看详情”. Unlocks are permanent and use
  advancements as the source of truth — no new saved-data schema.
- Cleanse unlock: the amber sacrifice control beside the tabs consumes
  Depleted Tomes from in-range shelves. Twelve total sacrifices complete
  `sacrifice_depleted_tomes` and unlock the page.
- Tomes unlock: four in-range Chiseled Bookshelves each holding at least one
  Enchanted Book complete `how_much_longer` (“还要多久……”) and unlock the page.
- The Grimoire gains chapter 9, explaining both unlocks and all lock modes.

## Tome locks

Lock state lives in the book's `CUSTOM_DATA` (`cet_lock_owner`,
`cet_lock_owner_name`, `cet_lock_mode`, `cet_lock_target_wear`), owned by the
locking player's UUID. Modes are `next_charge`, `full_charge`, and
`permanent`. Locking is free; locks never stop regeneration. Timed locks
expire exactly when wear reaches their target, are cleared when observed, and
notify an online owner with an actionbar message and sound. Locked books on
shelves emit marker particles.

Early manual unlock costs exactly **1 XP level + 1 Cleansing Grit per book**
(non-Creative, owner or Creative override). Server-side guards cover menu
apply/receipt paths, candidate generation, ritual/transmutation inputs,
anvil/grindstone results, container extraction (including hoppers), manual
shelf removal paths through `ContainerHelper`, and non-Creative shelf breaks.
Batch **Lock All Full** locks every currently charging, unlocked book.

## Wire & data

- Enchanting wire revision is now `26_1_2_revision_11`; revision 10 and older
  remain query-only handshake probes.
- `LiveShelfStatePayload` carries unlock flags, sacrifice progress, qualifying
  shelves, library totals, regeneration tier/gap/interval, and up to 256
  bounded `TomeBookEntry` records (64 enchantments each). The revision-10
  8/11-argument constructors remain source-compatible.
- `TomePageActionPayload` carries `LOCK_NEXT`, `LOCK_FULL`, `LOCK_PERMANENT`,
  `UNLOCK`, `LOCK_ALL_FULL`, and `SACRIFICE_DEPLETED`; the server revalidates
  menu target, shelf scope, slot contents, ownership, and costs.

## Verification gates

- `scripts/test_sync_26_1_2.py` (now pinning revision 11 + revision-10 probe)
- JUnit: pure TomeLock target/expiry/ETA tests in `AuditMechanicsTest`;
  full live-state + tome-action codec round trips in `NetworkCompatibilityTest`
- GitHub Actions compile, JUnit, and server smoke on Minecraft 26.1.2

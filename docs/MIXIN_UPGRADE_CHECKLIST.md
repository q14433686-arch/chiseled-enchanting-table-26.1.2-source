# Mixin Inventory & Minecraft Upgrade Checklist

> **Active config:** `src/main/resources/chiseled-enchanting-table-port.mixins.json`
> (`compatibilityLevel = "JAVA_25"`, `defaultRequire = 1`, `required = true`).
> Verified by `scripts/ci_static_checks.py` (Tiers A & B) on every push.

## 1. Active Mixin Inventory (13 Common Mixins)

| Mixin Class | Target Vanilla Class | Injection / Accessor Purpose | Config / Safety Guard |
| :--- | :--- | :--- | :--- |
| `anvil.RemoveEnchantMerge` | `AnvilMenu` | Blocks combining two identical-level Enchanted Books into level `L+1` on the anvil (redirects upgrade progression to the Chiseled Enchanting Table). | Gated by `ModRuntimeConfig.disableAnvilSameLevelBookMerge()`. |
| `anvil.RemoveXpCostScaling` | `AnvilMenu` | Optionally flattens prior-work `REPAIR_COST` exponential doubling (`Too Expensive!`) while preserving base item/enchantment XP cost. | Gated by `ModRuntimeConfig.anvilFlatRepairCost()` (default `false` on fresh installs); uses explicit `require = 1` on `createResult`. |
| `bookshelf.AddRandomTick` | `BlockBehaviour` / `ChiseledBookShelfBlock` | Enables random ticking on Chiseled Bookshelves so shelves holding Enchanted Books emit facing-aware enchant particles and subtle ambient audio (`ChiseledBookshelfTick`). | Client/server safe; uses `BlockStateProperties.HORIZONTAL_FACING` via `TomeParticleEffects.slotWorldCoordinates`. |
| `bookshelf.LockedBookContainerMixin` | `ChiseledBookShelfBlock`, `ChiseledBookShelfBlockEntity`, `BlockBehaviour` | Enforces player-owned Master Tome locks against manual interaction, hopper extraction (`removeItem` + `canTakeItemThroughFace` via `WorldlyContainer`), and block breaking (`getDestroyProgress`, `require = 1`). | Automatically clears stale lock components if a shelf is broken externally (explosions/pistons) or if a locked book enters the main station slot. |
| `enchanting_table.DisableBookEnchanting` | `EnchantmentMenu` | Prevents enchanting plain `minecraft:book` (or all items when configured) in the vanilla Enchanting Table. | Gated by `ModRuntimeConfig.vanillaTableMode()` (`BOOKS` / `ALL` / `NONE`). |
| `grindstone.GrindstoneBookDepleteMixin` | `GrindstoneMenu` (anonymous result/input slots) | Converts grindstone-disenchanted Enchanted Books into `chiseled_enchanting_table:depleted_tome` instead of plain `minecraft:book` across normal click and quick-move paths. | Gated by `ModRuntimeConfig.grindstoneDepletedTome()`; keeps `Slot#onQuickCraft` / `onSwapCraft` `protected` visibility intact. |
| `loot.ReloadableServerRegistriesMixin` | `ReloadableServerRegistries` | Preloads bundled/datapack loot rules (`ChiseledEnchantingTableData.preloadForLootRegistries`, including `depleted_tome_chest_spawns`) before `LootTableEvents.MODIFY` fires during world/server bootstrap. | Fail-safe fallback to bundled JSON if a datapack entry is malformed. |
| `structure.LevelChunkPostProcessMixin` | `LevelChunk` | Runs server-thread post-processing (`BookshelfBookFiller`) for marked stronghold/piece chiseled bookshelves without deadlocking worldgen worker threads ([issue #3](https://github.com/q14433686-arch/chiseled-enchanting-table-26.1.2-source/issues/3)). | Server-thread only (`ServerLevel` check). |
| `structure.StructurePieceAccessor` | `StructurePiece` | Exposes bounding box / orientation helpers needed during structure shelf replacement and deferred book filling. | Pure `@Accessor` interface. |
| `structure.StructurePieceMarkMixin` | `StructurePiece` | Marks procedural `StructurePiece` instances (e.g., Stronghold libraries) that replaced wooden bookshelves with chiseled bookshelves so `LevelChunkPostProcessMixin` fills them safely on the main thread. | Zero worldgen-thread `BlockEntity` access. |
| `structure.StructurePlaceSettingsMixin` | `StructurePlaceSettings` | Attaches the bookshelf replacement/orientation processor to Jigsaw/NBT structure template placements. | Respects `ModRuntimeConfig.populateStructureChiseledShelves()`. |
| `structure.StructureTemplatePoolProjectionMixin` | `StructureTemplatePool` | Ensures structure processor lists propagate cleanly across rigid/terrain-matching template pool projections. | Idempotent processor attachment. |
| `villager.VillagerInfinitySanitizeMixin` | `AbstractVillager` / `Villager` | When `restrict_villager_infinity_books` is enabled, rewrites librarian `minecraft:infinity` Enchanted Book offers to `minecraft:power` I and reprices base emerald cost into the fair L1 `[5, 8]` emerald range (`xp <= 5`) while preserving demand/reputation modifiers. | Gated by `ModRuntimeConfig.restrictVillagerInfinityBooks()` (default `false` on fresh installs). |

## 2. Optional Client Shader Compatibility Guard (Non-Mixin)

- `chiseled_enchanting_table.client.compat.IrisGlintPreload`: Pre-warms enchantment glint render buffers only when Fabric Loader detects `iris` (`FabricLoader.getInstance().isModLoaded("iris")`), avoiding unnecessary buffer allocation in vanilla/Sodium-only environments.

## 3. Step-by-Step Upgrade Verification Checklist (Minecraft `26.x -> 26.y`)

1. **Static & Descriptor Audit**:
   - Run `python3 scripts/ci_static_checks.py` to confirm every class in `chiseled-enchanting-table-port.mixins.json` exists and is not excluded in `build.gradle`.
   - Verify Mojmap method signatures and visibility (`public` vs `protected` on `Slot`, `AnvilMenu#createResult`, `BlockBehaviour#getDestroyProgress`, `ChiseledBookShelfBlockEntity` `WorldlyContainer` methods).
2. **Compile & Remap Check**:
   - Verify zero Mixin AP target/descriptor warnings in `compileJava` and `compileClientJava` (`build-reports/compile-java.log`).
3. **In-World Smoke Matrix**:
   - **Vanilla Enchanting Table**: Insert plain `Book` vs `Diamond Sword` under `vanilla_table_mode = books / all / none`.
   - **Anvil**: Test `Sharpness III + Sharpness III` books (blocked when `disable_anvil_same_level_book_merge = true`) and multi-repair prior-work cost under `anvil_flat_repair_cost = false / true`.
   - **Grindstone**: Disenchant an `Enchanted Book` via both normal click and Shift-click (`quickMoveStack`) with a full/partial inventory -> yields `Depleted Tome`.
   - **Locked Chiseled Bookshelf**: Lock a Master Tome -> verify manual right-click, hopper beneath shelf, and survival left-click mining are blocked for non-authorized actions, while breaking via `/setblock` or explosion drops an unlocked book.
   - **Worldgen**: Generate a Village Library, Ancient City, Woodland Mansion, and Stronghold Library -> verify zero worker-thread stalls, shelves face outward, and shelves contain Enchanted Books + weathered Depleted Tomes (`<= 30%`).
   - **Librarian Villager**: Enable `restrict_villager_infinity_books` -> verify an Infinity book offer converts to `Power I` at `5..8` base emeralds and `<= 5` villager XP while keeping hero-of-the-village / cured-villager discounts intact.

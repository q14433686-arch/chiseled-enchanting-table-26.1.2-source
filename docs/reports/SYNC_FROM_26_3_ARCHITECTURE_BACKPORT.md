# Cross-Branch Architecture & Runtime Hardening Sync (`26.3` `d2abe21` -> `26.1.2`)

> **Date**: 2026-10-24  
> **Source Reference (`26.3`)**: `d2abe2101d016a31d05cbbc28e7eba93a0d0d8b5` (PR #16 + PR #19)  
> **Target Line (`26.1.2`)**: `chiseled-enchanting-table-26.1.2-source` (`3e61f5de3ba7679ccbc92b73443dead149c96840`), Minecraft `26.1.2` / Fabric Loader `0.19.3` / Fabric API `0.151.0+26.1.2` / Release `1.1.0` (`EnchantingProtocol.REVISION = "26_1_2_revision_6"`)

---

## 1. Backported Runtime, Security & Architecture Fixes (`R1–R8` + `N1–N6` + Viewer/Loot Hardenings)

1. **Unified `RuleBook` + `RuntimeRules` + Atomic `RuntimeFrame` Publication (`R1`, `R2`, `R6`, `R7`)**:
   - Added pure game-version-independent `RuleBook` and `RuntimeRules`: stored preferences (`ModRuntimeConfig.preferences()`), active effective values (`ModRuntimeConfig.effective()` / `ChiseledEnchantingTableData.runtimeConfiguration()`), and UI editing drafts (`ChiseledEnchantingConfigScreen`) are strictly separated.
   - `ChiseledEnchantingTableData.RuntimeFrame` publishes all domain rule tables, `PackPolicy`, effective config DTO, and `RuleBook.Snapshot` atomically via a single `volatile` reference only **after** atomic disk persistence succeeds (`StandardCopyOption.ATOMIC_MOVE`).
   - Fail-closed validation across all datapack tables and `pack_policy.json` (invalid JSON types, unknown keys, or world attempts to govern startup-only `enchanted_book_stack_size` throw `IllegalArgumentException`), with full rollback of cached raw tables in `onResourceManagerReload` if any table fails.
   - Whole-file selected-layer inspection in `collectExternalDatapackKeys` via `resource.sourcePackId()` (with bundled jar JSON comparison fallback when aggregated under `"fabric"` / `"vanilla"`).
   - Hardened `ModConfigNetworking`: LAN host check via `server.isSingleplayerOwner(player.nameAndId())` (guests on Open-to-LAN worlds are read-only unless explicitly granted permission), monotonic `requestId` save acknowledgements, active pre-restart value hints (`config.chiseled_enchanting_table.tip.active_value`), and open table closure on rule changes.

2. **Per-World-Session Shared Bookshelf Regeneration (`R3`, `R4`, `R5`)**:
   - Added `RegenerationScheduler` + `WorldRegeneration` hooked into `ServerTickEvents.END_SERVER_TICK` and `ServerLifecycleEvents.SERVER_STOPPED`.
   - Aggregates all ticking tables per `ServerLevel` 20-tick window before settling once at the fastest eligible rate without summing overlapping tables, validating chunk load state, `ChiseledBookShelfBlockEntity` identity, slot `ItemStack` reference, and wear count before write-back (zero per-second NBT churn; zero unloaded/backpack/cross-world time credit).

3. **Persistent Table Escrow, Bounded Ritual Queue, Quarantine & `/cet cancel-ritual` (`N1–N5`)**:
   - Enforced active-ritual escrow locks across all `Container` mutation/automation hooks (`removeItem`, `removeItemNoUpdate`, `setItem`, `canPlaceItem`, `canTakeItem`) on `ChiseledEnchantingTableBlockEntity`.
   - Bound active rituals to `ritualOwnerUuid`, random non-zero `ritualSession`, and `ritualTarget` snapshot; enforced pre-payment admission (`<= 256` steps, `<= 32,000` characters, enchantment level `1..255`, `long`-safe conflict refund budget).
   - Pre-validated target stack and registry enchantments before clearing `ritualQueue` in `completeActiveRitual` (charging sacrifice XP / rolling Master Tome degradation only after commit succeeds), quarantining (`ritualBlocked = true`) malformed saves or removed enchantment IDs while preserving the stored item, and adding `/cet cancel-ritual` for owners.
   - Scoped `syncOpenMenusWatchingTable` to `menu.world() == level && pos.equals(menu.tableBlockPos())`.

4. **Protocol Isolation (`R8`), Conditional Loot Codecs, Librarian Band Idempotency (`N6`) & Live Viewers**:
   - Added `EnchantingProtocol` (`26_1_2_revision_6`) + `PayloadLimits` (`MAX_COLLECTION_ENTRIES = 65_536`), `container_id` / `session` routing on `LiveShelfStatePayload`, `SkipRitualPayload`, and `SyncRitualStatePayload`, and server-authoritative per-player `TransformationDisplay` catalogs synced dynamically to open menus, JEI (`hideRecipes` / `addRecipes` / `unhideRecipes`), and REI (`DynamicDisplayGenerator` + `EntryStacks.equalsFuzzy`).
   - Fixed `RemoveEnchantedBooksFunction.MAP_CODEC` and `DepthBasedEfficiencyFunction.MAP_CODEC` from `MapCodec.unit(...)` to `RecordCodecBuilder.mapCodec(i -> commonFields(i).apply(i, ...::new))` so datapack loot predicates actually evaluate.
   - Scoped `sanitizeVillagerInfinityOffers` to `Items.ENCHANTED_BOOK` mutated in-place (preserving merchant uses/discounts/demand) and added `cet_commission_band` custom-data idempotency markers in `DepletedTomeWorldMechanics`.
   - Synced all **11** locales to **621** keys and added `ConfigurationSafetyTest`, `FullPortRuntimeTest`, `NetworkCompatibilityTest`, `RuleArchitectureTest`, `scripts/test_sync_26_1_2.py`, and CI `server-smoke` (`scripts/server_smoke.py`).

---

## 2. Preserved `26.1.2` vs `26.3` Version Differences (`注意差异`)

| Subsystem | `26.3` Branch | `26.1.2` Branch (Preserved / Adapted) |
|---|---|---|
| **Toolchain & Pins** | MC `26.3`, Loader `0.19.5`, Fabric API `0.162.0+26.3`, Loom `1.17-SNAPSHOT`, REI `26.3.+` | MC `26.1.2`, Loader `0.19.3`, Fabric API `0.151.0+26.1.2`, Loom `1.16-SNAPSHOT`, REI `26.1.+` |
| **Client Screen API** | `Minecraft.getInstance().gui.setScreen(...)` / `.gui.screen()` | `Minecraft.getInstance().setScreen(...)` / `.screen` |
| **Block Codec** | `BaseEntityBlock#codec()` removed in `26.3` | `CODEC = simpleCodec(ChiseledEnchantingTableBlock::new)` + `@Override protected MapCodec<? extends BaseEntityBlock> codec()` kept |
| **Player Item Drop** | 3-arg `player.drop(stack, false, Prediction.PREDICTED)` | 2-arg `player.drop(stack, false)` |
| **Block Entity Renderer** | `PoseStack#rotate` / `rotateDegrees` + 9-arg `submitModel` + `submitCrumblingOverlay` | `PoseStack#mulPose` + 10-arg `submitModel(..., renderState.breakProgress)` kept |
| **Loot Java API** | `ContextIntProviders`, `UniformContainerBase`, `Optional<Holder<LootItemCondition>>`, `context.getOptional(...)` | `ConstantValue`, `LootPoolSingletonContainer`, `List<LootItemCondition>` (with `commonFields`), `context.getOptionalParameter(LootContextParams.ORIGIN)` kept |
| **Loot & Advancement JSON** | Single `"condition"` / `"modifier"` objects; `"recipes": [...]` list | Array `"conditions"` / `"functions"` schema (including `scripts/server_smoke.py` probes); `"recipe_id"` / `"recipe"` strings kept |
| **Structure Worldgen** | `implements StructureProcessor`, `BuiltInRegistries.STRUCTURE_PROCESSOR`, `markPosForPostProcessing` | `extends StructureProcessor`, `StructureProcessorType.register(...)`, `markPosForPostprocessing` kept (with full off-thread `computeFill` deadlock fix) |
| **Wire Revision** | `26_3_revision_6` | `26_1_2_revision_6` |

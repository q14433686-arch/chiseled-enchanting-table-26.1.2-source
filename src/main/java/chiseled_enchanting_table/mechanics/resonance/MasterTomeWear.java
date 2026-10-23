package chiseled_enchanting_table.mechanics.resonance;

import chiseled_enchanting_table.registry.BlockRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Pure, data-driven Master Tome charge / wear, {@code minecraft:infinity} immunity, and two-tier
 * library bookshelf regeneration ({@code 38} &amp; {@code 42} shelves) for {@code Enchanted Book}s
 * stored in nearby {@link ChiseledBookShelfBlockEntity} slots.
 *
 * <p>Key invariants:</p>
 * <ul>
 *   <li>Any {@code Enchanted Book} carrying {@code minecraft:infinity} ({@link #hasInfinityImmunity})
 *       is permanently immune to durability wear ({@code 0} charges consumed per use).</li>
 *   <li>Fresh untouched or fully recovered books never carry {@link #NBT_WEAR_KEY} /
 *       {@link #NBT_REGEN_TICKS_KEY} so canonical {@code stack64} stacking is 100% preserved.</li>
 *   <li>All non-immune books in Chiseled Bookshelves passively recover {@code +1} charge at an
 *       interval determined by effective nearby bookshelf count {@code S}, with two resonance
 *       breakthrough tiers at {@code 38} shelves ({@code 3.0} game days = {@code 3,600s/+1}) and
 *       {@code 42} shelves ({@code 1.0} game day = {@code 1,200s/+1}).</li>
 * </ul>
 */
public final class MasterTomeWear {
    public static final Identifier INFINITY_ENCHANTMENT_ID = Identifier.withDefaultNamespace("infinity");
    public static final String NBT_WEAR_KEY = "cet_tome_wear";
    public static final String NBT_REGEN_TICKS_KEY = "cet_tome_regen_ticks";
    public static final String NBT_REGEN_BUCKET_KEY = "cet_tome_regen_bucket";

    /** Sentinel value in synced UI maps indicating an {@code Infinity}-blessed immortal Master Tome. */
    public static final int IMMORTAL_CHARGES = -1;

    public static final int DEFAULT_MAX_TOME_USES = 16;
    public static final int DEFAULT_BOOK_COPY_WEAR_COST = 4;

    public static final boolean DEFAULT_REGEN_ENABLED = true;
    /** 6 Minecraft game days (6 * 1,200s = 7,200s = 144,000 ticks) at 0 shelves. */
    public static final int DEFAULT_REGEN_BASE_SECONDS = 7_200;
    /** Tier 1 breakthrough at 38 effective shelves: 3 Minecraft game days (3,600s = 72,000 ticks) per +1 durability. */
    public static final int DEFAULT_REGEN_TIER1_SHELVES = 38;
    public static final int DEFAULT_REGEN_TIER1_SECONDS = 3_600;
    /** Tier 2 breakthrough at 42 effective shelves: 1 Minecraft game day (1,200s = 24,000 ticks) per +1 durability. */
    public static final int DEFAULT_REGEN_TIER2_SHELVES = 42;
    public static final int DEFAULT_REGEN_TIER2_SECONDS = 1_200;

    private MasterTomeWear() {}

    public enum ConsumptionMode {
        WEAR,
        KEEP,
        CONSUME;

        public String token() {
            return switch (this) {
                case WEAR -> "wear";
                case KEEP -> "keep";
                case CONSUME -> "consume";
            };
        }

        public static ConsumptionMode fromToken(String raw) {
            if (raw == null || raw.isBlank()) {
                return WEAR;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "keep", "infinite", "none" -> KEEP;
                case "consume", "single_use", "deplete" -> CONSUME;
                default -> WEAR;
            };
        }
    }

    public enum DepleteBehavior {
        STEP_DOWN,
        DEPLETED_TOME;

        public String token() {
            return switch (this) {
                case STEP_DOWN -> "step_down";
                case DEPLETED_TOME -> "depleted_tome";
            };
        }

        public static DepleteBehavior fromToken(String raw) {
            if (raw == null || raw.isBlank()) {
                return STEP_DOWN;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "depleted_tome", "deplete", "destroy_level" -> DEPLETED_TOME;
                default -> STEP_DOWN;
            };
        }
    }

    /** Pure headless transition result for unit testing &amp; deterministic shelf updates. */
    public record WearStepResult(
        int nextEnchantLevel,
        int nextWearUsed,
        boolean steppedDown,
        boolean depletedCompletely
    ) {}

    public enum WearOutcome {
        NONE,
        IMMORTAL,
        WORN,
        STEPPED_DOWN,
        DEPLETED
    }

    /**
     * Returns {@code true} iff {@code stack} carries {@code minecraft:infinity} at level {@code >= 1},
     * granting complete Master Tome durability immunity for every enchantment on this book.
     */
    public static boolean hasInfinityImmunity(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        ItemEnchantments enchants = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        for (var entry : enchants.entrySet()) {
            if (entry.getIntValue() <= 0) {
                continue;
            }
            var keyOpt = entry.getKey().unwrapKey();
            if (keyOpt.isPresent() && INFINITY_ENCHANTMENT_ID.equals(keyOpt.get().identifier())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code 0} below Tier 1 ({@code < 38}), {@code 1} for Tier 1 ({@code 38..41}),
     * and {@code 2} for Tier 2 ({@code >= 42}).
     */
    public static int regenTierIndex(LibraryResonance.Config cfg, int nearbyBookshelves) {
        LibraryResonance.Config c = cfg != null ? cfg : LibraryResonance.Config.DEFAULT;
        int shelves = Math.max(0, nearbyBookshelves);
        if (shelves >= c.tomeRegenTier2Shelves()) {
            return 2;
        }
        if (shelves >= c.tomeRegenTier1Shelves()) {
            return 1;
        }
        return 0;
    }

    /**
     * Computes exact game ticks ({@code 20 ticks = 1.0s}) required to passively regenerate {@code +1}
     * Master Tome durability charge at {@code nearbyBookshelves}, featuring two resonance breakthroughs
     * at {@code tier1Shelves} (default {@code 38 -> 3,600s}) and {@code tier2Shelves} (default {@code 42 -> 1,200s}).
     */
    public static int computeRegenIntervalTicks(LibraryResonance.Config cfg, int nearbyBookshelves) {
        LibraryResonance.Config c = cfg != null ? cfg : LibraryResonance.Config.DEFAULT;
        if (!c.tomeRegenEnabled()) {
            return Integer.MAX_VALUE;
        }
        int s = Math.max(0, nearbyBookshelves);
        int s1 = Math.max(1, c.tomeRegenTier1Shelves());
        int s2 = Math.max(s1, c.tomeRegenTier2Shelves());

        int baseTicks = Math.max(20, c.tomeRegenBaseSeconds() * 20);
        int tier1Ticks = Math.max(20, Math.min(baseTicks, c.tomeRegenTier1Seconds() * 20));
        int tier2Ticks = Math.max(20, Math.min(tier1Ticks, c.tomeRegenTier2Seconds() * 20));

        if (s < s1) {
            double mid1 = Math.max(tier1Ticks, 0.5 * (baseTicks + tier1Ticks));
            double raw = baseTicks - ((baseTicks - mid1) * s) / (double) s1;
            return Math.max(tier1Ticks, (int) Math.round(raw));
        }
        if (s < s2) {
            double mid2 = Math.max(tier2Ticks, 0.5 * (tier1Ticks + tier2Ticks));
            double span = Math.max(1, s2 - s1);
            double raw = tier1Ticks - ((tier1Ticks - mid2) * (s - s1)) / span;
            return Math.max(tier2Ticks, (int) Math.round(raw));
        }
        int excess = s - s2;
        int floorTicks = Math.max(20, tier2Ticks / 2);
        int scaled = (int) Math.round(tier2Ticks / (1.0 + 0.05 * excess));
        return Math.max(floorTicks, Math.min(tier2Ticks, scaled));
    }

    public static double computeRegenSeconds(LibraryResonance.Config cfg, int nearbyBookshelves) {
        return computeRegenIntervalTicks(cfg, nearbyBookshelves) / 20.0;
    }

    public static WearStepResult computeWearStep(
        ConsumptionMode mode,
        DepleteBehavior depleteBehavior,
        int maxTomeUses,
        int bookCopyWearCost,
        int currentEnchantLevel,
        int currentWearUsed,
        boolean targetIsBook
    ) {
        return computeWearStep(
            mode,
            depleteBehavior,
            maxTomeUses,
            bookCopyWearCost,
            currentEnchantLevel,
            currentWearUsed,
            targetIsBook,
            false
        );
    }

    public static WearStepResult computeWearStep(
        ConsumptionMode mode,
        DepleteBehavior depleteBehavior,
        int maxTomeUses,
        int bookCopyWearCost,
        int currentEnchantLevel,
        int currentWearUsed,
        boolean targetIsBook,
        boolean hasInfinityEnchant
    ) {
        int safeLevel = Math.max(1, currentEnchantLevel);
        int safeMaxUses = Math.max(1, maxTomeUses);
        if (hasInfinityEnchant || mode == ConsumptionMode.KEEP) {
            int preservedWear = hasInfinityEnchant ? 0 : Math.max(0, Math.min(safeMaxUses - 1, currentWearUsed));
            return new WearStepResult(safeLevel, preservedWear, false, false);
        }
        int safeWear = Math.max(0, Math.min(safeMaxUses - 1, currentWearUsed));
        int delta = mode == ConsumptionMode.CONSUME
            ? safeMaxUses
            : (targetIsBook ? Math.max(1, bookCopyWearCost) : 1);
        int updatedWear = safeWear + delta;
        if (updatedWear < safeMaxUses) {
            return new WearStepResult(safeLevel, updatedWear, false, false);
        }
        if (mode == ConsumptionMode.CONSUME || depleteBehavior == DepleteBehavior.DEPLETED_TOME || safeLevel <= 1) {
            return new WearStepResult(0, 0, false, true);
        }
        return new WearStepResult(safeLevel - 1, 0, true, false);
    }

    public static int wearCostForTarget(LibraryResonance.Config cfg, boolean targetIsBook) {
        LibraryResonance.Config c = cfg != null ? cfg : LibraryResonance.Config.DEFAULT;
        return switch (c.tomeConsumptionMode()) {
            case KEEP -> 0;
            case CONSUME -> c.maxTomeUses();
            case WEAR -> targetIsBook ? c.bookCopyWearCost() : 1;
        };
    }

    public static int getWearUsed(ItemStack stack) {
        if (stack == null || stack.isEmpty() || hasInfinityImmunity(stack)) {
            return 0;
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) {
            return 0;
        }
        CompoundTag root = customData.copyTag();
        return Math.max(0, root.getIntOr(NBT_WEAR_KEY, 0));
    }

    public static int remainingCharges(ItemStack stack, LibraryResonance.Config cfg) {
        LibraryResonance.Config c = cfg != null ? cfg : LibraryResonance.Config.DEFAULT;
        int maxUses = Math.max(1, c.maxTomeUses());
        int wearUsed = getWearUsed(stack);
        return Math.max(1, maxUses - wearUsed);
    }

    public static void setWearUsed(ItemStack stack, int wearUsed) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        int clamped = hasInfinityImmunity(stack) ? 0 : Math.max(0, wearUsed);
        CustomData existing = stack.get(DataComponents.CUSTOM_DATA);
        if (clamped == 0) {
            if (existing == null || existing.isEmpty()) {
                return;
            }
            CompoundTag tag = existing.copyTag();
            tag.remove(NBT_WEAR_KEY);
            tag.remove(NBT_REGEN_TICKS_KEY);
            tag.remove(NBT_REGEN_BUCKET_KEY);
            if (tag.isEmpty()) {
                stack.remove(DataComponents.CUSTOM_DATA);
            } else {
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            }
            return;
        }
        CompoundTag tag = existing != null ? existing.copyTag() : new CompoundTag();
        tag.putInt(NBT_WEAR_KEY, clamped);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private record CandidateSlot(
        ChiseledBookShelfBlockEntity shelf,
        BlockPos pos,
        BlockState state,
        int slot,
        ItemStack stack,
        Holder<Enchantment> holder,
        int storedLevel,
        int wearUsed,
        boolean infinite
    ) {}

    private static final Comparator<CandidateSlot> CANDIDATE_ORDER = Comparator
        .comparing((CandidateSlot c) -> !c.infinite())
        .thenComparingInt(CandidateSlot::storedLevel)
        .thenComparing(Comparator.comparingInt(CandidateSlot::wearUsed).reversed());

    private static List<CandidateSlot> collectCandidates(
        Level level,
        BlockPos tablePos,
        BookshelfProgression.Config shelfCfg,
        Holder<Enchantment> targetHolder,
        int requiredLevel
    ) {
        if (shelfCfg != null && shelfCfg.requireBreathingRoom()) {
            boolean ceilingBlocked = chiseled_enchanting_table.block.ChiseledEnchantingTableBlock.isTableCeilingBlocked(level, tablePos);
            int openAdj = chiseled_enchanting_table.block.ChiseledEnchantingTableBlock.countOpenAdjacentBlocks(level, tablePos);
            if (BookshelfProgression.effectiveShelvesAfterBreathingRoom(1, ceilingBlocked, openAdj, shelfCfg) <= 0) {
                return List.of();
            }
        }
        List<CandidateSlot> candidates = new ArrayList<>();
        boolean checkLos = shelfCfg != null && shelfCfg.requireLineOfSight();
        for (BlockPos offset : chiseled_enchanting_table.block.ChiseledEnchantingTableBlock.getScanOffsets(shelfCfg)) {
            BlockPos scanPos = tablePos.offset(offset);
            if (!level.hasChunkAt(scanPos)) continue;
            if (checkLos && !chiseled_enchanting_table.block.ChiseledEnchantingTableBlock.hasClearLineOfSight(level, tablePos, scanPos)) {
                continue;
            }
            BlockState st = level.getBlockState(scanPos);
            if (!st.is(Blocks.CHISELED_BOOKSHELF)) continue;
            if (!(level.getBlockEntity(scanPos) instanceof ChiseledBookShelfBlockEntity cbs)) continue;
            for (int slot = 0; slot < ChiseledBookShelfBlockEntity.MAX_BOOKS_IN_STORAGE; slot++) {
                ItemStack slotStack = cbs.getItem(slot);
                if (!slotStack.is(Items.ENCHANTED_BOOK)) continue;
                int storedLvl = EnchantmentHelper.getEnchantmentsForCrafting(slotStack).getLevel(targetHolder);
                if (storedLvl >= requiredLevel) {
                    boolean inf = hasInfinityImmunity(slotStack);
                    candidates.add(new CandidateSlot(
                        cbs,
                        scanPos.immutable(),
                        st,
                        slot,
                        slotStack,
                        targetHolder,
                        storedLvl,
                        inf ? 0 : getWearUsed(slotStack),
                        inf
                    ));
                }
            }
        }
        candidates.sort(CANDIDATE_ORDER);
        return candidates;
    }

    /**
     * Builds the exact per-{@link chiseled_enchanting_table.utils.EnchantmentWithLevel} live Master Tome
     * charge map using the identical candidate order used when applying wear ({@link #IMMORTAL_CHARGES}
     * when backed by an {@code Infinity} tome, or exact remaining charges {@code 1..maxTomeUses}).
     */
    public static Map<chiseled_enchanting_table.utils.EnchantmentWithLevel, Integer> snapshotLiveMasterCharges(
        Level level,
        BlockPos tablePos,
        BookshelfProgression.Config shelfCfg,
        LibraryResonance.Config resonanceCfg,
        Map<Identifier, Integer> storedMasterLevels
    ) {
        if (level == null || tablePos == null || storedMasterLevels == null || storedMasterLevels.isEmpty()) {
            return Map.of();
        }
        LibraryResonance.Config cfg = resonanceCfg != null ? resonanceCfg : LibraryResonance.Config.DEFAULT;
        Map<chiseled_enchanting_table.utils.EnchantmentWithLevel, Integer> out = new HashMap<>();
        var reg = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        for (var entry : storedMasterLevels.entrySet()) {
            Identifier id = entry.getKey();
            int topLvl = Math.min(255, Math.max(1, entry.getValue()));
            Holder<Enchantment> holder;
            try {
                holder = reg.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, id));
            } catch (Exception ignored) {
                continue;
            }
            List<CandidateSlot> allForId = collectCandidates(level, tablePos, shelfCfg, holder, 1);
            if (allForId.isEmpty()) {
                continue;
            }
            for (int reqLvl = 1; reqLvl <= topLvl; reqLvl++) {
                for (CandidateSlot c : allForId) {
                    if (c.storedLevel() >= reqLvl) {
                        int chargeVal = c.infinite()
                            ? IMMORTAL_CHARGES
                            : Math.max(1, cfg.maxTomeUses() - c.wearUsed());
                        out.put(new chiseled_enchanting_table.utils.EnchantmentWithLevel(id, reqLvl), chargeVal);
                        break;
                    }
                }
            }
        }
        return Map.copyOf(out);
    }

    /**
     * Passively ticks Master Tome durability recovery once every {@code 20} game ticks ({@code 1s})
     * for all non-{@code Infinity} worn books in nearby Chiseled Bookshelves.
     *
     * @return {@code true} if at least one stored book recovered one or more durability points.
     */
    public static boolean tickLibraryRegeneration(
        Level level,
        BlockPos tablePos,
        BookshelfProgression.Config shelfCfg,
        LibraryResonance.Config resonanceCfg,
        int nearbyBookshelves
    ) {
        LibraryResonance.Config cfg = resonanceCfg != null ? resonanceCfg : LibraryResonance.Config.DEFAULT;
        WorldRegeneration.observe(level, tablePos, shelfCfg, cfg, nearbyBookshelves);
        return false; // settlement happens once at END_WORLD_TICK, never per-table here
    }

    /**
     * Applies Master Tome wear (or zero wear if blessed with {@code minecraft:infinity}) inside
     * surrounding Chiseled Bookshelves when a Master-Tome-backed enchantment action succeeds.
     */
    public static WearOutcome applyShelfWear(
        Level level,
        BlockPos tablePos,
        BookshelfProgression.Config shelfCfg,
        LibraryResonance.Config resonanceCfg,
        Identifier enchantmentId,
        int requiredLevel,
        boolean targetIsBook
    ) {
        LibraryResonance.Config cfg = resonanceCfg != null ? resonanceCfg : LibraryResonance.Config.DEFAULT;
        if (level == null || level.isClientSide() || tablePos == null || enchantmentId == null) {
            return WearOutcome.NONE;
        }
        if (cfg.tomeConsumptionMode() == ConsumptionMode.KEEP) {
            return WearOutcome.NONE;
        }

        Holder<Enchantment> targetHolder;
        try {
            targetHolder = level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, enchantmentId));
        } catch (Exception ignored) {
            return WearOutcome.NONE;
        }

        List<CandidateSlot> candidates = collectCandidates(level, tablePos, shelfCfg, targetHolder, requiredLevel);
        if (candidates.isEmpty()) {
            return WearOutcome.NONE;
        }

        CandidateSlot chosen = candidates.getFirst();
        if (chosen.infinite()) {
            return WearOutcome.IMMORTAL;
        }

        WearStepResult step = computeWearStep(
            cfg.tomeConsumptionMode(),
            cfg.tomeDepleteBehavior(),
            cfg.maxTomeUses(),
            cfg.bookCopyWearCost(),
            chosen.storedLevel(),
            chosen.wearUsed(),
            targetIsBook,
            false
        );

        ItemStack mutated = chosen.stack().copy();
        ItemEnchantments currentEnchants = EnchantmentHelper.getEnchantmentsForCrafting(mutated);
        ItemEnchantments.Mutable nextEnchants = new ItemEnchantments.Mutable(currentEnchants);
        nextEnchants.set(chosen.holder(), Math.max(0, step.nextEnchantLevel()));

        EnchantmentHelper.updateEnchantments(mutated, m -> {
            var keys = new HashSet<>(m.keySet());
            for (var k : keys) m.set(k, 0);
            for (var key : nextEnchants.keySet()) {
                int lv = nextEnchants.getLevel(key);
                if (lv > 0) {
                    m.set(key, lv);
                }
            }
        });

        WearOutcome outcome;
        if (EnchantmentHelper.getEnchantmentsForCrafting(mutated).isEmpty()) {
            mutated = new ItemStack(BlockRegistry.DEPLETED_TOME, 1);
            outcome = WearOutcome.DEPLETED;
        } else {
            setWearUsed(mutated, step.nextWearUsed());
            chiseled_enchanting_table.mechanics.enchanting.EnchantmentProvenance.pruneToCurrentEnchantments(
                mutated,
                EnchantmentHelper.getEnchantmentsForCrafting(mutated)
            );
            outcome = step.steppedDown() ? WearOutcome.STEPPED_DOWN : WearOutcome.WORN;
        }

        chosen.shelf().setItem(chosen.slot(), mutated);
        chosen.shelf().setChanged();
        if (outcome == WearOutcome.WORN) {
            WorldRegeneration.acknowledgeInPlaceWear(
                level,
                chosen.pos(),
                chosen.slot(),
                chosen.stack(),
                mutated,
                step.nextWearUsed()
            );
        }
        BlockState latestState = level.getBlockState(chosen.pos());
        level.sendBlockUpdated(chosen.pos(), chosen.state(), latestState, Block.UPDATE_ALL);

        if (outcome == WearOutcome.DEPLETED || outcome == WearOutcome.STEPPED_DOWN) {
            level.playSound(null, chosen.pos(), SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1.0F, 0.72F);
        }
        return outcome;
    }
}

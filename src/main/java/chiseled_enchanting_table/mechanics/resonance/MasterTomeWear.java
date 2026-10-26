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
    // Pure item-data surface lives in TomeWearData (audit COUP-03 split); these aliases
    // keep every existing caller compiling while there is exactly one implementation.
    public static final Identifier INFINITY_ENCHANTMENT_ID = TomeWearData.INFINITY_ENCHANTMENT_ID;
    public static final Identifier UNBREAKING_ENCHANTMENT_ID = TomeWearData.UNBREAKING_ENCHANTMENT_ID;
    public static final String NBT_WEAR_KEY = TomeWearData.NBT_WEAR_KEY;
    public static final String NBT_REGEN_TICKS_KEY = TomeWearData.NBT_REGEN_TICKS_KEY;
    public static final String NBT_REGEN_BUCKET_KEY = TomeWearData.NBT_REGEN_BUCKET_KEY;
    public static final String NBT_GENERATION_KEY = TomeWearData.NBT_GENERATION_KEY;

    /** Sentinel value in synced UI maps indicating an {@code Infinity}-blessed immortal Master Tome. */
    public static final int IMMORTAL_CHARGES = -1;

    // Bundled defaults are generated from library_resonance.json (BundledDefaults);
    // never retype the numbers here.
    public static final int DEFAULT_MAX_TOME_USES = chiseled_enchanting_table.BundledDefaults.MAX_TOME_USES;
    public static final int DEFAULT_BOOK_COPY_WEAR_COST = chiseled_enchanting_table.BundledDefaults.BOOK_COPY_WEAR_COST;
    public static final double DEFAULT_UNBREAKING_WAIVER_PER_LEVEL = chiseled_enchanting_table.BundledDefaults.UNBREAKING_WAIVER_PER_LEVEL;
    public static final double DEFAULT_UNBREAKING_WAIVER_MAX_CHANCE = chiseled_enchanting_table.BundledDefaults.UNBREAKING_WAIVER_MAX_CHANCE;
    public static final boolean DEFAULT_WILD_BOOK_WEAR_ENABLED = chiseled_enchanting_table.BundledDefaults.WILD_BOOK_WEAR_ENABLED;
    /** Default weights for remaining charges {@code 1, 2, 3, 4}: {@code 80%} of wild books spawn with {@code 1–2} durability. */
    public static final List<Integer> DEFAULT_WILD_REMAINING_WEIGHTS = chiseled_enchanting_table.BundledDefaults.WILD_REMAINING_WEIGHTS;

    public static final boolean DEFAULT_REGEN_ENABLED = chiseled_enchanting_table.BundledDefaults.TOME_REGEN_ENABLED;
    /** 6 Minecraft game days (6 * 1,200s = 7,200s = 144,000 ticks) at 0 shelves. */
    public static final int DEFAULT_REGEN_BASE_SECONDS = chiseled_enchanting_table.BundledDefaults.TOME_REGEN_BASE_SECONDS;
    /** Tier 1 breakthrough at 38 effective shelves: 3 Minecraft game days (3,600s = 72,000 ticks) per +1 durability. */
    public static final int DEFAULT_REGEN_TIER1_SHELVES = chiseled_enchanting_table.BundledDefaults.TOME_REGEN_TIER1_SHELVES;
    public static final int DEFAULT_REGEN_TIER1_SECONDS = chiseled_enchanting_table.BundledDefaults.TOME_REGEN_TIER1_SECONDS;
    /** Tier 2 breakthrough at 42 effective shelves: 1 Minecraft game day (1,200s = 24,000 ticks) per +1 durability. */
    public static final int DEFAULT_REGEN_TIER2_SHELVES = chiseled_enchanting_table.BundledDefaults.TOME_REGEN_TIER2_SHELVES;
    public static final int DEFAULT_REGEN_TIER2_SECONDS = chiseled_enchanting_table.BundledDefaults.TOME_REGEN_TIER2_SECONDS;

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
        WAIVED_BY_UNBREAKING,
        WORN,
        STEPPED_DOWN,
        DEPLETED
    }

    /** Detailed per-step attribution &amp; wear result returning the exact chosen Master Tome stack. */
    public record ResolvedTomeUse(
        WearOutcome outcome,
        boolean usedInfinityTome,
        ItemStack chosenTomeSnapshot
    ) {
        public static final ResolvedTomeUse NONE = new ResolvedTomeUse(WearOutcome.NONE, false, ItemStack.EMPTY);
    }

    /** Returns the stored {@code minecraft:unbreaking} level on {@code stack} ({@code 0} if absent). */
    public static int getUnbreakingLevel(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        ItemEnchantments enchants = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        for (var entry : enchants.entrySet()) {
            int lv = entry.getIntValue();
            if (lv <= 0) {
                continue;
            }
            var keyOpt = entry.getKey().unwrapKey();
            if (keyOpt.isPresent() && UNBREAKING_ENCHANTMENT_ID.equals(keyOpt.get().identifier())) {
                return lv;
            }
        }
        return 0;
    }

    /**
     * Computes the probability {@code [0.0, 0.30]} that a non-{@code Infinity} template book carrying
     * {@code minecraft:unbreaking} at {@code unbreakingLevel} waives durability loss on use.
     */
    public static double computeUnbreakingWaiverChance(LibraryResonance.Config cfg, int unbreakingLevel) {
        if (unbreakingLevel <= 0) {
            return 0.0;
        }
        LibraryResonance.Config c = cfg != null ? cfg : LibraryResonance.Config.DEFAULT;
        double cap = Math.max(0.0, Math.min(0.30, c.unbreakingWaiverMaxChance()));
        double perLvl = Math.max(0.0, c.unbreakingWaiverPerLevel());
        return Math.min(cap, unbreakingLevel * perLvl);
    }

    public static double computeUnbreakingWaiverChance(int unbreakingLevel, LibraryResonance.Config cfg) {
        return computeUnbreakingWaiverChance(cfg, unbreakingLevel);
    }

    public static double computeUnbreakingWaiverChance(ItemStack stack, LibraryResonance.Config cfg) {
        return computeUnbreakingWaiverChance(cfg, getUnbreakingLevel(stack));
    }

    /**
     * Rolls remaining durability charges {@code [1..maxTomeUses]} for a wild-spawned Enchanted Book
     * using {@code remainingWeights} (default {@code [45, 35, 15, 5]} -> {@code 80%} have {@code 1–2} left).
     */
    public static int rollWildRemainingCharges(
        net.minecraft.util.RandomSource random,
        int maxTomeUses,
        List<Integer> remainingWeights
    ) {
        int safeMax = Math.max(1, maxTomeUses);
        if (safeMax == 1) {
            return 1;
        }
        List<Integer> weights = (remainingWeights != null && !remainingWeights.isEmpty())
            ? remainingWeights
            : DEFAULT_WILD_REMAINING_WEIGHTS;
        int[] bucketWeights = new int[safeMax];
        long totalWeight = 0L;
        for (int idx = 0; idx < safeMax; idx++) {
            int w;
            if (idx < weights.size()) {
                w = Math.max(0, weights.get(idx));
            } else {
                w = Math.max(1, weights.getLast());
            }
            bucketWeights[idx] = w;
            totalWeight += w;
        }
        if (totalWeight <= 0L) {
            return 1;
        }
        long pick = Math.floorMod(random.nextLong(), totalWeight);
        long acc = 0L;
        for (int idx = 0; idx < safeMax; idx++) {
            acc += bucketWeights[idx];
            if (pick < acc) {
                return idx + 1;
            }
        }
        return safeMax;
    }

    /**
     * Applies pre-wear to a newly rolled wild {@code Enchanted Book} (chest loot or wild Chiseled Bookshelf),
     * skipping {@code Infinity} books and preserving clean NBT when full durability is rolled.
     */
    public static void applyWildBookWear(
        ItemStack stack,
        net.minecraft.util.RandomSource random,
        LibraryResonance.Config cfg
    ) {
        if (stack == null || !stack.is(Items.ENCHANTED_BOOK) || hasInfinityImmunity(stack)) {
            return;
        }
        LibraryResonance.Config c = cfg != null ? cfg : LibraryResonance.Config.DEFAULT;
        if (!c.wildBookWearEnabled() || c.tomeConsumptionMode() != ConsumptionMode.WEAR) {
            return;
        }
        int safeMax = Math.max(1, c.maxTomeUses());
        if (safeMax <= 1) {
            return;
        }
        int remaining = rollWildRemainingCharges(random, safeMax, c.wildBookRemainingWeights());
        int wearUsed = Math.max(0, Math.min(safeMax - 1, safeMax - remaining));
        if (wearUsed > 0) {
            setWearUsed(stack, wearUsed);
        }
    }

    /**
     * Returns {@code true} iff {@code stack} carries an active {@code #wear_immune} enchantment AND
     * its copy lineage generation does not exceed {@code cfg.wearImmuneMaxGeneration()}.
     */
    public static boolean hasEffectiveWearImmunity(LibraryResonance.Config cfg, ItemStack stack) {
        LibraryResonance.Config c = cfg != null ? cfg : LibraryResonance.Config.DEFAULT;
        return TomeWearData.hasEffectiveWearImmunity(stack, c.wearImmuneMaxGeneration());
    }

    /**
     * Returns {@code true} iff {@code stack} qualifies for effective wear immunity under the active
     * server resonance configuration.
     */
    public static boolean hasInfinityImmunity(ItemStack stack) {
        return hasEffectiveWearImmunity(LibraryResonance.activeServerConfig(), stack);
    }

    /** Wear immunity via {@code #chiseled_enchanting_table:wear_immune} (ships with Infinity). */
    public static boolean hasWearImmunity(ItemStack stack) {
        return TomeWearData.hasWearImmunity(stack);
    }

    public static int getGeneration(ItemStack stack) {
        return TomeWearData.getGeneration(stack);
    }

    public static void setGeneration(ItemStack stack, int generation) {
        TomeWearData.setGeneration(stack, generation);
    }

    public static int stampInheritedGeneration(ItemStack stack, int inheritedGeneration) {
        return TomeWearData.stampInheritedGeneration(stack, inheritedGeneration);
    }

    public static int effectiveMaxUses(ItemStack stack, LibraryResonance.Config cfg) {
        LibraryResonance.Config c = cfg != null ? cfg : LibraryResonance.Config.DEFAULT;
        return TomeWearData.effectiveMaxUses(stack, c.maxTomeUses(), c.wearImmuneMaxGeneration());
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
     * Master Tome durability charge at {@code nearbyBookshelves}, interpolating piecewise-linearly
     * with zero step cliffs through {@code (0, baseTicks)} -&gt; {@code (tier1Shelves=38, tier1Ticks=3600s)}
     * -&gt; {@code (tier2Shelves=42, tier2Ticks=1200s)}.
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

        if (s <= s1) {
            double raw = baseTicks - ((double) (baseTicks - tier1Ticks) * s) / (double) s1;
            return Math.max(tier1Ticks, (int) Math.round(raw));
        }
        if (s < s2) {
            double span = Math.max(1, s2 - s1);
            double raw = tier1Ticks - ((double) (tier1Ticks - tier2Ticks) * (s - s1)) / span;
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
        if (mode == ConsumptionMode.CONSUME) {
            return new WearStepResult(0, 0, false, true);
        }
        // Historical flat scapegoat pricing (kept for the datapack "flat" mode and
        // for every pre-existing caller/test): any sacrifice refills to full.
        int overflowWear = TomeWearData.computeOverflowNextWear(
            TomeWearData.ScapegoatPricing.FLAT, safeMaxUses, 0, 0);
        if (depleteBehavior == DepleteBehavior.DEPLETED_TOME || safeLevel <= 1) {
            return new WearStepResult(0, overflowWear, false, true);
        }
        return new WearStepResult(safeLevel - 1, overflowWear, true, false);
    }

    /**
     * Essence-priced wear step: {@code maxTomeUses} must already be the tome's
     * generation-faded effective cap. On overflow the sacrifice only buys back
     * charges in proportion to {@code burnedEssence / topEssenceInTome} (design
     * audit case 2: "献祭看诚意" — burning a junk entry must not fully refill a
     * tome whose value sits in another entry).
     */
    public static WearStepResult computeWearStep(
        ConsumptionMode mode,
        DepleteBehavior depleteBehavior,
        int maxTomeUses,
        int bookCopyWearCost,
        int currentEnchantLevel,
        int currentWearUsed,
        boolean targetIsBook,
        boolean hasInfinityEnchant,
        TomeWearData.ScapegoatPricing scapegoatPricing,
        int burnedEssence,
        int topEssenceInTome
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
        if (mode == ConsumptionMode.CONSUME) {
            return new WearStepResult(0, 0, false, true);
        }
        int overflowWear = TomeWearData.computeOverflowNextWear(
            scapegoatPricing, safeMaxUses, burnedEssence, topEssenceInTome);
        if (depleteBehavior == DepleteBehavior.DEPLETED_TOME || safeLevel <= 1) {
            return new WearStepResult(0, overflowWear, false, true);
        }
        return new WearStepResult(safeLevel - 1, overflowWear, true, false);
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
        return TomeWearData.getWearUsed(stack);
    }

    /** Remaining charges against the generation-faded cap (never below 1 while the entry exists). */
    public static int remainingCharges(ItemStack stack, LibraryResonance.Config cfg) {
        LibraryResonance.Config c = cfg != null ? cfg : LibraryResonance.Config.DEFAULT;
        return TomeWearData.remainingCharges(stack, c.maxTomeUses(), c.wearImmuneMaxGeneration());
    }

    public static void setWearUsed(ItemStack stack, int wearUsed) {
        TomeWearData.setWearUsed(stack, wearUsed);
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
        LibraryResonance.Config resonanceCfg,
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
        LibraryResonance.Config cfg = resonanceCfg != null ? resonanceCfg : LibraryResonance.Config.DEFAULT;
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
                // Player-locked tomes keep regenerating but can never back an enchantment.
                // 1.0.3 Classic ignores lock state entirely (no locks existed then).
                if (chiseled_enchanting_table.data.ChiseledEnchantingTableData.runtimeConfiguration().tomeLocksEnabled()
                    && TomeLockData.isEffectivelyLocked(slotStack)) continue;
                int storedLvl = EnchantmentHelper.getEnchantmentsForCrafting(slotStack).getLevel(targetHolder);
                if (storedLvl >= requiredLevel) {
                    boolean inf = hasEffectiveWearImmunity(cfg, slotStack);
                    candidates.add(new CandidateSlot(
                        cbs,
                        scanPos.immutable(),
                        st,
                        slot,
                        slotStack,
                        targetHolder,
                        storedLvl,
                        getWearUsed(slotStack),
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
     * when backed by an unoverloaded {@code #wear_immune} tome within generation bounds, or exact remaining charges).
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
            List<CandidateSlot> allForId = collectCandidates(level, tablePos, shelfCfg, cfg, holder, 1);
            if (allForId.isEmpty()) {
                continue;
            }
            for (int reqLvl = 1; reqLvl <= topLvl; reqLvl++) {
                for (CandidateSlot c : allForId) {
                    if (c.storedLevel() >= reqLvl) {
                        boolean fullImmortal = c.infinite()
                            && c.wearUsed() == 0
                            && TomeWearData.countNonCurseStoredEnchantments(c.stack()) <= cfg.wearImmuneFullWaiverMaxEnchants();
                        int chargeVal = fullImmortal
                            ? IMMORTAL_CHARGES
                            : TomeWearData.remainingCharges(
                                cfg.maxTomeUses(),
                                TomeWearData.getGeneration(c.stack()),
                                c.wearUsed(),
                                TomeWearData.hasWearImmunity(c.stack()),
                                cfg.wearImmuneMaxGeneration()
                            );
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
     * Resolves the exact Master Tome backing {@code (enchantmentId, requiredLevel)} and applies wear
     * (honoring {@code Infinity} immunity and {@code Unbreaking} waiver up to {@code <= 30%}), returning
     * a snapshot of the exact chosen tome so downstream ritual/cross-line checks bind strictly to the
     * tome that was actually used.
     */
    public static ResolvedTomeUse applyShelfWearDetailed(
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
            return ResolvedTomeUse.NONE;
        }

        Holder<Enchantment> targetHolder;
        try {
            targetHolder = level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, enchantmentId));
        } catch (Exception ignored) {
            return ResolvedTomeUse.NONE;
        }

        List<CandidateSlot> candidates = collectCandidates(level, tablePos, shelfCfg, cfg, targetHolder, requiredLevel);
        if (candidates.isEmpty()) {
            return ResolvedTomeUse.NONE;
        }

        CandidateSlot chosen = candidates.getFirst();
        ItemStack chosenSnapshot = chosen.stack().copy();
        boolean usedWearImmuneTome = chosen.infinite();
        if (usedWearImmuneTome) {
            int nonCurseCount = TomeWearData.countNonCurseStoredEnchantments(chosenSnapshot);
            double immuneWaiver = TomeWearData.computeWearImmuneWaiverChance(cfg, nonCurseCount);
            double unbreakingWaiver = computeUnbreakingWaiverChance(cfg, getUnbreakingLevel(chosenSnapshot));
            double effectiveWaiver = Math.max(immuneWaiver, unbreakingWaiver);
            if (effectiveWaiver >= 1.0 || level.getRandom().nextDouble() < effectiveWaiver) {
                return new ResolvedTomeUse(WearOutcome.IMMORTAL, true, chosenSnapshot);
            }
        }
        if (cfg.tomeConsumptionMode() == ConsumptionMode.KEEP) {
            return new ResolvedTomeUse(WearOutcome.NONE, usedWearImmuneTome, chosenSnapshot);
        }

        if (!usedWearImmuneTome) {
            int unbreakingLvl = getUnbreakingLevel(chosenSnapshot);
            double waiveChance = computeUnbreakingWaiverChance(cfg, unbreakingLvl);
            if (waiveChance > 0.0 && level.getRandom().nextDouble() < waiveChance) {
                return new ResolvedTomeUse(WearOutcome.WAIVED_BY_UNBREAKING, false, chosenSnapshot);
            }
        }

        // Generation fade + essence-priced scapegoat: price the worn entry against the
        // most valuable entry actually stored on this tome (scale 1.0 = raw potential,
        // independent of the player's refund settings).
        int burnedEssence = chiseled_enchanting_table.mechanics.enchanting.DisenchantmentApplication.essencePotential(
            cfg, chosen.holder(), chosen.storedLevel(), 1.0);
        int topEssence = burnedEssence;
        for (var stored : EnchantmentHelper.getEnchantmentsForCrafting(chosenSnapshot).entrySet()) {
            topEssence = Math.max(topEssence,
                chiseled_enchanting_table.mechanics.enchanting.DisenchantmentApplication.essencePotential(
                    cfg, stored.getKey(), stored.getIntValue(), 1.0));
        }
        WearStepResult step = computeWearStep(
            cfg.tomeConsumptionMode(),
            cfg.tomeDepleteBehavior(),
            TomeWearData.effectiveMaxUses(chosenSnapshot, cfg.maxTomeUses(), cfg.wearImmuneMaxGeneration()),
            cfg.bookCopyWearCost(),
            chosen.storedLevel(),
            chosen.wearUsed(),
            targetIsBook,
            false,
            cfg.scapegoatPricing(),
            burnedEssence,
            topEssence
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
            // Remember which enchantment consumed the charges: the Tome page names it
            // next to the recharge timers (a tome's durability belongs to one enchantment
            // at a time, and step-down also only ever touches that one).
            ItemStack recordedStack = mutated;
            chosen.holder().unwrapKey().ifPresent(key ->
                TomeWearData.recordLastUsed(recordedStack, key.identifier(), chosen.storedLevel()));
            chiseled_enchanting_table.mechanics.enchanting.EnchantmentProvenance.pruneToCurrentEnchantments(
                mutated,
                EnchantmentHelper.getEnchantmentsForCrafting(mutated)
            );
            outcome = step.steppedDown() ? WearOutcome.STEPPED_DOWN : WearOutcome.WORN;
        }

        chosen.shelf().setItem(chosen.slot(), mutated);
        chosen.shelf().setChanged();
        if ((outcome == WearOutcome.WORN || outcome == WearOutcome.STEPPED_DOWN) && step.nextWearUsed() > 0) {
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
        // Round 4 particle cue: full depletion of the consumed enchantment (level
        // removed or tome reduced to a Depleted Tome) gets teleport particles.
        boolean fullyDepleted = step.nextEnchantLevel() <= 0 || outcome == WearOutcome.DEPLETED;
        if (fullyDepleted && cfg.tomeConsumptionMode() != ConsumptionMode.KEEP) {
            TomeParticleEffects.portalAt(level, chosen.pos(), chosen.slot());
        }
        return new ResolvedTomeUse(outcome, usedWearImmuneTome, chosenSnapshot);
    }
}

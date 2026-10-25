package chiseled_enchanting_table.mechanics.enchanting;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.mechanics.resonance.LibraryResonance;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.ApplyEnchantmentPayload;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.OptionType;
import chiseled_enchanting_table.menu.ChiseledEnchantingTableScreenHandler;
import chiseled_enchanting_table.registry.BlockRegistry;
import chiseled_enchanting_table.utils.Advancement;
import chiseled_enchanting_table.utils.EnchantmentWithLevel;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.Vec3;

/**
 * Unified, single-source disenchanting, step-down downgrading, targeted single-trait stripping,
 * and curse purification mechanic with cached inductive XP conservation across all levels {@code 1..255}
 * and level-slice provenance tracking ({@code D3}).
 */
public final class DisenchantmentApplication {
    public static final Identifier DISENCHANT_ALL_ID = ChiseledEnchantingTable.identifier("disenchant_all");
    public static final Identifier STRIP_SINGLE_ID = ChiseledEnchantingTable.identifier("strip_single");
    public static final Identifier PURGE_CURSE_ID = ChiseledEnchantingTable.identifier("purge_curse");
    public static final int CONSERVATION_SAFETY_MARGIN_POINTS =
        chiseled_enchanting_table.BundledDefaults.CONSERVATION_SAFETY_MARGIN_POINTS;

    private static final TagKey<Enchantment> CURSE_TAG =
        chiseled_enchanting_table.registry.CetTags.Enchantments.CURSE;
    private static final TagKey<Enchantment> TREASURE_TAG =
        chiseled_enchanting_table.registry.CetTags.Enchantments.TREASURE;

    private record PotentialCacheKey(
        int maxLevel,
        int anvilCost,
        boolean treasure,
        long scaleBits,
        int maxDiscount,
        long creditBits,
        LibraryResonance.EssenceValuation essenceValuation
    ) {}

    private static final ConcurrentHashMap<PotentialCacheKey, int[]> POTENTIAL_CACHE = new ConcurrentHashMap<>();

    private DisenchantmentApplication() {}

    public static void clearPotentialCache() {
        POTENTIAL_CACHE.clear();
    }

    public static boolean isCurse(Holder<Enchantment> holder) {
        return holder != null && holder.is(CURSE_TAG);
    }

    public static boolean isTreasure(Holder<Enchantment> holder) {
        return holder != null && holder.is(TREASURE_TAG);
    }

    public static double rarityMultiplier(Holder<Enchantment> holder) {
        return rarityMultiplier(LibraryResonance.activeServerConfig(), holder);
    }

    public static double rarityMultiplier(LibraryResonance.Config resonanceCfg, Holder<Enchantment> holder) {
        return rarityMultiplierFromParams(
            resonanceCfg,
            Math.max(1, holder.value().getAnvilCost()),
            isTreasure(holder)
        );
    }

    public static double rarityMultiplierFromParams(
        LibraryResonance.Config resonanceCfg,
        int anvilCost,
        boolean treasure
    ) {
        LibraryResonance.Config cfg = resonanceCfg != null ? resonanceCfg : LibraryResonance.Config.DEFAULT;
        LibraryResonance.EssenceValuation ev = cfg.essenceValuation();
        int safeAnvil = Math.max(1, anvilCost);
        double base = Math.min(
            ev.rarityMaxMultiplier(),
            Math.max(ev.rarityMinMultiplier(), 1.0 + ev.rarityLogSlope() * Math.log(safeAnvil))
        );
        if (treasure) {
            base *= ev.treasureMultiplier();
        }
        return base;
    }

    public static int essencePotential(Holder<Enchantment> holder, int level, double refundScale) {
        return essencePotential(LibraryResonance.activeServerConfig(), holder, level, refundScale);
    }

    public static int essencePotential(
        LibraryResonance.Config resonanceCfg,
        Holder<Enchantment> holder,
        int level,
        double refundScale
    ) {
        if (holder == null || level <= 0 || isCurse(holder) || refundScale <= 0.0) {
            return 0;
        }
        int maxLevel = Math.max(1, holder.value().getMaxLevel());
        int clampedLevel = Math.min(level, maxLevel);
        return essencePotentialFromParams(
            resonanceCfg,
            maxLevel,
            clampedLevel,
            holder.value().getAnvilCost(),
            isTreasure(holder),
            refundScale
        );
    }

    public static int essencePotentialFromParams(
        int maxLevel,
        int clampedLevel,
        int anvilCost,
        boolean treasure,
        double refundScale
    ) {
        return essencePotentialFromParams(
            LibraryResonance.activeServerConfig(),
            maxLevel,
            clampedLevel,
            anvilCost,
            treasure,
            refundScale
        );
    }

    public static int essencePotentialFromParams(
        LibraryResonance.Config resonanceCfg,
        int maxLevel,
        int clampedLevel,
        int anvilCost,
        boolean treasure,
        double refundScale
    ) {
        if (clampedLevel <= 0) {
            return 0;
        }
        int cappedMax = Math.max(1, maxLevel);
        int target = Math.min(clampedLevel, cappedMax);
        int[] v = buildPotentialTable(resonanceCfg, cappedMax, anvilCost, treasure, refundScale);
        return v[target];
    }

    /**
     * Pure headless provenance-exact potential across paid vs external slices {@code 1..clampedLevel}:
     * <ul>
     *   <li>Telescopes identically to {@code V_table[L]} when all slices {@code 0..L-1} are set.</li>
     *   <li>Telescopes identically to {@code V_ext[L]} when {@code paidSlices} is empty.</li>
     *   <li>Satisfies exact step-down additivity {@code P(k, S) - P(b, S) + P(b, S) == P(k, S)}
     *       and hybrid upgrade conservation {@code P(k, S ∪ [a..k)) - cost(a -> k) <= P(a, S) - 5}.</li>
     * </ul>
     */
    public static int provenanceSlicePotential(
        LibraryResonance.Config resonanceCfg,
        int maxLevel,
        int clampedLevel,
        int anvilCost,
        boolean treasure,
        double tableRefundScale,
        double externalRefundScale,
        BitSet paidSlices
    ) {
        if (clampedLevel <= 0) {
            return 0;
        }
        int cappedMax = Math.max(1, maxLevel);
        int target = Math.min(clampedLevel, cappedMax);
        int[] vTable = buildPotentialTable(resonanceCfg, cappedMax, anvilCost, treasure, tableRefundScale);
        int[] vExt = buildPotentialTable(resonanceCfg, cappedMax, anvilCost, treasure, externalRefundScale);
        int sum = 0;
        for (int j = 1; j <= target; j++) {
            boolean paidAtTable = paidSlices != null && paidSlices.get(j - 1);
            sum += paidAtTable ? (vTable[j] - vTable[j - 1]) : (vExt[j] - vExt[j - 1]);
        }
        return sum;
    }

    public static int provenanceEssencePotential(
        LibraryResonance.Config resonanceCfg,
        ItemStack stack,
        Holder<Enchantment> holder,
        int level,
        double tableRefundScale,
        double externalRefundScale
    ) {
        if (holder == null || level <= 0 || isCurse(holder)) {
            return 0;
        }
        Identifier enchId = holder.unwrapKey().map(k -> k.identifier()).orElse(null);
        BitSet paidSlices = EnchantmentProvenance.getPaidSlices(stack, enchId);
        int maxLevel = Math.max(1, holder.value().getMaxLevel());
        return provenanceSlicePotential(
            resonanceCfg,
            maxLevel,
            Math.min(level, maxLevel),
            holder.value().getAnvilCost(),
            isTreasure(holder),
            tableRefundScale,
            externalRefundScale,
            paidSlices
        );
    }

    /**
     * Authoritative memoized potential table {@code V[0..cappedMax]} for all runtime, UI, recipe
     * viewer, reload self-check, and JUnit conservation proof callers.
     */
    public static int[] buildPotentialTable(
        LibraryResonance.Config resonanceCfg,
        int maxLevel,
        int anvilCost,
        boolean treasure,
        double refundScale
    ) {
        LibraryResonance.Config cfg = resonanceCfg != null ? resonanceCfg : LibraryResonance.Config.DEFAULT;
        int cappedMax = Math.max(1, maxLevel);
        double clampedScale = Math.max(0.0, Math.min(1.0, Double.isFinite(refundScale) ? refundScale : 1.0));
        if (clampedScale <= 0.0) {
            return new int[cappedMax + 1];
        }
        int safeAnvil = Math.max(1, anvilCost);
        int maxDiscount = cfg.maxTotalXpDiscountPercent();
        LibraryResonance.EssenceValuation ev = cfg.essenceValuation();
        int safetyMargin = ev.conservationSafetyMarginPoints();
        var key = new PotentialCacheKey(
            cappedMax,
            safeAnvil,
            treasure,
            Double.doubleToLongBits(clampedScale),
            maxDiscount,
            Double.doubleToLongBits(cfg.incrementalStepCreditFactor()),
            ev
        );
        int[] cached = POTENTIAL_CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        double rarity = rarityMultiplierFromParams(cfg, safeAnvil, treasure);
        double baseMax = cappedMax == 1 ? 34.0 : 48.0;

        int[] v = new int[cappedMax + 1];
        for (int k = 1; k <= cappedMax; k++) {
            double r = (double) k / (double) cappedMax;
            int desired = (int) Math.round(clampedScale * baseMax * rarity * Math.pow(r, 1.65));
            int fullMin = XpCostConversion.minPointsRequired(
                XpCostConversion.forMasterTomeEnchant(cfg, k, cappedMax, 0, maxDiscount)
            );
            int cap = Math.max(0, fullMin - safetyMargin);
            for (int a = 1; a < k; a++) {
                int stepMin = XpCostConversion.minPointsRequired(
                    XpCostConversion.forIncrementalMasterTomeEnchant(cfg, a, k, cappedMax, 0, maxDiscount)
                );
                int stepCap = Math.max(0, v[a] + stepMin - safetyMargin);
                if (stepCap < cap) {
                    cap = stepCap;
                }
            }
            int bounded = Math.min(desired, cap);
            v[k] = Math.max(v[k - 1], bounded);
        }
        if (POTENTIAL_CACHE.size() >= 512) {
            POTENTIAL_CACHE.clear();
        }
        POTENTIAL_CACHE.put(key, v);
        return v;
    }

    private static final int[] CONSERVATION_PROBE_MAX_LEVELS = buildConservationProbeMaxLevels();

    private static int[] buildConservationProbeMaxLevels() {
        int[] extra = {80, 100, 127, 150, 200, 255};
        int[] out = new int[64 + extra.length];
        for (int m = 1; m <= 64; m++) {
            out[m - 1] = m;
        }
        System.arraycopy(extra, 0, out, 64, extra.length);
        return out;
    }

    public static boolean verifyRuntimeConservation(double refundScale) {
        return verifyRuntimeConservation(LibraryResonance.activeServerConfig(), refundScale);
    }

    public static boolean verifyRuntimeConservation(LibraryResonance.Config resonanceCfg, double refundScale) {
        try {
            assertRuntimeConservationStrict(resonanceCfg, refundScale);
            return true;
        } catch (IllegalStateException ex) {
            ChiseledEnchantingTable.LOGGER.error(
                "Disenchant XP conservation invariant triggered during datapack reload; activating zero-refund fail-safe: {}",
                ex.getMessage(),
                ex
            );
            return false;
        }
    }

    public static void assertRuntimeConservationStrict(LibraryResonance.Config resonanceCfg, double refundScale) {
        clearPotentialCache();
        LibraryResonance.Config cfg = resonanceCfg != null ? resonanceCfg : LibraryResonance.Config.DEFAULT;
        int maxDiscount = cfg.maxTotalXpDiscountPercent();
        int[] probeAnvilCosts = {1, 2, 4, 8, 25, 100};
        for (int m : CONSERVATION_PROBE_MAX_LEVELS) {
            int[] fullMins = new int[m + 1];
            int[][] stepMins = new int[m + 1][m + 1];
            for (int k = 1; k <= m; k++) {
                fullMins[k] = XpCostConversion.minPointsRequired(
                    XpCostConversion.forMasterTomeEnchant(cfg, k, m, 0, maxDiscount)
                );
                for (int a = 1; a < k; a++) {
                    stepMins[a][k] = XpCostConversion.minPointsRequired(
                        XpCostConversion.forIncrementalMasterTomeEnchant(cfg, a, k, m, 0, maxDiscount)
                    );
                }
            }
            for (int anvil : probeAnvilCosts) {
                for (boolean treas : new boolean[] {false, true}) {
                    int[] v = buildPotentialTable(cfg, m, anvil, treas, refundScale);
                    for (int k = 1; k <= m; k++) {
                        int fullMin = fullMins[k];
                        if (v[k] < v[k - 1] || v[k] > fullMin) {
                            throw new IllegalStateException(
                                "Disenchant XP conservation violated at M=" + m + ", k=" + k + ": V=" + v[k] + ", minFull=" + fullMin
                            );
                        }
                        for (int a = 1; a < k; a++) {
                            int stepMin = stepMins[a][k];
                            if (v[k] - v[a] > stepMin) {
                                throw new IllegalStateException(
                                    "Disenchant step conservation violated at M=" + m + ", " + a + "->" + k
                                        + ": dV=" + (v[k] - v[a]) + ", stepMin=" + stepMin
                                );
                            }
                        }
                    }
                }
            }
        }
    }

    public static int refundForStepDown(Holder<Enchantment> holder, int currentLevel, int targetLevel, double refundScale) {
        return refundForStepDown(LibraryResonance.activeServerConfig(), holder, currentLevel, targetLevel, refundScale);
    }

    public static int refundForStepDown(
        LibraryResonance.Config resonanceCfg,
        Holder<Enchantment> holder,
        int currentLevel,
        int targetLevel,
        double refundScale
    ) {
        return provenanceRefundForStepDown(
            resonanceCfg,
            ItemStack.EMPTY,
            holder,
            currentLevel,
            targetLevel,
            refundScale,
            refundScale
        );
    }

    public static int provenanceRefundForStepDown(
        LibraryResonance.Config resonanceCfg,
        ItemStack stack,
        Holder<Enchantment> holder,
        int currentLevel,
        int targetLevel,
        double tableRefundScale,
        double externalRefundScale
    ) {
        if (holder == null || isCurse(holder) || currentLevel <= targetLevel || targetLevel < 0) {
            return 0;
        }
        int curV = provenanceEssencePotential(resonanceCfg, stack, holder, currentLevel, tableRefundScale, externalRefundScale);
        int nextV = targetLevel == 0
            ? 0
            : provenanceEssencePotential(resonanceCfg, stack, holder, targetLevel, tableRefundScale, externalRefundScale);
        return Math.max(0, curV - nextV);
    }

    public static int totalFullDisenchantRefund(ItemStack stack, double refundScale) {
        return totalFullDisenchantRefund(
            LibraryResonance.activeServerConfig(),
            stack,
            refundScale,
            ChiseledEnchantingTableData.disenchantExternalRefundScale()
        );
    }

    public static int totalFullDisenchantRefund(
        LibraryResonance.Config resonanceCfg,
        ItemStack stack,
        double refundScale
    ) {
        return totalFullDisenchantRefund(
            resonanceCfg,
            stack,
            refundScale,
            ChiseledEnchantingTableData.disenchantExternalRefundScale()
        );
    }

    public static int totalFullDisenchantRefund(
        LibraryResonance.Config resonanceCfg,
        ItemStack stack,
        double tableRefundScale,
        double externalRefundScale
    ) {
        if (stack.isEmpty()) {
            return 0;
        }
        ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        if (enchantments.isEmpty()) {
            return 0;
        }
        int sum = 0;
        for (var entry : enchantments.entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            if (!isCurse(holder)) {
                sum += provenanceEssencePotential(
                    resonanceCfg,
                    stack,
                    holder,
                    entry.getIntValue(),
                    tableRefundScale,
                    externalRefundScale
                );
            }
        }
        return sum;
    }

    public static int countNonCurseEnchantments(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        int count = 0;
        for (var entry : enchantments.entrySet()) {
            if (!isCurse(entry.getKey()) && entry.getIntValue() > 0) {
                count++;
            }
        }
        return count;
    }

    public static void apply(ChiseledEnchantingTableScreenHandler menu, ApplyEnchantmentPayload payload) {
        if (!ChiseledEnchantingTableData.disenchantEnabled() || menu.isRitualActive()) {
            return;
        }
        OptionType type = payload.option_type();
        ItemStack target = menu.get_enchantable_item();
        if (target.isEmpty() || target.getCount() != 1) {
            return;
        }

        switch (type) {
            case DISENCHANT_ALL -> applyFullDisenchant(menu, target);
            case DOWNGRADE_ENCHANTMENT -> applyDowngrade(menu, target, payload.enchantment_id(), payload.enchantment_level());
            case STRIP_SINGLE_ENCHANTMENT -> applyTargetedStrip(menu, target, payload.enchantment_id());
            case PURGE_CURSE -> applyCursePurge(menu, target, payload.enchantment_id());
            default -> {}
        }
    }

    private static void applyFullDisenchant(ChiseledEnchantingTableScreenHandler menu, ItemStack target) {
        ItemEnchantments current = EnchantmentHelper.getEnchantmentsForCrafting(target);
        if (current.isEmpty()) {
            return;
        }
        int nonCurseCount = countNonCurseEnchantments(target);
        if (nonCurseCount <= 0) {
            return;
        }
        var cfg = menu.libraryResonanceConfig();
        int totalRefund = totalFullDisenchantRefund(
            cfg,
            target,
            menu.disenchantRefundScale(),
            menu.disenchantExternalRefundScale()
        );

        ItemEnchantments.Mutable next = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        for (var entry : current.entrySet()) {
            if (isCurse(entry.getKey())) {
                next.set(entry.getKey(), entry.getIntValue());
            }
        }

        ItemStack resulting = finishEnchantmentWrite(target, next.toImmutable());
        InkStainLore.reconcileAfterRemovalCleanse(resulting);
        menu.setEnchantableSlot(resulting);
        awardRefundOrbs(menu, totalRefund);
        playCleanseFeedback(menu, 1.15f);
        Advancement.give(menu.player, "cleanse_enchantment");
    }

    private static void applyDowngrade(
        ChiseledEnchantingTableScreenHandler menu,
        ItemStack target,
        Identifier targetId,
        int desiredLevel
    ) {
        ItemEnchantments current = EnchantmentHelper.getEnchantmentsForCrafting(target);
        Holder<Enchantment> matched = findHolderById(current, targetId);
        if (matched == null || isCurse(matched)) {
            return;
        }
        int curLvl = current.getLevel(matched);
        if (desiredLevel <= 0 || desiredLevel >= curLvl) {
            return;
        }
        var cfg = menu.libraryResonanceConfig();
        int refund = provenanceRefundForStepDown(
            cfg,
            target,
            matched,
            curLvl,
            desiredLevel,
            menu.disenchantRefundScale(),
            menu.disenchantExternalRefundScale()
        );

        ItemEnchantments.Mutable next = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        for (var entry : current.entrySet()) {
            if (entry.getKey().equals(matched)) {
                next.set(matched, desiredLevel);
            } else {
                next.set(entry.getKey(), entry.getIntValue());
            }
        }

        ItemStack resulting = finishEnchantmentWrite(target, next.toImmutable());
        menu.setEnchantableSlot(resulting);
        awardRefundOrbs(menu, refund);
        playCleanseFeedback(menu, 1.05f);
        Advancement.give(menu.player, "cleanse_enchantment");
    }

    private static void applyTargetedStrip(
        ChiseledEnchantingTableScreenHandler menu,
        ItemStack target,
        Identifier targetId
    ) {
        ItemEnchantments current = EnchantmentHelper.getEnchantmentsForCrafting(target);
        Holder<Enchantment> matched = findHolderById(current, targetId);
        if (matched == null || isCurse(matched)) {
            return;
        }
        int curLvl = current.getLevel(matched);
        var ewl = new EnchantmentWithLevel(targetId, curLvl);
        var xpReq = menu.resolveEffectiveXpRequirement(ewl, OptionType.STRIP_SINGLE_ENCHANTMENT);
        List<ItemStack> affordableBundle = menu.findMatchingAffordableCostBundle(ewl, OptionType.STRIP_SINGLE_ENCHANTMENT);
        if (!xpReq.isMetBy(menu.player) || (!menu.player.getAbilities().instabuild && affordableBundle == null)) {
            return;
        }

        XpCostConversion.deductXp(menu.player, xpReq);
        menu.deductMatchedBundle(affordableBundle);

        ItemEnchantments.Mutable next = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        for (var entry : current.entrySet()) {
            if (!entry.getKey().equals(matched)) {
                next.set(entry.getKey(), entry.getIntValue());
            }
        }

        ItemStack resulting = finishEnchantmentWrite(target, next.toImmutable());
        InkStainLore.reconcileAfterRemovalCleanse(resulting);
        menu.setEnchantableSlot(resulting);
        playCleanseFeedback(menu, 0.95f);
        Advancement.give(menu.player, "cleanse_enchantment");
    }

    private static void applyCursePurge(
        ChiseledEnchantingTableScreenHandler menu,
        ItemStack target,
        Identifier targetId
    ) {
        if (!menu.disenchantAllowCursePurge()) {
            return;
        }
        ItemEnchantments current = EnchantmentHelper.getEnchantmentsForCrafting(target);
        Holder<Enchantment> matched = findHolderById(current, targetId);
        if (matched == null || !isCurse(matched)) {
            return;
        }
        var ewl = new EnchantmentWithLevel(targetId, current.getLevel(matched));
        var xpReq = menu.resolveEffectiveXpRequirement(ewl, OptionType.PURGE_CURSE);
        List<ItemStack> affordableBundle = menu.findMatchingAffordableCostBundle(ewl, OptionType.PURGE_CURSE);
        if (!xpReq.isMetBy(menu.player) || (!menu.player.getAbilities().instabuild && affordableBundle == null)) {
            return;
        }

        XpCostConversion.deductXp(menu.player, xpReq);
        menu.deductMatchedBundle(affordableBundle);

        ItemEnchantments.Mutable next = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        for (var entry : current.entrySet()) {
            if (!entry.getKey().equals(matched)) {
                next.set(entry.getKey(), entry.getIntValue());
            }
        }

        ItemStack resulting = finishEnchantmentWrite(target, next.toImmutable());
        InkStainLore.reconcileAfterRemovalCleanse(resulting);
        menu.setEnchantableSlot(resulting);
        playCleanseFeedback(menu, 0.72f);
        Advancement.give(menu.player, "purge_curse");
    }

    private static Holder<Enchantment> findHolderById(ItemEnchantments enchantments, Identifier id) {
        for (var entry : enchantments.entrySet()) {
            if (entry.getKey().unwrapKey().map(k -> k.identifier().equals(id)).orElse(false)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static ItemStack finishEnchantmentWrite(ItemStack source, ItemEnchantments remaining) {
        if (source.is(Items.ENCHANTED_BOOK) && remaining.isEmpty()) {
            return new ItemStack(BlockRegistry.DEPLETED_TOME, 1);
        }
        ItemStack copy = source.copy();
        if (copy.is(Items.ENCHANTED_BOOK)) {
            copy.set(DataComponents.STORED_ENCHANTMENTS, remaining);
        } else {
            copy.set(DataComponents.ENCHANTMENTS, remaining);
        }
        EnchantmentProvenance.pruneToCurrentEnchantments(copy, remaining);
        return copy;
    }

    public static void awardRefundOrbs(ChiseledEnchantingTableScreenHandler menu, int refundPoints) {
        if (refundPoints <= 0) {
            return;
        }
        Player player = menu.player;
        if (player.level() instanceof ServerLevel serverLevel) {
            BlockPos tablePos = menu.tableBlockPos();
            Vec3 spawnPos = tablePos != null
                ? Vec3.atCenterOf(tablePos).add(0.0, 0.85, 0.0)
                : player.position().add(0.0, 0.5, 0.0);
            ExperienceOrb.award(serverLevel, spawnPos, refundPoints);
        }
    }

    private static void playCleanseFeedback(ChiseledEnchantingTableScreenHandler menu, float pitch) {
        Player player = menu.player;
        if (player.level() instanceof ServerLevel serverLevel) {
            BlockPos pos = menu.tableBlockPos();
            BlockPos soundPos = pos != null ? pos : player.blockPosition();
            serverLevel.playSound(null, soundPos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.85f, pitch);
            serverLevel.playSound(null, soundPos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.90f, 1.15f);
        }
    }
}

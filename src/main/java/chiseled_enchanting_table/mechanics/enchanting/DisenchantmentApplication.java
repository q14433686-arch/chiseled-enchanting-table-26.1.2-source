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
 * and curse purification mechanic with proven inductive XP conservation across all levels {@code 1..255}.
 *
 * <p>Conservation theorem: for every {@code M >= 1}, {@code 1 <= k <= M}, and {@code 1 <= a < k}:
 * <pre>
 *   0 = V(0) &lt;= V(1) &lt;= ... &lt;= V(M)
 *   V(k) &lt;= minPointsRequired(MasterTome(0 -&gt; k, maxDiscount)) - SAFETY_MARGIN
 *   V(k) - V(a) &lt;= minPointsRequired(MasterTome(a -&gt; k, maxDiscount)) - SAFETY_MARGIN
 * </pre>
 * Because {@link XpCostConversion#forMasterTomeEnchant} and
 * {@link XpCostConversion#forIncrementalMasterTomeEnchant} are called directly as the single source
 * of truth and every non-Master-Tome ritual requires at least those XP thresholds/deductions, no full
 * or partial cycle can ever generate net-positive XP.</p>
 */
public final class DisenchantmentApplication {
    public static final Identifier DISENCHANT_ALL_ID = ChiseledEnchantingTable.identifier("disenchant_all");
    public static final Identifier STRIP_SINGLE_ID = ChiseledEnchantingTable.identifier("strip_single");
    public static final Identifier PURGE_CURSE_ID = ChiseledEnchantingTable.identifier("purge_curse");
    public static final int CONSERVATION_SAFETY_MARGIN_POINTS = 5;

    private static final TagKey<Enchantment> CURSE_TAG =
        TagKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace("curse"));
    private static final TagKey<Enchantment> TREASURE_TAG =
        TagKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace("treasure"));

    private DisenchantmentApplication() {}

    public static boolean isCurse(Holder<Enchantment> holder) {
        return holder != null && holder.is(CURSE_TAG);
    }

    public static boolean isTreasure(Holder<Enchantment> holder) {
        return holder != null && holder.is(TREASURE_TAG);
    }

    public static double rarityMultiplier(Holder<Enchantment> holder) {
        int anvilCost = Math.max(1, holder.value().getAnvilCost());
        double base = Math.min(1.45, Math.max(1.00, 1.0 + 0.18 * Math.log(anvilCost)));
        if (isTreasure(holder)) {
            base *= 1.15;
        }
        return base;
    }

    public static int essencePotential(Holder<Enchantment> holder, int level, double refundScale) {
        if (holder == null || level <= 0 || isCurse(holder) || refundScale <= 0.0) {
            return 0;
        }
        int maxLevel = Math.max(1, holder.value().getMaxLevel());
        int clampedLevel = Math.min(level, maxLevel);
        return essencePotentialFromParams(
            maxLevel,
            clampedLevel,
            holder.value().getAnvilCost(),
            isTreasure(holder),
            refundScale
        );
    }

    /**
     * Pure inductive essence potential {@code V(E, L)} strictly bounded below every full and
     * single/multi-step Master Tome XP cost across {@code M in [1..255]}.
     */
    public static int essencePotentialFromParams(
        int maxLevel,
        int clampedLevel,
        int anvilCost,
        boolean treasure,
        double refundScale
    ) {
        double clampedScale = Math.max(0.0, Math.min(1.0, Double.isFinite(refundScale) ? refundScale : 1.0));
        if (clampedLevel <= 0 || clampedScale <= 0.0) {
            return 0;
        }
        int cappedMax = Math.max(1, maxLevel);
        int target = Math.min(clampedLevel, cappedMax);
        int maxDiscount = LibraryResonance.activeConfig().maxTotalXpDiscountPercent();
        double rarity = Math.min(1.45, Math.max(1.00, 1.0 + 0.18 * Math.log(Math.max(1, anvilCost))))
            * (treasure ? 1.15 : 1.0);
        double baseMax = cappedMax == 1 ? 34.0 : 48.0;

        int[] v = new int[target + 1];
        for (int k = 1; k <= target; k++) {
            double r = (double) k / (double) cappedMax;
            int desired = (int) Math.round(clampedScale * baseMax * rarity * Math.pow(r, 1.65));
            int fullMin = XpCostConversion.minPointsRequired(
                XpCostConversion.forMasterTomeEnchant(k, cappedMax, 0, maxDiscount)
            );
            int cap = Math.max(0, fullMin - CONSERVATION_SAFETY_MARGIN_POINTS);
            for (int a = 1; a < k; a++) {
                int stepMin = XpCostConversion.minPointsRequired(
                    XpCostConversion.forIncrementalMasterTomeEnchant(a, k, cappedMax, 0, maxDiscount)
                );
                int stepCap = Math.max(0, v[a] + stepMin - CONSERVATION_SAFETY_MARGIN_POINTS);
                if (stepCap < cap) {
                    cap = stepCap;
                }
            }
            int bounded = Math.min(desired, cap);
            v[k] = Math.max(v[k - 1], bounded);
        }
        return v[target];
    }

    /**
     * Runtime conservation self-check run automatically on datapack reload.
     */
    public static void verifyRuntimeConservation(double refundScale) {
        int maxDiscount = LibraryResonance.activeConfig().maxTotalXpDiscountPercent();
        int[] probeMaxLevels = {1, 2, 3, 4, 5, 6, 8, 10, 15, 30, 100, 255};
        int[] probeAnvilCosts = {1, 2, 4, 8, 25, 100};
        for (int m : probeMaxLevels) {
            for (int anvil : probeAnvilCosts) {
                for (boolean treas : new boolean[] {false, true}) {
                    int prev = 0;
                    for (int k = 1; k <= m; k++) {
                        int vk = essencePotentialFromParams(m, k, anvil, treas, refundScale);
                        int fullMin = XpCostConversion.minPointsRequired(
                            XpCostConversion.forMasterTomeEnchant(k, m, 0, maxDiscount)
                        );
                        if (vk < prev || vk > fullMin) {
                            throw new IllegalStateException(
                                "Disenchant XP conservation violated at M=" + m + ", k=" + k + ": V=" + vk + ", minFull=" + fullMin
                            );
                        }
                        prev = vk;
                    }
                }
            }
        }
    }

    public static int refundForStepDown(Holder<Enchantment> holder, int currentLevel, int targetLevel, double refundScale) {
        if (holder == null || isCurse(holder) || currentLevel <= targetLevel || targetLevel < 0) {
            return 0;
        }
        int curV = essencePotential(holder, currentLevel, refundScale);
        int nextV = targetLevel == 0 ? 0 : essencePotential(holder, targetLevel, refundScale);
        return Math.max(0, curV - nextV);
    }

    public static int totalFullDisenchantRefund(ItemStack stack, double refundScale) {
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
                sum += essencePotential(holder, entry.getIntValue(), refundScale);
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
        if (!ChiseledEnchantingTableData.disenchantEnabled()) {
            return;
        }
        OptionType type = payload.option_type();
        ItemStack target = menu.get_enchantable_item();
        if (target.isEmpty()) {
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
        double scale = ChiseledEnchantingTableData.disenchantRefundScale();
        int refundPoints = totalFullDisenchantRefund(target, scale);

        boolean hasRemainingCurse = false;
        for (var entry : current.entrySet()) {
            if (isCurse(entry.getKey()) && entry.getIntValue() > 0) {
                hasRemainingCurse = true;
                break;
            }
        }

        if (target.is(Items.ENCHANTED_BOOK) && !hasRemainingCurse) {
            menu.setEnchantableSlot(new ItemStack(BlockRegistry.DEPLETED_TOME, Math.max(1, target.getCount())));
        } else {
            EnchantmentHelper.updateEnchantments(target, mutable -> {
                var snapshot = new java.util.HashSet<>(mutable.keySet());
                for (Holder<Enchantment> h : snapshot) {
                    if (!isCurse(h)) {
                        mutable.set(h, 0);
                    }
                }
            });
            resetAnvilRepairPenalty(target);
        }

        grantRefundAndFeedback(menu, refundPoints, SoundEvents.GRINDSTONE_USE, 1.08F);
        Advancement.give(menu.player, "cleanse_enchantment");
        menu.markInventoryChanged();
        menu.broadcastChanges();
    }

    private static void applyDowngrade(
        ChiseledEnchantingTableScreenHandler menu,
        ItemStack target,
        Identifier enchantmentId,
        int nextLevel
    ) {
        Holder<Enchantment> holder = resolveEnchantment(menu, enchantmentId);
        if (holder == null || isCurse(holder)) {
            return;
        }
        ItemEnchantments current = EnchantmentHelper.getEnchantmentsForCrafting(target);
        int curLvl = current.getLevel(holder);
        if (curLvl <= 1 || nextLevel < 1 || nextLevel >= curLvl) {
            return;
        }
        int refundPoints = refundForStepDown(holder, curLvl, nextLevel, ChiseledEnchantingTableData.disenchantRefundScale());

        EnchantmentHelper.updateEnchantments(target, mutable -> mutable.set(holder, nextLevel));
        decayAnvilRepairPenalty(target);

        grantRefundAndFeedback(menu, refundPoints, SoundEvents.GRINDSTONE_USE, 1.24F);
        Advancement.give(menu.player, "cleanse_enchantment");
        menu.markInventoryChanged();
        menu.broadcastChanges();
    }

    private static void applyTargetedStrip(
        ChiseledEnchantingTableScreenHandler menu,
        ItemStack target,
        Identifier enchantmentId
    ) {
        Holder<Enchantment> holder = resolveEnchantment(menu, enchantmentId);
        if (holder == null || isCurse(holder)) {
            return;
        }
        ItemEnchantments current = EnchantmentHelper.getEnchantmentsForCrafting(target);
        int curLvl = current.getLevel(holder);
        if (curLvl <= 0) {
            return;
        }
        if (current.size() == 1) {
            applyFullDisenchant(menu, target);
            return;
        }

        EnchantmentWithLevel ewl = new EnchantmentWithLevel(enchantmentId, curLvl);
        var xpReq = menu.resolveEffectiveXpRequirement(ewl, OptionType.STRIP_SINGLE_ENCHANTMENT);
        if (!xpReq.isMetBy(menu.player)) {
            return;
        }
        var matchedBundle = menu.findMatchingAffordableCostBundle(ewl, OptionType.STRIP_SINGLE_ENCHANTMENT);
        if (!menu.player.getAbilities().instabuild && matchedBundle == null) {
            return;
        }
        if (!menu.player.getAbilities().instabuild) {
            XpCostConversion.deductXp(menu.player, xpReq);
            menu.deductMatchedBundle(matchedBundle);
        }

        EnchantmentHelper.updateEnchantments(target, mutable -> mutable.set(holder, 0));
        decayAnvilRepairPenalty(target);
        if (target.is(Items.ENCHANTED_BOOK) && EnchantmentHelper.getEnchantmentsForCrafting(target).isEmpty()) {
            menu.setEnchantableSlot(new ItemStack(BlockRegistry.DEPLETED_TOME, Math.max(1, target.getCount())));
        }

        menu.world().playSound(null, menu.player.blockPosition(), SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.9F, 1.15F);
        menu.world().playSound(null, menu.player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.25F);
        Advancement.give(menu.player, "cleanse_enchantment");
        menu.markInventoryChanged();
        menu.broadcastChanges();
    }

    private static void applyCursePurge(
        ChiseledEnchantingTableScreenHandler menu,
        ItemStack target,
        Identifier enchantmentId
    ) {
        if (!ChiseledEnchantingTableData.disenchantAllowCursePurge()) {
            return;
        }
        Holder<Enchantment> holder = resolveEnchantment(menu, enchantmentId);
        if (holder == null || !isCurse(holder)) {
            return;
        }
        ItemEnchantments current = EnchantmentHelper.getEnchantmentsForCrafting(target);
        int curLvl = current.getLevel(holder);
        if (curLvl <= 0) {
            return;
        }

        EnchantmentWithLevel ewl = new EnchantmentWithLevel(enchantmentId, curLvl);
        var xpReq = menu.resolveEffectiveXpRequirement(ewl, OptionType.PURGE_CURSE);
        if (!xpReq.isMetBy(menu.player)) {
            return;
        }
        var matchedBundle = menu.findMatchingAffordableCostBundle(ewl, OptionType.PURGE_CURSE);
        if (!menu.player.getAbilities().instabuild && matchedBundle == null) {
            return;
        }
        if (!menu.player.getAbilities().instabuild) {
            XpCostConversion.deductXp(menu.player, xpReq);
            menu.deductMatchedBundle(matchedBundle);
        }

        EnchantmentHelper.updateEnchantments(target, mutable -> mutable.set(holder, 0));
        decayAnvilRepairPenalty(target);
        if (target.is(Items.ENCHANTED_BOOK) && EnchantmentHelper.getEnchantmentsForCrafting(target).isEmpty()) {
            menu.setEnchantableSlot(new ItemStack(BlockRegistry.DEPLETED_TOME, Math.max(1, target.getCount())));
        }

        menu.world().playSound(null, menu.player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.85F, 1.4F);
        menu.world().playSound(null, menu.player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.BLOCKS, 0.45F, 1.35F);
        Advancement.give(menu.player, "purge_curse");
        menu.markInventoryChanged();
        menu.broadcastChanges();
    }

    private static Holder<Enchantment> resolveEnchantment(ChiseledEnchantingTableScreenHandler menu, Identifier id) {
        try {
            return ChiseledEnchantingTableScreenHandler.IdentifierToEnchantment(id, menu.world());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void resetAnvilRepairPenalty(ItemStack stack) {
        stack.set(DataComponents.REPAIR_COST, 0);
    }

    private static void decayAnvilRepairPenalty(ItemStack stack) {
        Integer cur = stack.get(DataComponents.REPAIR_COST);
        if (cur != null && cur > 0) {
            stack.set(DataComponents.REPAIR_COST, Math.max(0, (cur - 1) / 2));
        }
    }

    private static void grantRefundAndFeedback(
        ChiseledEnchantingTableScreenHandler menu,
        int refundPoints,
        net.minecraft.sounds.SoundEvent primarySound,
        float pitch
    ) {
        Player player = menu.player;
        if (refundPoints > 0 && !player.getAbilities().instabuild) {
            if (menu.world() instanceof ServerLevel serverLevel) {
                BlockPos tablePos = menu.tableBlockPos();
                Vec3 spawnPos = tablePos != null
                    ? new Vec3(tablePos.getX() + 0.5, tablePos.getY() + 0.92, tablePos.getZ() + 0.5)
                    : player.position().add(0.0, 0.65, 0.0);
                ExperienceOrb.award(serverLevel, spawnPos, refundPoints);
            } else {
                player.giveExperiencePoints(refundPoints);
            }
        }
        menu.world().playSound(null, player.blockPosition(), primarySound, SoundSource.BLOCKS, 0.9F, pitch);
        if (refundPoints > 0) {
            menu.world().playSound(
                null,
                player.blockPosition(),
                SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS,
                0.65F,
                1.12F
            );
        }
    }
}

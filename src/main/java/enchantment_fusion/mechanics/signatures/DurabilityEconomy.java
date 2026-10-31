package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.config.FusionConfigStore;
import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.jetbrains.annotations.Nullable;

/** Real XP repair / real equipment wear for the user-selected mending × unbreaking pair. */
public final class DurabilityEconomy {
    public static final String GROWTH = "overflow_plating";
    public static final String RESCUE = "self_healing_lattice";
    public static final String MENDING = "minecraft:mending";
    public static final String UNBREAKING = "minecraft:unbreaking";
    private static final EquipmentSlot[] EQUIPPED = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private DurabilityEconomy() {}

    public static boolean isGrowth(@Nullable FusedPair pair) {
        return pair != null && MENDING.equals(pair.primaryId()) && UNBREAKING.equals(pair.secondaryId());
    }
    public static boolean isRescue(@Nullable FusedPair pair) {
        return pair != null && UNBREAKING.equals(pair.primaryId()) && MENDING.equals(pair.secondaryId());
    }
    public static boolean matches(@Nullable FusedPair pair, String signature) {
        return (isGrowth(pair) && GROWTH.equals(signature)) || (isRescue(pair) && RESCUE.equals(signature));
    }
    public static boolean supportsCarrier(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getMaxStackSize() == 1
            && stack.isDamageableItem() && stack.getMaxDamage() > 0;
    }
    public static boolean applies(FusedPair pair, ItemStack stack, String signature) {
        return matches(pair, signature) && supportsCarrier(stack);
    }
    public static @Nullable FusedPair selected(@Nullable ItemStack stack, boolean growth) {
        if (!supportsCarrier(stack)) return null;
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs()) {
            if ((growth ? isGrowth(pair) : isRescue(pair))
                && SignatureCatalog.resolve(pair).map(entry -> matches(pair, entry.id())).orElse(false)) return pair;
        }
        return null;
    }
    public static boolean actuallyEquipped(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) return false;
        for (EquipmentSlot slot : EQUIPPED) if (player.getItemBySlot(slot) == stack) return true;
        return false;
    }
    public static boolean enter() {
        if (Boolean.TRUE.equals(ACTIVE.get())) return false;
        ACTIVE.set(Boolean.TRUE);
        return true;
    }
    public static void exit() { ACTIVE.remove(); }

    private static @Nullable Holder<Enchantment> enchant(ServerLevel level, String id) {
        return FusionStationOperations.lookupEnchantment(level.registryAccess(), id).orElse(null);
    }
    private static boolean containsNative(ItemStack stack, @Nullable Holder<Enchantment> holder) {
        return holder != null && stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(holder) > 0;
    }

    /** Invoked only after an orb actually decreased this equipped stack's damage. */
    public static void afterOrbRepair(ServerPlayer player, ItemStack stack, int actualRepair) {
        FusedPair pair = selected(stack, true);
        if (pair == null || actualRepair <= 0 || !actuallyEquipped(player, stack) || !player.isAlive()) return;
        ServerLevel level = player.level();
        int current = stack.getMaxDamage();
        int base = stack.getOrDefault(FusionDataComponents.DURABILITY_GROWTH_BASE, current);
        int cap = DurabilityEconomyMath.growthCap(base,
            FusionConfigStore.signatureInt(GROWTH, "growth_cap_percent", 200, 100, 500));
        int chance = DurabilityEconomyMath.growthChance(
            FusionConfigStore.signatureInt(GROWTH, "growth_chance_percent", 20, 0, 100), pair.secondaryLevel());
        if (base <= 0 || current >= cap || chance <= 0 || level.getRandom().nextInt(100) >= chance) return;
        int step = DurabilityEconomyMath.growthStep(base,
            FusionConfigStore.signatureInt(GROWTH, "growth_per_thousand", 3, 1, 50));
        int next = (int) Math.min(cap, (long) current + step);
        if (next <= current) return;
        stack.set(FusionDataComponents.DURABILITY_GROWTH_BASE, base);
        // Vanilla's own persistent MAX_DAMAGE component stays on this exact stack even if
        // the fusion is later extracted, cleaned, or removed.
        stack.set(DataComponents.MAX_DAMAGE, next);
        CompendiumDiscoveryTracker.recordDiscovery(player, pair.primaryId(), pair.secondaryId());
    }

    /**
     * Vanilla repairs all normally eligible items first. With XP left over, a damaged non-native
     * growth carrier may use the real datapack Mending effect without forging an enchantment on it.
     */
    public static int repairNonNative(ServerPlayer player, int amount) {
        if (amount <= 0 || !player.isAlive()) return amount;
        ServerLevel level = player.level();
        Holder<Enchantment> mending = enchant(level, MENDING);
        if (mending == null || !mending.value().effects().has(EnchantmentEffectComponents.REPAIR_WITH_XP)) return amount;
        Set<ItemStack> seen = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (EquipmentSlot slot : EQUIPPED) {
            ItemStack stack = player.getItemBySlot(slot);
            FusedPair pair = selected(stack, true);
            if (pair == null || !seen.add(stack) || !stack.isDamaged() || containsNative(stack, mending)) continue;
            int vanilla = EnchantmentHelper.modifyDurabilityToRepairFromXp(level, stack, amount);
            MutableFloat repairValue = new MutableFloat(vanilla);
            mending.value().modifyDurabilityToRepairFromXp(level, pair.primaryLevel(), stack, repairValue);
            int capacity = Math.max(0, repairValue.intValue());
            int before = stack.getDamageValue();
            int planned = Math.min(before, capacity);
            if (capacity <= 0 || planned <= 0) continue;
            stack.setDamageValue(before - planned);
            int actual = Math.clamp(before - stack.getDamageValue(), 0, planned);
            if (actual <= 0) continue;
            int spent = (int) Math.min(amount, (long) actual * amount / capacity);
            amount -= spent; // Match vanilla's XP-to-durability remainder calculation.
            afterOrbRepair(player, stack, actual);
            if (amount <= 0) return 0;
        }
        return amount;
    }

    /** Apply the actual datapack Unbreaking effect when this valid host cannot project it natively. */
    public static int nonNativeUnbreaking(ServerLevel level, ItemStack stack, int vanillaAmount) {
        FusedPair pair = selected(stack, false);
        if (pair == null || vanillaAmount <= 0) return vanillaAmount;
        Holder<Enchantment> unbreaking = enchant(level, UNBREAKING);
        if (unbreaking == null || containsNative(stack, unbreaking)) return vanillaAmount;
        MutableFloat modified = new MutableFloat(vanillaAmount);
        unbreaking.value().modifyDurabilityChange(level, pair.primaryLevel(), stack, modified);
        return modified.intValue();
    }

    private record Wear(ItemStack stack, int before, int max, int capacity) {}

    /**
     * Called only after a real threshold-crossing wear operation. First verify and apportion the
     * complete cost; insufficient other held or worn equipment causes no mutation or XP.
     */
    public static void convertEquipmentWear(ServerPlayer player, ItemStack source, FusedPair pair, int sourceMaximum) {
        if (!player.isAlive() || sourceMaximum <= 0 || !isRescue(pair)) return;
        int xp = DurabilityEconomyMath.conversionXp(sourceMaximum,
            FusionConfigStore.signatureInt(RESCUE, "conversion_xp_cap", 64, 1, 256));
        int cost = xp * 4; // More than vanilla Mending's two durability per XP; no free vanilla loop.
        List<Wear> pieces = new ArrayList<>();
        Set<ItemStack> seen = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        seen.add(source);
        for (EquipmentSlot slot : EQUIPPED) {
            ItemStack equipment = player.getItemBySlot(slot);
            if (!supportsCarrier(equipment) || !seen.add(equipment)) continue;
            int max = equipment.getMaxDamage(), before = equipment.getDamageValue();
            int safe = Math.max(0, max - before - 1); // never break another equipped item
            if (safe > 0) pieces.add(new Wear(equipment, before, max, safe));
        }
        int[] caps = pieces.stream().mapToInt(Wear::capacity).toArray();
        int[] debits = DurabilityEconomyMath.splitWear(cost, caps);
        if (debits == null) return;
        for (Wear piece : pieces) {
            if (!actuallyEquipped(player, piece.stack()) || piece.stack().getMaxDamage() != piece.max()
                || piece.stack().getDamageValue() != piece.before()) return;
        }
        for (int i = 0; i < pieces.size(); i++) {
            Wear piece = pieces.get(i);
            if (!actuallyEquipped(player, piece.stack()) || piece.stack().getMaxDamage() != piece.max()
                || piece.stack().getDamageValue() != piece.before()) {
                refundUntouchedWear(player, pieces, debits, i);
                return;
            }
            if (debits[i] > 0) piece.stack().setDamageValue(piece.before() + debits[i]);
            if (!actuallyEquipped(player, piece.stack()) || piece.stack().getMaxDamage() != piece.max()
                || piece.stack().getDamageValue() != piece.before() + debits[i]) {
                refundUntouchedWear(player, pieces, debits, i + 1);
                return;
            }
        }
        // A callback on one piece may have touched an earlier piece. Do not emit XP unless
        // every debit is still present on the original equipped object.
        for (int i = 0; i < pieces.size(); i++) {
            Wear piece = pieces.get(i);
            if (!actuallyEquipped(player, piece.stack()) || piece.stack().getMaxDamage() != piece.max()
                || piece.stack().getDamageValue() != piece.before() + debits[i]) {
                refundUntouchedWear(player, pieces, debits, pieces.size());
                return;
            }
        }
        ExperienceOrb orb = new ExperienceOrb(player.level(), player.getX(), player.getY(), player.getZ(), xp);
        if (!player.level().addFreshEntity(orb)) {
            refundUntouchedWear(player, pieces, debits, pieces.size());
            orb.discard();
            return;
        }
        CompendiumDiscoveryTracker.recordDiscovery(player, pair.primaryId(), pair.secondaryId());
    }

    private static void refundUntouchedWear(ServerPlayer player, List<Wear> pieces, int[] debits, int count) {
        // Never overwrite changes made by another callback, even when that means we
        // cannot provide a full rollback across arbitrary third-party listeners.
        for (int i = 0; i < count; i++) {
            Wear piece = pieces.get(i);
            if (debits[i] > 0 && actuallyEquipped(player, piece.stack())
                && piece.stack().getMaxDamage() == piece.max()
                && piece.stack().getDamageValue() == piece.before() + debits[i]) {
                piece.stack().setDamageValue(piece.before());
            }
        }
    }

    private static long availableXp(ServerPlayer player) {
        return DurabilityEconomyMath.spendableXp(player.experienceLevel, player.experienceProgress,
            player.getXpNeededForNextLevel());
    }

    /** Pay XP, then saturation, then food. A preflight under one durability pays nothing. */
    public static int payRescue(ServerPlayer player, int maximum) {
        if (!player.isAlive() || maximum <= 0) return 0;
        int target = DurabilityEconomyMath.rescueTarget(maximum,
            FusionConfigStore.signatureInt(RESCUE, "rescue_percent", 10, 1, 100),
            FusionConfigStore.signatureInt(RESCUE, "rescue_durability_cap", 4096, 1, 10_000));
        FoodData food = player.getFoodData();
        float saturation = food.getSaturationLevel();
        if (!Float.isFinite(saturation) || saturation < 0.0F) saturation = 0.0F;
        int hunger = Math.max(0, food.getFoodLevel());
        long xp = Math.max(0L, availableXp(player));
        int affordable = (int) Math.min(target, Math.floor(Math.min((double) target,
            2.0D * Math.min(xp, (long) target) + saturation + hunger)));
        if (affordable < 1) return 0;

        int spendXp = (int) Math.min(xp, (affordable + 1L) / 2L);
        long beforeXp = xp;
        if (spendXp > 0) {
            int scoreBefore = player.getScore();
            player.giveExperiencePoints(-spendXp);
            // Vanilla's negative XP helper also subtracts death score. Only undo exactly
            // that side effect; leave unrelated concurrent score changes untouched.
            if (player.getScore() == scoreBefore - spendXp) player.setScore(scoreBefore);
        }
        long afterXp = availableXp(player);
        long actualXp = afterXp < 0 ? 0 : Math.clamp(beforeXp - afterXp, 0L, (long) spendXp);
        double paid = 2.0D * actualXp;
        double need = Math.max(0.0D, affordable - paid);
        float satBefore = food.getSaturationLevel();
        if (!Float.isFinite(satBefore) || satBefore < 0.0F) satBefore = 0.0F;
        float satWanted = (float) Math.min(satBefore, need);
        if (satWanted > 0.0F) food.setSaturation(Math.max(0.0F, satBefore - satWanted));
        float satAfter = food.getSaturationLevel();
        if (Float.isFinite(satAfter)) paid += Math.max(0.0D, satBefore - satAfter);
        need = Math.max(0.0D, affordable - paid);
        int foodBefore = Math.max(0, food.getFoodLevel());
        int foodWanted = Math.min(foodBefore, (int) Math.ceil(Math.max(0.0D, need - 0.000001D)));
        if (foodWanted > 0) food.setFoodLevel(foodBefore - foodWanted);
        paid += Math.max(0, foodBefore - Math.max(0, food.getFoodLevel()));
        return (int) Math.min(target, Math.floor(paid + 0.000001D));
    }
}

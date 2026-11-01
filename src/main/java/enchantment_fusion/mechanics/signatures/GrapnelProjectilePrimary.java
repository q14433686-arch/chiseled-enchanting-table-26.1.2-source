package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.registry.FusionDataComponents;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.jetbrains.annotations.Nullable;

/** Shared data-pack primary projection at actual ammunition and projectile construction phases. */
public final class GrapnelProjectilePrimary {
    private static final ThreadLocal<Boolean> REPLAY = ThreadLocal.withInitial(() -> false);
    private GrapnelProjectilePrimary() {}
    public static int level(@Nullable ItemStack weapon, String primary) {
        if (weapon == null || weapon.isEmpty()) return 0;
        int best = 0;
        for (FusedPair pair : weapon.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if (primary.equals(pair.primaryId()) && GrapnelSkills.applies(pair, weapon)) best = Math.max(best, pair.primaryLevel());
        return best;
    }
    public static @Nullable Holder<Enchantment> missing(ServerLevel level, ItemStack weapon, String primary) {
        Holder<Enchantment> holder = FusionStationOperations.lookupEnchantment(level.registryAccess(), primary).orElse(null);
        return holder == null || weapon.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(holder) > 0 ? null : holder;
    }
    /** Called inside vanilla useAmmo before the actual split / intangible-projectile decision. */
    public static int ammo(ServerLevel level, ItemStack weapon, ItemStack ammunition, LivingEntity shooter, int vanilla) {
        if (Boolean.TRUE.equals(REPLAY.get()) || !(shooter instanceof ServerPlayer owner) || owner.level() != level
            || (owner.getMainHandItem() != weapon && owner.getOffhandItem() != weapon)) return vanilla;
        int primaryLevel = level(weapon, GrapnelSkills.INFINITY);
        Holder<Enchantment> infinity = primaryLevel > 0 ? missing(level, weapon, GrapnelSkills.INFINITY) : null;
        if (infinity == null) return vanilla;
        MutableFloat value = new MutableFloat(vanilla); REPLAY.set(true);
        try { infinity.value().modifyAmmoCount(level, primaryLevel, ammunition, value); }
        finally { REPLAY.remove(); }
        return Float.isFinite(value.floatValue()) ? Math.max(0, value.intValue()) : vanilla;
    }
    /** Called while vanilla constructs an arrow within the exact real projectile-weapon shot scope. */
    public static int piercing(ServerLevel level, ItemStack weapon, ItemStack ammunition, int vanilla) {
        if (Boolean.TRUE.equals(REPLAY.get()) || !GrapnelVolley.isActualShot(level, weapon)) return vanilla;
        int primaryLevel = level(weapon, GrapnelSkills.PIERCING);
        Holder<Enchantment> piercing = primaryLevel > 0 ? missing(level, weapon, GrapnelSkills.PIERCING) : null;
        if (piercing == null) return vanilla;
        MutableFloat value = new MutableFloat(vanilla); REPLAY.set(true);
        try { piercing.value().modifyPiercingCount(level, primaryLevel, ammunition, value); }
        finally { REPLAY.remove(); }
        return Float.isFinite(value.floatValue()) ? Math.max(0, value.intValue()) : vanilla;
    }
}

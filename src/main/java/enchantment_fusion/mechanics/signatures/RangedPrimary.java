package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.MatrixEffectExecutor;
import enchantment_fusion.model.FusedPair;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.jetbrains.annotations.Nullable;

/** Shared single replay of a missing data-pack Power, Punch or Flame primary on real arrows. */
public final class RangedPrimary {
    private static final ThreadLocal<Boolean> REPLAY = ThreadLocal.withInitial(() -> false);
    private RangedPrimary() {}
    static int level(@Nullable ItemStack weapon, String primary) {
        int result = Math.max(Math.max(GrapnelProjectilePrimary.level(weapon, primary), RangedWearSkills.primaryLevel(weapon, primary)),
            Math.max(Math.max(RangedWorkSkills.primaryLevel(weapon, primary), RangedBreathSkills.primaryLevel(weapon, primary)),
                Math.max(Math.max(RangedTideSkills.primaryLevel(weapon, primary), RangedSneakSkills.primaryLevel(weapon, primary)),
                    RangedGuardSkills.primaryLevel(weapon, primary))));
        FusedPair thermal = ThermalProjectileSkills.selected(weapon);
        return thermal != null && primary.equals(thermal.primaryId()) ? Math.max(result, thermal.primaryLevel()) : result;
    }
    private static @Nullable Holder<Enchantment> missing(ServerLevel level, ItemStack weapon, String id) {
        Holder<Enchantment> holder = FusionStationOperations.lookupEnchantment(level.registryAccess(), id).orElse(null);
        return holder == null || weapon.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(holder) > 0 ? null : holder;
    }
    private static boolean valid(ServerLevel level, ItemStack weapon, DamageSource source) {
        return !Boolean.TRUE.equals(REPLAY.get()) && weapon != null && !weapon.isEmpty()
            && source.getDirectEntity() instanceof AbstractArrow arrow && arrow.level() == level
            && arrow.getOwner() instanceof ServerPlayer owner && source.getEntity() == owner
            && !PhantomVolley.isGhost(arrow) && !MatrixEffectExecutor.isExecuting();
    }
    public static float damage(ServerLevel level, ItemStack weapon, Entity victim, DamageSource source, float value) {
        if (!valid(level, weapon, source)) return value;
        int rank = level(weapon, RangedWearSkills.POWER);
        Holder<Enchantment> power = rank > 0 ? missing(level, weapon, RangedWearSkills.POWER) : null;
        if (power == null) return value;
        MutableFloat result = new MutableFloat(value); REPLAY.set(true);
        try { power.value().modifyDamage(level, rank, weapon, victim, source, result); }
        finally { REPLAY.remove(); }
        return Float.isFinite(result.floatValue()) ? result.floatValue() : value;
    }
    public static float knockback(ServerLevel level, ItemStack weapon, Entity victim, DamageSource source, float value) {
        int rank;
        if (valid(level, weapon, source)) rank = level(weapon, RangedWearSkills.PUNCH);
        else {
            // The older thermal signature also projected Punch for non-player arrow owners.
            // Preserve that behavior without granting unrelated player-only templates to mobs.
            FusedPair thermal = ThermalProjectileSkills.selected(weapon);
            if (Boolean.TRUE.equals(REPLAY.get()) || !(source.getDirectEntity() instanceof AbstractArrow)
                || thermal == null || !RangedWearSkills.PUNCH.equals(thermal.primaryId())) return value;
            rank = thermal.primaryLevel();
        }
        Holder<Enchantment> punch = rank > 0 ? missing(level, weapon, RangedWearSkills.PUNCH) : null;
        if (punch == null) return value;
        MutableFloat result = new MutableFloat(value); REPLAY.set(true);
        try { punch.value().modifyKnockback(level, rank, weapon, victim, source, result); }
        finally { REPLAY.remove(); }
        return Float.isFinite(result.floatValue()) ? result.floatValue() : value;
    }
    public static void spawned(Projectile projectile) {
        if (Boolean.TRUE.equals(REPLAY.get()) || !(projectile instanceof AbstractArrow arrow)
            || !(arrow.level() instanceof ServerLevel level) || !(arrow.getOwner() instanceof ServerPlayer owner)
            || PhantomVolley.isGhost(arrow)) return;
        ItemStack weapon = arrow.getWeaponItem(); int rank = level(weapon, RangedWearSkills.FLAME);
        Holder<Enchantment> flame = rank > 0 && weapon != null ? missing(level, weapon, RangedWearSkills.FLAME) : null;
        if (flame == null) return;
        REPLAY.set(true);
        try { flame.value().onProjectileSpawned(level, rank, new EnchantedItemInUse(weapon, null, owner, arrow::onItemBreak), arrow); }
        finally { REPLAY.remove(); }
    }
}

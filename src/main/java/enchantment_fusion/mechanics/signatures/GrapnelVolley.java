package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.registry.FusionDataComponents;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.jetbrains.annotations.Nullable;

/** The actual draw/list/spawn phases behind the Multishot ▷ Lure adapter. */
public final class GrapnelVolley {
    private static final int MAX_ARROWS_PER_BATCH = 32;
    public static final class Batch {
        private final @Nullable Batch previous;
        private final LivingEntity shooter;
        private final ItemStack weapon;
        private final List<WeakReference<AbstractArrow>> arrows = new ArrayList<>();
        private boolean overflow, completed, valid;
        private Batch(@Nullable Batch previous, LivingEntity shooter, ItemStack weapon) {
            this.previous = previous; this.shooter = shooter; this.weapon = weapon;
        }
    }
    private static final ThreadLocal<Batch> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> REPLAY = ThreadLocal.withInitial(() -> false);
    private GrapnelVolley() {}

    private static int level(@Nullable ItemStack weapon) {
        if (weapon == null || weapon.isEmpty()) return 0;
        int best = 0;
        for (FusedPair pair : weapon.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if (GrapnelSkills.MULTISHOT.equals(pair.primaryId()) && GrapnelSkills.applies(pair, weapon)) best = Math.max(best, pair.primaryLevel());
        return best;
    }
    private static @Nullable Holder<Enchantment> missing(ServerLevel level, ItemStack weapon) {
        Holder<Enchantment> holder = FusionStationOperations.lookupEnchantment(level.registryAccess(), GrapnelSkills.MULTISHOT).orElse(null);
        return holder == null || weapon.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(holder) > 0 ? null : holder;
    }
    /** Replays the data-pack projectile count at draw/load, before vanilla consumes ammunition. */
    public static int count(ServerLevel level, ItemStack weapon, Entity shooter, int vanilla) {
        if (Boolean.TRUE.equals(REPLAY.get()) || !(shooter instanceof ServerPlayer owner)
            || (owner.getMainHandItem() != weapon && owner.getOffhandItem() != weapon)) return vanilla;
        int valueLevel = level(weapon); Holder<Enchantment> holder = valueLevel > 0 ? missing(level, weapon) : null;
        if (holder == null) return vanilla;
        MutableFloat value = new MutableFloat(vanilla); REPLAY.set(true);
        try { holder.value().modifyProjectileCount(level, valueLevel, weapon, shooter, value); }
        finally { REPLAY.remove(); }
        return Float.isFinite(value.floatValue()) ? Math.max(0, value.intValue()) : vanilla;
    }
    /** The matching spread component is replayed at the real shot, not on a later hit. */
    public static float spread(ServerLevel level, ItemStack weapon, Entity shooter, float vanilla) {
        if (Boolean.TRUE.equals(REPLAY.get()) || !(shooter instanceof ServerPlayer owner)
            || (owner.getMainHandItem() != weapon && owner.getOffhandItem() != weapon)) return vanilla;
        int valueLevel = level(weapon); Holder<Enchantment> holder = valueLevel > 0 ? missing(level, weapon) : null;
        if (holder == null) return vanilla;
        MutableFloat value = new MutableFloat(vanilla); REPLAY.set(true);
        try { holder.value().modifyProjectileSpread(level, valueLevel, weapon, shooter, value); }
        finally { REPLAY.remove(); }
        return Float.isFinite(value.floatValue()) ? Math.max(0.0F, value.floatValue()) : vanilla;
    }
    /** Always masks nested shots, even when the nested weapon is not a grapnel carrier. */
    public static Batch begin(LivingEntity shooter, ItemStack weapon) {
        Batch frame = new Batch(CURRENT.get(), shooter, weapon); CURRENT.set(frame); return frame;
    }
    public static @Nullable Batch launched(AbstractArrow arrow, LivingEntity shooter, ItemStack weapon) {
        Batch frame = CURRENT.get();
        if (frame == null || frame.shooter != shooter || frame.weapon != weapon || level(weapon) <= 0
            || !(shooter instanceof ServerPlayer owner) || arrow.getOwner() != owner || PhantomVolley.isGhost(arrow)) return null;
        if (frame.arrows.size() >= MAX_ARROWS_PER_BATCH) { frame.overflow = true; return frame; }
        for (WeakReference<AbstractArrow> entry : frame.arrows) if (entry.get() == arrow) { frame.overflow = true; return frame; }
        frame.arrows.add(new WeakReference<>(arrow)); return frame;
    }
    public static void end(Batch frame, boolean completed) {
        if (CURRENT.get() == frame) {
            if (frame.previous == null) CURRENT.remove(); else CURRENT.set(frame.previous);
        }
        int real = 0;
        if (completed && !frame.overflow && frame.shooter instanceof ServerPlayer owner) {
            for (WeakReference<AbstractArrow> entry : frame.arrows) {
                AbstractArrow arrow = entry.get();
                if (arrow != null && arrow.isAlive() && arrow.level() == owner.level() && arrow.getOwner() == owner
                    && owner.level().getEntity(arrow.getUUID()) == arrow && !PhantomVolley.isGhost(arrow)) real++;
            }
        }
        frame.valid = real >= 2; frame.completed = true;
        frame.arrows.clear();
    }
    public static boolean proven(@Nullable Batch frame) { return frame != null && frame.completed && frame.valid; }
}

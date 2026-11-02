package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.mechanics.MatrixEffectExecutor;
import enchantment_fusion.mechanics.transmute.TransmuteRuntimeSupport;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Reusable evidence for direct player swing/stab effects. A positive return from hurtOrSimulate
 * alone is not enough: both health and absorption are measured across the real damage call.
 * A listener must still check its own target, equipment, provenance and signature priority.
 */
public final class ConfirmedMelee {
    public record Hit(ServerPlayer player, LivingEntity target, ItemStack weapon, EquipmentSlot slot,
                      DamageSource source, boolean full, float actualLoss, boolean eligibleBefore,
                      int fireBefore, boolean sneakingBefore) {
        public Hit(ServerPlayer player, LivingEntity target, ItemStack weapon, EquipmentSlot slot, DamageSource source,
                   boolean full, float actualLoss, boolean eligibleBefore) {
            this(player, target, weapon, slot, source, full, actualLoss, eligibleBefore, 0, false);
        }
    }
    public record Observation(ServerPlayer player, LivingEntity target, ItemStack weapon, EquipmentSlot slot,
                              DamageSource source, boolean full, float health, float absorption, boolean eligibleBefore,
                              int fireBefore, boolean sneakingBefore) {
        public Observation(ServerPlayer player, LivingEntity target, ItemStack weapon, EquipmentSlot slot, DamageSource source,
                           boolean full, float health, float absorption, boolean eligibleBefore) {
            this(player, target, weapon, slot, source, full, health, absorption, eligibleBefore, 0, false);
        }
    }
    private static final List<Consumer<Hit>> LISTENERS = new CopyOnWriteArrayList<>();
    private static final ThreadLocal<Boolean> DISPATCHING = ThreadLocal.withInitial(() -> false);
    private ConfirmedMelee() {}

    public static void register(Consumer<Hit> listener) {
        if (listener != null && !LISTENERS.contains(listener)) LISTENERS.add(listener);
    }

    public static @Nullable Observation before(Player player, ItemStack weapon, EquipmentSlot slot, Entity target,
                                                DamageSource source, boolean full) {
        if (!(player instanceof ServerPlayer owner) || !(target instanceof LivingEntity victim)
            || !owner.isAlive() || !victim.isAlive() || owner.level() != victim.level()
            || source.getEntity() != owner || source.getDirectEntity() != owner
            || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypeTags.BYPASSES_EFFECTS)
            || source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS) || MatrixEffectExecutor.isExecuting()
            || GlazedStrikeEffect.isApplyingBonus() || AmberSealSkill.isApplyingBurst() || LoyaltyGuard.isExecuting()
            || Boolean.TRUE.equals(TransmuteRuntimeSupport.IN_CUSTOM_COMBAT.get())
            || Boolean.TRUE.equals(DISPATCHING.get())) return null;
        boolean eligible = LoyaltyGuard.eligibleTarget(owner, victim) && owner.level().mayInteract(owner, victim.blockPosition())
            && (!(victim instanceof Player other) || other.canHarmPlayer(owner));
        return new Observation(owner, victim, weapon, slot, source, full, victim.getHealth(), victim.getAbsorptionAmount(), eligible,
            victim.getRemainingFireTicks(), owner.isShiftKeyDown());
    }

    public static void after(@Nullable Observation observation, boolean accepted) {
        if (observation == null || !accepted || !observation.player().isAlive()
            || observation.player().level() != observation.target().level()) return;
        float loss = ConfirmedMeleeMath.actualLoss(observation.health(), observation.absorption(),
            observation.target().getHealth(), observation.target().getAbsorptionAmount());
        if (loss <= 0) return;
        Hit hit = new Hit(observation.player(), observation.target(), observation.weapon(), observation.slot(),
            observation.source(), observation.full(), loss, observation.eligibleBefore(), observation.fireBefore(), observation.sneakingBefore());
        boolean previous = Boolean.TRUE.equals(DISPATCHING.get());
        DISPATCHING.set(true);
        try {
            for (Consumer<Hit> listener : LISTENERS) {
                try { listener.accept(hit); }
                catch (RuntimeException error) { EnchantmentFusion.LOGGER.error("Confirmed melee listener failed", error); }
            }
        } finally { DISPATCHING.set(previous); }
    }
}

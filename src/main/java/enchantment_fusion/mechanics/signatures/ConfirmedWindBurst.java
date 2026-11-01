package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.mechanics.FusionStationOperations;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentTarget;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** A real Wind Burst invocation and its observed upward impulse; subscribers prove their own hit provenance. */
public final class ConfirmedWindBurst {
    public record Effect(ServerLevel level, ServerPlayer owner, LivingEntity victim, ItemStack stack,
                         DamageSource source, int enchantmentLevel) {}
    private static final List<Consumer<Effect>> LISTENERS = new CopyOnWriteArrayList<>();
    private ConfirmedWindBurst() {}
    public static void register(Consumer<Effect> listener) {
        if (listener != null && !LISTENERS.contains(listener)) LISTENERS.add(listener);
    }
    public static boolean upwardImpulse(@Nullable Vec3 before, @Nullable Vec3 after) {
        return before != null && after != null && Double.isFinite(before.x) && Double.isFinite(before.y) && Double.isFinite(before.z)
            && Double.isFinite(after.x) && Double.isFinite(after.y) && Double.isFinite(after.z) && after.y - before.y > 1.0E-4D;
    }
    public static void observe(Enchantment enchantment, ServerLevel level, int enchantmentLevel, EnchantedItemInUse item,
                               EnchantmentTarget side, Entity victim, DamageSource source, @Nullable Vec3 before) {
        if (LISTENERS.isEmpty() || side != EnchantmentTarget.ATTACKER || item.inSlot() != EquipmentSlot.MAINHAND
            || !(item.owner() instanceof ServerPlayer owner) || !(victim instanceof LivingEntity target) || owner.level() != level
            || source.getEntity() != owner || source.getDirectEntity() != owner || !upwardImpulse(before, owner.getDeltaMovement())
            || !FusionStationOperations.lookupEnchantment(level.registryAccess(), MeleeWindSkills.WIND)
                .map(h -> h.value() == enchantment).orElse(false)) return;
        Effect effect = new Effect(level, owner, target, item.itemStack(), source, enchantmentLevel);
        for (Consumer<Effect> listener : LISTENERS) try { listener.accept(effect); }
        catch (RuntimeException error) { EnchantmentFusion.LOGGER.error("Confirmed wind-burst listener failed", error); }
    }
}

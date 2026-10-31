package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.config.FusionConfigStore;
import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.jetbrains.annotations.Nullable;

/** Real direct attacks capture actual resisted movement; the two orders choose when and how to release it. */
public final class MomentumSealSkills {
    public static final String FORWARD = "momentum_vault";
    public static final String REVERSE = "crystal_snare";
    private static final String KNOCKBACK = "minecraft:knockback";
    private static final String SILK = "minecraft:silk_touch";
    private static final Set<FusionCarrier> WEAPONS = Set.of(FusionCarrier.SWORD, FusionCarrier.AXE, FusionCarrier.SPEAR, FusionCarrier.MACE);
    private static final Identifier SLOW_ID = EnchantmentFusion.id("momentum_seal_control");
    private static final AttributeModifier STOP_MOVEMENT = new AttributeModifier(SLOW_ID, -1.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    private static final int MAX_MARKS = 128;
    private static final double MAX_RANGE_SQUARED = 24.0D * 24.0D;
    private static final ThreadLocal<Attack> CURRENT = new ThreadLocal<>();
    private static final Map<UUID, Mark> MARKS = new HashMap<>();
    private static final Map<UUID, Mark> HELD_TARGETS = new HashMap<>();

    private MomentumSealSkills() {}

    public static boolean matches(@Nullable FusedPair pair, String signature) {
        return pair != null && ((KNOCKBACK.equals(pair.primaryId()) && SILK.equals(pair.secondaryId()) && FORWARD.equals(signature))
            || (SILK.equals(pair.primaryId()) && KNOCKBACK.equals(pair.secondaryId()) && REVERSE.equals(signature)));
    }
    public static boolean supportsCarrier(Set<FusionCarrier> roles) {
        return roles != null && roles.stream().anyMatch(WEAPONS::contains);
    }
    public static boolean applies(@Nullable FusedPair pair, @Nullable ItemStack stack, String signature) {
        return matches(pair, signature) && stack != null && !stack.isEmpty() && supportsCarrier(FusionCarrier.classify(stack));
    }
    public static @Nullable FusedPair selected(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty() || !supportsCarrier(FusionCarrier.classify(stack))) return null;
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if (SignatureCatalog.resolve(pair).map(entry -> matches(pair, entry.id())).orElse(false)) return pair;
        return null;
    }
    public static boolean hasEquipped(ServerPlayer player) {
        return selected(player.getMainHandItem()) != null || selected(player.getOffhandItem()) != null;
    }
    private static boolean sameWeapon(ServerPlayer player, ItemStack stack, FusedPair pair) {
        return (player.getMainHandItem() == stack || player.getOffhandItem() == stack) && pair.equals(selected(stack));
    }
    private static boolean validTarget(ServerPlayer owner, LivingEntity target) {
        return owner.isAlive() && !owner.isSpectator() && owner.level() == target.level() && !target.isRemoved()
            && GlazedStrikeEffect.canAffect(owner, target) && owner.distanceToSqr(target) <= MAX_RANGE_SQUARED
            && owner.level().mayInteract(owner, target.blockPosition());
    }
    private static int parameter(String id, String key, int fallback, int min, int max) {
        return FusionConfigStore.signatureInt(id, key, fallback, min, max);
    }

    /** A frame masks unrelated or reentrant swings; even invalid frames must be restored. */
    public static final class Attack {
        private final @Nullable Attack previous;
        private final Player player;
        private final Entity target;
        private final ItemStack weapon;
        private final @Nullable FusedPair pair;
        private @Nullable DamageSource source;
        private boolean succeeded;
        private Attack(@Nullable Attack previous, Player player, Entity target, ItemStack weapon) {
            this.previous = previous; this.player = player; this.target = target; this.weapon = weapon;
            this.pair = player instanceof ServerPlayer ? selected(weapon) : null;
        }
    }
    public static Attack beginAttack(Player player, ItemStack weapon, Entity target) {
        Attack frame = new Attack(CURRENT.get(), player, target, weapon);
        CURRENT.set(frame);
        return frame;
    }
    public static void endAttack(Attack frame) {
        if (frame.previous == null) CURRENT.remove(); else CURRENT.set(frame.previous);
    }
    public static void damageResult(Player player, Entity target, DamageSource source, boolean succeeded) {
        Attack frame = CURRENT.get();
        if (frame == null || frame.player != player || frame.target != target || source.getEntity() != player
            || source.getDirectEntity() != player) return;
        frame.source = source; frame.succeeded = succeeded;
    }
    private static boolean usable(@Nullable Attack frame, Player player, Entity entity) {
        return frame != null && frame.player == player && frame.target == entity && frame.succeeded && frame.source != null
            && frame.pair != null && player instanceof ServerPlayer owner && entity instanceof LivingEntity target
            && sameWeapon(owner, frame.weapon, frame.pair) && validTarget(owner, target);
    }

    /** Apply the actual data-pack Knockback once when it is not already projected on the weapon used by vanilla. */
    public static float adjustKnockback(Player player, Entity target, float vanilla) {
        Attack frame = CURRENT.get();
        if (!usable(frame, player, target)) return vanilla;
        ServerPlayer owner = (ServerPlayer) player;
        ServerLevel level = owner.level();
        Holder<Enchantment> knockback = FusionStationOperations.lookupEnchantment(level.registryAccess(), KNOCKBACK).orElse(null);
        if (knockback == null) return vanilla;
        int nativeLevel = frame.weapon.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(knockback);
        if (frame.weapon == player.getWeaponItem() && nativeLevel > 0) return vanilla;
        int levelOfEffect = KNOCKBACK.equals(frame.pair.primaryId()) ? frame.pair.primaryLevel() : frame.pair.secondaryLevel();
        MutableFloat effect = new MutableFloat(0.0F);
        knockback.value().modifyKnockback(level, levelOfEffect, frame.weapon, target, frame.source, effect);
        float extra = effect.floatValue() / 2.0F; // LivingEntity.getKnockback applies the same division.
        return Float.isFinite(extra) ? Math.max(0.0F, vanilla + extra) : vanilla;
    }

    private static final class Mark {
        final UUID owner;
        final LivingEntity target;
        final ItemStack weapon;
        final FusedPair pair;
        final Vec3 original;
        final boolean reverse;
        long expires;
        double speed;
        boolean held;
        Mark(ServerPlayer owner, LivingEntity target, ItemStack weapon, FusedPair pair, Vec3 velocity, long expires) {
            this.owner = owner.getUUID(); this.target = target; this.weapon = weapon; this.pair = pair;
            this.original = MomentumSealMath.direction(velocity); this.speed = MomentumSealMath.speed(velocity);
            this.reverse = SILK.equals(pair.primaryId()); this.expires = expires;
        }
    }

    /** Called inside vanilla knockback before its ServerPlayer motion packet/velocity restoration. */
    public static void afterLivingKnockback(Player player, LivingEntity target, Vec3 before) {
        Attack frame = CURRENT.get();
        if (!usable(frame, player, target)) return;
        Vec3 after = target.getDeltaMovement();
        // A knockback-resistant target is not frozen by a force that never moved it.
        if (MomentumSealMath.speed(after.subtract(before)) < 0.01D || MomentumSealMath.speed(after) < 0.01D) return;
        ServerPlayer owner = (ServerPlayer) player;
        Mark previous = MARKS.get(owner.getUUID());
        if ((previous != null && previous.held) || HELD_TARGETS.containsKey(target.getUUID())) return;
        if (previous == null && MARKS.size() >= MAX_MARKS) return;
        String id = SILK.equals(frame.pair.primaryId()) ? REVERSE : FORWARD;
        int lifetime = parameter(id, "candidate_ticks", 40, 1, 400);
        Mark mark = new Mark(owner, target, frame.weapon, frame.pair, after, owner.level().getGameTime() + lifetime);
        MARKS.put(owner.getUUID(), mark);
        if (mark.reverse) {
            if (!hold(owner, mark)) MARKS.remove(owner.getUUID(), mark);
        } else {
            owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.momentum_ready"), true);
        }
    }
    /** ServerPlayer.causeExtraKnockback can restore its pre-hit velocity after sending a packet. */
    public static void afterKnockbackCall(Player player, Entity target) {
        if (!(player instanceof ServerPlayer owner)) return;
        Mark mark = MARKS.get(owner.getUUID());
        if (mark != null && mark.held && mark.target == target && valid(owner, mark)) stopHorizontal(mark.target, true);
    }
    private static boolean hold(ServerPlayer owner, Mark mark) {
        if (HELD_TARGETS.containsKey(mark.target.getUUID()) || !valid(owner, mark)) return false;
        AttributeInstance speed = mark.target.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return false;
        // Target state is transient and each target has at most one controlling mark.
        if (!speed.hasModifier(SLOW_ID)) speed.addTransientModifier(STOP_MOVEMENT);
        mark.held = true;
        int silk = SILK.equals(mark.pair.primaryId()) ? mark.pair.primaryLevel() : mark.pair.secondaryLevel();
        int base = parameter(mark.reverse ? REVERSE : FORWARD, "control_ticks", 20, 1, 200);
        mark.expires = owner.level().getGameTime() + Math.min(240, base + Math.clamp(silk, 1, 10) * 4);
        HELD_TARGETS.put(mark.target.getUUID(), mark);
        stopHorizontal(mark.target, true);
        owner.sendSystemMessage(Component.translatable(mark.reverse
            ? "message.enchantment_fusion.momentum_direction" : "message.enchantment_fusion.momentum_captured"), true);
        return true;
    }
    private static void stopHorizontal(LivingEntity target, boolean notifyPlayer) {
        Vec3 movement = target.getDeltaMovement();
        target.setDeltaMovement(0.0D, movement.y, 0.0D);
        target.hurtMarked = true;
        if (notifyPlayer && target instanceof ServerPlayer player) player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }
    private static boolean valid(ServerPlayer owner, Mark mark) {
        return sameWeapon(owner, mark.weapon, mark.pair) && validTarget(owner, mark.target);
    }
    private static void clearMark(Mark mark) {
        MARKS.remove(mark.owner, mark);
        HELD_TARGETS.remove(mark.target.getUUID(), mark);
        AttributeInstance speed = mark.target.getAttribute(Attributes.MOVEMENT_SPEED);
        if (mark.held && speed != null) speed.removeModifier(SLOW_ID);
    }
    private static void release(@Nullable ServerPlayer owner, Mark mark, Vec3 preferred, boolean applyForce) {
        clearMark(mark); // Remove control first; never retain an iterator across a callback.
        if (!mark.held || !applyForce || owner == null || !validTarget(owner, mark.target)) return;
        Vec3 motion = MomentumSealMath.release(preferred, mark.original, mark.speed, mark.target.getDeltaMovement().y);
        mark.target.setDeltaMovement(motion);
        mark.target.hurtMarked = true;
        if (mark.target instanceof ServerPlayer player) player.connection.send(new ClientboundSetEntityMotionPacket(player));
        owner.level().sendParticles(ParticleTypes.CRIT, mark.target.getX(), mark.target.getY(0.5D), mark.target.getZ(),
            8, 0.18D, 0.2D, 0.18D, 0.02D);
    }
    public static boolean activate(ServerPlayer player) {
        Mark mark = MARKS.get(player.getUUID());
        if (mark == null || !valid(player, mark) || player.level().getGameTime() >= mark.expires) {
            if (mark != null) release(player, mark, mark.original, true);
            player.sendSystemMessage(Component.translatable("message.enchantment_fusion.momentum_not_ready"), true);
            return false;
        }
        if (!mark.held) {
            Vec3 current = MomentumSealMath.horizontal(mark.target.getDeltaMovement());
            double speed = MomentumSealMath.speed(current);
            if (speed < 0.01D || current.dot(mark.original) <= 0.0D) {
                player.sendSystemMessage(Component.translatable("message.enchantment_fusion.momentum_not_moving"), true);
                return false;
            }
            mark.speed = speed; // Save the remaining actual motion, not an imaginary full-strength launch.
            return hold(player, mark);
        }
        Vec3 direction = mark.reverse ? player.getLookAngle() : mark.original;
        release(player, mark, direction, true);
        CompendiumDiscoveryTracker.recordDiscovery(player, mark.pair.primaryId(), mark.pair.secondaryId());
        player.sendSystemMessage(Component.translatable("message.enchantment_fusion.momentum_released"), true);
        return true;
    }
    public static void tickServer(MinecraftServer server) {
        for (Mark mark : List.copyOf(MARKS.values())) {
            if (MARKS.get(mark.owner) != mark) continue;
            ServerPlayer owner = server.getPlayerList().getPlayer(mark.owner);
            if (owner == null || !valid(owner, mark)) { release(owner, mark, mark.original, true); continue; }
            if (owner.level().getGameTime() >= mark.expires) { release(owner, mark, mark.original, true); continue; }
            if (mark.held) {
                stopHorizontal(mark.target, false);
                if (owner.level().getGameTime() % 5 == 0) owner.level().sendParticles(ParticleTypes.END_ROD,
                    mark.target.getX(), mark.target.getY(0.5D), mark.target.getZ(), 1, 0.2D, 0.2D, 0.2D, 0.0D);
            }
        }
    }
    public static void onDisconnect(ServerPlayer player) {
        Mark mark = MARKS.get(player.getUUID());
        if (mark != null) release(player, mark, mark.original, true);
    }
    public static void clear() {
        for (Mark mark : List.copyOf(MARKS.values())) clearMark(mark);
        MARKS.clear(); HELD_TARGETS.clear(); CURRENT.remove();
    }
}

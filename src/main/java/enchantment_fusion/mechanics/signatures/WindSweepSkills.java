package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.config.FusionConfigStore;
import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.MatrixEffectExecutor;
import enchantment_fusion.mechanics.transmute.TransmuteRuntimeSupport;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.jetbrains.annotations.Nullable;

/** Observes actual resisted knockback and actual sword sweeps; neither direction places blocks. */
public final class WindSweepSkills {
    public static final String EYE = "cyclone_repulse", CORE = "gale_vortex";
    private static final String KNOCKBACK = "minecraft:knockback", SWEEP = "minecraft:sweeping_edge";
    private static final Set<FusionCarrier> MELEE = Set.of(FusionCarrier.SWORD, FusionCarrier.AXE,
        FusionCarrier.SPEAR, FusionCarrier.MACE, FusionCarrier.TRIDENT);
    private static final int MAX_EYES = 128, MAX_CORES = 128, MAX_VICTIMS = 48;
    private record Gear(ItemStack stack, FusedPair pair) {}
    private static final class Eye {
        final UUID owner, target; final ResourceKey<Level> dimension; final Gear gear; final long created, expires;
        Vec3 last;
        Eye(ServerPlayer owner, LivingEntity target, Gear gear, long created, long expires) {
            this.owner = owner.getUUID(); this.target = target.getUUID(); this.dimension = owner.level().dimension();
            this.gear = gear; this.created = created; this.expires = expires; this.last = target.position();
        }
    }
    private record Core(UUID owner, ResourceKey<Level> dimension, Gear gear, Vec3 center, long created, long expires, boolean skipInitialSwing) {
        Core armed() { return new Core(owner, dimension, gear, center, created, expires, false); }
    }
    public static final class Attack {
        final @Nullable Attack previous; final Player player; final ItemStack weapon; final Entity target; final @Nullable Gear gear;
        @Nullable DamageSource source; boolean damaged;
        Attack(@Nullable Attack previous, Player player, ItemStack weapon, Entity target) {
            this.previous = previous; this.player = player; this.weapon = weapon; this.target = target;
            this.gear = player instanceof ServerPlayer && player.getMainHandItem() == weapon ? selected(weapon) : null;
        }
    }
    public static final class Hit {
        final @Nullable Attack frame; final @Nullable LivingEntity target; final @Nullable DamageSource source;
        final float health, absorption;
        Hit(@Nullable Attack frame, @Nullable LivingEntity target, @Nullable DamageSource source) {
            this.frame = frame; this.target = target; this.source = source;
            this.health = target == null ? 0 : target.getHealth(); this.absorption = target == null ? 0 : target.getAbsorptionAmount();
        }
    }
    private static final ThreadLocal<Attack> CURRENT = new ThreadLocal<>();
    private static final List<Eye> EYES = new ArrayList<>();
    private static final Map<UUID, Core> CORES = new HashMap<>();
    private WindSweepSkills() {}

    public static boolean matches(@Nullable FusedPair pair, String signature) {
        return pair != null && (KNOCKBACK.equals(pair.primaryId()) && SWEEP.equals(pair.secondaryId()) && EYE.equals(signature)
            || SWEEP.equals(pair.primaryId()) && KNOCKBACK.equals(pair.secondaryId()) && CORE.equals(signature));
    }
    public static boolean supportsCarrier(Set<FusionCarrier> roles, String signature) {
        return roles != null && (EYE.equals(signature) ? roles.stream().anyMatch(MELEE::contains)
            : CORE.equals(signature) && roles.contains(FusionCarrier.SWORD));
    }
    public static boolean applies(@Nullable FusedPair pair, @Nullable ItemStack stack, String signature) {
        return matches(pair, signature) && stack != null && !stack.isEmpty() && supportsCarrier(FusionCarrier.classify(stack), signature);
    }
    private static @Nullable Gear selected(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if (SignatureCatalog.resolve(pair).map(e -> applies(pair, stack, e.id())).orElse(false)) return new Gear(stack, pair);
        return null;
    }
    private static boolean ready(ServerPlayer player) { return player.isAlive() && !player.isSpectator(); }
    private static boolean owns(ServerPlayer player, Gear gear) {
        if (gear.stack().isEmpty() || !gear.stack().getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().contains(gear.pair())
            || !SignatureCatalog.resolve(gear.pair()).map(e -> applies(gear.pair(), gear.stack(), e.id())).orElse(false)) return false;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) if (player.getInventory().getItem(i) == gear.stack()) return true;
        return false;
    }
    private static boolean target(ServerPlayer owner, LivingEntity entity) {
        return owner.level() == entity.level() && LoyaltyGuard.eligibleTarget(owner, entity)
            && owner.level().mayInteract(owner, entity.blockPosition()) && (!(entity instanceof Player other) || other.canHarmPlayer(owner));
    }
    private static boolean direct(ServerPlayer owner, DamageSource source) {
        return source.getEntity() == owner && source.getDirectEntity() == owner
            && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !source.is(DamageTypeTags.BYPASSES_EFFECTS)
            && !source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS) && !MatrixEffectExecutor.isExecuting()
            && !GlazedStrikeEffect.isApplyingBonus() && !AmberSealSkill.isApplyingBurst() && !LoyaltyGuard.isExecuting()
            && !Boolean.TRUE.equals(TransmuteRuntimeSupport.IN_CUSTOM_COMBAT.get());
    }
    private static int param(String id, String name, int fallback, int min, int max) {
        return FusionConfigStore.signatureInt(id, name, fallback, min, max);
    }
    public static Attack beginAttack(Player player, ItemStack stack, Entity entity) {
        Attack frame = new Attack(CURRENT.get(), player, stack, entity); CURRENT.set(frame); return frame;
    }
    public static void endAttack(Attack frame) { if (frame.previous == null) CURRENT.remove(); else CURRENT.set(frame.previous); }
    public static Hit beforeDamage(Player player, ItemStack stack, Entity entity, DamageSource source) {
        Attack frame = CURRENT.get();
        if (!(player instanceof ServerPlayer owner) || frame == null || frame.player != player || frame.weapon != stack
            || frame.target != entity || frame.gear == null || !ready(owner) || !owns(owner, frame.gear)
            || !(entity instanceof LivingEntity living) || !target(owner, living) || !direct(owner, source)) return new Hit(null, null, null);
        return new Hit(frame, living, source);
    }
    public static void afterDamage(Hit hit, boolean accepted) {
        Attack frame = hit.frame; LivingEntity victim = hit.target;
        if (!accepted || frame == null || victim == null || CURRENT.get() != frame || !(frame.player instanceof ServerPlayer owner)
            || !ready(owner) || owner.getMainHandItem() != frame.weapon || frame.gear == null || !owns(owner, frame.gear)
            || hit.source == null || !direct(owner, hit.source)) return;
        float loss = Math.max(0, hit.health - victim.getHealth()) + Math.max(0, hit.absorption - victim.getAbsorptionAmount());
        if (Float.isFinite(loss) && loss > 0) { frame.damaged = true; frame.source = hit.source; }
    }
    private static boolean usable(Attack frame, Player player, Entity entity) {
        return frame != null && frame.player == player && frame.target == entity && frame.damaged && frame.source != null
            && frame.gear != null && player instanceof ServerPlayer owner && ready(owner)
            && owner.getMainHandItem() == frame.weapon && owns(owner, frame.gear) && entity instanceof LivingEntity living && target(owner, living);
    }
    /** Non-native weapons replay the actual Knockback data-pack effect once. */
    public static float adjustKnockback(Player player, Entity target, float vanilla) {
        Attack frame = CURRENT.get();
        if (!usable(frame, player, target) || !KNOCKBACK.equals(frame.gear.pair().primaryId())) return vanilla;
        // A legacy item containing multiple Knockback pairs can already have replayed it in the momentum hook.
        if (MomentumSealSkills.selected(frame.weapon) != null) return vanilla;
        ServerPlayer owner = (ServerPlayer) player; ServerLevel level = owner.level();
        Holder<Enchantment> enchantment = FusionStationOperations.lookupEnchantment(level.registryAccess(), KNOCKBACK).orElse(null);
        if (enchantment == null || frame.weapon.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(enchantment) > 0) return vanilla;
        MutableFloat value = new MutableFloat(0.0F);
        enchantment.value().modifyKnockback(level, frame.gear.pair().primaryLevel(), frame.weapon, target, frame.source, value);
        float extra = value.floatValue() / 2.0F;
        return Float.isFinite(extra) ? Math.max(0.0F, vanilla + extra) : vanilla;
    }
    /** Called before vanilla restores a player's server velocity after sending its knockback packet. */
    public static void afterKnockback(Player player, LivingEntity victim, Vec3 before) {
        Attack frame = CURRENT.get();
        if (!usable(frame, player, victim) || !KNOCKBACK.equals(frame.gear.pair().primaryId())) return;
        Vec3 change = victim.getDeltaMovement().subtract(before);
        if (!Double.isFinite(change.x) || !Double.isFinite(change.z) || change.horizontalDistanceSqr() < 0.0001D) return;
        ServerPlayer owner = (ServerPlayer) player; long now = owner.level().getGameTime();
        EYES.removeIf(eye -> eye.owner.equals(owner.getUUID()) && eye.target.equals(victim.getUUID()));
        List<Eye> owned = EYES.stream().filter(eye -> eye.owner.equals(owner.getUUID())).toList();
        if (owned.size() >= 2) EYES.remove(owned.getFirst());
        if (EYES.size() >= MAX_EYES) return;
        EYES.add(new Eye(owner, victim, frame.gear, now, now + param(EYE, "lifetime_ticks", 80, 10, 400)));
        owner.level().sendParticles(ParticleTypes.CLOUD, victim.getX(), victim.getY(0.5D), victim.getZ(), 10, 0.2D, 0.3D, 0.2D, 0.02D);
        CompendiumDiscoveryTracker.recordDiscovery(owner, KNOCKBACK, SWEEP);
    }
    /** Called at the head of vanilla doSweepAttack after the primary real hit. */
    public static void afterRealSweep(Player player, Entity originalTarget, DamageSource source) {
        Attack frame = CURRENT.get();
        if (frame == null || frame.player != player || frame.target != originalTarget || !frame.damaged || frame.source != source
            || frame.gear == null || !SWEEP.equals(frame.gear.pair().primaryId()) || !(player instanceof ServerPlayer owner)
            || !ready(owner) || owner.getMainHandItem() != frame.weapon || !owns(owner, frame.gear) || !direct(owner, source)) return;
        long now = owner.level().getGameTime(); Core previous = CORES.get(owner.getUUID());
        if (previous != null && previous.gear().stack() == frame.weapon && now < previous.expires()) return;
        if (previous == null && CORES.size() >= MAX_CORES) return;
        Vec3 look = owner.getLookAngle(); Vec3 direction = new Vec3(look.x, 0, look.z);
        if (direction.lengthSqr() < 1.0E-8D) return;
        Vec3 from = owner.getEyePosition(), center = from.add(direction.normalize().scale(2.2D));
        if (!owner.level().mayInteract(owner, net.minecraft.core.BlockPos.containing(center))
            || !VirtualCombatSight.clear(owner.level(), owner, from, center)) return;
        CORES.put(owner.getUUID(), new Core(owner.getUUID(), owner.level().dimension(), frame.gear, center, now,
            now + param(CORE, "lifetime_ticks", 80, 10, 400), true));
        owner.level().sendParticles(ParticleTypes.CLOUD, center.x, center.y, center.z, 12, 0.25D, 0.25D, 0.25D, 0.02D);
        CompendiumDiscoveryTracker.recordDiscovery(owner, SWEEP, KNOCKBACK);
    }
    /** A subsequent real client main-hand swing, including an empty-air swing, releases an aimed core. */
    public static void onSwing(ServerPlayer owner, InteractionHand hand) {
        Core core = CORES.get(owner.getUUID()); long now = owner.level().getGameTime();
        if (hand != InteractionHand.MAIN_HAND || core == null || !ready(owner) || !core.dimension().equals(owner.level().dimension())
            || owner.getMainHandItem() != core.gear().stack() || !owns(owner, core.gear()) || now >= core.expires()) return;
        // The initial sword attack packet is followed by its own animation packet. Never
        // mistake a delayed original animation for the separately requested second swing.
        if (core.skipInitialSwing()) { CORES.replace(owner.getUUID(), core, core.armed()); return; }
        if (now - core.created() < 4) return;
        Vec3 from = owner.getEyePosition(), toward = core.center().subtract(from); Vec3 look = owner.getLookAngle();
        if (!WindSweepMath.aimed(toward.lengthSqr(), look.lengthSqr(), toward.normalize().dot(look.normalize()))
            || !VirtualCombatSight.clear(owner.level(), owner, from, core.center())) return;
        if (!CORES.remove(owner.getUUID(), core)) return;
        affect(owner, core.gear(), core.center(), null, true);
        owner.level().sendParticles(ParticleTypes.EXPLOSION, core.center().x, core.center().y, core.center().z, 1, 0, 0, 0, 0);
    }
    private static void affect(ServerPlayer owner, Gear gear, Vec3 center, @Nullable LivingEntity anchor, boolean burst) {
        String id = burst || SWEEP.equals(gear.pair().primaryId()) ? CORE : EYE;
        int radiusBase = param(id, "radius_tenths", 30, 10, 60);
        int sweepingLevel = SWEEP.equals(gear.pair().primaryId()) ? gear.pair().primaryLevel() : gear.pair().secondaryLevel();
        double radius = Math.min(6.0D, (radiusBase + 2 * Math.clamp(sweepingLevel - 1, 0, 9)) / 10.0D);
        ServerLevel level = owner.level(); AABB area = AABB.ofSize(center, radius * 2, radius * 2, radius * 2);
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, area, e -> e != anchor && target(owner, e));
        victims.sort(Comparator.comparingDouble(e -> e.distanceToSqr(center))); int count = 0;
        for (LivingEntity victim : victims) {
            if (++count > MAX_VICTIMS) break;
            Vec3 to = victim.getBoundingBox().getCenter(); Vec3 horizontal = new Vec3(to.x - center.x, 0, to.z - center.z);
            double distance = horizontal.lengthSqr();
            if (!Double.isFinite(distance) || distance < 0.04D || to.distanceToSqr(center) > radius * radius
                || !VirtualCombatSight.clear(level, owner, center, to)) continue;
            Vec3 direction = horizontal.normalize().scale(burst ? 1 : -1);
            double resistance = victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
            Vec3 velocity = victim.getDeltaMovement(); double already = velocity.x * direction.x + velocity.z * direction.z;
            int strength = burst ? param(CORE, "burst_force_hundredths", 85, 10, 200) : param(id, "pull_force_hundredths", 12, 1, 50);
            double force = WindSweepMath.force(strength / 100.0D, resistance, already, burst);
            if (force <= 0) continue;
            victim.push(direction.x * force, burst ? 0.12D * (1.0D - Math.clamp(resistance, 0, 1)) : 0, direction.z * force);
            victim.hurtMarked = true;
            if (victim instanceof ServerPlayer player) player.connection.send(new ClientboundSetEntityMotionPacket(player));
        }
    }
    public static void tickServer(MinecraftServer server) {
        for (Eye eye : List.copyOf(EYES)) {
            ServerLevel level = server.getLevel(eye.dimension); ServerPlayer owner = server.getPlayerList().getPlayer(eye.owner);
            Entity entity = level == null ? null : level.getEntity(eye.target);
            if (level == null || owner == null || owner.level() != level || !ready(owner) || !owns(owner, eye.gear)
                || !(entity instanceof LivingEntity anchor) || !target(owner, anchor) || level.getGameTime() >= eye.expires
                || eye.last.distanceToSqr(anchor.position()) > 16.0D) { EYES.remove(eye); continue; }
            eye.last = anchor.position();
            if ((level.getGameTime() - eye.created) % 5 == 0) {
                Vec3 center = anchor.getBoundingBox().getCenter(); affect(owner, eye.gear, center, anchor, false);
                level.sendParticles(ParticleTypes.CLOUD, center.x, center.y, center.z, 3, 0.25D, 0.25D, 0.25D, 0.01D);
            }
        }
        for (Core core : List.copyOf(CORES.values())) {
            ServerLevel level = server.getLevel(core.dimension()); ServerPlayer owner = server.getPlayerList().getPlayer(core.owner());
            if (level == null || owner == null || owner.level() != level || !ready(owner) || !owns(owner, core.gear())
                || level.getGameTime() >= core.expires()) { CORES.remove(core.owner(), core); continue; }
            if ((level.getGameTime() - core.created()) % 5 == 0) {
                affect(owner, core.gear(), core.center(), null, false);
                level.sendParticles(ParticleTypes.CLOUD, core.center().x, core.center().y, core.center().z, 3, 0.3D, 0.3D, 0.3D, 0.01D);
            }
        }
    }
    public static void onDisconnect(UUID id) {
        EYES.removeIf(eye -> eye.owner.equals(id) || eye.target.equals(id)); CORES.remove(id);
    }
    public static void clear() { EYES.clear(); CORES.clear(); CURRENT.remove(); }
}

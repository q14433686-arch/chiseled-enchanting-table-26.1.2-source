package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mechanics.MatrixEffectExecutor;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** 43.3: a real melee mark chains virtual current; a real thrown trident leaves a bounded trace. */
public final class MeleeLightningSkills {
    public static final String CHANNELING = "minecraft:channeling";
    private static final int MAX_OWNERS = 256, MAX_FLIGHTS = 4096, LIFETIME = 120;
    private static final Set<FusionCarrier> WEAPONS = Set.of(FusionCarrier.SWORD, FusionCarrier.AXE, FusionCarrier.SPEAR, FusionCarrier.MACE);
    private static final OwnedTargetMarks MARKS = new OwnedTargetMarks(MAX_OWNERS, MeleeLightningSkills::applies);
    private record Flight(UUID owner, ResourceKey<Level> dimension, ItemStack stack, FusedPair pair, long expires) {}
    private static final Map<ThrownTrident, Flight> FLIGHTS = new WeakHashMap<>();
    private record Trace(ThrownTrident projectile, ItemStack stack, FusedPair pair, ResourceKey<Level> dimension,
                         Vec3 origin, long expires, boolean armed) {
        Trace armedTrace() { return new Trace(projectile, stack, pair, dimension, origin, expires, true); }
    }
    private static final Map<UUID, Trace> TRACES = new HashMap<>();
    private static final ThreadLocal<Damage> DAMAGE = new ThreadLocal<>();
    private MeleeLightningSkills() {}

    public static void init() {
        ConfirmedMelee.register(MeleeLightningSkills::onHit);
        MeleeAttackThemes.registerPrimaryTemplate("melee_lightning", (pair, stack) -> attackFirst(pair) && applies(pair, stack));
    }
    public static boolean attackFirst(@Nullable FusedPair pair) {
        return pair != null && MeleeAttackThemes.isAttack(pair.primaryId()) && CHANNELING.equals(pair.secondaryId());
    }
    public static boolean channelFirst(@Nullable FusedPair pair) {
        return pair != null && CHANNELING.equals(pair.primaryId()) && MeleeAttackThemes.isAttack(pair.secondaryId());
    }
    public static boolean matches(@Nullable FusedPair pair) {
        // Both 32.1 Sharpness orders are selected exact designs, even though only one
        // currently has a default SignatureCatalog entry. Do not seize the other order.
        if (pair == null || MeleeAttackThemes.SHARP.equals(pair.primaryId()) || MeleeAttackThemes.SHARP.equals(pair.secondaryId())) return false;
        return (attackFirst(pair) || channelFirst(pair)) && SignatureCatalog.resolve(pair).isEmpty();
    }
    public static boolean supportsCarrier(@Nullable FusedPair pair, @Nullable Set<FusionCarrier> roles) {
        if (roles == null) return false;
        return attackFirst(pair) ? roles.stream().anyMatch(WEAPONS::contains) : channelFirst(pair) && roles.contains(FusionCarrier.TRIDENT);
    }
    public static boolean applies(@Nullable FusedPair pair, @Nullable ItemStack stack) {
        return matches(pair) && stack != null && !stack.isEmpty() && supportsCarrier(pair, FusionCarrier.classify(stack));
    }
    private static @Nullable FusedPair select(ItemStack stack, boolean reverse) {
        if (stack == null || stack.isEmpty()) return null;
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if ((reverse ? channelFirst(pair) : attackFirst(pair)) && applies(pair, stack)) return pair;
        return null;
    }
    private static boolean ready(ServerPlayer owner) { return owner != null && owner.isAlive() && !owner.isSpectator(); }
    private static boolean target(ServerPlayer owner, LivingEntity target) {
        return target.level() == owner.level() && LoyaltyGuard.eligibleTarget(owner, target) && owner.level().hasChunkAt(target.blockPosition())
            && owner.level().mayInteract(owner, target.blockPosition()) && (!(target instanceof Player other) || other.canHarmPlayer(owner));
    }
    private static boolean valid(ServerPlayer owner, Trace trace) {
        if (!ready(owner) || trace == null || !trace.dimension().equals(owner.level().dimension()) || owner.level().getGameTime() >= trace.expires()) return false;
        ThrownTrident projectile = trace.projectile(); Flight flight = FLIGHTS.get(projectile);
        return flight != null && flight.owner().equals(owner.getUUID()) && flight.dimension().equals(trace.dimension())
            && owner.level().getGameTime() < flight.expires() && flight.stack() == trace.stack() && flight.pair().equals(trace.pair())
            && projectile.isAlive() && projectile.level() == owner.level() && owner.level().getEntity(projectile.getUUID()) == projectile
            && projectile.getOwner() == owner && projectile.getWeaponItem() == trace.stack() && applies(trace.pair(), trace.stack())
            && trace.stack().getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().contains(trace.pair());
    }
    private static boolean usableAt(ServerPlayer owner, Trace trace) {
        BlockPos origin = BlockPos.containing(trace.origin());
        return valid(owner, trace) && owner.position().distanceToSqr(trace.origin()) <= 144.0D
            && owner.level().isInWorldBounds(origin) && owner.level().hasChunkAt(origin) && owner.level().mayInteract(owner, origin)
            && !owner.level().getServer().isUnderSpawnProtection(owner.level(), origin, owner);
    }
    public static boolean hasEquipped(ServerPlayer owner) {
        Trace trace = TRACES.get(owner.getUUID()); return trace != null && valid(owner, trace);
    }
    public static boolean activate(ServerPlayer owner) {
        Trace trace = TRACES.get(owner.getUUID());
        if (trace == null || trace.armed() || !usableAt(owner, trace) || owner.containerMenu != owner.inventoryMenu) return false;
        TRACES.put(owner.getUUID(), trace.armedTrace());
        owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.melee_lightning_ready"), true); return true;
    }
    /** Only the real TridentItem release may establish provenance; synthetic copies cannot. */
    public static void launched(Projectile projectile, LivingEntity shooter, ItemStack consumed) {
        if (!(projectile instanceof ThrownTrident trident) || !(shooter instanceof ServerPlayer owner) || !ready(owner)
            || trident.getOwner() != owner || trident.level() != owner.level() || owner.level().getEntity(trident.getUUID()) != trident
            || FLIGHTS.size() >= MAX_FLIGHTS) return;
        ItemStack stack = trident.getWeaponItem(); FusedPair pair = select(stack, true);
        if (pair == null || consumed == null || !ItemStack.matches(stack, consumed)) return;
        FLIGHTS.put(trident, new Flight(owner.getUUID(), owner.level().dimension(), stack, pair, owner.level().getGameTime() + 1200L));
    }
    static @Nullable FusedPair primaryProjectile(ServerLevel level, ItemStack stack, Entity projectile) {
        if (ChannelingTridentPrimary.replaying() || MatrixEffectExecutor.isExecuting() || !(projectile instanceof ThrownTrident trident)
            || !(trident.getOwner() instanceof ServerPlayer owner) || owner.level() != level || trident.level() != level
            || trident.getWeaponItem() != stack) return null;
        Flight flight = FLIGHTS.get(trident);
        return flight != null && flight.owner().equals(owner.getUUID()) && flight.dimension().equals(level.dimension())
            && level.getGameTime() < flight.expires() && flight.stack() == stack && applies(flight.pair(), stack)
            && stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().contains(flight.pair()) ? flight.pair() : null;
    }
    public static final class TridentHit {
        final ServerPlayer owner; final LivingEntity target; final ThrownTrident projectile; final ItemStack stack;
        final FusedPair pair; final Vec3 origin; final float health, absorption; final boolean eligible;
        TridentHit(ServerPlayer owner, LivingEntity target, ThrownTrident projectile, ItemStack stack, FusedPair pair, boolean eligible) {
            this.owner = owner; this.target = target; this.projectile = projectile; this.stack = stack; this.pair = pair;
            this.origin = target.getBoundingBox().getCenter(); this.health = target.getHealth(); this.absorption = target.getAbsorptionAmount();
            this.eligible = eligible;
        }
    }
    /** Observe the actual trident damage request; collision or a nearby projectile is not evidence. */
    public static @Nullable TridentHit beginTrident(ThrownTrident projectile, Entity victim, DamageSource source) {
        if (!(projectile.getOwner() instanceof ServerPlayer owner) || !(victim instanceof LivingEntity target)
            || !ready(owner) || owner.level() != projectile.level() || owner.level() != target.level()
            || owner.level().getEntity(projectile.getUUID()) != projectile || source.getDirectEntity() != projectile || source.getEntity() != owner
            || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypeTags.BYPASSES_EFFECTS)
            || source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS) || MatrixEffectExecutor.isExecuting()) return null;
        ItemStack stack = projectile.getWeaponItem(); FusedPair pair = primaryProjectile(owner.level(), stack, projectile);
        return pair == null ? null : new TridentHit(owner, target, projectile, stack, pair, target(owner, target));
    }
    public static void endTrident(@Nullable TridentHit hit, boolean accepted) {
        if (hit == null || !accepted || !hit.eligible || !ready(hit.owner) || hit.owner.level() != hit.target.level()
            || hit.owner.level() != hit.projectile.level() || hit.projectile.getOwner() != hit.owner
            || hit.projectile.getWeaponItem() != hit.stack || !applies(hit.pair, hit.stack)
            || ConfirmedMeleeMath.actualLoss(hit.health, hit.absorption, hit.target.getHealth(), hit.target.getAbsorptionAmount()) <= 0) return;
        if (!TRACES.containsKey(hit.owner.getUUID()) && TRACES.size() >= MAX_OWNERS) return;
        Trace trace = new Trace(hit.projectile, hit.stack, hit.pair, hit.owner.level().dimension(), hit.origin,
            hit.owner.level().getGameTime() + LIFETIME, false);
        if (!usableAt(hit.owner, trace)) return;
        TRACES.put(hit.owner.getUUID(), trace);
        hit.owner.level().sendParticles(ParticleTypes.ELECTRIC_SPARK, hit.origin.x, hit.origin.y, hit.origin.z, 8, 0.2D, 0.2D, 0.2D, 0.01D);
        hit.owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.melee_lightning_marked"), true);
    }
    public static final class Damage {
        final @Nullable Damage previous; final @Nullable ConfirmedMelee.Observation observation; final @Nullable Trace trace; final float amount;
        Damage(@Nullable Damage previous, @Nullable ConfirmedMelee.Observation observation, @Nullable Trace trace, float amount) {
            this.previous = previous; this.observation = observation; this.trace = trace; this.amount = amount;
        }
        public float damage() { return amount; }
    }
    public static float traceDamage(float original, int level) {
        if (!Float.isFinite(original) || original <= 0) return original;
        return Math.max(original, Math.min(1024.0F, original + Math.min(7.0F, 2.0F + 0.5F * Math.clamp(level, 1, 10))));
    }
    public static Damage beginDamage(@Nullable ConfirmedMelee.Observation observation, float amount) {
        Damage previous = DAMAGE.get(); Trace chosen = null;
        if (previous == null && observation != null && observation.slot() == EquipmentSlot.MAINHAND && observation.eligibleBefore()
            && observation.player().getMainHandItem() == observation.weapon() && Float.isFinite(amount) && amount > 0) {
            ServerPlayer owner = observation.player(); Trace trace = TRACES.get(owner.getUUID()); LivingEntity victim = observation.target();
            if (trace != null && trace.armed() && usableAt(owner, trace) && target(owner, victim)
                && MeleeAttackThemes.matchesTarget(trace.pair().secondaryId(), victim)
                && trace.origin().distanceToSqr(victim.getBoundingBox().getCenter()) <= 36.0D
                && VirtualCombatSight.clear(owner.level(), owner, trace.origin(), victim.getBoundingBox().getCenter())) chosen = trace;
        }
        Damage frame = new Damage(previous, observation, chosen, chosen == null ? amount : traceDamage(amount, chosen.pair().secondaryLevel()));
        DAMAGE.set(frame); return frame;
    }
    public static void endDamage(Damage frame) {
        if (DAMAGE.get() != frame) { DAMAGE.remove(); return; }
        if (frame.previous == null) DAMAGE.remove(); else DAMAGE.set(frame.previous);
    }
    private static void onHit(ConfirmedMelee.Hit hit) {
        ServerPlayer owner = hit.player(); LivingEntity victim = hit.target();
        if (hit.slot() != EquipmentSlot.MAINHAND || !hit.eligibleBefore() || !ready(owner) || owner.getMainHandItem() != hit.weapon()) return;
        FusedPair pair = select(hit.weapon(), false);
        if (pair != null && MeleeAttackThemes.matchesTarget(pair.primaryId(), victim)) {
            OwnedTargetMarks.Mark mark = MARKS.get(owner, "weapon");
            if (mark != null && mark.stack() == hit.weapon() && mark.pair().equals(pair) && mark.target().equals(victim.getUUID())
                && MARKS.remove(owner, "weapon", mark)) {
                float damage = Math.min(8.0F, 2.0F + 0.35F * Math.clamp(pair.primaryLevel(), 1, 10) + 0.25F * Math.clamp(pair.secondaryLevel(), 1, 10));
                VirtualArc.chain(owner, victim.getBoundingBox().getCenter(), victim,
                    candidate -> MeleeAttackThemes.eligible(owner, candidate, pair.primaryId()), damage, 3);
                CompendiumDiscoveryTracker.recordDiscovery(owner, pair.primaryId(), pair.secondaryId());
            } else if (hit.full() && victim.isAlive() && MARKS.put(owner, "weapon", EquipmentSlot.MAINHAND, hit.weapon(), pair, victim, LIFETIME)) {
                owner.level().sendParticles(ParticleTypes.ELECTRIC_SPARK, victim.getX(), victim.getY(0.6D), victim.getZ(), 4, 0.15D, 0.2D, 0.15D, 0.01D);
            }
        }
        Damage frame = DAMAGE.get();
        if (frame == null || frame.previous != null || frame.observation == null || frame.observation.player() != owner
            || frame.observation.target() != victim || frame.observation.weapon() != hit.weapon() || frame.trace == null
            || !usableAt(owner, frame.trace) || !TRACES.remove(owner.getUUID(), frame.trace)) return;
        VirtualArc.show(owner, frame.trace.origin(), victim.getBoundingBox().getCenter());
        CompendiumDiscoveryTracker.recordDiscovery(owner, frame.trace.pair().primaryId(), frame.trace.pair().secondaryId());
    }
    public static void tickServer(MinecraftServer server) {
        MARKS.tickServer(server);
        FLIGHTS.entrySet().removeIf(entry -> {
            ThrownTrident projectile = entry.getKey(); Flight flight = entry.getValue();
            ServerPlayer owner = server.getPlayerList().getPlayer(flight.owner());
            return owner == null || !ready(owner) || !projectile.isAlive() || projectile.level() != owner.level()
                || owner.level().getEntity(projectile.getUUID()) != projectile || projectile.getOwner() != owner
                || !flight.dimension().equals(owner.level().dimension()) || owner.level().getGameTime() >= flight.expires()
                || projectile.getWeaponItem() != flight.stack() || !applies(flight.pair(), flight.stack())
                || !flight.stack().getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().contains(flight.pair());
        });
        TRACES.entrySet().removeIf(entry -> {
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey()); return owner == null || !valid(owner, entry.getValue());
        });
    }
    public static void clearPlayer(UUID id) { MARKS.clearPlayer(id); TRACES.remove(id); FLIGHTS.values().removeIf(f -> f.owner().equals(id)); }
    public static void clearServer() { MARKS.clear(); TRACES.clear(); FLIGHTS.clear(); DAMAGE.remove(); }
}

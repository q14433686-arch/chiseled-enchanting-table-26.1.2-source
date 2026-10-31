package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.config.FusionConfigStore;
import enchantment_fusion.data.FusionMatrixRegistry;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentTarget;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.jetbrains.annotations.Nullable;

/** A visible opposite-side weakness and an earned, non-pickup spirit blade. */
public final class SoulBladeSkills {
    public static final String WEAKNESS = "sanctified_execute", BLADE = "radiant_shockwave";
    private static final String SHARP = "minecraft:sharpness", SMITE = "minecraft:smite";
    private static final Set<FusionCarrier> WEAPONS = Set.of(FusionCarrier.SWORD, FusionCarrier.AXE,
        FusionCarrier.SPEAR, FusionCarrier.MACE, FusionCarrier.TRIDENT);
    private static final int MAX_MARKS = 256, MAX_CHARGES = 128;
    private record Gear(ItemStack stack, FusedPair pair) {}
    private record Key(UUID owner, UUID target, ResourceKey<Level> dimension) {}
    private record Weakpoint(Key key, Gear gear, Vec3 ownerSide, long expires) {}
    private static final class Charge {
        final UUID owner; final ResourceKey<Level> dimension; final Gear gear; int stacks; long expires;
        Charge(ServerPlayer owner, Gear gear, long expires) {
            this.owner = owner.getUUID(); this.dimension = owner.level().dimension(); this.gear = gear; this.expires = expires; this.stacks = 1;
        }
    }
    private record Impact(LivingEntity entity, Vec3 point, double distance) {}
    public static final class Attack {
        final @Nullable Attack previous; final Player player; final ItemStack weapon; final Entity target; final @Nullable Gear gear;
        final boolean full; final Vec3 direction; @Nullable Weakpoint prepared;
        Attack(@Nullable Attack previous, Player player, ItemStack weapon, Entity target, boolean full) {
            this.previous = previous; this.player = player; this.weapon = weapon; this.target = target; this.full = full;
            this.direction = player.getLookAngle();
            this.gear = player instanceof ServerPlayer && player.getMainHandItem() == weapon ? selected(weapon) : null;
        }
    }
    public static final class Hit {
        final @Nullable Attack frame; final @Nullable LivingEntity target; final @Nullable DamageSource source;
        final float health, absorption; final boolean undead; final Vec3 ownerSide; final @Nullable Weakpoint weakness;
        final @Nullable Charge charge; final int stacks;
        Hit(@Nullable Attack frame, @Nullable LivingEntity target, @Nullable DamageSource source, boolean undead,
            Vec3 ownerSide, @Nullable Weakpoint weakness, @Nullable Charge charge) {
            this.frame = frame; this.target = target; this.source = source; this.undead = undead; this.ownerSide = ownerSide;
            this.health = target == null ? 0 : target.getHealth(); this.absorption = target == null ? 0 : target.getAbsorptionAmount();
            this.weakness = weakness; this.charge = charge; this.stacks = charge == null ? 0 : charge.stacks;
        }
    }
    private static final ThreadLocal<Attack> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> PRIMARY_REPLAY = ThreadLocal.withInitial(() -> false);
    private static final Map<Key, Weakpoint> WEAKPOINTS = new HashMap<>();
    private static final List<Charge> CHARGES = new ArrayList<>();
    private SoulBladeSkills() {}
    private static int param(String id, String name, int fallback, int min, int max) {
        return FusionConfigStore.signatureInt(id, name, fallback, min, max);
    }
    public static boolean matches(@Nullable FusedPair pair, String signature) {
        return pair != null && (SHARP.equals(pair.primaryId()) && SMITE.equals(pair.secondaryId()) && WEAKNESS.equals(signature)
            || SMITE.equals(pair.primaryId()) && SHARP.equals(pair.secondaryId()) && BLADE.equals(signature));
    }
    public static boolean supportsCarrier(Set<FusionCarrier> roles) { return roles != null && roles.stream().anyMatch(WEAPONS::contains); }
    public static boolean applies(@Nullable FusedPair pair, @Nullable ItemStack stack, String signature) {
        return matches(pair, signature) && stack != null && !stack.isEmpty() && supportsCarrier(FusionCarrier.classify(stack));
    }
    private static @Nullable Gear selected(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if (SignatureCatalog.resolve(pair).map(e -> applies(pair, stack, e.id())).orElse(false)) return new Gear(stack, pair);
        return null;
    }
    private static boolean ready(ServerPlayer owner) { return owner.isAlive() && !owner.isSpectator(); }
    private static boolean owns(ServerPlayer owner, Gear gear) {
        if (gear.stack().isEmpty() || !gear.stack().getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().contains(gear.pair())
            || !SignatureCatalog.resolve(gear.pair()).map(e -> applies(gear.pair(), gear.stack(), e.id())).orElse(false)) return false;
        for (int i = 0; i < owner.getInventory().getContainerSize(); i++) if (owner.getInventory().getItem(i) == gear.stack()) return true;
        return false;
    }
    private static boolean eligible(ServerPlayer owner, LivingEntity target) {
        return owner.level() == target.level() && LoyaltyGuard.eligibleTarget(owner, target)
            && owner.level().mayInteract(owner, target.blockPosition()) && (!(target instanceof Player other) || other.canHarmPlayer(owner));
    }
    private static boolean undead(LivingEntity target) {
        return BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(target.getType()).is(EntityTypeTags.UNDEAD);
    }
    private static boolean direct(ServerPlayer owner, DamageSource source) {
        return source.getEntity() == owner && source.getDirectEntity() == owner
            && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !source.is(DamageTypeTags.BYPASSES_EFFECTS)
            && !source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS) && !MatrixEffectExecutor.isExecuting()
            && !GlazedStrikeEffect.isApplyingBonus() && !AmberSealSkill.isApplyingBurst() && !LoyaltyGuard.isExecuting()
            && !Boolean.TRUE.equals(TransmuteRuntimeSupport.IN_CUSTOM_COMBAT.get()) && !Boolean.TRUE.equals(PRIMARY_REPLAY.get());
    }
    public static Attack beginAttack(Player player, ItemStack weapon, Entity target, boolean full) {
        Attack attack = new Attack(CURRENT.get(), player, weapon, target, full); CURRENT.set(attack); return attack;
    }
    public static void endAttack(Attack attack) { if (attack.previous == null) CURRENT.remove(); else CURRENT.set(attack.previous); }
    private static @Nullable Charge findCharge(ServerPlayer owner, Gear gear) {
        return CHARGES.stream().filter(charge -> charge.owner.equals(owner.getUUID()) && charge.dimension.equals(owner.level().dimension())
            && charge.gear.stack() == gear.stack() && charge.gear.pair().equals(gear.pair()) && owner.level().getGameTime() < charge.expires)
            .findFirst().orElse(null);
    }
    public static Hit beforeDamage(Player player, ItemStack weapon, Entity entity, DamageSource source) {
        Attack frame = CURRENT.get();
        if (!(player instanceof ServerPlayer owner) || frame == null || frame.player != player || frame.weapon != weapon || frame.target != entity
            || frame.gear == null || !ready(owner) || !owns(owner, frame.gear) || !(entity instanceof LivingEntity living)
            || !eligible(owner, living) || !direct(owner, source)) return new Hit(null, null, null, false, Vec3.ZERO, null, null);
        Vec3 side = owner.position().subtract(living.position());
        return new Hit(frame, living, source, undead(living), side, frame.prepared,
            SMITE.equals(frame.gear.pair().primaryId()) ? findCharge(owner, frame.gear) : null);
    }
    public static void afterDamage(Hit hit, boolean accepted) {
        Attack frame = hit.frame; LivingEntity target = hit.target;
        if (!accepted || frame == null || target == null || CURRENT.get() != frame || !(frame.player instanceof ServerPlayer owner)
            || !ready(owner) || owner.getMainHandItem() != frame.weapon || frame.gear == null || !owns(owner, frame.gear)
            || hit.source == null || !direct(owner, hit.source) || owner.level() != target.level()) return;
        float loss = Math.max(0, hit.health - target.getHealth()) + Math.max(0, hit.absorption - target.getAbsorptionAmount());
        if (!Float.isFinite(loss) || loss <= 0) return;
        ServerLevel level = owner.level(); Gear gear = frame.gear;
        if (SHARP.equals(gear.pair().primaryId()) && hit.undead && frame.full) {
            Key key = new Key(owner.getUUID(), target.getUUID(), level.dimension());
            Weakpoint mark = hit.weakness;
            if (mark != null && WEAKPOINTS.remove(key, mark)) {
                if (target.isAlive() && eligible(owner, target)) target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1));
                level.sendParticles(ParticleTypes.SOUL, target.getX(), target.getY(0.5D), target.getZ(), 18, 0.2D, 0.3D, 0.2D, 0.03D);
                CompendiumDiscoveryTracker.recordDiscovery(owner, SHARP, SMITE);
            } else if (target.isAlive()) markWeakpoint(owner, target, gear, hit.ownerSide);
        } else if (SMITE.equals(gear.pair().primaryId())) {
            if (frame.full && hit.charge != null && CHARGES.contains(hit.charge) && hit.stacks > 0
                && hit.charge.stacks >= hit.stacks && level.getGameTime() < hit.charge.expires) {
                hit.charge.stacks -= hit.stacks;
                if (hit.charge.stacks == 0) CHARGES.remove(hit.charge);
                releaseBlade(owner, gear, target, frame.direction, hit.stacks);
            }
            if (hit.undead && !target.isAlive()) addCharge(owner, gear);
        }
    }
    private static void markWeakpoint(ServerPlayer owner, LivingEntity target, Gear gear, Vec3 rawSide) {
        Vec3 side = new Vec3(rawSide.x, 0, rawSide.z);
        if (!Double.isFinite(side.x) || !Double.isFinite(side.z) || side.lengthSqr() < 1.0E-8D) return;
        Key key = new Key(owner.getUUID(), target.getUUID(), owner.level().dimension()); long now = owner.level().getGameTime();
        Weakpoint old = WEAKPOINTS.get(key);
        if (old != null && now < old.expires() && old.gear().stack() == gear.stack() && old.gear().pair().equals(gear.pair())) return;
        if (old == null && WEAKPOINTS.size() >= MAX_MARKS) return;
        WEAKPOINTS.put(key, new Weakpoint(key, gear, side.normalize(), now + param(WEAKNESS, "weakness_ticks", 120, 20, 1200)));
        CompendiumDiscoveryTracker.recordDiscovery(owner, SHARP, SMITE);
    }
    private static void addCharge(ServerPlayer owner, Gear gear) {
        long now = owner.level().getGameTime(); Charge charge = findCharge(owner, gear);
        if (charge == null) {
            if (CHARGES.size() >= MAX_CHARGES) return;
            CHARGES.add(new Charge(owner, gear, now + param(BLADE, "charge_ticks", 240, 20, 1200)));
        } else {
            charge.stacks = Math.min(5, charge.stacks + 1);
            charge.expires = now + param(BLADE, "charge_ticks", 240, 20, 1200);
        }
        owner.level().sendParticles(ParticleTypes.SOUL, owner.getX(), owner.getY(0.6D), owner.getZ(), 8, 0.18D, 0.25D, 0.18D, 0.02D);
        CompendiumDiscoveryTracker.recordDiscovery(owner, SMITE, SHARP);
    }
    /** Whether this handler will replay this primary on the exact selected legacy pair. */
    public static boolean replaysPrimary(ItemStack weapon, String primary) {
        Gear gear = selected(weapon);
        return gear != null && primary.equals(gear.pair().primaryId());
    }
    private static boolean primaryReplayedBySoulThread(ItemStack weapon, String primary) {
        return SoulThreadSkills.replaysPrimary(weapon, primary);
    }
    /** Preserve the native data-pack main enchantment; add a prepared weakpoint bonus before vanilla damage. */
    public static float modifyDamage(ServerLevel level, ItemStack weapon, Entity victim, DamageSource source, float vanilla) {
        if (Boolean.TRUE.equals(PRIMARY_REPLAY.get()) || !(source.getEntity() instanceof ServerPlayer owner)
            || source.getDirectEntity() != owner || owner.getMainHandItem() != weapon) return vanilla;
        Gear gear = selected(weapon); if (gear == null) return vanilla;
        Holder<Enchantment> primary = FusionStationOperations.lookupEnchantment(level.registryAccess(), gear.pair().primaryId()).orElse(null);
        float adjusted = vanilla;
        if (primary != null && weapon.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(primary) == 0
            && !primaryReplayedBySoulThread(weapon, gear.pair().primaryId())) {
            MutableFloat value = new MutableFloat(vanilla); PRIMARY_REPLAY.set(true);
            try { primary.value().modifyDamage(level, gear.pair().primaryLevel(), weapon, victim, source, value); }
            finally { PRIMARY_REPLAY.remove(); }
            if (Float.isFinite(value.floatValue())) adjusted = value.floatValue();
        }
        Attack frame = CURRENT.get();
        if (frame == null || frame.player != owner || frame.weapon != weapon || frame.target != victim || !frame.full
            || frame.gear == null || !SHARP.equals(frame.gear.pair().primaryId()) || !direct(owner, source)
            || !(victim instanceof LivingEntity living) || !eligible(owner, living) || !undead(living) || !owns(owner, gear)) return adjusted;
        Weakpoint mark = WEAKPOINTS.get(new Key(owner.getUUID(), living.getUUID(), level.dimension()));
        Vec3 side = owner.position().subtract(living.position());
        if (mark != null && level.getGameTime() < mark.expires() && mark.gear().stack() == weapon && mark.gear().pair().equals(gear.pair())
            && SoulBladeMath.opposite(mark.ownerSide().x, mark.ownerSide().z, side.x, side.z)) {
            frame.prepared = mark;
            adjusted += SoulBladeMath.weaknessBonus(gear.pair().primaryLevel(), gear.pair().secondaryLevel());
        }
        return Float.isFinite(adjusted) ? adjusted : vanilla;
    }
    public static void nonNativePostAttack(ServerLevel level, Player player, ItemStack weapon, Entity victim, DamageSource source) {
        if (Boolean.TRUE.equals(PRIMARY_REPLAY.get()) || !(player instanceof ServerPlayer owner) || owner.getMainHandItem() != weapon
            || source.getEntity() != owner || source.getDirectEntity() != owner) return;
        Gear gear = selected(weapon); if (gear == null || primaryReplayedBySoulThread(weapon, gear.pair().primaryId())) return;
        Holder<Enchantment> primary = FusionStationOperations.lookupEnchantment(level.registryAccess(), gear.pair().primaryId()).orElse(null);
        if (primary == null || weapon.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(primary) > 0) return;
        PRIMARY_REPLAY.set(true);
        try { primary.value().doPostAttack(level, gear.pair().primaryLevel(), new EnchantedItemInUse(weapon, EquipmentSlot.MAINHAND, owner),
            EnchantmentTarget.ATTACKER, victim, source); }
        finally { PRIMARY_REPLAY.remove(); }
    }
    /** Only a client miss report can request this; the server owns every resource and readiness check. */
    public static void onAirAttack(ServerPlayer owner) {
        if (!ready(owner) || owner.containerMenu != owner.inventoryMenu || owner.isUsingItem()) return;
        ItemStack weapon = owner.getMainHandItem(); Gear gear = selected(weapon);
        if (gear == null || !SMITE.equals(gear.pair().primaryId()) || !owns(owner, gear) || owner.cannotAttackWithItem(weapon, 5)
            || owner.getAttackStrengthScale(0.5F) < FusionMatrixRegistry.serverSnapshot().economy().meleeFullCharge()) return;
        Vec3 look = owner.getLookAngle();
        if (!Double.isFinite(look.x) || !Double.isFinite(look.y) || !Double.isFinite(look.z) || look.lengthSqr() < 1.0E-8D) return;
        Charge charge = findCharge(owner, gear);
        if (charge == null || charge.stacks <= 0 || !CHARGES.remove(charge)) return;
        owner.resetAttackStrengthTicker(); // vanilla client also resets this for a miss
        releaseBlade(owner, gear, null, look, charge.stacks);
    }
    private static void releaseBlade(ServerPlayer owner, Gear gear, @Nullable LivingEntity directVictim, Vec3 look, int stacks) {
        if (!Double.isFinite(look.x) || !Double.isFinite(look.y) || !Double.isFinite(look.z) || look.lengthSqr() < 1.0E-8D) return;
        ServerLevel level = owner.level(); Vec3 start = owner.getEyePosition(), unit = look.normalize();
        double length = param(BLADE, "blade_range_tenths", 90, 20, 160) / 10.0D;
        Vec3 end = start;
        // Short bounded steps stop the visual and attack before the first wall or unloaded chunk.
        int steps = (int) Math.ceil(length * 2.0D);
        for (int i = 1; i <= steps; i++) {
            Vec3 next = start.add(unit.scale(length * i / steps));
            if (!VirtualCombatSight.clear(level, owner, start, next)) break;
            end = next;
        }
        AABB box = new AABB(start, end).inflate(0.5D); List<Impact> impacts = new ArrayList<>();
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != directVictim && eligible(owner, e))) {
            var intersection = candidate.getBoundingBox().inflate(0.2D).clip(start, end);
            if (intersection.isEmpty()) continue;
            Vec3 point = intersection.get(); impacts.add(new Impact(candidate, point, point.distanceToSqr(start)));
        }
        impacts.sort(Comparator.comparingDouble(Impact::distance));
        boolean previous = Boolean.TRUE.equals(TransmuteRuntimeSupport.IN_CUSTOM_COMBAT.get());
        TransmuteRuntimeSupport.IN_CUSTOM_COMBAT.set(true);
        try {
            int count = 0;
            for (Impact impact : impacts) {
                if (++count > 8) break;
                LivingEntity victim = impact.entity();
                if (!eligible(owner, victim) || !VirtualCombatSight.clear(level, owner, start, impact.point())) continue;
                victim.hurtServer(level, level.damageSources().playerAttack(owner), SoulBladeMath.bladeDamage(stacks, gear.pair().secondaryLevel()));
            }
        } finally { TransmuteRuntimeSupport.IN_CUSTOM_COMBAT.set(previous); }
        double actual = Math.sqrt(start.distanceToSqr(end));
        int particles = Math.max(1, (int) Math.ceil(actual * 2));
        for (int i = 0; i <= particles; i++) {
            Vec3 point = start.add(unit.scale(actual * i / particles));
            level.sendParticles(ParticleTypes.SOUL, point.x, point.y, point.z, 1, 0.02D, 0.02D, 0.02D, 0.005D);
        }
    }
    public static void tickServer(MinecraftServer server) {
        for (Weakpoint mark : List.copyOf(WEAKPOINTS.values())) {
            ServerLevel level = server.getLevel(mark.key().dimension()); ServerPlayer owner = server.getPlayerList().getPlayer(mark.key().owner());
            Entity entity = level == null ? null : level.getEntity(mark.key().target());
            if (level == null || owner == null || owner.level() != level || !ready(owner) || !owns(owner, mark.gear())
                || !(entity instanceof LivingEntity target) || !eligible(owner, target) || !undead(target) || level.getGameTime() >= mark.expires()) {
                WEAKPOINTS.remove(mark.key(), mark); continue;
            }
            if (level.getGameTime() % 5 == 0) {
                Vec3 point = target.position().subtract(mark.ownerSide().scale(target.getBbWidth() / 2.0D + 0.6D)).add(0, target.getBbHeight() * 0.5D, 0);
                level.sendParticles(ParticleTypes.SOUL, point.x, point.y, point.z, 2, 0.1D, 0.1D, 0.1D, 0.005D);
            }
        }
        for (Charge charge : List.copyOf(CHARGES)) {
            ServerLevel level = server.getLevel(charge.dimension); ServerPlayer owner = server.getPlayerList().getPlayer(charge.owner);
            if (level == null || owner == null || owner.level() != level || !ready(owner) || !owns(owner, charge.gear)
                || level.getGameTime() >= charge.expires) CHARGES.remove(charge);
        }
    }
    public static void onDisconnect(UUID id) {
        WEAKPOINTS.keySet().removeIf(key -> key.owner().equals(id) || key.target().equals(id));
        CHARGES.removeIf(charge -> charge.owner.equals(id));
    }
    public static void clear() { WEAKPOINTS.clear(); CHARGES.clear(); CURRENT.remove(); PRIMARY_REPLAY.remove(); }
}

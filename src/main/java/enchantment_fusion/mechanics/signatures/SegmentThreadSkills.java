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
import java.util.HashMap;
import java.util.HashSet;
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

/** Confirmed arthropod strikes leave a trip mark or a virtual, wall-blocked cutting thread. */
public final class SegmentThreadSkills {
    public static final String TRIP = "hamstring_rend", THREAD = "neurotoxin_bleed";
    private static final String SHARP = "minecraft:sharpness", BANE = "minecraft:bane_of_arthropods";
    private static final Set<FusionCarrier> WEAPONS = Set.of(FusionCarrier.SWORD, FusionCarrier.AXE,
        FusionCarrier.SPEAR, FusionCarrier.MACE, FusionCarrier.TRIDENT);
    private static final int MAX_MARKS = 256, MAX_THREADS = 128, MAX_THREAD_VICTIMS = 16;
    private record Gear(ItemStack stack, FusedPair pair) {}
    private record Key(UUID owner, UUID target, ResourceKey<Level> dimension) {}
    private static final class Mark {
        final Key key; final Gear gear;
        long created, expires; Vec3 last; boolean exposed;
        Mark(ServerPlayer owner, LivingEntity target, Gear gear, long expires) {
            this.key = new Key(owner.getUUID(), target.getUUID(), owner.level().dimension());
            this.gear = gear; this.created = owner.level().getGameTime(); this.expires = expires; this.last = target.position();
        }
    }
    private static final class Thread {
        final UUID owner, target; final ResourceKey<Level> dimension; final Gear gear;
        final Set<UUID> struck = new HashSet<>(); final long expires;
        Vec3 lastOwner, lastTarget;
        Thread(ServerPlayer owner, LivingEntity target, Gear gear, long expires) {
            this.owner = owner.getUUID(); this.target = target.getUUID(); this.dimension = owner.level().dimension();
            this.gear = gear; this.expires = expires; this.lastOwner = owner.position(); this.lastTarget = target.position();
        }
    }
    public static final class Attack {
        final @Nullable Attack previous; final Player player; final ItemStack weapon; final Entity target;
        final @Nullable Gear gear; final boolean full; @Nullable Mark prepared;
        Attack(@Nullable Attack previous, Player player, ItemStack weapon, Entity target, boolean full) {
            this.previous = previous; this.player = player; this.weapon = weapon; this.target = target; this.full = full;
            this.gear = player instanceof ServerPlayer && player.getMainHandItem() == weapon ? selected(weapon) : null;
        }
    }
    public static final class Hit {
        final @Nullable Attack frame; final @Nullable LivingEntity target; final @Nullable DamageSource source;
        final @Nullable Mark prepared; final float health, absorption;
        Hit(@Nullable Attack frame, @Nullable LivingEntity target, @Nullable DamageSource source) {
            this.frame = frame; this.target = target; this.source = source; this.prepared = frame == null ? null : frame.prepared;
            this.health = target == null ? 0 : target.getHealth(); this.absorption = target == null ? 0 : target.getAbsorptionAmount();
        }
    }
    private static final ThreadLocal<Attack> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> PRIMARY_REPLAY = ThreadLocal.withInitial(() -> false);
    private static final Map<Key, Mark> MARKS = new HashMap<>();
    private static final Map<UUID, Thread> THREADS = new HashMap<>();
    private SegmentThreadSkills() {}
    private static int param(String id, String name, int fallback, int min, int max) {
        return FusionConfigStore.signatureInt(id, name, fallback, min, max);
    }
    public static boolean matches(@Nullable FusedPair pair, String signature) {
        return pair != null && (SHARP.equals(pair.primaryId()) && BANE.equals(pair.secondaryId()) && TRIP.equals(signature)
            || BANE.equals(pair.primaryId()) && SHARP.equals(pair.secondaryId()) && THREAD.equals(signature));
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
    private static boolean arthropod(LivingEntity target) {
        return BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(target.getType()).is(EntityTypeTags.ARTHROPOD);
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
    public static Hit beforeDamage(Player player, ItemStack weapon, Entity entity, DamageSource source) {
        Attack frame = CURRENT.get();
        if (!(player instanceof ServerPlayer owner) || frame == null || frame.player != player || frame.weapon != weapon
            || frame.target != entity || frame.gear == null || !ready(owner) || !owns(owner, frame.gear)
            || !(entity instanceof LivingEntity target) || !eligible(owner, target) || !arthropod(target) || !direct(owner, source))
            return new Hit(null, null, null);
        return new Hit(frame, target, source);
    }
    public static void afterDamage(Hit hit, boolean accepted) {
        Attack frame = hit.frame; LivingEntity target = hit.target;
        if (!accepted || frame == null || target == null || CURRENT.get() != frame || !(frame.player instanceof ServerPlayer owner)
            || !ready(owner) || owner.getMainHandItem() != frame.weapon || frame.gear == null || !owns(owner, frame.gear)
            || hit.source == null || !direct(owner, hit.source) || owner.level() != target.level()) return;
        float loss = Math.max(0, hit.health - target.getHealth()) + Math.max(0, hit.absorption - target.getAbsorptionAmount());
        if (!Float.isFinite(loss) || loss <= 0) return;
        Gear gear = frame.gear; ServerLevel level = owner.level();
        if (SHARP.equals(gear.pair().primaryId()) && frame.full) {
            Key key = new Key(owner.getUUID(), target.getUUID(), level.dimension());
            if (hit.prepared != null && MARKS.remove(key, hit.prepared)) {
                level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.5D), target.getZ(), 18, 0.25D, 0.25D, 0.25D, 0.03D);
            } else if (target.isAlive() && eligible(owner, target)) mark(owner, target, gear);
        } else if (BANE.equals(gear.pair().primaryId()) && target.isAlive() && eligible(owner, target)) {
            tether(owner, target, gear);
        }
    }
    private static void mark(ServerPlayer owner, LivingEntity target, Gear gear) {
        Key key = new Key(owner.getUUID(), target.getUUID(), owner.level().dimension()); long now = owner.level().getGameTime();
        Mark current = MARKS.get(key);
        if (current != null && now < current.expires && current.gear.stack() == gear.stack() && current.gear.pair().equals(gear.pair())) return;
        if (current == null && MARKS.size() >= MAX_MARKS) return;
        MARKS.put(key, new Mark(owner, target, gear, now + param(TRIP, "mark_ticks", 100, 20, 600)));
        CompendiumDiscoveryTracker.recordDiscovery(owner, SHARP, BANE);
    }
    private static void tether(ServerPlayer owner, LivingEntity target, Gear gear) {
        if (!THREADS.containsKey(owner.getUUID()) && THREADS.size() >= MAX_THREADS) return;
        long now = owner.level().getGameTime();
        THREADS.put(owner.getUUID(), new Thread(owner, target, gear, now + param(THREAD, "thread_ticks", 100, 20, 400)));
        CompendiumDiscoveryTracker.recordDiscovery(owner, BANE, SHARP);
    }
    /** Whether this handler owns the selected cross-carrier primary replay. */
    public static boolean replaysPrimary(ItemStack weapon, String primary) {
        Gear gear = selected(weapon);
        return gear != null && primary.equals(gear.pair().primaryId());
    }
    private static boolean replayedEarlier(ItemStack weapon, String primary) {
        return SoulThreadSkills.replaysPrimary(weapon, primary) || SoulBladeSkills.replaysPrimary(weapon, primary);
    }
    /** Replay a missing primary without changing native carriers; prepare a bonus only on a tripped mark. */
    public static float modifyDamage(ServerLevel level, ItemStack weapon, Entity victim, DamageSource source, float vanilla) {
        if (Boolean.TRUE.equals(PRIMARY_REPLAY.get()) || !(source.getEntity() instanceof ServerPlayer owner)
            || source.getDirectEntity() != owner || owner.getMainHandItem() != weapon) return vanilla;
        Gear gear = selected(weapon); if (gear == null) return vanilla;
        Holder<Enchantment> primary = FusionStationOperations.lookupEnchantment(level.registryAccess(), gear.pair().primaryId()).orElse(null);
        float adjusted = vanilla;
        if (primary != null && weapon.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(primary) == 0
            && !replayedEarlier(weapon, gear.pair().primaryId())) {
            MutableFloat value = new MutableFloat(vanilla); PRIMARY_REPLAY.set(true);
            try { primary.value().modifyDamage(level, gear.pair().primaryLevel(), weapon, victim, source, value); }
            finally { PRIMARY_REPLAY.remove(); }
            if (Float.isFinite(value.floatValue())) adjusted = value.floatValue();
        }
        Attack frame = CURRENT.get();
        if (frame == null || frame.player != owner || frame.weapon != weapon || frame.target != victim || !frame.full
            || frame.gear == null || !SHARP.equals(frame.gear.pair().primaryId()) || !direct(owner, source)
            || !(victim instanceof LivingEntity target) || !eligible(owner, target) || !arthropod(target) || !owns(owner, gear)) return adjusted;
        Mark mark = MARKS.get(new Key(owner.getUUID(), target.getUUID(), level.dimension()));
        if (mark != null && mark.exposed && level.getGameTime() < mark.expires
            && mark.gear.stack() == weapon && mark.gear.pair().equals(gear.pair())) {
            frame.prepared = mark;
            adjusted += SegmentThreadMath.openingBonus(gear.pair().primaryLevel(), gear.pair().secondaryLevel());
        }
        return Float.isFinite(adjusted) ? adjusted : vanilla;
    }
    public static void nonNativePostAttack(ServerLevel level, Player player, ItemStack weapon, Entity victim, DamageSource source) {
        if (Boolean.TRUE.equals(PRIMARY_REPLAY.get()) || !(player instanceof ServerPlayer owner) || owner.getMainHandItem() != weapon
            || source.getEntity() != owner || source.getDirectEntity() != owner) return;
        Gear gear = selected(weapon); if (gear == null || replayedEarlier(weapon, gear.pair().primaryId())) return;
        Holder<Enchantment> primary = FusionStationOperations.lookupEnchantment(level.registryAccess(), gear.pair().primaryId()).orElse(null);
        if (primary == null || weapon.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(primary) > 0) return;
        PRIMARY_REPLAY.set(true);
        try { primary.value().doPostAttack(level, gear.pair().primaryLevel(), new EnchantedItemInUse(weapon, EquipmentSlot.MAINHAND, owner),
            EnchantmentTarget.ATTACKER, victim, source); }
        finally { PRIMARY_REPLAY.remove(); }
    }
    public static void tickServer(MinecraftServer server) {
        for (Mark mark : List.copyOf(MARKS.values())) {
            ServerLevel level = server.getLevel(mark.key.dimension()); ServerPlayer owner = server.getPlayerList().getPlayer(mark.key.owner());
            Entity entity = level == null ? null : level.getEntity(mark.key.target());
            if (level == null || owner == null || owner.level() != level || !ready(owner) || !owns(owner, mark.gear)
                || !(entity instanceof LivingEntity target) || !eligible(owner, target) || !arthropod(target) || level.getGameTime() >= mark.expires) {
                MARKS.remove(mark.key, mark); continue;
            }
            Vec3 now = target.position(), movement = now.subtract(mark.last); mark.last = now;
            if (!Double.isFinite(movement.lengthSqr()) || movement.lengthSqr() > 16.0D) {
                MARKS.remove(mark.key, mark); continue;
            }
            if (!mark.exposed && level.getGameTime() - mark.created >= 2 && SegmentThreadMath.trips(movement.x, movement.y, movement.z)) {
                Vec3 velocity = target.getDeltaMovement();
                target.setDeltaMovement(0, Math.min(0, velocity.y), 0); target.hurtMarked = true;
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 1));
                mark.exposed = true; mark.expires = level.getGameTime() + param(TRIP, "opening_ticks", 60, 10, 300);
                level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.5D), target.getZ(), 12, 0.25D, 0.2D, 0.25D, 0.02D);
            }
            if (level.getGameTime() % 5 == 0) level.sendParticles(mark.exposed ? ParticleTypes.CRIT : ParticleTypes.ENCHANTED_HIT,
                target.getX(), target.getY(0.5D), target.getZ(), 2, 0.15D, 0.15D, 0.15D, 0.005D);
        }
        for (Thread thread : List.copyOf(THREADS.values())) tickThread(server, thread);
    }
    private static void tickThread(MinecraftServer server, Thread thread) {
        ServerLevel level = server.getLevel(thread.dimension); ServerPlayer owner = server.getPlayerList().getPlayer(thread.owner);
        Entity entity = level == null ? null : level.getEntity(thread.target);
        if (level == null || owner == null || owner.level() != level || !ready(owner) || owner.getMainHandItem() != thread.gear.stack()
            || !owns(owner, thread.gear) || !(entity instanceof LivingEntity target) || !eligible(owner, target) || !arthropod(target)
            || level.getGameTime() >= thread.expires || thread.struck.size() >= MAX_THREAD_VICTIMS) {
            THREADS.remove(thread.owner, thread); return;
        }
        Vec3 nowOwner = owner.position(), nowTarget = target.position(), oldOwner = thread.lastOwner;
        double ownerStep = nowOwner.distanceToSqr(oldOwner), targetStep = nowTarget.distanceToSqr(thread.lastTarget);
        thread.lastOwner = nowOwner; thread.lastTarget = nowTarget;
        double range = param(THREAD, "thread_range_tenths", 90, 30, 120) / 10.0D;
        Vec3 anchor = target.getBoundingBox().getCenter(), from = owner.getBoundingBox().getCenter();
        Vec3 oldFrom = oldOwner.add(0, owner.getBbHeight() * 0.5D, 0);
        if (!Double.isFinite(ownerStep) || !Double.isFinite(targetStep) || ownerStep > 16 || targetStep > 16
            || nowOwner.distanceToSqr(nowTarget) > range * range || !VirtualCombatSight.clear(level, owner, from, anchor)) {
            THREADS.remove(thread.owner, thread); return;
        }
        if (level.getGameTime() % 5 == 0) {
            int count = Math.max(1, (int) Math.ceil(from.distanceTo(anchor) * 2));
            for (int i = 0; i <= count; i++) {
                Vec3 p = from.lerp(anchor, (double) i / count);
                level.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0.01D, 0.01D, 0.01D, 0.005D);
            }
        }
        if (!SegmentThreadMath.circles(nowTarget.x, nowTarget.z, oldOwner.x, oldOwner.z, nowOwner.x, nowOwner.z)
            || !VirtualCombatSight.clear(level, owner, oldFrom, anchor)) return;
        sweep(level, owner, target, thread, anchor, oldFrom, from);
    }
    private static void sweep(ServerLevel level, ServerPlayer owner, LivingEntity anchorTarget, Thread thread,
                              Vec3 anchor, Vec3 oldFrom, Vec3 from) {
        double minX = Math.min(anchor.x, Math.min(oldFrom.x, from.x)), maxX = Math.max(anchor.x, Math.max(oldFrom.x, from.x));
        double minY = Math.min(anchor.y, Math.min(oldFrom.y, from.y)), maxY = Math.max(anchor.y, Math.max(oldFrom.y, from.y));
        double minZ = Math.min(anchor.z, Math.min(oldFrom.z, from.z)), maxZ = Math.max(anchor.z, Math.max(oldFrom.z, from.z));
        AABB bounds = new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(2.5D, 0.5D, 2.5D);
        List<LivingEntity> candidates = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, bounds,
            entity -> entity != anchorTarget && !thread.struck.contains(entity.getUUID()) && eligible(owner, entity)));
        candidates.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(anchor)));
        boolean previous = Boolean.TRUE.equals(TransmuteRuntimeSupport.IN_CUSTOM_COMBAT.get());
        TransmuteRuntimeSupport.IN_CUSTOM_COMBAT.set(true);
        try {
            int attempts = 0;
            for (LivingEntity victim : candidates) {
                if (thread.struck.size() >= MAX_THREAD_VICTIMS || attempts >= MAX_THREAD_VICTIMS || THREADS.get(thread.owner) != thread
                    || owner.level() != level || !ready(owner) || owner.getMainHandItem() != thread.gear.stack() || !owns(owner, thread.gear)
                    || !eligible(owner, anchorTarget)) break;
                if (!eligible(owner, victim) || victim == anchorTarget || thread.struck.contains(victim.getUUID())) continue;
                Vec3 center = victim.getBoundingBox().getCenter(); double width = Math.min(2.5D, victim.getBbWidth() / 2.0D + 0.15D);
                if (!SegmentThreadMath.swept(anchor.x, anchor.z, oldFrom.x, oldFrom.z, from.x, from.z, center.x, center.z, width)
                    || !VirtualCombatSight.clear(level, owner, anchor, center) || !VirtualCombatSight.clear(level, owner, from, center)) continue;
                double y = SegmentThreadMath.sweptHeight(anchor.x, anchor.y, anchor.z, oldFrom.x, oldFrom.y, oldFrom.z,
                    from.x, from.y, from.z, center.x, center.z);
                if (!Double.isFinite(y) || victim.getBoundingBox().maxY < y - 0.2D || victim.getBoundingBox().minY > y + 0.2D) continue;
                attempts++; float health = victim.getHealth(), absorption = victim.getAbsorptionAmount();
                boolean accepted = victim.hurtServer(level, level.damageSources().playerAttack(owner),
                    SegmentThreadMath.threadDamage(thread.gear.pair().primaryLevel(), thread.gear.pair().secondaryLevel()));
                float loss = Math.max(0, health - victim.getHealth()) + Math.max(0, absorption - victim.getAbsorptionAmount());
                if (accepted && Float.isFinite(loss) && loss > 0) thread.struck.add(victim.getUUID());
            }
        } finally { TransmuteRuntimeSupport.IN_CUSTOM_COMBAT.set(previous); }
    }
    public static void onDisconnect(UUID owner) { MARKS.values().removeIf(mark -> mark.key.owner().equals(owner)); THREADS.remove(owner); }
    public static void clear() { MARKS.clear(); THREADS.clear(); CURRENT.remove(); PRIMARY_REPLAY.remove(); }
}

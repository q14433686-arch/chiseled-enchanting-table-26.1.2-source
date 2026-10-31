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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
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
import org.jetbrains.annotations.Nullable;

/** Real full hits draw virtual ember lines; actual vanilla sweeping hits place chain fuses. */
public final class FireSweepSkills {
    public static final String LINE = "wildfire_arc", FUSE = "soulburn_detonate";
    private static final String FIRE = "minecraft:fire_aspect", SWEEP = "minecraft:sweeping_edge";
    private static final Set<FusionCarrier> MELEE = Set.of(FusionCarrier.SWORD, FusionCarrier.AXE,
        FusionCarrier.SPEAR, FusionCarrier.MACE, FusionCarrier.TRIDENT);
    private static final int MAX_LINES = 128, MAX_FUSES = 512, MAX_BURSTS = 256, MAX_PER_LINE = 64;
    private record Gear(ItemStack stack, FusedPair pair) {}
    private record FuseKey(UUID owner, UUID target, ResourceKey<Level> dimension) {}
    private record FuseMark(FuseKey key, Gear gear, long expires) {}
    private record Burst(UUID chain, int index, FuseKey key, Gear gear, Vec3 from, double range, long due, boolean killedRoot) {
        Burst from(Vec3 position) { return new Burst(chain, index, key, gear, position, range, due, killedRoot); }
    }
    private static final class EmberLine {
        final UUID owner; final ResourceKey<Level> dimension; final Gear gear;
        final Vec3 center, forward; final double halfLength; final long created, expires;
        final Map<UUID, Vec3> previous = new HashMap<>(); final Set<UUID> affected = new HashSet<>();
        EmberLine(ServerPlayer owner, Gear gear, Vec3 center, Vec3 forward, double halfLength, long now, long expires) {
            this.owner = owner.getUUID(); this.dimension = owner.level().dimension(); this.gear = gear;
            this.center = center; this.forward = forward; this.halfLength = halfLength; this.created = now; this.expires = expires;
        }
    }
    public static final class Swing {
        final @Nullable Swing previous; final Player player; final ItemStack weapon;
        boolean detonated;
        Swing(@Nullable Swing previous, Player player) { this.previous = previous; this.player = player; this.weapon = player.getWeaponItem(); }
    }
    public static final class Hit {
        final @Nullable ServerPlayer owner; final @Nullable LivingEntity target; final @Nullable Gear gear;
        final @Nullable FuseMark fuse; final DamageSource source; final boolean full;
        final float health, absorption; final Vec3 position;
        Hit(@Nullable ServerPlayer owner, @Nullable LivingEntity target, @Nullable Gear gear, @Nullable FuseMark fuse,
            DamageSource source, boolean full, float health, float absorption, Vec3 position) {
            this.owner = owner; this.target = target; this.gear = gear; this.fuse = fuse; this.source = source;
            this.full = full; this.health = health; this.absorption = absorption; this.position = position;
        }
    }
    private static final List<EmberLine> LINES = new ArrayList<>();
    private static final Map<FuseKey, FuseMark> FUSES = new HashMap<>();
    private static final List<Burst> BURSTS = new ArrayList<>();
    private static final ThreadLocal<Swing> SWING = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> PRIMARY_REPLAY = ThreadLocal.withInitial(() -> false);
    private FireSweepSkills() {}
    private static int param(String id, String name, int fallback, int min, int max) {
        return FusionConfigStore.signatureInt(id, name, fallback, min, max);
    }
    public static boolean matches(@Nullable FusedPair pair, String signature) {
        return pair != null && (FIRE.equals(pair.primaryId()) && SWEEP.equals(pair.secondaryId()) && LINE.equals(signature)
            || SWEEP.equals(pair.primaryId()) && FIRE.equals(pair.secondaryId()) && FUSE.equals(signature));
    }
    public static boolean supportsCarrier(Set<FusionCarrier> roles, String signature) {
        if (roles == null) return false;
        return LINE.equals(signature) ? roles.stream().anyMatch(MELEE::contains)
            : FUSE.equals(signature) && roles.contains(FusionCarrier.SWORD);
    }
    public static boolean applies(@Nullable FusedPair pair, @Nullable ItemStack stack, String signature) {
        return matches(pair, signature) && stack != null && !stack.isEmpty()
            && supportsCarrier(FusionCarrier.classify(stack), signature);
    }
    private static @Nullable Gear selected(ItemStack stack) {
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
    private static boolean eligible(ServerPlayer owner, LivingEntity victim) {
        return LoyaltyGuard.eligibleTarget(owner, victim) && owner.level().mayInteract(owner, victim.blockPosition())
            && (!(victim instanceof Player other) || other.canHarmPlayer(owner));
    }
    private static boolean direct(ServerPlayer owner, DamageSource source) {
        return source.getEntity() == owner && source.getDirectEntity() == owner
            && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !source.is(DamageTypeTags.BYPASSES_EFFECTS)
            && !source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS) && !MatrixEffectExecutor.isExecuting() && !GlazedStrikeEffect.isApplyingBonus()
            && !AmberSealSkill.isApplyingBurst() && !LoyaltyGuard.isExecuting()
            && !Boolean.TRUE.equals(TransmuteRuntimeSupport.IN_CUSTOM_COMBAT.get()) && !Boolean.TRUE.equals(PRIMARY_REPLAY.get());
    }
    public static Swing beginSwing(Player player) { Swing swing = new Swing(SWING.get(), player); SWING.set(swing); return swing; }
    public static void endSwing(Swing swing) { if (swing.previous == null) SWING.remove(); else SWING.set(swing.previous); }
    public static Hit beginHit(Player player, ItemStack weapon, Entity target, DamageSource source, boolean full) {
        ServerPlayer owner = player instanceof ServerPlayer p ? p : null;
        LivingEntity living = target instanceof LivingEntity l ? l : null;
        Gear gear = owner != null && ready(owner) && owner.getMainHandItem() == weapon && living != null && eligible(owner, living)
            && direct(owner, source) ? selected(weapon) : null;
        FuseMark fuse = gear == null || !SWEEP.equals(gear.pair().primaryId()) ? null
            : FUSES.get(new FuseKey(owner.getUUID(), living.getUUID(), owner.level().dimension()));
        return new Hit(owner, living, gear, fuse, source, full, living == null ? 0 : living.getHealth(),
            living == null ? 0 : living.getAbsorptionAmount(), target.position());
    }
    private static boolean lost(float health, float absorption, LivingEntity target) {
        float amount = Math.max(0, health - target.getHealth()) + Math.max(0, absorption - target.getAbsorptionAmount());
        return Float.isFinite(amount) && amount > 0;
    }
    public static void endHit(Hit hit, boolean accepted) {
        ServerPlayer owner = hit.owner; LivingEntity target = hit.target; Gear gear = hit.gear;
        if (!accepted || owner == null || target == null || gear == null || !ready(owner) || owner.level() != target.level()
            || owner.getMainHandItem() != gear.stack() || !owns(owner, gear) || !direct(owner, hit.source)
            || !lost(hit.health, hit.absorption, target) || !owner.level().mayInteract(owner, target.blockPosition())) return;
        if (FIRE.equals(gear.pair().primaryId()) && hit.full) createLine(owner, gear, hit.position);
        else if (hit.fuse != null && FUSES.get(hit.fuse.key()) == hit.fuse && hit.fuse.gear().stack() == gear.stack()
            && hit.fuse.gear().pair().equals(gear.pair()) && owner.level().getGameTime() < hit.fuse.expires()) {
            Swing swing = SWING.get();
            if (swing != null && swing.player == owner && swing.weapon == gear.stack()) swing.detonated = true;
            startChain(owner, gear, hit.fuse, hit.position, !target.isAlive());
        }
    }
    private static void createLine(ServerPlayer owner, Gear gear, Vec3 center) {
        ServerLevel level = owner.level(); if (LINES.size() >= MAX_LINES) return;
        Vec3 forward = new Vec3(center.x - owner.getX(), 0, center.z - owner.getZ());
        if (forward.lengthSqr() < 1.0E-8D) { Vec3 look = owner.getLookAngle(); forward = new Vec3(look.x, 0, look.z); }
        if (forward.lengthSqr() < 1.0E-8D) return;
        forward = forward.normalize();
        List<EmberLine> owned = LINES.stream().filter(line -> line.owner.equals(owner.getUUID())).toList();
        if (owned.size() >= 2) LINES.remove(owned.getFirst());
        int base = param(LINE, "half_length_tenths", 25, 5, 60);
        double halfLength = Math.min(6.0D, (base + 2 * Math.clamp(gear.pair().secondaryLevel() - 1, 0, 9)) / 10.0D);
        long now = level.getGameTime();
        LINES.add(new EmberLine(owner, gear, center, forward, halfLength, now,
            now + param(LINE, "lifetime_ticks", 100, 20, 400)));
        level.sendParticles(ParticleTypes.FLAME, center.x, center.y + 0.3D, center.z, 12, 0.3D, 0.2D, 0.3D, 0.01D);
        CompendiumDiscoveryTracker.recordDiscovery(owner, FIRE, SWEEP);
    }
    /** Called only around the actual victim.hurtServer invocation inside vanilla doSweepAttack. */
    public static void afterSweep(Player player, LivingEntity victim, DamageSource source, float health, float absorption, boolean accepted) {
        if (!(player instanceof ServerPlayer owner) || !accepted || !ready(owner) || !eligible(owner, victim)
            || !direct(owner, source) || !lost(health, absorption, victim)) return;
        Swing swing = SWING.get(); ItemStack weapon = owner.getMainHandItem();
        if (swing == null || swing.player != owner || swing.weapon != weapon || swing.detonated) return;
        Gear gear = selected(weapon); if (gear == null || !SWEEP.equals(gear.pair().primaryId()) || !owns(owner, gear)) return;
        FuseKey key = new FuseKey(owner.getUUID(), victim.getUUID(), owner.level().dimension());
        if (FUSES.size() >= MAX_FUSES && !FUSES.containsKey(key)) return;
        FUSES.put(key, new FuseMark(key, gear, owner.level().getGameTime() + param(FUSE, "fuse_lifetime_ticks", 120, 20, 1200)));
        owner.level().sendParticles(ParticleTypes.SMOKE, victim.getX(), victim.getY() + victim.getBbHeight() * 0.6D,
            victim.getZ(), 6, 0.15D, 0.2D, 0.15D, 0.01D);
        CompendiumDiscoveryTracker.recordDiscovery(owner, SWEEP, FIRE);
    }
    private static void startChain(ServerPlayer owner, Gear gear, FuseMark start, Vec3 startPosition, boolean killedRoot) {
        ServerLevel level = owner.level(); long now = level.getGameTime();
        int firstDelay = param(FUSE, "first_delay_ticks", 10, 1, 40), step = param(FUSE, "step_delay_ticks", 3, 1, 20);
        double range = param(FUSE, "link_range_tenths", 40, 10, 100) / 10.0D;
        List<FuseMark> ordered = new ArrayList<>(); List<Vec3> origins = new ArrayList<>();
        FuseMark current = start; Vec3 cursor = startPosition.add(0, 0.6D, 0); Set<FuseKey> chosen = new HashSet<>();
        while (current != null && ordered.size() < 8 && BURSTS.size() + ordered.size() < MAX_BURSTS) {
            ordered.add(current); origins.add(cursor); chosen.add(current.key());
            Entity live = level.getEntity(current.key().target());
            Vec3 point = live instanceof LivingEntity l ? l.getBoundingBox().getCenter() : cursor;
            FuseMark next = null; double best = range * range;
            for (FuseMark candidate : FUSES.values()) {
                if (chosen.contains(candidate.key()) || !candidate.key().owner().equals(owner.getUUID())
                    || !candidate.key().dimension().equals(level.dimension()) || candidate.gear().stack() != gear.stack()
                    || !candidate.gear().pair().equals(gear.pair()) || now >= candidate.expires()) continue;
                Entity entity = level.getEntity(candidate.key().target());
                if (!(entity instanceof LivingEntity living) || !eligible(owner, living)) continue;
                Vec3 to = living.getBoundingBox().getCenter(); double distance = point.distanceToSqr(to);
                if (distance <= best && VirtualCombatSight.clear(level, owner, point, to)) { best = distance; next = candidate; }
            }
            cursor = point; current = next;
        }
        UUID chain = UUID.randomUUID();
        for (int i = 0; i < ordered.size(); i++) {
            FuseMark mark = ordered.get(i);
            if (!FUSES.remove(mark.key(), mark)) break;
            BURSTS.add(new Burst(chain, i, mark.key(), gear, origins.get(i), range,
                now + firstDelay + (long) i * step, i == 0 && killedRoot));
        }
    }
    /** Preserve real data-pack Fire Aspect once on a supported non-native weapon. */
    public static void nonNativePostAttack(ServerLevel level, Player player, ItemStack weapon, Entity victim, DamageSource source) {
        if (Boolean.TRUE.equals(PRIMARY_REPLAY.get()) || !(player instanceof ServerPlayer owner) || owner.getMainHandItem() != weapon
            || source.getEntity() != owner || source.getDirectEntity() != owner) return;
        Gear gear = selected(weapon); if (gear == null || !FIRE.equals(gear.pair().primaryId())) return;
        Holder<Enchantment> primary = FusionStationOperations.lookupEnchantment(level.registryAccess(), FIRE).orElse(null);
        if (primary == null || weapon.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(primary) > 0) return;
        PRIMARY_REPLAY.set(true);
        try { primary.value().doPostAttack(level, gear.pair().primaryLevel(), new EnchantedItemInUse(weapon, EquipmentSlot.MAINHAND, owner),
            EnchantmentTarget.ATTACKER, victim, source); }
        finally { PRIMARY_REPLAY.remove(); }
    }
    private static void tickLine(ServerLevel level, ServerPlayer owner, EmberLine line) {
        double search = line.halfLength + 4.0D;
        AABB box = AABB.ofSize(line.center, search * 2, 5.0D, search * 2);
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, box, e -> eligible(owner, e));
        victims.sort(Comparator.comparingDouble(e -> e.distanceToSqr(line.center)));
        Set<UUID> seen = new HashSet<>(); int count = 0;
        for (LivingEntity victim : victims) {
            if (++count > MAX_PER_LINE) break;
            UUID id = victim.getUUID(); seen.add(id); Vec3 position = victim.position(), old = line.previous.get(id);
            if (old != null && !line.affected.contains(id) && line.affected.size() < MAX_PER_LINE
                && Math.abs(position.y - line.center.y) <= 2.0D
                && FireSweepMath.crosses(old.x, old.z, position.x, position.z, line.center.x, line.center.z,
                    line.forward.x, line.forward.z, line.halfLength, 4.0D)
                && VirtualCombatSight.clear(level, owner, line.center.add(0, 0.5D, 0), victim.getBoundingBox().getCenter())) {
                line.affected.add(id);
                if (!victim.fireImmune()) victim.igniteForSeconds(param(LINE, "ignition_seconds", 3, 1, 20));
            }
            if (FireSweepMath.beyondCenter(position.x, position.z, line.center.x, line.center.z, line.forward.x, line.forward.z)) {
                line.previous.put(id, position);
            }
        }
        line.previous.keySet().removeIf(id -> !seen.contains(id));
        if ((level.getGameTime() - line.created) % 5 == 0) {
            for (int i = -4; i <= 4; i++) {
                double p = i * line.halfLength / 4.0D;
                level.sendParticles(ParticleTypes.FLAME, line.center.x - line.forward.z * p, line.center.y + 0.25D,
                    line.center.z + line.forward.x * p, 1, 0, 0.03D, 0, 0.005D);
            }
        }
    }
    private static @Nullable Vec3 tickBurst(ServerLevel level, ServerPlayer owner, Burst burst) {
        Entity entity = level.getEntity(burst.key().target());
        // The attack that lit the first fuse may itself have killed the marked target.
        // Its last position remains a spark, but a later broken link terminates the chain.
        if (burst.killedRoot() && (entity == null || !entity.isAlive())) return burst.from();
        if (!(entity instanceof LivingEntity victim) || !eligible(owner, victim)
            || burst.from().distanceToSqr(victim.getBoundingBox().getCenter()) > burst.range() * burst.range()
            || !VirtualCombatSight.clear(level, owner, burst.from(), victim.getBoundingBox().getCenter())) return null;
        float damage = Math.min(12.0F, 2.0F + Math.clamp(burst.gear().pair().secondaryLevel(), 1, 10));
        DamageSource fire = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypes.IN_FIRE), owner);
        if (!victim.hurtServer(level, fire, damage)) return null;
        if (!victim.fireImmune()) victim.igniteForSeconds(param(FUSE, "ignition_seconds", 3, 1, 20));
        level.sendParticles(ParticleTypes.EXPLOSION, victim.getX(), victim.getY() + victim.getBbHeight() * 0.5D,
            victim.getZ(), 1, 0, 0, 0, 0);
        return victim.getBoundingBox().getCenter();
    }
    public static void tickServer(MinecraftServer server) {
        for (EmberLine line : List.copyOf(LINES)) {
            ServerLevel level = server.getLevel(line.dimension); ServerPlayer owner = server.getPlayerList().getPlayer(line.owner);
            if (level == null || owner == null || owner.level() != level || !ready(owner) || !owns(owner, line.gear)
                || level.getGameTime() >= line.expires) { LINES.remove(line); continue; }
            tickLine(level, owner, line);
        }
        for (FuseMark fuse : List.copyOf(FUSES.values())) {
            ServerLevel level = server.getLevel(fuse.key().dimension()); ServerPlayer owner = server.getPlayerList().getPlayer(fuse.key().owner());
            Entity target = level == null ? null : level.getEntity(fuse.key().target());
            if (level == null || owner == null || owner.level() != level || !ready(owner) || !owns(owner, fuse.gear())
                || level.getGameTime() >= fuse.expires() || !(target instanceof LivingEntity living) || !eligible(owner, living)) FUSES.remove(fuse.key(), fuse);
        }
        for (Burst snapshot : List.copyOf(BURSTS)) {
            // A preceding link can move this origin or cancel the remainder in the same tick.
            Burst burst = BURSTS.stream().filter(entry -> entry.chain().equals(snapshot.chain()) && entry.index() == snapshot.index())
                .findFirst().orElse(null);
            if (burst == null) continue;
            ServerLevel level = server.getLevel(burst.key().dimension()); ServerPlayer owner = server.getPlayerList().getPlayer(burst.key().owner());
            if (level == null || owner == null || owner.level() != level || !ready(owner) || !owns(owner, burst.gear())) {
                BURSTS.removeIf(entry -> entry.chain().equals(burst.chain())); continue;
            }
            if (level.getGameTime() >= burst.due()) {
                BURSTS.remove(burst);
                Vec3 endpoint = tickBurst(level, owner, burst);
                if (endpoint == null) BURSTS.removeIf(entry -> entry.chain().equals(burst.chain()));
                else for (int i = 0; i < BURSTS.size(); i++) {
                    Burst next = BURSTS.get(i);
                    if (next.chain().equals(burst.chain()) && next.index() == burst.index() + 1) {
                        BURSTS.set(i, next.from(endpoint)); break;
                    }
                }
            }
        }
    }
    public static void onDisconnect(UUID id) {
        LINES.removeIf(line -> line.owner.equals(id));
        FUSES.keySet().removeIf(key -> key.owner().equals(id) || key.target().equals(id));
        Set<UUID> interrupted = new HashSet<>();
        for (Burst burst : BURSTS) if (burst.key().owner().equals(id) || burst.key().target().equals(id)) interrupted.add(burst.chain());
        BURSTS.removeIf(burst -> interrupted.contains(burst.chain()));
    }
    public static void clear() { LINES.clear(); FUSES.clear(); BURSTS.clear(); SWING.remove(); PRIMARY_REPLAY.remove(); }
}

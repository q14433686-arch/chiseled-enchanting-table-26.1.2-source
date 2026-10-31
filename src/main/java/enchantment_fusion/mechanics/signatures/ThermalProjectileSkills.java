package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.config.FusionConfigStore;
import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mixin.AbstractArrowAccessor;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.jetbrains.annotations.Nullable;

/** Heat shields follow real arrows; their reverse creates virtual fire after an actual knockback settles. */
public final class ThermalProjectileSkills {
    public static final String FLAME = "minecraft:flame", PUNCH = "minecraft:punch";
    public static final String BARRIER = "explosive_flare_arrow", VORTEX = "meteor_streak";
    private static final int MAX_FLIGHTS = 4096, MAX_MARKS = 512, MAX_FIELDS = 256, MAX_AFFECTED = 32;
    private static final class Flight {
        final UUID owner;
        final ResourceKey<Level> dimension;
        final ItemStack weapon;
        final FusedPair pair;
        final long expires;
        final Set<UUID> redirected = new HashSet<>();
        Flight(ServerPlayer owner, ItemStack weapon, FusedPair pair) {
            this.owner = owner.getUUID(); this.dimension = owner.level().dimension();
            this.weapon = weapon; this.pair = pair; this.expires = owner.level().getGameTime() + 1200;
        }
    }
    private static final class Mark {
        final UUID owner, target;
        final ResourceKey<Level> dimension;
        final ItemStack weapon;
        final FusedPair pair;
        final long expires;
        Vec3 last;
        boolean airborne, observed;
        int stillTicks;
        Mark(ServerPlayer owner, LivingEntity target, Flight flight, long now) {
            this.owner = owner.getUUID(); this.target = target.getUUID(); this.dimension = flight.dimension;
            this.weapon = flight.weapon; this.pair = flight.pair;
            this.expires = now + param(VORTEX, "landing_wait_ticks", 80, 10, 200);
            this.last = target.position(); this.airborne = !target.onGround();
        }
    }
    private static final class Field {
        final UUID owner;
        final ResourceKey<Level> dimension;
        final ItemStack weapon;
        final FusedPair pair;
        final Vec3 center;
        final long expires;
        int age;
        final Set<UUID> affected = new HashSet<>(), pending = new HashSet<>();
        Field(ServerPlayer owner, Mark mark, Vec3 center, long now) {
            this.owner = owner.getUUID(); this.dimension = mark.dimension; this.weapon = mark.weapon;
            this.pair = mark.pair; this.center = center;
            this.expires = now + param(VORTEX, "vortex_lifetime_ticks", 60, 1, 200);
        }
    }
    // Logical server thread only; callbacks can mutate the lists during damage, so tick snapshots are used.
    private static final Map<AbstractArrow, Flight> FLIGHTS = new WeakHashMap<>();
    private static final List<Mark> MARKS = new ArrayList<>();
    private static final List<Field> FIELDS = new ArrayList<>();
    private ThermalProjectileSkills() {}
    private static int param(String id, String key, int fallback, int min, int max) {
        return FusionConfigStore.signatureInt(id, key, fallback, min, max);
    }
    public static boolean matches(@Nullable FusedPair pair, String signature) {
        return pair != null && (FLAME.equals(pair.primaryId()) && PUNCH.equals(pair.secondaryId()) && BARRIER.equals(signature)
            || PUNCH.equals(pair.primaryId()) && FLAME.equals(pair.secondaryId()) && VORTEX.equals(signature));
    }
    public static boolean supportsCarrier(Set<FusionCarrier> roles) {
        return roles != null && (roles.contains(FusionCarrier.BOW) || roles.contains(FusionCarrier.CROSSBOW));
    }
    public static boolean applies(@Nullable FusedPair pair, @Nullable ItemStack stack, String signature) {
        return matches(pair, signature) && stack != null && !stack.isEmpty() && supportsCarrier(FusionCarrier.classify(stack));
    }
    public static @Nullable FusedPair selected(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if (SignatureCatalog.resolve(pair).map(entry -> applies(pair, stack, entry.id())).orElse(false)) return pair;
        return null;
    }
    private static boolean held(ServerPlayer owner, ItemStack stack) {
        return owner.getMainHandItem() == stack || owner.getOffhandItem() == stack;
    }
    private static boolean owns(ServerPlayer owner, ItemStack stack) {
        var inv = owner.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i) == stack) return true;
        return false;
    }
    private static boolean same(ServerPlayer owner, ItemStack stack, FusedPair pair, ResourceKey<Level> dimension) {
        return owner.isAlive() && !owner.isSpectator() && owner.level().dimension().equals(dimension)
            && !stack.isEmpty() && owns(owner, stack) && pair.equals(selected(stack));
    }
    /** Only real central shots own virtual shield/settle state. Vanilla still fires and consumes ammunition. */
    public static void launched(Projectile projectile, LivingEntity shooter, int index, @Nullable ItemStack weapon) {
        if (!(projectile instanceof AbstractArrow arrow) || !(shooter instanceof ServerPlayer owner) || index != 0
            || weapon == null || !held(owner, weapon) || !owner.isAlive() || owner.isSpectator()
            || PhantomVolley.isGhost(arrow) || FLIGHTS.size() >= MAX_FLIGHTS) return;
        FusedPair pair = selected(weapon);
        if (pair != null) FLIGHTS.put(arrow, new Flight(owner, weapon, pair));
    }
    /** On non-native crossbows, run the real datapack Flame projectile-spawn effect once. */
    public static void applyPrimarySpawn(Projectile projectile) {
        if (!(projectile instanceof AbstractArrow arrow) || !(arrow.level() instanceof ServerLevel level)
            || !(arrow.getOwner() instanceof ServerPlayer owner) || PhantomVolley.isGhost(arrow)) return;
        ItemStack weapon = arrow.getWeaponItem();
        FusedPair pair = selected(weapon);
        if (pair == null || !FLAME.equals(pair.primaryId())) return;
        var nativeFlame = FusionStationOperations.lookupEnchantment(level.registryAccess(), FLAME);
        if (nativeFlame.isEmpty() || weapon.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(nativeFlame.get()) > 0) return;
        var item = new EnchantedItemInUse(weapon, null, owner, arrow::onItemBreak);
        nativeFlame.get().value().onProjectileSpawned(level, pair.primaryLevel(), item, arrow);
    }
    /** Preserve the real datapack Punch value effect on a non-native crossbow, without double counting native Punch. */
    public static float primaryKnockback(ServerLevel level, ItemStack weapon, Entity target, DamageSource source, float vanilla) {
        FusedPair pair = selected(weapon);
        if (pair == null || !PUNCH.equals(pair.primaryId()) || !(source.getDirectEntity() instanceof AbstractArrow)) return vanilla;
        var nativePunch = FusionStationOperations.lookupEnchantment(level.registryAccess(), PUNCH);
        if (nativePunch.isEmpty() || weapon.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(nativePunch.get()) > 0) return vanilla;
        MutableFloat result = new MutableFloat(vanilla);
        nativePunch.get().value().modifyKnockback(level, pair.primaryLevel(), weapon, target, source, result);
        return Float.isFinite(result.floatValue()) ? result.floatValue() : vanilla;
    }
    private static boolean hostile(ServerPlayer owner, LivingEntity target) {
        if (!target.isAlive() || target == owner || target.isSpectator() || target.isInvulnerable()
            || target.isAlliedTo(owner) || owner.isAlliedTo(target) || !owner.level().mayInteract(owner, target.blockPosition())) return false;
        if (target instanceof Player player) return !player.getAbilities().invulnerable && owner.canHarmPlayer(player);
        if (PhantomVolley.isProtected(owner, target)) return false;
        if (target instanceof NeutralMob) return target instanceof Mob mob && mob.getTarget() == owner;
        return target instanceof Enemy;
    }
    private static boolean clearLine(ServerLevel level, Entity viewer, Vec3 from, Vec3 to) {
        return level.clipIncludingBorder(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, viewer))
            .getType() == HitResult.Type.MISS;
    }
    private static Vec3 visibleStep(ServerLevel level, Projectile projectile) {
        Vec3 step = projectile.getDeltaMovement();
        Vec3 start = projectile.position();
        if (!Double.isFinite(step.x) || !Double.isFinite(step.y) || !Double.isFinite(step.z)
            || !Double.isFinite(start.x) || !Double.isFinite(start.y) || !Double.isFinite(start.z)) return Vec3.ZERO;
        if (step.lengthSqr() > 256.0D) step = step.normalize().scale(16.0D);
        return level.clipIncludingBorder(new ClipContext(start, start.add(step), ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE, projectile)).getLocation().subtract(start);
    }
    /** Once per passing hostile projectile, nudge it sideways without changing its owner or creating ammunition. */
    public static void tickArrow(AbstractArrow arrow) {
        Flight flight = FLIGHTS.get(arrow);
        if (flight == null || !FLAME.equals(flight.pair.primaryId()) || !(arrow.level() instanceof ServerLevel level)
            || !(arrow.getOwner() instanceof ServerPlayer owner) || !owner.getUUID().equals(flight.owner)
            || level.getGameTime() >= flight.expires || !same(owner, flight.weapon, flight.pair, flight.dimension)
            || !arrow.isAlive() || arrow.noPhysics || ((AbstractArrowAccessor) arrow).ef$isInGround()
            || arrow.getDeltaMovement().lengthSqr() < 0.01D || flight.redirected.size() >= MAX_AFFECTED) return;
        double radius = Math.min(4.0D, param(BARRIER, "barrier_radius_tenths", 15, 5, 40) / 10.0D);
        if (arrow.tickCount % 4 == 0) level.sendParticles(ParticleTypes.FLAME, arrow.getX(), arrow.getY(), arrow.getZ(),
            2, 0.12D, 0.12D, 0.12D, 0.0D);
        Vec3 start = arrow.position(), arrowStep = visibleStep(level, arrow);
        if (arrowStep.lengthSqr() < 1.0E-8D) return;
        Vec3 axis = arrowStep.normalize();
        // Include the ordinary one-tick approach of a hostile projectile rather than
        // sampling only endpoints; the bounded extra search cannot cover every modded speed.
        AABB sweep = new AABB(start, start.add(arrowStep)).inflate(radius + 4.0D);
        for (Projectile other : level.getEntitiesOfClass(Projectile.class, sweep, p ->
            p != arrow && p.isAlive() && !p.noPhysics && p.getDeltaMovement().lengthSqr() > 0.01D
                && !flight.redirected.contains(p.getUUID()) && p.getOwner() instanceof LivingEntity attacker && hostile(owner, attacker))) {
            if (other instanceof AbstractArrow otherArrow && ((AbstractArrowAccessor) otherArrow).ef$isInGround()) continue;
            Vec3 relative = other.position().subtract(start), otherStep = visibleStep(level, other);
            if (otherStep.lengthSqr() < 1.0E-8D) continue;
            double t = ThermalProjectileMath.approachTime(relative, otherStep.subtract(arrowStep));
            if (t < 0.0D) continue;
            Vec3 arrowNear = start.add(arrowStep.scale(t)), otherNear = other.position().add(otherStep.scale(t));
            Vec3 separation = otherNear.subtract(arrowNear);
            if (separation.lengthSqr() > radius * radius || !clearLine(level, arrow, arrowNear, otherNear)) continue;
            Vec3 lateral = separation.subtract(axis.scale(separation.dot(axis)));
            if (lateral.lengthSqr() < 1.0E-8D) lateral = axis.cross(new Vec3(0, 1, 0));
            if (lateral.lengthSqr() < 1.0E-8D) lateral = axis.cross(new Vec3(1, 0, 0));
            if (lateral.lengthSqr() < 1.0E-8D) continue;
            flight.redirected.add(other.getUUID());
            double force = 0.35D + 0.15D * Math.clamp(flight.pair.secondaryLevel(), 1, 5);
            other.setDeltaMovement(other.getDeltaMovement().add(lateral.normalize().scale(force)));
            other.hurtMarked = true;
            level.sendParticles(ParticleTypes.FLAME, other.getX(), other.getY(), other.getZ(), 4, 0.08D, 0.08D, 0.08D, 0.01D);
            CompendiumDiscoveryTracker.recordDiscovery(owner, FLAME, PUNCH);
            if (flight.redirected.size() >= MAX_AFFECTED) break;
        }
    }
    /** The push hook runs only after vanilla confirmed arrow damage and computed its actual resistance-adjusted push. */
    public static void knockedBack(AbstractArrow arrow, LivingEntity target, Vec3 before) {
        Flight flight = FLIGHTS.get(arrow);
        if (flight == null || !PUNCH.equals(flight.pair.primaryId()) || !(arrow.level() instanceof ServerLevel level)
            || !(arrow.getOwner() instanceof ServerPlayer owner) || !owner.getUUID().equals(flight.owner)
            || level.getGameTime() >= flight.expires || !same(owner, flight.weapon, flight.pair, flight.dimension)
            || target.getType() == EntityType.ENDERMAN || !hostile(owner, target)
            || target.getDeltaMovement().subtract(before).horizontalDistanceSqr() < 1.0E-8D) return;
        MARKS.removeIf(mark -> mark.owner.equals(owner.getUUID()) && mark.target.equals(target.getUUID()) && mark.weapon == flight.weapon);
        if (MARKS.size() >= MAX_MARKS) MARKS.removeFirst();
        MARKS.add(new Mark(owner, target, flight, level.getGameTime()));
    }
    public static void tickServer(MinecraftServer server) {
        FLIGHTS.entrySet().removeIf(entry -> !entry.getKey().isAlive()
            || entry.getKey().level().getGameTime() >= entry.getValue().expires);
        for (Mark mark : List.copyOf(MARKS)) {
            if (!MARKS.contains(mark)) continue;
            ServerLevel level = server.getLevel(mark.dimension);
            ServerPlayer owner = server.getPlayerList().getPlayer(mark.owner);
            Entity entity = level == null ? null : level.getEntity(mark.target);
            if (level == null || owner == null || owner.level() != level || !same(owner, mark.weapon, mark.pair, mark.dimension)
                || !(entity instanceof LivingEntity target) || !hostile(owner, target) || level.getGameTime() >= mark.expires) {
                MARKS.remove(mark); continue;
            }
            long now = level.getGameTime();
            // The first observation only establishes a post-impact baseline; a target
            // standing on the ground when shot has not landed yet.
            if (!mark.observed) {
                mark.observed = true; mark.airborne |= !target.onGround(); mark.last = target.position(); continue;
            }
            if (!target.onGround()) mark.airborne = true;
            boolean landed = mark.airborne && target.onGround();
            double moved = target.position().distanceToSqr(mark.last);
            mark.stillTicks = moved < 0.0025D && target.getDeltaMovement().lengthSqr() < 0.02D ? mark.stillTicks + 1 : 0;
            mark.last = target.position();
            if (!landed && mark.stillTicks < 3) continue;
            MARKS.remove(mark); // commit before field creation and callbacks
            if (FIELDS.size() >= MAX_FIELDS) FIELDS.removeFirst();
            FIELDS.add(new Field(owner, mark, target.position(), now));
            CompendiumDiscoveryTracker.recordDiscovery(owner, PUNCH, FLAME);
        }
        for (Field field : List.copyOf(FIELDS)) {
            if (!FIELDS.contains(field)) continue;
            ServerLevel level = server.getLevel(field.dimension);
            ServerPlayer owner = server.getPlayerList().getPlayer(field.owner);
            if (level == null || owner == null || owner.level() != level || !same(owner, field.weapon, field.pair, field.dimension)
                || level.getGameTime() >= field.expires) { FIELDS.remove(field); continue; }
            if (field.age++ % 5 != 0) continue;
            level.sendParticles(ParticleTypes.FLAME, field.center.x, field.center.y + 0.25D, field.center.z,
                8, 0.5D, 0.2D, 0.5D, 0.01D);
            if (field.affected.size() >= MAX_AFFECTED) continue;
            double radius = Math.min(5.0D, 1.5D + 0.25D * Math.clamp(field.pair.primaryLevel(), 1, 10));
            AABB area = AABB.ofSize(field.center.add(0, 0.8D, 0), radius * 2, 2.5D, radius * 2);
            for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, area, e -> !field.affected.contains(e.getUUID())
                && !field.pending.contains(e.getUUID()) && hostile(owner, e))) {
                Vec3 center = victim.getBoundingBox().getCenter();
                if (center.distanceToSqr(field.center.add(0, 0.8D, 0)) > radius * radius
                    || !clearLine(level, owner, field.center.add(0, 0.2D, 0), center)) continue;
                if (!field.pending.add(victim.getUUID())) continue; // reserve before damage callbacks
                try {
                    DamageSource source = new DamageSource(level.damageSources().inFire().typeHolder(), owner);
                    float damage = 1.0F + 0.5F * Math.clamp(field.pair.secondaryLevel(), 1, 5);
                    if (victim.hurtServer(level, source, damage)) {
                        field.affected.add(victim.getUUID());
                        if (FIELDS.contains(field) && same(owner, field.weapon, field.pair, field.dimension)
                            && hostile(owner, victim)) victim.igniteForSeconds(2.0F + field.pair.secondaryLevel());
                    }
                } finally {
                    field.pending.remove(victim.getUUID());
                }
                if (field.affected.size() >= MAX_AFFECTED) break;
            }
        }
    }
    public static void onUnload(Entity entity) {
        if (entity instanceof AbstractArrow arrow) FLIGHTS.remove(arrow);
    }
    public static void onDisconnect(UUID player) {
        FLIGHTS.entrySet().removeIf(entry -> entry.getValue().owner.equals(player));
        MARKS.removeIf(mark -> mark.owner.equals(player)); FIELDS.removeIf(field -> field.owner.equals(player));
    }
    public static void clear() { FLIGHTS.clear(); MARKS.clear(); FIELDS.clear(); }
}

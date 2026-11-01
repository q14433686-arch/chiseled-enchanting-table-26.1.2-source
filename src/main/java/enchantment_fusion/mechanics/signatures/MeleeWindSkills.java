package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mechanics.FusionStationOperations;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
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

/** 43.4: a movement-led virtual blade, or an updraft confirmed by the same real Wind Burst. */
public final class MeleeWindSkills {
    public static final String WIND = "minecraft:wind_burst";
    private static final Set<FusionCarrier> WEAPONS = Set.of(FusionCarrier.SWORD, FusionCarrier.AXE, FusionCarrier.SPEAR, FusionCarrier.MACE);
    private static final int MAX_OWNERS = 128, MAX_BLADE_VICTIMS = 8;
    private static final long BLADE_TICKS = 80, UPDRAFT_TICKS = 100;
    private record Gear(ItemStack stack, FusedPair pair) {}
    private record Snapshot(LivingEntity target, Vec3 position, AABB bounds) {}
    private record Capture(Blade blade, Map<UUID, Snapshot> candidates) {}
    private record BladeStep(ConfirmedPlayerMove.Step movement, Map<UUID, Snapshot> candidates) {}
    private static final class Blade {
        final UUID owner; final ResourceKey<Level> dimension; final Gear gear; final long expires;
        final Set<UUID> hit = new HashSet<>(); final List<BladeStep> steps = new ArrayList<>();
        Vec3 position, lastOwner;
        Blade(ServerPlayer owner, LivingEntity victim, Gear gear) {
            this.owner = owner.getUUID(); this.dimension = owner.level().dimension(); this.gear = gear;
            this.expires = owner.level().getGameTime() + BLADE_TICKS; this.lastOwner = owner.position();
            this.position = victim.getBoundingBox().getCenter(); this.hit.add(victim.getUUID());
        }
    }
    private record Pending(Gear gear, UUID victim, ResourceKey<Level> dimension, DamageSource source, long tick) {}
    private record Updraft(Gear gear, ResourceKey<Level> dimension, Vec3 center, long expires) {}
    private static final Map<UUID, Blade> BLADES = new HashMap<>();
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final Map<UUID, Updraft> UPDRAFTS = new HashMap<>();
    private static final ThreadLocal<Boolean> REPLAYING = ThreadLocal.withInitial(() -> false);
    private static boolean initialized;
    private MeleeWindSkills() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        ConfirmedMelee.register(MeleeWindSkills::onHit);
        ConfirmedWindBurst.register(MeleeWindSkills::onWindBurst);
        MeleeAttackThemes.registerPrimaryTemplate("melee_wind", (pair, stack) -> attackFirst(pair) && applies(pair, stack));
        ConfirmedPlayerMove.register(new ConfirmedPlayerMove.Observer() {
            @Override public boolean interested(ServerPlayer player) { return BLADES.containsKey(player.getUUID()); }
            @Override public @Nullable Object before(ServerPlayer player, Vec3 requested) {
                Blade blade = BLADES.get(player.getUUID());
                if (blade == null || requested == null || !Double.isFinite(requested.x) || !Double.isFinite(requested.y)
                    || !Double.isFinite(requested.z) || requested.lengthSqr() > 4.0D) return null;
                Vec3 predicted = blade.position;
                for (BladeStep pending : blade.steps) predicted = predicted.add(pending.movement().to().subtract(pending.movement().from()));
                List<LivingEntity> targets = player.level().getEntitiesOfClass(LivingEntity.class,
                    new AABB(predicted, predicted.add(requested)).inflate(0.6D), e -> e != player && e.isAlive() && !e.isSpectator());
                if (targets.size() > 32) return null;
                Map<UUID, Snapshot> snapshots = new HashMap<>();
                for (LivingEntity target : targets) snapshots.put(target.getUUID(), new Snapshot(target, target.position(), target.getBoundingBox()));
                return new Capture(blade, snapshots);
            }
            @Override public void moved(ServerPlayer player, ConfirmedPlayerMove.Step step, @Nullable Object evidence) {
                Blade blade = BLADES.get(player.getUUID()); if (blade == null) return;
                if (!(evidence instanceof Capture capture) || capture.blade() != blade || blade.steps.size() >= 4) {
                    BLADES.remove(player.getUUID(), blade); return;
                }
                Map<UUID, Snapshot> stable = new HashMap<>();
                for (var entry : capture.candidates().entrySet())
                    if (stationary(player.level(), entry.getValue())) stable.put(entry.getKey(), entry.getValue());
                blade.steps.add(new BladeStep(step, Map.copyOf(stable)));
            }
            @Override public void invalid(ServerPlayer player) { BLADES.remove(player.getUUID()); }
        });
    }
    public static boolean attackFirst(@Nullable FusedPair pair) {
        return pair != null && MeleeAttackThemes.isAttack(pair.primaryId()) && WIND.equals(pair.secondaryId());
    }
    public static boolean windFirst(@Nullable FusedPair pair) {
        return pair != null && WIND.equals(pair.primaryId()) && MeleeAttackThemes.isAttack(pair.secondaryId());
    }
    public static boolean matches(@Nullable FusedPair pair) {
        return (attackFirst(pair) || windFirst(pair)) && SignatureCatalog.resolve(pair).isEmpty();
    }
    public static boolean supportsCarrier(@Nullable FusedPair pair, Set<FusionCarrier> roles) {
        if (roles == null) return false;
        return attackFirst(pair) ? roles.stream().anyMatch(WEAPONS::contains) : windFirst(pair) && roles.contains(FusionCarrier.MACE);
    }
    public static boolean applies(@Nullable FusedPair pair, @Nullable ItemStack stack) {
        return matches(pair) && stack != null && !stack.isEmpty() && supportsCarrier(pair, FusionCarrier.classify(stack));
    }
    private static @Nullable Gear select(ItemStack stack, boolean reverse) {
        if (stack == null || stack.isEmpty()) return null;
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if ((reverse ? windFirst(pair) : attackFirst(pair)) && applies(pair, stack)) return new Gear(stack, pair);
        return null;
    }
    private static boolean stationary(ServerLevel level, Snapshot snapshot) {
        LivingEntity target = snapshot.target();
        return target.isAlive() && target.level() == level && level.getEntity(target.getUUID()) == target
            && target.position().distanceToSqr(snapshot.position()) <= 1.0E-6D && target.getBoundingBox().equals(snapshot.bounds());
    }
    private static boolean ready(ServerPlayer owner) { return owner != null && owner.isAlive() && !owner.isSpectator(); }
    private static boolean owns(ServerPlayer owner, Gear gear) {
        return ready(owner) && owner.getMainHandItem() == gear.stack() && applies(gear.pair(), gear.stack())
            && gear.stack().getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().contains(gear.pair());
    }
    private static boolean usableCenter(ServerPlayer owner, Vec3 center) {
        if (!Double.isFinite(center.x) || !Double.isFinite(center.y) || !Double.isFinite(center.z)) return false;
        BlockPos pos = BlockPos.containing(center);
        return owner.level().isInWorldBounds(pos) && owner.level().hasChunkAt(pos) && owner.level().mayInteract(owner, pos)
            && !owner.level().getServer().isUnderSpawnProtection(owner.level(), pos, owner);
    }
    private static boolean valid(ServerPlayer owner, Updraft field) {
        return owns(owner, field.gear()) && owner.level().dimension().equals(field.dimension())
            && owner.level().getGameTime() < field.expires() && usableCenter(owner, field.center());
    }
    /** A tall cylinder accommodates the real wind launch, but not a remote attack. */
    public static boolean insideUpdraft(Vec3 center, Vec3 player) {
        if (center == null || player == null || !Double.isFinite(center.x) || !Double.isFinite(center.y) || !Double.isFinite(center.z)
            || !Double.isFinite(player.x) || !Double.isFinite(player.y) || !Double.isFinite(player.z)) return false;
        double x = player.x - center.x, y = player.y - center.y, z = player.z - center.z;
        return x * x + z * z <= 9.0D && y >= -2.0D && y <= 6.0D;
    }
    public static float virtualDamage(int attack, int wind) {
        return Math.min(8.0F, 2.0F + 0.3F * Math.clamp(attack, 1, 10) + 0.2F * Math.clamp(wind, 1, 10));
    }
    public static boolean upwardImpulse(@Nullable Vec3 before, @Nullable Vec3 after) {
        return ConfirmedWindBurst.upwardImpulse(before, after);
    }
    private static void onHit(ConfirmedMelee.Hit hit) {
        ServerPlayer owner = hit.player(); LivingEntity victim = hit.target(); ItemStack weapon = hit.weapon();
        if (hit.slot() != EquipmentSlot.MAINHAND || !hit.eligibleBefore() || !ready(owner) || owner.getMainHandItem() != weapon) return;
        Gear forward = select(weapon, false);
        if (forward != null && hit.full() && owns(owner, forward) && MeleeAttackThemes.matchesTarget(forward.pair().primaryId(), victim)
            && usableCenter(owner, victim.getBoundingBox().getCenter()) && (BLADES.containsKey(owner.getUUID()) || BLADES.size() < MAX_OWNERS)) {
            Blade blade = new Blade(owner, victim, forward); BLADES.put(owner.getUUID(), blade);
            owner.level().sendParticles(ParticleTypes.SWEEP_ATTACK, blade.position.x, blade.position.y, blade.position.z, 1, 0, 0, 0, 0);
            CompendiumDiscoveryTracker.recordDiscovery(owner, forward.pair().primaryId(), forward.pair().secondaryId());
        }
        Gear reverse = select(weapon, true);
        if (reverse == null || !owns(owner, reverse) || !MeleeAttackThemes.matchesTarget(reverse.pair().secondaryId(), victim)) return;
        Updraft field = UPDRAFTS.get(owner.getUUID()); long now = owner.level().getGameTime();
        if (field != null && valid(owner, field) && field.gear().equals(reverse)
            && insideUpdraft(field.center(), owner.position()) && UPDRAFTS.remove(owner.getUUID(), field)) {
            PENDING.remove(owner.getUUID());
            VirtualWindRing.emit(owner, owner.position(), victim, 3.5D,
                virtualDamage(reverse.pair().secondaryLevel(), reverse.pair().primaryLevel()), 8,
                target -> owns(owner, reverse) && MeleeAttackThemes.eligible(owner, target, reverse.pair().secondaryId()));
            CompendiumDiscoveryTracker.recordDiscovery(owner, reverse.pair().primaryId(), reverse.pair().secondaryId());
        } else if (PENDING.containsKey(owner.getUUID()) || PENDING.size() < MAX_OWNERS) {
            // The original post-attack effect runs later. Do not turn an ordinary mace hit into wind.
            PENDING.put(owner.getUUID(), new Pending(reverse, victim.getUUID(), owner.level().dimension(), hit.source(), now));
        }
    }
    /** The shared observation must still match this direction's own confirmed melee hit. */
    private static void onWindBurst(ConfirmedWindBurst.Effect effect) {
        ServerPlayer owner = effect.owner(); ServerLevel level = effect.level();
        Pending pending = PENDING.get(owner.getUUID());
        if (pending == null || pending.source() != effect.source() || pending.tick() != level.getGameTime()
            || !pending.victim().equals(effect.victim().getUUID()) || !pending.dimension().equals(level.dimension())
            || effect.stack() != pending.gear().stack() || !owns(owner, pending.gear())
            || effect.enchantmentLevel() < pending.gear().pair().primaryLevel() || !usableCenter(owner, owner.position())) return;
        if (UPDRAFTS.containsKey(owner.getUUID()) || UPDRAFTS.size() < MAX_OWNERS) {
            long now = level.getGameTime();
            UPDRAFTS.put(owner.getUUID(), new Updraft(pending.gear(), level.dimension(), owner.position(), now + UPDRAFT_TICKS));
            PENDING.remove(owner.getUUID(), pending);
            level.sendParticles(ParticleTypes.CLOUD, owner.getX(), owner.getY() + 0.3D, owner.getZ(), 8, 0.3D, 0.15D, 0.3D, 0.01D);
        }
    }
    /** One missing primary is shared by all compatible Wind Burst directions on this item. */
    public static int primaryReplayLevel(int nativeLevel, int meleeLevel, int grapnelLevel) {
        return nativeLevel > 0 ? 0 : Math.max(0, Math.max(meleeLevel, grapnelLevel));
    }
    /** Preserve a missing primary at vanilla's post-attack timing without replaying a native copy. */
    public static void afterPostAttack(ServerLevel level, Player player, ItemStack weapon, Entity victim, DamageSource source) {
        if (!(player instanceof ServerPlayer owner) || owner.level() != level || owner.getMainHandItem() != weapon
            || source.getEntity() != owner || source.getDirectEntity() != owner) return;
        Gear gear = select(weapon, true);
        int meleeLevel = gear != null && owns(owner, gear) ? gear.pair().primaryLevel() : 0;
        int grapnelLevel = GrapnelWind.primaryLevel(owner, weapon);
        if ((meleeLevel > 0 || grapnelLevel > 0) && !Boolean.TRUE.equals(REPLAYING.get())) {
            Holder<Enchantment> nativeWind = FusionStationOperations.lookupEnchantment(level.registryAccess(), WIND).orElse(null);
            int nativeLevel = nativeWind == null ? 0 : weapon.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(nativeWind);
            int levelToReplay = primaryReplayLevel(nativeLevel, meleeLevel, grapnelLevel);
            if (nativeWind != null && levelToReplay > 0) {
                REPLAYING.set(true);
                try { nativeWind.value().doPostAttack(level, levelToReplay, new EnchantedItemInUse(weapon, EquipmentSlot.MAINHAND, owner),
                    EnchantmentTarget.ATTACKER, victim, source); }
                finally { REPLAYING.remove(); }
            }
        }
        Pending pending = PENDING.get(owner.getUUID());
        if (pending != null && pending.source() == source && pending.victim().equals(victim.getUUID())) PENDING.remove(owner.getUUID(), pending);
        GrapnelWind.afterPostAttack(owner, victim, source);
    }
    private static boolean bladeTarget(ServerPlayer owner, Blade blade, LivingEntity target) {
        return owns(owner, blade.gear) && BLADES.get(blade.owner) == blade && !blade.hit.contains(target.getUUID())
            && blade.hit.size() <= MAX_BLADE_VICTIMS && MeleeAttackThemes.eligible(owner, target, blade.gear.pair().primaryId());
    }
    private static void bladeLanded(ServerPlayer owner, Blade blade, LivingEntity target) {
        blade.hit.add(target.getUUID());
        if (owns(owner, blade.gear) && BLADES.get(blade.owner) == blade
            && MeleeAttackThemes.eligible(owner, target, blade.gear.pair().primaryId()))
            VirtualWindRing.push(owner, target, owner.position(), 0.5D);
    }
    public static void tickServer(MinecraftServer server) {
        for (Blade blade : List.copyOf(BLADES.values())) {
            ServerPlayer owner = server.getPlayerList().getPlayer(blade.owner); ServerLevel level = server.getLevel(blade.dimension);
            if (owner == null || level == null || owner.level() != level || !owns(owner, blade.gear) || level.getGameTime() >= blade.expires
                || !ConfirmedPlayerMove.connected(blade.lastOwner, owner.position(), blade.steps.stream().map(BladeStep::movement).toList())
                || !usableCenter(owner, blade.position)) {
                BLADES.remove(blade.owner, blade); continue;
            }
            for (BladeStep recorded : List.copyOf(blade.steps)) {
                if (BLADES.get(blade.owner) != blade || !owns(owner, blade.gear) || owner.level() != level) {
                    BLADES.remove(blade.owner, blade); break;
                }
                ConfirmedPlayerMove.Step step = recorded.movement();
                Vec3 motion = step.to().subtract(step.from()), next = blade.position.add(motion);
                if (motion.lengthSqr() > 2.25D || !usableCenter(owner, next) || next.distanceToSqr(owner.position()) > 144.0D
                    || !VirtualCombatSight.clear(level, owner, blade.position, next)) { BLADES.remove(blade.owner, blade); break; }
                if (blade.hit.size() <= MAX_BLADE_VICTIMS) {
                    VirtualBlade.sweep(owner, blade.position, motion,
                        virtualDamage(blade.gear.pair().primaryLevel(), blade.gear.pair().secondaryLevel()),
                        MAX_BLADE_VICTIMS, target -> bladeTarget(owner, blade, target)
                            && recorded.candidates().containsKey(target.getUUID()) && stationary(level, recorded.candidates().get(target.getUUID())),
                        ParticleTypes.SWEEP_ATTACK, target -> bladeLanded(owner, blade, target));
                }
                blade.position = next;
            }
            if (BLADES.get(blade.owner) == blade) {
                blade.lastOwner = owner.position(); blade.steps.clear();
                VirtualBlade.contact(owner, blade.position, virtualDamage(blade.gear.pair().primaryLevel(), blade.gear.pair().secondaryLevel()),
                    MAX_BLADE_VICTIMS, target -> bladeTarget(owner, blade, target), target -> bladeLanded(owner, blade, target));
                if (BLADES.get(blade.owner) == blade && level.getGameTime() % 5 == 0) level.sendParticles(ParticleTypes.CLOUD,
                    blade.position.x, blade.position.y, blade.position.z, 1, 0.03D, 0.03D, 0.03D, 0.0D);
            }
        }
        UPDRAFTS.entrySet().removeIf(entry -> {
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey()); return owner == null || !valid(owner, entry.getValue());
        });
        PENDING.entrySet().removeIf(entry -> {
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey()); return owner == null
                || !owner.level().dimension().equals(entry.getValue().dimension()) || owner.level().getGameTime() > entry.getValue().tick();
        });
    }
    public static void clearPlayer(UUID id) { BLADES.remove(id); PENDING.remove(id); UPDRAFTS.remove(id); }
    public static void clearServer() { BLADES.clear(); PENDING.clear(); UPDRAFTS.clear(); REPLAYING.remove(); }
}

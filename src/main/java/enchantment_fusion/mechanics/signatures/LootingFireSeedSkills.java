package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mechanics.SignaturePairs.SignatureEntry;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Looting -> Fire Aspect: a real direct kill leaves a virtual, owner-triggered fire ring. */
public final class LootingFireSeedSkills {
    public static final String LOOTING = "minecraft:looting", FIRE = "minecraft:fire_aspect", SIGNATURE = "searing_feast";
    private static final int MAX_SEEDS = 256, MAX_PER_OWNER = 3, MAX_VICTIMS = 32;
    private static final long SEED_TICKS = 100, RING_TICKS = 60;
    private record Gear(ItemStack weapon, FusedPair pair) {}
    private static final class Seed {
        final UUID owner; final ResourceKey<Level> dimension; final Gear gear; final Vec3 position;
        final Map<UUID, Long> cooldowns = new HashMap<>();
        long expires, nextPulse; boolean active;
        Seed(ServerPlayer owner, Gear gear, Vec3 position, long now) {
            this.owner = owner.getUUID(); this.dimension = owner.level().dimension(); this.gear = gear;
            this.position = position; this.expires = now + SEED_TICKS;
        }
    }
    private static final List<Seed> SEEDS = new ArrayList<>();
    private LootingFireSeedSkills() {}
    public static void init() { ConfirmedMelee.register(LootingFireSeedSkills::onHit); }
    public static boolean selectedSignature(@Nullable SignatureEntry entry) {
        return entry != null && SIGNATURE.equals(entry.id()) && LOOTING.equals(entry.primaryId()) && FIRE.equals(entry.secondaryId());
    }
    public static boolean isPair(@Nullable FusedPair pair) {
        return pair != null && LOOTING.equals(pair.primaryId()) && FIRE.equals(pair.secondaryId())
            && SignatureCatalog.resolve(pair).map(LootingFireSeedSkills::selectedSignature).orElse(false);
    }
    public static boolean supportsCarrier(@Nullable Set<FusionCarrier> roles) { return MeleeAttackThemes.supportsWeapon(roles); }
    public static boolean applies(@Nullable FusedPair pair, @Nullable ItemStack stack) {
        return isPair(pair) && stack != null && !stack.isEmpty() && supportsCarrier(FusionCarrier.classify(stack));
    }
    private static @Nullable Gear select(ItemStack stack) {
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if (applies(pair, stack)) return new Gear(stack, pair);
        return null;
    }
    private static boolean ready(ServerPlayer owner) { return owner.isAlive() && !owner.isSpectator(); }
    private static boolean owns(ServerPlayer owner, Gear gear) {
        if (gear.weapon().isEmpty() || !applies(gear.pair(), gear.weapon())
            || !gear.weapon().getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().contains(gear.pair())) return false;
        for (int i = 0; i < owner.getInventory().getContainerSize(); i++) if (owner.getInventory().getItem(i) == gear.weapon()) return true;
        return false;
    }
    private static boolean finite(Vec3 p) { return Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z); }
    private static void onHit(ConfirmedMelee.Hit hit) {
        MeleeImpulseSkills.Attack frame = MeleeImpulseSkills.currentAttack(); ServerPlayer owner = hit.player();
        if (frame == null || frame.ambiguous || frame.player != owner || frame.weapon != hit.weapon() || frame.target != hit.target()
            || hit.slot() != EquipmentSlot.MAINHAND || !hit.eligibleBefore() || !ready(owner)
            || owner.getMainHandItem() != hit.weapon() || hit.source().getEntity() != owner || hit.source().getDirectEntity() != owner
            || !hit.target().isDeadOrDying() || hit.target().level() != owner.level() || owner.distanceToSqr(hit.target()) > 36.0D) return;
        Gear gear = select(hit.weapon()); Vec3 origin = hit.target().position();
        if (gear == null || !finite(origin)) return;
        if (!frame.completions.isEmpty()) { frame.ambiguous = true; return; }
        LivingEntity victim = hit.target(); ServerLevel level = owner.level();
        frame.completions.add(() -> {
            try { create(owner, victim, level, gear, origin); }
            catch (RuntimeException error) { EnchantmentFusion.LOGGER.error("Unable to create fire seed", error); }
        });
    }
    private static void create(ServerPlayer owner, LivingEntity victim, ServerLevel level, Gear gear, Vec3 origin) {
        BlockPos block = BlockPos.containing(origin);
        if (!ready(owner) || owner.level() != level || victim.level() != level || !victim.isDeadOrDying()
            || owner.getMainHandItem() != gear.weapon() || !owns(owner, gear) || !level.hasChunkAt(block)
            || !level.isInWorldBounds(block) || !level.mayInteract(owner, block)
            || victim instanceof Player other && (!owner.canHarmPlayer(other) || !other.canHarmPlayer(owner))) return;
        List<Seed> existing = SEEDS.stream().filter(seed -> seed.owner.equals(owner.getUUID())).toList();
        if (existing.size() >= MAX_PER_OWNER) SEEDS.remove(existing.getFirst());
        if (SEEDS.size() >= MAX_SEEDS) return;
        SEEDS.add(new Seed(owner, gear, origin, level.getGameTime()));
        level.sendParticles(ParticleTypes.FLAME, origin.x, origin.y + 0.3D, origin.z, 6, 0.2D, 0.15D, 0.2D, 0.005D);
        CompendiumDiscoveryTracker.recordDiscovery(owner, LOOTING, FIRE);
    }
    private static boolean activate(ServerLevel level, ServerPlayer owner, Seed seed) {
        Vec3 from = owner.getEyePosition(), to = seed.position.add(0, 0.3D, 0);
        if (owner.getMainHandItem() != seed.gear.weapon() || owner.position().distanceToSqr(seed.position) > 2.25D
            || !VirtualCombatSight.clear(level, owner, from, to)) return false;
        seed.active = true; seed.expires = level.getGameTime() + RING_TICKS; seed.nextPulse = level.getGameTime(); return true;
    }
    private static void tickRing(ServerLevel level, ServerPlayer owner, Seed seed) {
        long now = level.getGameTime(); if (now < seed.nextPulse) return; seed.nextPulse = now + 5;
        double radius = LootingFireSeedMath.radius(seed.gear.pair().secondaryLevel()); Vec3 center = seed.position.add(0, 0.35D, 0);
        for (int i = 0; i < 16; i++) {
            double angle = Math.PI * 2 * i / 16.0D;
            level.sendParticles(ParticleTypes.FLAME, center.x + Math.cos(angle) * radius, center.y,
                center.z + Math.sin(angle) * radius, 1, 0, 0, 0, 0);
        }
        seed.cooldowns.entrySet().removeIf(e -> now >= e.getValue());
        AABB area = AABB.ofSize(center, (radius + 1) * 2, 4.0D, (radius + 1) * 2);
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, area,
            victim -> !victim.fireImmune() && MeleeAttackThemes.eligible(owner, victim, MeleeAttackThemes.SHARP));
        victims.sort(Comparator.comparingDouble(victim -> victim.distanceToSqr(center)));
        for (LivingEntity victim : victims) {
            if (!SEEDS.contains(seed) || owner.level() != level || !ready(owner) || !owns(owner, seed.gear)
                || seed.cooldowns.size() >= MAX_VICTIMS) break;
            Vec3 target = victim.getBoundingBox().getCenter();
            if (!LootingFireSeedMath.onRing(target.x - center.x, target.y - center.y, target.z - center.z, radius)
                || seed.cooldowns.containsKey(victim.getUUID()) || !VirtualCombatSight.clear(level, owner, center, target)) continue;
            seed.cooldowns.put(victim.getUUID(), now + 20); // reserve before any reentrant damage callback
            float health = victim.getHealth(), absorption = victim.getAbsorptionAmount();
            DamageSource source = new DamageSource(level.damageSources().inFire().typeHolder(), owner);
            boolean previous = Boolean.TRUE.equals(TransmuteRuntimeSupport.IN_CUSTOM_COMBAT.get());
            TransmuteRuntimeSupport.IN_CUSTOM_COMBAT.set(true);
            try {
                if (victim.hurtServer(level, source, LootingFireSeedMath.damage(seed.gear.pair().secondaryLevel()))
                    && ConfirmedMeleeMath.actualLoss(health, absorption, victim.getHealth(), victim.getAbsorptionAmount()) > 0
                    && SEEDS.contains(seed) && owner.level() == level && ready(owner) && owns(owner, seed.gear)
                    && MeleeAttackThemes.eligible(owner, victim, MeleeAttackThemes.SHARP))
                    victim.igniteForSeconds(LootingFireSeedMath.burnSeconds(seed.gear.pair().secondaryLevel()));
            } catch (RuntimeException error) {
                EnchantmentFusion.LOGGER.error("Unable to apply virtual fire ring to {}", victim.getUUID(), error);
            } finally { TransmuteRuntimeSupport.IN_CUSTOM_COMBAT.set(previous); }
        }
    }
    public static void tickServer(MinecraftServer server) {
        for (Seed seed : List.copyOf(SEEDS)) {
            ServerPlayer owner = server.getPlayerList().getPlayer(seed.owner); ServerLevel level = server.getLevel(seed.dimension);
            BlockPos block = BlockPos.containing(seed.position);
            if (owner == null || level == null || owner.level() != level || !ready(owner) || !owns(owner, seed.gear)
                || level.getGameTime() >= seed.expires || !level.hasChunkAt(block) || !level.mayInteract(owner, block)) {
                SEEDS.remove(seed); continue;
            }
            if (!seed.active && !activate(level, owner, seed)) {
                if (level.getGameTime() % 10 == 0) level.sendParticles(ParticleTypes.FLAME, seed.position.x,
                    seed.position.y + 0.3D, seed.position.z, 3, 0.1D, 0.15D, 0.1D, 0.001D);
                continue;
            }
            tickRing(level, owner, seed);
        }
    }
    public static void clearPlayer(UUID id) { SEEDS.removeIf(seed -> seed.owner.equals(id)); }
    public static void clearServer() { SEEDS.clear(); }
}

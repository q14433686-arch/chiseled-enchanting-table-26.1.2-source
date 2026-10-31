package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Selected 42.2 underwater melee/respiration template, with real air payment. */
public final class WaterPressureSkills {
    public static final String BREATH = "minecraft:respiration";
    private static final int MAX_BUBBLES = 256, MAX_PREPARED = 256;
    private static final long BUBBLE_TICKS = 100, PREPARED_TICKS = 100;
    private record Gear(ItemStack stack, FusedPair pair) {}
    private record Bubble(UUID owner, UUID target, ResourceKey<Level> dimension, Gear gear, long expires) {}
    private record Prepared(UUID owner, ResourceKey<Level> dimension, Gear gear, long expires) {}
    private static final List<Bubble> BUBBLES = new ArrayList<>();
    private static final Map<UUID, Prepared> PREPARED = new HashMap<>();
    private WaterPressureSkills() {}

    public static void init() {
        ConfirmedMelee.register(WaterPressureSkills::onHit);
        MeleeAttackThemes.registerPrimaryTemplate("water_pressure", (pair, stack) -> isAttackFirst(pair) && applies(pair, stack));
    }
    public static boolean isAttackFirst(@Nullable FusedPair pair) {
        return pair != null && MeleeAttackThemes.isAttack(pair.primaryId()) && BREATH.equals(pair.secondaryId());
    }
    public static boolean isBreathFirst(@Nullable FusedPair pair) {
        return pair != null && BREATH.equals(pair.primaryId()) && MeleeAttackThemes.isAttack(pair.secondaryId());
    }
    public static boolean matches(@Nullable FusedPair pair) {
        return (isAttackFirst(pair) || isBreathFirst(pair)) && SignatureCatalog.resolve(pair).isEmpty();
    }
    public static boolean supportsCarrier(FusedPair pair, Set<FusionCarrier> roles) {
        return roles != null && (isAttackFirst(pair) && MeleeAttackThemes.supportsWeapon(roles)
            || isBreathFirst(pair) && roles.contains(FusionCarrier.HEAD_ARMOR));
    }
    public static boolean applies(@Nullable FusedPair pair, @Nullable ItemStack stack) {
        return pair != null && stack != null && !stack.isEmpty() && matches(pair) && supportsCarrier(pair, FusionCarrier.classify(stack));
    }
    private static boolean hasPair(Gear gear) {
        return !gear.stack().isEmpty() && gear.stack().getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY)
            .pairs().contains(gear.pair()) && applies(gear.pair(), gear.stack());
    }
    private static boolean owns(ServerPlayer owner, Gear gear) {
        if (!hasPair(gear)) return false;
        for (int i = 0; i < owner.getInventory().getContainerSize(); i++) if (owner.getInventory().getItem(i) == gear.stack()) return true;
        return false;
    }
    private static boolean ready(ServerPlayer owner) { return owner.isAlive() && !owner.isSpectator(); }
    private static boolean underwater(ServerPlayer owner) { return owner.isEyeInFluid(FluidTags.WATER); }
    private static String attackId(FusedPair pair) { return isAttackFirst(pair) ? pair.primaryId() : pair.secondaryId(); }
    private static List<Gear> equipped(ItemStack stack, boolean attackFirst) {
        if (stack == null || stack.isEmpty()) return List.of();
        List<Gear> result = new ArrayList<>();
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs()) {
            if ((attackFirst ? isAttackFirst(pair) : isBreathFirst(pair)) && applies(pair, stack)) result.add(new Gear(stack, pair));
        }
        return result;
    }
    private static @Nullable Gear helmet(ServerPlayer owner) {
        List<Gear> gears = equipped(owner.getItemBySlot(EquipmentSlot.HEAD), false);
        return gears.stream().max(Comparator.comparingLong(gear -> (long) gear.pair().primaryLevel() + gear.pair().secondaryLevel())).orElse(null);
    }
    public static boolean hasEquipped(ServerPlayer owner) { return helmet(owner) != null; }

    /** Called only after the server has selected and rate-limited the player's active skill. */
    public static boolean activate(ServerPlayer owner) {
        Gear gear = helmet(owner);
        if (!ready(owner) || !underwater(owner) || owner.containerMenu != owner.inventoryMenu || gear == null) return false;
        long now = owner.level().getGameTime(); Prepared old = PREPARED.get(owner.getUUID());
        if (old != null && now < old.expires() && old.dimension().equals(owner.level().dimension())
            && owner.getItemBySlot(EquipmentSlot.HEAD) == old.gear().stack() && hasPair(old.gear())) {
            owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.water_pressure_ready"), true); return false;
        }
        if (old != null) PREPARED.remove(owner.getUUID());
        if (PREPARED.size() >= MAX_PREPARED) return false;
        int air = owner.getAirSupply(), max = owner.getMaxAirSupply();
        if (!WaterPressureMath.canPay(air, max)) {
            owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.water_pressure_air"), true); return false;
        }
        int cost = WaterPressureMath.cost(max);
        owner.setAirSupply(air - cost);
        PREPARED.put(owner.getUUID(), new Prepared(owner.getUUID(), owner.level().dimension(), gear, now + PREPARED_TICKS));
        owner.level().sendParticles(ParticleTypes.BUBBLE, owner.getX(), owner.getEyeY(), owner.getZ(), 10, 0.2D, 0.2D, 0.2D, 0.03D);
        owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.water_pressure_paid", cost), true);
        return true;
    }
    private static @Nullable Bubble findBubble(ServerPlayer owner, LivingEntity target, Gear gear) {
        for (Bubble bubble : BUBBLES) if (bubble.owner().equals(owner.getUUID()) && bubble.target().equals(target.getUUID())
            && bubble.dimension().equals(owner.level().dimension()) && bubble.gear().stack() == gear.stack()
            && bubble.gear().pair().equals(gear.pair())) return bubble;
        return null;
    }
    private static void onHit(ConfirmedMelee.Hit hit) {
        ServerPlayer owner = hit.player(); LivingEntity target = hit.target(); long now = owner.level().getGameTime();
        if (!ready(owner) || !underwater(owner) || hit.slot() != EquipmentSlot.MAINHAND || !hit.eligibleBefore() || !target.isInWater()) return;
        if (owner.getMainHandItem() == hit.weapon()) {
            for (Gear gear : equipped(hit.weapon(), true)) {
                if (!owns(owner, gear) || !MeleeAttackThemes.matchesTarget(attackId(gear.pair()), target)) continue;
                Bubble bubble = findBubble(owner, target, gear);
                if (bubble != null && now < bubble.expires()) {
                    BUBBLES.remove(bubble); burst(owner, target, gear);
                } else if (target.isAlive()) {
                    if (bubble != null) BUBBLES.remove(bubble);
                    if (BUBBLES.size() < MAX_BUBBLES) {
                        BUBBLES.add(new Bubble(owner.getUUID(), target.getUUID(), owner.level().dimension(), gear, now + BUBBLE_TICKS));
                        owner.level().sendParticles(ParticleTypes.BUBBLE, target.getX(), target.getY(0.5D), target.getZ(), 10, 0.2D, 0.25D, 0.2D, 0.03D);
                        CompendiumDiscoveryTracker.recordDiscovery(owner, gear.pair().primaryId(), gear.pair().secondaryId());
                    }
                }
            }
        }
        Prepared prepared = PREPARED.get(owner.getUUID());
        if (prepared == null || now >= prepared.expires() || !prepared.dimension().equals(owner.level().dimension())
            || owner.getItemBySlot(EquipmentSlot.HEAD) != prepared.gear().stack() || !hasPair(prepared.gear())
            || !MeleeAttackThemes.matchesTarget(attackId(prepared.gear().pair()), target)) return;
        PREPARED.remove(owner.getUUID(), prepared);
        FusedPair pair = prepared.gear().pair();
        VirtualBlade.cast(owner, owner.getLookAngle(), target, 5.5D, WaterPressureMath.damage(pair.secondaryLevel(), pair.primaryLevel()),
            8, victim -> victim.isInWater() && MeleeAttackThemes.eligible(owner, victim, attackId(pair)), ParticleTypes.BUBBLE);
        CompendiumDiscoveryTracker.recordDiscovery(owner, pair.primaryId(), pair.secondaryId());
    }
    private static void burst(ServerPlayer owner, LivingEntity target, Gear gear) {
        ServerLevel level = owner.level(); FusedPair pair = gear.pair();
        Vec3 center = target.getBoundingBox().getCenter(); double radius = WaterPressureMath.radius(pair.secondaryLevel());
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(center, radius * 2, radius * 2, radius * 2),
            entity -> entity != target && entity.isInWater() && MeleeAttackThemes.eligible(owner, entity, attackId(pair)));
        victims.sort(Comparator.comparingDouble(victim -> victim.distanceToSqr(center)));
        boolean previous = Boolean.TRUE.equals(TransmuteRuntimeSupport.IN_CUSTOM_COMBAT.get());
        TransmuteRuntimeSupport.IN_CUSTOM_COMBAT.set(true);
        try {
            int count = 0;
            for (LivingEntity victim : victims) {
                Vec3 point = victim.getBoundingBox().getCenter();
                if (center.distanceToSqr(point) > radius * radius || !VirtualCombatSight.clear(level, owner, center, point)
                    || !victim.isInWater() || !MeleeAttackThemes.eligible(owner, victim, attackId(pair))) continue;
                if (++count > 16) break;
                victim.hurtServer(level, level.damageSources().playerAttack(owner), WaterPressureMath.damage(pair.primaryLevel(), pair.secondaryLevel()));
            }
        } finally { TransmuteRuntimeSupport.IN_CUSTOM_COMBAT.set(previous); }
        level.sendParticles(ParticleTypes.BUBBLE_POP, center.x, center.y, center.z, 24, radius * 0.25D, radius * 0.2D, radius * 0.25D, 0.03D);
        CompendiumDiscoveryTracker.recordDiscovery(owner, pair.primaryId(), pair.secondaryId());
    }
    public static void tickServer(MinecraftServer server) {
        for (Bubble bubble : List.copyOf(BUBBLES)) {
            ServerLevel level = server.getLevel(bubble.dimension()); ServerPlayer owner = server.getPlayerList().getPlayer(bubble.owner());
            Entity entity = level == null ? null : level.getEntity(bubble.target());
            if (level == null || owner == null || owner.level() != level || !ready(owner) || !owns(owner, bubble.gear())
                || !(entity instanceof LivingEntity target) || !target.isInWater()
                || !MeleeAttackThemes.eligible(owner, target, attackId(bubble.gear().pair())) || level.getGameTime() >= bubble.expires()) {
                BUBBLES.remove(bubble); continue;
            }
            if (level.getGameTime() % 10 == 0) level.sendParticles(ParticleTypes.BUBBLE, target.getX(), target.getY(0.5D), target.getZ(),
                3, 0.15D, 0.2D, 0.15D, 0.01D);
        }
        PREPARED.entrySet().removeIf(entry -> {
            Prepared prepared = entry.getValue(); ServerLevel level = server.getLevel(prepared.dimension());
            ServerPlayer owner = server.getPlayerList().getPlayer(prepared.owner());
            return level == null || owner == null || owner.level() != level || !ready(owner) || level.getGameTime() >= prepared.expires()
                || owner.getItemBySlot(EquipmentSlot.HEAD) != prepared.gear().stack() || !hasPair(prepared.gear());
        });
    }
    public static void clearPlayer(UUID id) { BUBBLES.removeIf(bubble -> bubble.owner().equals(id)); PREPARED.remove(id); }
    public static void clearServer() { BUBBLES.clear(); PREPARED.clear(); }
}

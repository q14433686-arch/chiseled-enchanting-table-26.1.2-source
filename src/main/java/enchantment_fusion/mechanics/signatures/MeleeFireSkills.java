package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
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
import org.jetbrains.annotations.Nullable;

/** 43.1: one shared real-hit/target-mark adapter for the three attack enchantments. */
public final class MeleeFireSkills {
    public static final String FIRE = "minecraft:fire_aspect";
    private static final int MAX_MARKS = 512, MARK_TICKS = 120, MAX_FIRE_TICKS = 200;
    private static final Set<FusionCarrier> WEAPONS = Set.of(FusionCarrier.SWORD, FusionCarrier.AXE, FusionCarrier.SPEAR, FusionCarrier.MACE);
    private record Gear(ItemStack stack, FusedPair pair) {}
    private record Pending(Gear gear, UUID target, ResourceKey<Level> dimension, DamageSource source, long tick) {}
    private static final OwnedTargetMarks MARKS = new OwnedTargetMarks(MAX_MARKS, MeleeFireSkills::applies);
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final ThreadLocal<Boolean> REPLAYING = ThreadLocal.withInitial(() -> false);
    private MeleeFireSkills() {}

    public static void init() {
        ConfirmedMelee.register(MeleeFireSkills::onHit);
        MeleeAttackThemes.registerPrimaryTemplate("melee_fire", (pair, stack) -> attackFirst(pair) && applies(pair, stack));
    }
    public static boolean attackFirst(@Nullable FusedPair pair) {
        return pair != null && MeleeAttackThemes.isAttack(pair.primaryId()) && FIRE.equals(pair.secondaryId());
    }
    public static boolean fireFirst(@Nullable FusedPair pair) {
        return pair != null && FIRE.equals(pair.primaryId()) && MeleeAttackThemes.isAttack(pair.secondaryId());
    }
    /** Exact signatures and secondary wildcards always win over this family. */
    public static boolean matches(@Nullable FusedPair pair) {
        return (attackFirst(pair) || fireFirst(pair)) && SignatureCatalog.resolve(pair).isEmpty();
    }
    public static boolean supportsCarrier(Set<FusionCarrier> roles) { return roles != null && roles.stream().anyMatch(WEAPONS::contains); }
    public static boolean applies(@Nullable FusedPair pair, @Nullable ItemStack stack) {
        return matches(pair) && stack != null && !stack.isEmpty() && supportsCarrier(FusionCarrier.classify(stack));
    }
    private static @Nullable Gear select(@Nullable ItemStack stack, boolean reverse) {
        if (stack == null || stack.isEmpty()) return null;
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if ((reverse ? fireFirst(pair) : attackFirst(pair)) && applies(pair, stack)) return new Gear(stack, pair);
        return null;
    }
    private static boolean ready(ServerPlayer owner) { return owner.isAlive() && !owner.isSpectator(); }
    private static boolean owns(ServerPlayer owner, Gear gear) {
        return owner.getMainHandItem() == gear.stack() && applies(gear.pair(), gear.stack())
            && gear.stack().getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().contains(gear.pair());
    }
    private static String channel(boolean reverse) { return reverse ? "fire-first" : "attack-first"; }
    private static void putMark(ServerPlayer owner, Gear gear, LivingEntity target, boolean reverse) {
        if (MARKS.put(owner, channel(reverse), EquipmentSlot.MAINHAND, gear.stack(), gear.pair(), target, MARK_TICKS))
            owner.level().sendParticles(ParticleTypes.FLAME, target.getX(), target.getY(0.6D), target.getZ(), 4, 0.12D, 0.15D, 0.12D, 0.01D);
    }
    private static void onHit(ConfirmedMelee.Hit hit) {
        ServerPlayer owner = hit.player(); LivingEntity target = hit.target(); ItemStack weapon = hit.weapon();
        if (hit.slot() != EquipmentSlot.MAINHAND || !hit.eligibleBefore() || !ready(owner) || owner.getMainHandItem() != weapon) return;
        Gear attack = select(weapon, false);
        if (attack != null && hit.full() && target.isAlive() && target.isOnFire()
            && MeleeAttackThemes.matchesTarget(attack.pair().primaryId(), target) && owns(owner, attack)) {
            putMark(owner, attack, target, false);
        }
        Gear fire = select(weapon, true);
        if (fire == null || !owns(owner, fire) || !MeleeAttackThemes.matchesTarget(fire.pair().secondaryId(), target)) return;
        OwnedTargetMarks.Mark mark = MARKS.get(owner, channel(true));
        if (mark != null && mark.stack() == weapon && mark.pair().equals(fire.pair())
            && mark.target().equals(target.getUUID()) && target.isOnFire() && MARKS.remove(owner, channel(true), mark)) {
            PENDING.remove(owner.getUUID());
            float damage = Math.min(8.0F, 2.0F + 0.35F * Math.clamp(fire.pair().primaryLevel(), 1, 10)
                + 0.25F * Math.clamp(fire.pair().secondaryLevel(), 1, 10));
            VirtualBlade.cast(owner, owner.getLookAngle(), target, 6.0D, damage, 6,
                victim -> MeleeAttackThemes.eligible(owner, victim, fire.pair().secondaryId()), ParticleTypes.FLAME);
            CompendiumDiscoveryTracker.recordDiscovery(owner, fire.pair().primaryId(), fire.pair().secondaryId());
        } else if (target.isAlive()) {
            // The vanilla fire effect runs *after* the real damage call. Confirm it in afterPostAttack.
            if (PENDING.containsKey(owner.getUUID()) || PENDING.size() < MAX_MARKS / 2)
                PENDING.put(owner.getUUID(), new Pending(fire, target.getUUID(), owner.level().dimension(), hit.source(), owner.level().getGameTime()));
        }
    }
    /** True only when the actively held weapon can prepare a burning-target extraction. */
    public static boolean hasEquipped(ServerPlayer owner) { return select(owner.getMainHandItem(), false) != null; }
    public static boolean activate(ServerPlayer owner) {
        OwnedTargetMarks.Mark mark = MARKS.get(owner, channel(false));
        if (mark == null) return false;
        Entity entity = owner.level().getEntity(mark.target());
        if (!(entity instanceof LivingEntity target) || !target.isAlive() || !target.isOnFire()
            || !MeleeAttackThemes.eligible(owner, target, mark.pair().primaryId())
            || owner.distanceToSqr(target) > 64.0D
            || !VirtualCombatSight.clear(owner.level(), owner, owner.getEyePosition(), target.getBoundingBox().getCenter())) return false;
        int remaining = target.getRemainingFireTicks();
        if (remaining <= 0 || !MARKS.remove(owner, channel(false), mark)) return false;
        target.clearFire();
        float damage = 2.0F + 0.3F * Math.clamp(mark.pair().primaryLevel(), 1, 10)
            + 0.3F * Math.clamp(mark.pair().secondaryLevel(), 1, 10) + Math.min(MAX_FIRE_TICKS, remaining) / 80.0F;
        VirtualBlade.cast(owner, owner.getLookAngle(), null, 6.0D, damage, 6,
            victim -> MeleeAttackThemes.eligible(owner, victim, mark.pair().primaryId()), ParticleTypes.FLAME);
        CompendiumDiscoveryTracker.recordDiscovery(owner, mark.pair().primaryId(), mark.pair().secondaryId());
        owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.melee_fire_extracted"), true);
        return true;
    }
    /** The existing glazed-strike adapter performs its own non-native ignition during the damage callback. */
    private static boolean glazedAlreadyOwnsFire(ItemStack weapon) {
        for (FusedPair pair : weapon.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if (SignatureCatalog.resolve(pair).map(entry -> GlazedStrikeEffect.applies(pair, weapon, entry.id())).orElse(false)) return true;
        return false;
    }
    /** Observe the actual Fire Aspect invocation; never reroll it to infer its effect. */
    public static void observePrimaryFire(Enchantment enchantment, ServerLevel level, int enchantmentLevel, EnchantedItemInUse item,
                                          EnchantmentTarget side, Entity victim, DamageSource source, int fireBefore) {
        if (side != EnchantmentTarget.ATTACKER || item.inSlot() != EquipmentSlot.MAINHAND
            || !(item.owner() instanceof ServerPlayer owner) || !(victim instanceof LivingEntity target) || owner.level() != level
            || !FusionStationOperations.lookupEnchantment(level.registryAccess(), FIRE).map(h -> h.value() == enchantment).orElse(false)) return;
        Pending pending = PENDING.get(owner.getUUID());
        if (pending == null || pending.source() != source || pending.tick() != level.getGameTime() || !pending.target().equals(target.getUUID())
            || !pending.dimension().equals(level.dimension()) || item.itemStack() != pending.gear().stack() || !owns(owner, pending.gear())
            || enchantmentLevel < pending.gear().pair().primaryLevel() || !target.isAlive()
            || target.getRemainingFireTicks() <= Math.max(0, fireBefore) || !target.isOnFire()
            || !MeleeAttackThemes.eligible(owner, target, pending.gear().pair().secondaryId())) return;
        putMark(owner, pending.gear(), target, true);
    }
    /** Called once after vanilla's post-attack effects and existing Fire Sweep replay. */
    public static void afterPostAttack(ServerLevel level, Player player, ItemStack weapon, Entity victim, DamageSource source) {
        if (!(player instanceof ServerPlayer owner) || !(victim instanceof LivingEntity target) || owner.level() != level
            || owner.getMainHandItem() != weapon || source.getEntity() != owner || source.getDirectEntity() != owner) return;
        Gear gear = select(weapon, true);
        if (gear == null) {
            FusedPair harvest = EmberHarvestSkills.selected(weapon);
            if (harvest != null) gear = new Gear(weapon, harvest);
        }
        if (gear != null && (owns(owner, gear) || EmberHarvestSkills.applies(gear.pair(), weapon)) && !Boolean.TRUE.equals(REPLAYING.get())) {
            Holder<Enchantment> primary = FusionStationOperations.lookupEnchantment(level.registryAccess(), FIRE).orElse(null);
            boolean nativePresent = primary != null && weapon.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(primary) > 0;
            if (primary != null && !nativePresent && !FireSweepSkills.replaysFirePrimary(weapon) && !glazedAlreadyOwnsFire(weapon)) {
                REPLAYING.set(true);
                try { primary.value().doPostAttack(level, gear.pair().primaryLevel(), new EnchantedItemInUse(weapon, EquipmentSlot.MAINHAND, owner),
                    EnchantmentTarget.ATTACKER, victim, source); }
                finally { REPLAYING.remove(); }
            }
        }
        Pending pending = PENDING.get(owner.getUUID());
        if (pending != null && pending.source() == source && pending.target().equals(target.getUUID())) PENDING.remove(owner.getUUID(), pending);
    }
    public static void tickServer(MinecraftServer server) {
        MARKS.tickServer(server);
        PENDING.entrySet().removeIf(entry -> {
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
            return owner == null || owner.level().getGameTime() > entry.getValue().tick();
        });
    }
    public static void clearPlayer(UUID id) { MARKS.clearPlayer(id); PENDING.remove(id); }
    public static void clearServer() { MARKS.clear(); PENDING.clear(); REPLAYING.remove(); }
}

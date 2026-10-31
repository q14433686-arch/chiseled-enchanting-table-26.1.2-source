package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
import net.minecraft.tags.EntityTypeTags;
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
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.jetbrains.annotations.Nullable;

/**
 * Selected 42.3 family: three attack themes × two ordered sneak directions.
 * Exact signatures (including data-pack changes) and secondary wildcards always win.
 */
public final class StealthBladeSkills {
    public static final String SHARP = "minecraft:sharpness", SMITE = "minecraft:smite";
    public static final String BANE = "minecraft:bane_of_arthropods", SNEAK = "minecraft:swift_sneak";
    private static final Set<String> ATTACK = Set.of(SHARP, SMITE, BANE);
    private static final Set<FusionCarrier> WEAPONS = Set.of(FusionCarrier.SWORD, FusionCarrier.AXE,
        FusionCarrier.SPEAR, FusionCarrier.MACE, FusionCarrier.TRIDENT);
    private static final int MAX_MARKS = 256, MAX_CHARGES = 256;
    private static final long MARK_TICKS = 120, READY_TICKS = 100, CHARGE_TICKS = 120;
    private static final ThreadLocal<Boolean> PRIMARY_REPLAY = ThreadLocal.withInitial(() -> false);
    private record Gear(ItemStack stack, FusedPair pair) {}
    private static final class Mark {
        final UUID owner; final ResourceKey<Level> dimension; final Gear gear; final Vec3 position;
        Vec3 last; long expires; boolean ready;
        Mark(ServerPlayer player, Gear gear, Vec3 position, long expires) {
            this.owner = player.getUUID(); this.dimension = player.level().dimension(); this.gear = gear;
            this.position = position; this.last = player.position(); this.expires = expires;
        }
    }
    private static final class Charge {
        final UUID owner; final ResourceKey<Level> dimension; final Gear gear;
        Vec3 last; double distance; long expires; boolean ready;
        Charge(ServerPlayer player, Gear gear, long expires) {
            this.owner = player.getUUID(); this.dimension = player.level().dimension(); this.gear = gear;
            this.last = player.position(); this.expires = expires;
        }
    }
    private static final List<Mark> MARKS = new ArrayList<>();
    private static final List<Charge> CHARGES = new ArrayList<>();
    private StealthBladeSkills() {}

    public static void init() { ConfirmedMelee.register(StealthBladeSkills::onHit); }
    public static boolean isAttackFirst(@Nullable FusedPair pair) {
        return pair != null && ATTACK.contains(pair.primaryId()) && SNEAK.equals(pair.secondaryId());
    }
    public static boolean isSneakFirst(@Nullable FusedPair pair) {
        return pair != null && SNEAK.equals(pair.primaryId()) && ATTACK.contains(pair.secondaryId());
    }
    public static boolean matches(@Nullable FusedPair pair) {
        return (isAttackFirst(pair) || isSneakFirst(pair)) && SignatureCatalog.resolve(pair).isEmpty();
    }
    public static boolean supportsCarrier(FusedPair pair, Set<FusionCarrier> roles) {
        return roles != null && (isAttackFirst(pair) && roles.stream().anyMatch(WEAPONS::contains)
            || isSneakFirst(pair) && roles.contains(FusionCarrier.LEG_ARMOR));
    }
    public static boolean applies(@Nullable FusedPair pair, @Nullable ItemStack stack) {
        return pair != null && stack != null && !stack.isEmpty() && matches(pair) && supportsCarrier(pair, FusionCarrier.classify(stack));
    }
    private static boolean hasPair(Gear gear) {
        return !gear.stack().isEmpty() && gear.stack().getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY)
            .pairs().contains(gear.pair()) && applies(gear.pair(), gear.stack());
    }
    private static boolean owns(ServerPlayer player, Gear gear) {
        if (!hasPair(gear)) return false;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) if (player.getInventory().getItem(i) == gear.stack()) return true;
        return false;
    }
    private static boolean ready(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator();
    }
    private static boolean category(FusedPair pair, LivingEntity target) {
        String attack = isAttackFirst(pair) ? pair.primaryId() : pair.secondaryId();
        if (SHARP.equals(attack)) return true;
        var type = BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(target.getType());
        return SMITE.equals(attack) && type.is(EntityTypeTags.UNDEAD)
            || BANE.equals(attack) && type.is(EntityTypeTags.ARTHROPOD);
    }
    private static boolean eligible(ServerPlayer owner, LivingEntity target, FusedPair pair) {
        return target.level() == owner.level() && category(pair, target) && LoyaltyGuard.eligibleTarget(owner, target)
            && owner.level().mayInteract(owner, target.blockPosition()) && (!(target instanceof Player other) || other.canHarmPlayer(owner));
    }
    private static List<Gear> equipped(ItemStack stack, boolean attackFirst) {
        if (stack == null || stack.isEmpty()) return List.of();
        List<Gear> result = new ArrayList<>();
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs()) {
            if ((attackFirst ? isAttackFirst(pair) : isSneakFirst(pair)) && applies(pair, stack)) result.add(new Gear(stack, pair));
        }
        return result;
    }
    private static @Nullable Mark findMark(ServerPlayer owner, Gear gear) {
        for (Mark mark : MARKS) if (mark.owner.equals(owner.getUUID()) && mark.dimension.equals(owner.level().dimension())
            && mark.gear.stack() == gear.stack() && mark.gear.pair().equals(gear.pair())) return mark;
        return null;
    }
    private static @Nullable Charge findCharge(ServerPlayer owner, Gear gear) {
        for (Charge charge : CHARGES) if (charge.owner.equals(owner.getUUID()) && charge.dimension.equals(owner.level().dimension())
            && charge.gear.stack() == gear.stack() && charge.gear.pair().equals(gear.pair())) return charge;
        return null;
    }
    private static void onHit(ConfirmedMelee.Hit hit) {
        ServerPlayer owner = hit.player(); LivingEntity target = hit.target();
        if (!ready(owner) || hit.slot() != EquipmentSlot.MAINHAND || !hit.eligibleBefore()) return;
        long now = owner.level().getGameTime();
        if (owner.getMainHandItem() == hit.weapon()) {
            for (Gear gear : equipped(hit.weapon(), true)) {
                if (!category(gear.pair(), target) || !owns(owner, gear)) continue;
                Mark mark = findMark(owner, gear);
                if (mark != null && mark.ready && now < mark.expires) {
                    MARKS.remove(mark);
                    release(owner, gear, target);
                } else if (hit.full()) {
                    if (mark != null) MARKS.remove(mark);
                    if (MARKS.size() < MAX_MARKS) {
                        MARKS.add(new Mark(owner, gear, target.position(), now + MARK_TICKS));
                        owner.level().sendParticles(ParticleTypes.ENCHANT, target.getX(), target.getY(0.2D), target.getZ(),
                            10, 0.25D, 0.05D, 0.25D, 0.01D);
                        CompendiumDiscoveryTracker.recordDiscovery(owner, gear.pair().primaryId(), gear.pair().secondaryId());
                    }
                }
            }
        }
        ItemStack legs = owner.getItemBySlot(EquipmentSlot.LEGS);
        for (Gear gear : equipped(legs, false)) {
            if (!category(gear.pair(), target) || !hasPair(gear)) continue;
            Charge charge = findCharge(owner, gear);
            if (charge != null && charge.ready && now < charge.expires) {
                CHARGES.remove(charge);
                release(owner, gear, target);
            }
        }
    }
    private static void release(ServerPlayer owner, Gear gear, LivingEntity directTarget) {
        FusedPair pair = gear.pair(); int attack = isAttackFirst(pair) ? pair.primaryLevel() : pair.secondaryLevel();
        int sneak = isAttackFirst(pair) ? pair.secondaryLevel() : pair.primaryLevel();
        VirtualBlade.cast(owner, owner.getLookAngle(), directTarget, StealthBladeMath.range(sneak),
            StealthBladeMath.damage(attack, sneak), 8, target -> eligible(owner, target, pair), ParticleTypes.SWEEP_ATTACK);
        CompendiumDiscoveryTracker.recordDiscovery(owner, pair.primaryId(), pair.secondaryId());
    }

    /** Existing native primaries stay native. Replay only a missing primary on an allowed cross-carrier. */
    public static float nonNativeDamage(ServerLevel level, ItemStack weapon, Entity victim, DamageSource source, float value) {
        if (Boolean.TRUE.equals(PRIMARY_REPLAY.get()) || !(source.getEntity() instanceof ServerPlayer owner)
            || source.getDirectEntity() != owner || owner.getMainHandItem() != weapon) return value;
        Set<String> processed = new HashSet<>(); float result = value;
        for (Gear gear : equipped(weapon, true)) {
            String id = gear.pair().primaryId();
            if (!processed.add(id) || replayedEarlier(weapon, id)) continue;
            Holder<Enchantment> enchantment = FusionStationOperations.lookupEnchantment(level.registryAccess(), id).orElse(null);
            if (enchantment == null || weapon.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(enchantment) > 0) continue;
            MutableFloat modified = new MutableFloat(result); PRIMARY_REPLAY.set(true);
            try { enchantment.value().modifyDamage(level, gear.pair().primaryLevel(), weapon, victim, source, modified); }
            finally { PRIMARY_REPLAY.remove(); }
            if (Float.isFinite(modified.floatValue())) result = modified.floatValue();
        }
        return result;
    }
    public static void nonNativePostAttack(ServerLevel level, Player player, ItemStack weapon, Entity victim, DamageSource source) {
        if (Boolean.TRUE.equals(PRIMARY_REPLAY.get()) || !(player instanceof ServerPlayer owner) || owner.getMainHandItem() != weapon
            || source.getEntity() != owner || source.getDirectEntity() != owner) return;
        Set<String> processed = new HashSet<>();
        for (Gear gear : equipped(weapon, true)) {
            String id = gear.pair().primaryId();
            if (!processed.add(id) || replayedEarlier(weapon, id)) continue;
            Holder<Enchantment> enchantment = FusionStationOperations.lookupEnchantment(level.registryAccess(), id).orElse(null);
            if (enchantment == null || weapon.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(enchantment) > 0) continue;
            PRIMARY_REPLAY.set(true);
            try { enchantment.value().doPostAttack(level, gear.pair().primaryLevel(), new EnchantedItemInUse(weapon, EquipmentSlot.MAINHAND, owner),
                EnchantmentTarget.ATTACKER, victim, source); }
            finally { PRIMARY_REPLAY.remove(); }
        }
    }
    private static boolean replayedEarlier(ItemStack weapon, String primary) {
        return SoulThreadSkills.replaysPrimary(weapon, primary) || SoulBladeSkills.replaysPrimary(weapon, primary)
            || SegmentThreadSkills.replaysPrimary(weapon, primary);
    }

    public static void tickServer(MinecraftServer server) {
        for (Mark mark : List.copyOf(MARKS)) {
            ServerPlayer owner = server.getPlayerList().getPlayer(mark.owner);
            ServerLevel level = server.getLevel(mark.dimension);
            if (owner == null || level == null || owner.level() != level || !ready(owner) || !owns(owner, mark.gear)
                || level.getGameTime() >= mark.expires) { MARKS.remove(mark); continue; }
            Vec3 current = owner.position(), previous = mark.last; mark.last = current;
            if (!mark.ready && owner.isShiftKeyDown()
                && StealthBladeMath.crosses(previous.x, previous.y, previous.z, current.x, current.y, current.z,
                    mark.position.x, mark.position.y, mark.position.z)
                && VirtualCombatSight.clear(level, owner, current.add(0, 0.5D, 0), mark.position.add(0, 0.5D, 0))) {
                mark.ready = true; mark.expires = level.getGameTime() + READY_TICKS;
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, current.x, current.y + 0.5D, current.z, 3, 0.15D, 0.15D, 0.15D, 0);
            }
            if (!mark.ready && level.getGameTime() % 10 == 0) {
                level.sendParticles(ParticleTypes.ENCHANT, mark.position.x, mark.position.y + 0.1D, mark.position.z,
                    3, 0.2D, 0.02D, 0.2D, 0);
            }
        }
        for (Charge charge : List.copyOf(CHARGES)) {
            ServerPlayer owner = server.getPlayerList().getPlayer(charge.owner); ServerLevel level = server.getLevel(charge.dimension);
            if (owner == null || level == null || owner.level() != level || !ready(owner)
                || owner.getItemBySlot(EquipmentSlot.LEGS) != charge.gear.stack() || !hasPair(charge.gear)
                || level.getGameTime() >= charge.expires) CHARGES.remove(charge);
        }
        for (ServerPlayer owner : server.getPlayerList().getPlayers()) {
            if (!ready(owner)) continue;
            ItemStack legs = owner.getItemBySlot(EquipmentSlot.LEGS); long now = owner.level().getGameTime();
            for (Gear gear : equipped(legs, false)) {
                Charge charge = findCharge(owner, gear);
                if (charge == null) {
                    if (!owner.isShiftKeyDown() || CHARGES.size() >= MAX_CHARGES) continue;
                    charge = new Charge(owner, gear, now + CHARGE_TICKS); CHARGES.add(charge);
                }
                Vec3 current = owner.position(), delta = current.subtract(charge.last); charge.last = current;
                if (!charge.ready && !owner.isShiftKeyDown()) charge.distance = 0;
                if (!charge.ready && owner.isShiftKeyDown()) {
                    double step = StealthBladeMath.step(delta.x, delta.y, delta.z);
                    if (step > 0) {
                        charge.distance = Math.min(StealthBladeMath.READY_DISTANCE, charge.distance + step);
                        charge.expires = now + CHARGE_TICKS;
                        if (charge.distance >= StealthBladeMath.READY_DISTANCE) {
                            charge.ready = true; charge.expires = now + READY_TICKS;
                            owner.level().sendParticles(ParticleTypes.SWEEP_ATTACK, current.x, current.y + 0.5D, current.z,
                                3, 0.15D, 0.15D, 0.15D, 0);
                        }
                    }
                }
            }
        }
    }
    public static void clearPlayer(UUID player) {
        MARKS.removeIf(mark -> mark.owner.equals(player)); CHARGES.removeIf(charge -> charge.owner.equals(player));
    }
    public static void clearServer() { MARKS.clear(); CHARGES.clear(); }
}

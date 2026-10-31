package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.config.FusionConfigStore;
import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mechanics.ExtraBlockHarvest;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.jetbrains.annotations.Nullable;

/** Density/Breach is deliberately one order-independent mechanic, not two renamed signatures. */
public final class HeavyCrushSkill {
    public static final String FORWARD = "armor_shatter";
    public static final String REVERSE = "armor_pressure";
    private static final Set<FusionCarrier> WEAPONS = Set.of(FusionCarrier.SWORD, FusionCarrier.AXE,
        FusionCarrier.SPEAR, FusionCarrier.MACE, FusionCarrier.TRIDENT, FusionCarrier.BOW, FusionCarrier.CROSSBOW);
    private static final Set<FusionCarrier> MELEE = Set.of(FusionCarrier.SWORD, FusionCarrier.AXE,
        FusionCarrier.SPEAR, FusionCarrier.MACE, FusionCarrier.TRIDENT);
    private static final Identifier OWNER_SLOW = EnchantmentFusion.id("heavy_crush_owner_slow");
    private static final Identifier TARGET_GRAVITY = EnchantmentFusion.id("heavy_crush_target_gravity");
    private static final AttributeModifier OWNER_MODIFIER = new AttributeModifier(OWNER_SLOW, -0.15D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    private static final AttributeModifier TARGET_MODIFIER = new AttributeModifier(TARGET_GRAVITY, 0.35D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    private static final int MAX_PENDING = 128;

    private record Weighted(LivingEntity target, ServerLevel level, long expires) {}
    private record Crater(UUID owner, UUID target, ResourceKey<Level> dimension, Vec3 position, ItemStack tool) {}
    private static final Map<UUID, Weighted> WEIGHTED = new HashMap<>();
    private static final List<Crater> CRATERS = new ArrayList<>();
    private record Replay(Enchantment enchantment, ItemStack weapon, Entity victim, DamageSource source) {}
    private static final ThreadLocal<Swing> SWING = new ThreadLocal<>();
    private static final ThreadLocal<Replay> REPLAYING = new ThreadLocal<>();

    public static final class Swing {
        private final @Nullable Swing previous;
        private final Player player;
        private final Entity target;
        private final ItemStack weapon;
        private final float charge;
        private Swing(@Nullable Swing previous, Player player, Entity target, ItemStack weapon, float charge) {
            this.previous = previous; this.player = player; this.target = target; this.weapon = weapon; this.charge = charge;
        }
    }
    public static final class Hit {
        private final ServerPlayer owner;
        private final LivingEntity target;
        private final FusedPair pair;
        private final float before;
        private final double impact;
        private final Vec3 position;
        private Hit(ServerPlayer owner, LivingEntity target, FusedPair pair, double impact) {
            this.owner = owner; this.target = target; this.pair = pair; this.impact = impact;
            this.before = target.getHealth() + target.getAbsorptionAmount(); this.position = target.position();
        }
    }
    private HeavyCrushSkill() {}

    public static boolean matches(@Nullable FusedPair pair, String signature) {
        if (HeavyCrushMath.levels(pair) == null) return false;
        return (HeavyCrushMath.DENSITY.equals(pair.primaryId()) && FORWARD.equals(signature))
            || (HeavyCrushMath.BREACH.equals(pair.primaryId()) && REVERSE.equals(signature));
    }
    public static boolean supportsCarrier(Set<FusionCarrier> roles) {
        return roles != null && roles.stream().anyMatch(WEAPONS::contains);
    }
    public static boolean applies(@Nullable FusedPair pair, @Nullable ItemStack stack, String signature) {
        return matches(pair, signature) && stack != null && !stack.isEmpty() && supportsCarrier(FusionCarrier.classify(stack));
    }
    public static @Nullable FusedPair selected(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty() || !supportsCarrier(FusionCarrier.classify(stack))) return null;
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs()) {
            if (SignatureCatalog.resolve(pair).map(entry -> matches(pair, entry.id())).orElse(false)) return pair;
        }
        return null;
    }
    public static boolean isMelee(@Nullable ItemStack stack) {
        return selected(stack) != null && FusionCarrier.classify(stack).stream().anyMatch(MELEE::contains);
    }
    public static float attackDelay(Player player, float original) {
        return isMelee(player.getMainHandItem()) ? original * 1.5F : original;
    }

    private static boolean eligibleSource(DamageSource source, Entity victim) {
        if (!(source.getEntity() instanceof ServerPlayer owner) || !(victim instanceof LivingEntity target)
            || !GlazedStrikeEffect.canAffect(owner, target)) return false;
        Entity direct = source.getDirectEntity();
        return direct == owner || (direct instanceof Projectile projectile && projectile.getOwner() == owner);
    }
    private static @Nullable Holder<Enchantment> enchant(ServerLevel level, String id) {
        return FusionStationOperations.lookupEnchantment(level.registryAccess(), id).orElse(null);
    }
    private static int nativeLevel(ItemStack stack, @Nullable Holder<Enchantment> holder) {
        return holder == null ? 0 : stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(holder);
    }

    private static boolean sameReplay(@Nullable Replay replay, ItemStack weapon, Entity victim, DamageSource source) {
        return replay != null && replay.weapon() == weapon && replay.victim() == victim && replay.source() == source;
    }
    private static void restoreReplay(@Nullable Replay previous) {
        if (previous == null) REPLAYING.remove(); else REPLAYING.set(previous);
    }

    /** Defer the two native effects so both orders replay them exactly once at the same point. */
    public static boolean deferNative(Enchantment enchantment, ServerLevel level, ItemStack weapon, Entity victim,
                                      DamageSource source, String id) {
        Replay replay = REPLAYING.get();
        if ((sameReplay(replay, weapon, victim, source) && replay.enchantment() == enchantment)
            || selected(weapon) == null || !eligibleSource(source, victim)) return false;
        Holder<Enchantment> holder = enchant(level, id);
        return holder != null && holder.value() == enchantment;
    }

    /** Apply the actual datapack Breach effect exactly once, then add Density pressure. */
    public static float armorEffect(ServerLevel level, ItemStack weapon, Entity victim, DamageSource source, float vanilla) {
        FusedPair pair = selected(weapon);
        if (pair == null || !eligibleSource(source, victim) || sameReplay(REPLAYING.get(), weapon, victim, source)) return vanilla;
        HeavyCrushMath.Levels levels = HeavyCrushMath.levels(pair);
        if (levels == null) return vanilla;
        Holder<Enchantment> breach = enchant(level, HeavyCrushMath.BREACH);
        MutableFloat value = new MutableFloat(vanilla);
        if (breach != null) {
            Replay previous = REPLAYING.get();
            REPLAYING.set(new Replay(breach.value(), weapon, victim, source));
            try {
                breach.value().modifyArmorEffectivness(level, Math.max(levels.breach(), nativeLevel(weapon, breach)), weapon, victim, source, value);
            } finally { restoreReplay(previous); }
        }
        return value.floatValue() - HeavyCrushMath.densityArmorReduction(levels.density());
    }

    /** On an actual mace smash, apply Density once using the current datapack effect. */
    public static float fallEffect(ServerLevel level, ItemStack weapon, Entity victim, DamageSource source, float vanilla) {
        FusedPair pair = selected(weapon);
        if (pair == null || !eligibleSource(source, victim) || sameReplay(REPLAYING.get(), weapon, victim, source)) return vanilla;
        HeavyCrushMath.Levels levels = HeavyCrushMath.levels(pair);
        Holder<Enchantment> density = enchant(level, HeavyCrushMath.DENSITY);
        if (levels == null || density == null) return vanilla;
        MutableFloat value = new MutableFloat(vanilla);
        Replay previous = REPLAYING.get();
        REPLAYING.set(new Replay(density.value(), weapon, victim, source));
        try {
            density.value().modifyFallBasedDamage(level, Math.max(levels.density(), nativeLevel(weapon, density)), weapon, victim, source, value);
        } finally { restoreReplay(previous); }
        return value.floatValue();
    }

    public static Swing beginSwing(Player player, Entity target) {
        Swing swing = new Swing(SWING.get(), player, target, player.getWeaponItem(), player.getAttackStrengthScale(0.5F));
        SWING.set(swing); // unrelated nested swings mask the parent
        return swing;
    }
    public static void endSwing(Swing swing) {
        if (swing.previous == null) SWING.remove(); else SWING.set(swing.previous);
    }
    public static @Nullable Hit beginMelee(Player player, ItemStack weapon, Entity target, DamageSource source, boolean stab) {
        if (!(player instanceof ServerPlayer owner) || !(target instanceof LivingEntity living) || !eligibleSource(source, target)) return null;
        FusedPair pair = selected(weapon);
        if (pair == null || !isMelee(weapon)) return null;
        float charge;
        if (stab) {
            // Kinetic spears are gated by vanilla's own damage-ready delay before this call.
            charge = owner.isUsingItem() && owner.getUseItem() == weapon ? 1.0F : owner.getAttackStrengthScale(0.5F);
        } else {
            Swing swing = SWING.get();
            if (swing == null || swing.player != player || swing.target != target || swing.weapon != weapon) return null;
            charge = swing.charge;
        }
        return new Hit(owner, living, pair, HeavyCrushMath.meleeImpact(charge, owner.fallDistance, owner.getDeltaMovement().length()));
    }
    public static @Nullable Hit beginProjectile(Projectile projectile, Entity target, DamageSource source) {
        ItemStack weapon = projectile.getWeaponItem();
        // Some version/mod combinations expose a thrown trident only through its pickup stack.
        if ((weapon == null || weapon.isEmpty()) && projectile instanceof net.minecraft.world.entity.projectile.arrow.ThrownTrident trident) {
            weapon = trident.getPickupItemStackOrigin();
        }
        return beginProjectile(projectile, weapon, target, source);
    }
    public static @Nullable Hit beginProjectile(Projectile projectile, @Nullable ItemStack weapon, Entity target, DamageSource source) {
        if (!(projectile.getOwner() instanceof ServerPlayer owner) || !(target instanceof LivingEntity living)
            || !eligibleSource(source, target) || PhantomVolley.isGhost(projectile)) return null;
        FusedPair pair = selected(weapon);
        return pair == null ? null : new Hit(owner, living, pair, HeavyCrushMath.projectileImpact(projectile.getDeltaMovement().length()));
    }
    public static void endHit(@Nullable Hit hit, boolean succeeded) {
        if (hit == null || !succeeded || hit.owner.level() != hit.target.level()) return;
        float actual = Math.max(0.0F, hit.before - hit.target.getHealth() - hit.target.getAbsorptionAmount());
        if (!(actual > 0.0F)) return;
        HeavyCrushMath.Levels levels = HeavyCrushMath.levels(hit.pair);
        if (levels == null) return;
        ServerLevel level = hit.owner.level();
        CompendiumDiscoveryTracker.recordDiscovery(hit.owner, hit.pair.primaryId(), hit.pair.secondaryId());
        if (hit.target.isAlive()) {
            hit.target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, Math.min(2, (levels.density() - 1) / 2)));
            addWeight(hit.target, level);
        }
        double minDamage = FusionConfigStore.signatureInt(FORWARD, "crater_min_damage", 6, 1, 1000);
        double minImpact = FusionConfigStore.signatureInt(FORWARD, "crater_min_impact", 5, 1, 100);
        ItemStack actualTool = hit.owner.getMainHandItem();
        if (CRATERS.size() < MAX_PENDING && !actualTool.isEmpty()
            && HeavyCrushMath.qualifies(actual, hit.impact, minDamage, minImpact)) {
            CRATERS.add(new Crater(hit.owner.getUUID(), hit.target.getUUID(), level.dimension(), hit.position, actualTool));
        }
    }

    private static boolean owns(AttributeInstance attribute, AttributeModifier modifier) {
        return attribute != null && modifier.equals(attribute.getModifier(modifier.id()))
            && !attribute.getPermanentModifiers().contains(modifier);
    }
    private static void addWeight(LivingEntity target, ServerLevel level) {
        AttributeInstance gravity = target.getAttribute(Attributes.GRAVITY);
        if (gravity == null) return;
        Weighted prior = WEIGHTED.get(target.getUUID());
        if (prior != null && prior.target() != target) { removeWeight(prior); WEIGHTED.remove(target.getUUID()); }
        if (gravity.hasModifier(TARGET_GRAVITY) && !owns(gravity, TARGET_MODIFIER)) return;
        if (!gravity.hasModifier(TARGET_GRAVITY)) gravity.addTransientModifier(TARGET_MODIFIER);
        WEIGHTED.put(target.getUUID(), new Weighted(target, level, level.getGameTime() + 60L));
    }
    private static void removeWeight(Weighted state) {
        AttributeInstance gravity = state.target().getAttribute(Attributes.GRAVITY);
        if (owns(gravity, TARGET_MODIFIER)) gravity.removeModifier(TARGET_GRAVITY);
    }
    public static void onUnload(Entity entity) {
        Weighted state = WEIGHTED.remove(entity.getUUID());
        if (state != null) removeWeight(state);
    }
    public static void tickPlayer(ServerPlayer player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        boolean heavy = player.isAlive() && !player.isSpectator()
            && (isMelee(player.getMainHandItem()) || isMelee(player.getOffhandItem()));
        if (heavy && !speed.hasModifier(OWNER_SLOW)) speed.addTransientModifier(OWNER_MODIFIER);
        else if (!heavy && owns(speed, OWNER_MODIFIER)) speed.removeModifier(OWNER_SLOW);
    }
    public static void tickServer(MinecraftServer server) {
        var iterator = WEIGHTED.values().iterator();
        while (iterator.hasNext()) {
            Weighted state = iterator.next();
            if (!state.target().isAlive() || state.target().isRemoved() || state.target().level() != state.level()
                || state.level().getGameTime() >= state.expires()) {
                removeWeight(state); iterator.remove();
            }
        }
        if (CRATERS.isEmpty()) return;
        List<Crater> pending = List.copyOf(CRATERS); CRATERS.clear();
        for (Crater crater : pending) {
            ServerPlayer owner = server.getPlayerList().getPlayer(crater.owner());
            ServerLevel level = server.getLevel(crater.dimension());
            if (owner == null || level == null || owner.level() != level || !owner.isAlive()
                || !ExtraBlockHarvest.hasOriginalTool(owner, crater.tool())) continue;
            ExtraBlockHarvest.defer(() -> excavate(level, owner, crater));
        }
    }
    public static void clear() {
        WEIGHTED.values().forEach(HeavyCrushSkill::removeWeight); WEIGHTED.clear(); CRATERS.clear(); SWING.remove();
    }
    public static void onDisconnect(ServerPlayer player) {
        CRATERS.removeIf(c -> c.owner().equals(player.getUUID()));
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (owns(speed, OWNER_MODIFIER)) speed.removeModifier(OWNER_SLOW);
    }

    private static boolean dryNeighbors(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos) || !level.getFluidState(pos).isEmpty()) return false;
        for (Direction direction : Direction.values()) {
            BlockPos adjacent = pos.relative(direction);
            if (!level.isInWorldBounds(adjacent) || !level.hasChunkAt(adjacent) || !level.getFluidState(adjacent).isEmpty()) return false;
        }
        return true;
    }
    private static boolean mineable(BlockState state) {
        return !state.isAir() && state.getFluidState().isEmpty() && ExtraBlockHarvest.isMatureCropOrNonCrop(state);
    }
    private static void excavate(ServerLevel level, ServerPlayer owner, Crater crater) {
        int radius = FusionConfigStore.signatureInt(FORWARD, "crater_radius", 1, 0, 2);
        int depth = FusionConfigStore.signatureInt(FORWARD, "crater_depth", 2, 1, 3);
        BlockPos ground = BlockPos.containing(crater.position().x, crater.position().y - 0.01D, crater.position().z);
        if (!level.isInWorldBounds(ground) || !level.hasChunkAt(ground) || level.getBlockState(ground).isAir()) return;
        int centerBroken = 0;
        // Center first. Do not press anyone down unless each center layer actually became air.
        for (int y = 0; y < depth; y++) {
            BlockPos pos = ground.below(y);
            if (!dryNeighbors(level, pos) || !ExtraBlockHarvest.tryBreak(level, owner, crater.tool(), pos, HeavyCrushSkill::mineable)
                || !level.getBlockState(pos).isAir()) break;
            centerBroken++;
        }
        for (int y = 0; y < centerBroken; y++) {
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos pos = ground.offset(dx, -y, dz);
                if (dryNeighbors(level, pos)) ExtraBlockHarvest.tryBreak(level, owner, crater.tool(), pos, HeavyCrushSkill::mineable);
            }
        }
        Entity entity = level.getEntity(crater.target());
        if (centerBroken <= 0 || !(entity instanceof LivingEntity target) || !target.isAlive() || target.isPassenger()
            || target.isNoGravity() || target.position().distanceToSqr(crater.position()) > 2.25D) return;
        Vec3 moved = target.position().add(0.0D, -centerBroken, 0.0D);
        if (level.noCollision(target, target.getBoundingBox().move(0.0D, -centerBroken, 0.0D))) {
            target.teleportTo(moved.x, moved.y, moved.z);
        }
    }
}

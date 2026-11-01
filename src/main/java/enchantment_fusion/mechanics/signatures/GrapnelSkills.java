package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.MatrixEffectExecutor;
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
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** 40.1 adapters: direct melee target marks and strictly proven block-drop marks. */
public final class GrapnelSkills {
    public static final String SIGNATURE = "spirit_grapnel", LURE = "minecraft:lure";
    public static final String EFFICIENCY = "minecraft:efficiency", SILK = "minecraft:silk_touch", FORTUNE = "minecraft:fortune";
    public static final String POWER = "minecraft:power", FLAME = "minecraft:flame", PUNCH = "minecraft:punch";
    public static final String MULTISHOT = "minecraft:multishot", INFINITY = "minecraft:infinity", PIERCING = "minecraft:piercing";
    public static final String QUICK = "minecraft:quick_charge";
    private static final Set<FusionCarrier> TOOLS = Set.of(FusionCarrier.PICKAXE, FusionCarrier.AXE, FusionCarrier.SHOVEL, FusionCarrier.HOE);
    private static final Set<String> MINING_PRIMARIES = Set.of(EFFICIENCY, SILK, FORTUNE);
    private static final int MAX_OWNERS = 256, MAX_DROPS = 64, MAX_FLIGHTS = 4096, MAX_FLIGHT_GEARS = 16;
    private static final long MARK_TICKS = 160;
    private record Gear(ItemStack stack, FusedPair pair, @Nullable ThrownTrident projectile) {
        Gear(ItemStack stack, FusedPair pair) { this(stack, pair, null); }
    }
    private sealed interface Mark permits TargetMark, DropsMark, PositionMark {
        Gear gear(); ResourceKey<Level> dimension(); long until();
    }
    private record TargetMark(Gear gear, ResourceKey<Level> dimension, long until, @Nullable EquipmentSlot slot, UUID target) implements Mark {}
    private record Drop(ItemEntity entity, ItemStack stack, ItemStack snapshot) {}
    private record DropsMark(Gear gear, ResourceKey<Level> dimension, long until, @Nullable EquipmentSlot slot, BlockPos origin, List<Drop> drops) implements Mark {}
    private record PositionMark(Gear gear, ResourceKey<Level> dimension, long until, @Nullable EquipmentSlot slot, Vec3 origin) implements Mark {}
    private static final Map<UUID, Mark> MARKS = new HashMap<>();
    private record Flight(UUID owner, ResourceKey<Level> dimension, List<Gear> gears, boolean fullDraw,
                          @Nullable GrapnelVolley.Batch volley, long expires) {}
    private static final Map<AbstractArrow, Flight> FLIGHTS = new WeakHashMap<>();
    private record PendingPush(UUID target, Flight flight, long tick) {}
    private static final Map<AbstractArrow, PendingPush> PENDING_PUSHES = new WeakHashMap<>();
    public static final class ArrowHit {
        private final AbstractArrow arrow; private final ServerPlayer owner; private final LivingEntity target;
        private final Flight flight; private final float health, absorption; private final boolean burning, eligibleBefore;
        private final Vec3 location; private final AABB impactBounds;
        private ArrowHit(AbstractArrow arrow, ServerPlayer owner, LivingEntity target, Flight flight, boolean burning,
                         boolean eligibleBefore, Vec3 location) {
            this.arrow = arrow; this.owner = owner; this.target = target; this.flight = flight; this.burning = burning;
            this.location = location; this.impactBounds = target.getBoundingBox();
            this.eligibleBefore = eligibleBefore; this.health = target.getHealth(); this.absorption = target.getAbsorptionAmount();
        }
    }
    private GrapnelSkills() {}
    public static void init() {
        ConfirmedMelee.register(GrapnelSkills::onHit);
        ConfirmedMelee.register(GrapnelImpact::onHit);
        GrapnelWind.init(); GrapnelThorns.init(); GrapnelAttribute.init(); GrapnelLunge.init(); GrapnelLooting.init(); GrapnelRiptide.init();
        MeleeAttackThemes.registerPrimaryTemplate("grapnel_attack", (pair, stack) -> attackFirst(pair) && applies(pair, stack));
    }
    public static boolean attackFirst(@Nullable FusedPair pair) {
        return pair != null && MeleeAttackThemes.isAttack(pair.primaryId()) && LURE.equals(pair.secondaryId());
    }
    public static boolean miningFirst(@Nullable FusedPair pair) {
        return pair != null && MINING_PRIMARIES.contains(pair.primaryId()) && LURE.equals(pair.secondaryId());
    }
    public static boolean rangedFirst(@Nullable FusedPair pair) {
        return pair != null && (POWER.equals(pair.primaryId()) || FLAME.equals(pair.primaryId()) || PUNCH.equals(pair.primaryId())
            || MULTISHOT.equals(pair.primaryId()) || INFINITY.equals(pair.primaryId()) || PIERCING.equals(pair.primaryId())
            || QUICK.equals(pair.primaryId()))
            && LURE.equals(pair.secondaryId());
    }
    public static boolean matches(@Nullable FusedPair pair) {
        return (attackFirst(pair) || miningFirst(pair) || rangedFirst(pair) || GrapnelDefense.isPair(pair)
            || GrapnelDurability.isPair(pair) || GrapnelImpact.isPair(pair) || GrapnelWind.isPair(pair) || GrapnelThorns.isPair(pair)
            || GrapnelAttribute.isPair(pair) || GrapnelLunge.isPair(pair) || GrapnelChanneling.isPair(pair) || GrapnelLooting.isPair(pair)
            || GrapnelRiptide.isPair(pair))
            && SignatureCatalog.resolve(pair).map(entry -> SIGNATURE.equals(entry.id()) && "*".equals(entry.primaryId())).orElse(false);
    }
    public static boolean supportsCarrier(Set<FusionCarrier> roles) { return MeleeAttackThemes.supportsWeapon(roles); }
    public static boolean supportsCarrier(@Nullable FusedPair pair, @Nullable Set<FusionCarrier> roles) {
        if (roles == null) return false;
        if (attackFirst(pair)) return supportsCarrier(roles);
        if (miningFirst(pair)) return roles.stream().anyMatch(TOOLS::contains);
        if (GrapnelDefense.isPair(pair)) return GrapnelDefense.supportsCarrier(pair, roles);
        if (GrapnelDurability.isPair(pair)) return !roles.isEmpty(); // damageability is checked on the actual host
        if (GrapnelImpact.isPair(pair)) return GrapnelImpact.supportsCarrier(pair, roles);
        if (GrapnelLunge.isPair(pair)) return GrapnelLunge.supportsCarrier(roles);
        if (GrapnelLooting.isPair(pair)) return GrapnelLooting.supportsCarrier(roles);
        if (GrapnelChanneling.isPair(pair)) return GrapnelChanneling.supportsCarrier(roles);
        if (GrapnelRiptide.isPair(pair)) return GrapnelRiptide.supportsCarrier(roles);
        if (GrapnelWind.isPair(pair)) return GrapnelWind.supportsCarrier(roles);
        if (GrapnelThorns.isPair(pair)) return GrapnelThorns.supportsCarrier(roles);
        if (GrapnelAttribute.isPair(pair)) return GrapnelAttribute.supportsCarrier(pair, roles);
        if (rangedFirst(pair)) {
            if (PIERCING.equals(pair.primaryId()) || QUICK.equals(pair.primaryId())) return roles.contains(FusionCarrier.CROSSBOW);
            return roles.contains(FusionCarrier.BOW) || (!POWER.equals(pair.primaryId()) && roles.contains(FusionCarrier.CROSSBOW));
        }
        return false;
    }
    public static boolean supportsHost(@Nullable FusedPair pair, @Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (GrapnelDurability.isPair(pair)) return DurabilityEconomy.supportsCarrier(stack);
        if (GrapnelThorns.isPair(pair)) return GrapnelThorns.supportsHost(stack);
        if (GrapnelLunge.isPair(pair)) return GrapnelLunge.supportsHost(stack);
        return supportsCarrier(pair, FusionCarrier.classify(stack));
    }
    public static boolean applies(@Nullable FusedPair pair, @Nullable ItemStack stack) {
        return matches(pair) && supportsHost(pair, stack);
    }
    private static @Nullable Gear selected(@Nullable ItemStack stack, boolean mining) {
        if (stack == null || stack.isEmpty()) return null;
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if ((mining ? miningFirst(pair) : attackFirst(pair)) && applies(pair, stack)) return new Gear(stack, pair);
        return null;
    }
    private static List<Gear> rangedGears(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return List.of();
        List<Gear> gears = new ArrayList<>();
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs()) {
            if (rangedFirst(pair) && applies(pair, stack)) gears.add(new Gear(stack, pair));
            if (gears.size() >= MAX_FLIGHT_GEARS) break;
        }
        return List.copyOf(gears);
    }
    private static boolean ready(ServerPlayer player) { return player.isAlive() && !player.isSpectator() && !player.isCreative(); }
    private static boolean owns(ServerPlayer player, Gear gear) {
        if (!applies(gear.pair(), gear.stack()) || !gear.stack().getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY)
            .pairs().contains(gear.pair())) return false;
        if (gear.projectile() != null) return GrapnelTridentFlight.valid(player, gear.projectile(), gear.stack(), gear.pair());
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) if (player.getInventory().getItem(i) == gear.stack()) return true;
        return false;
    }
    private static boolean canMark(ServerPlayer owner) { return MARKS.size() < MAX_OWNERS || MARKS.containsKey(owner.getUUID()); }
    private static void onHit(ConfirmedMelee.Hit hit) {
        ServerPlayer owner = hit.player(); Gear gear = selected(hit.weapon(), false);
        if (gear == null || !ready(owner) || hit.slot() != EquipmentSlot.MAINHAND || owner.getMainHandItem() != hit.weapon()
            || !hit.eligibleBefore() || !hit.target().isAlive() || !MeleeAttackThemes.matchesTarget(gear.pair().primaryId(), hit.target())
            || (MeleeAttackThemes.SHARP.equals(gear.pair().primaryId()) && !hit.full()) || !owns(owner, gear) || !canMark(owner)) return;
        markTarget(owner, hit.target(), gear);
    }
    /** Capture the actual launcher and draw state inside a real bow/crossbow shot, never from a nearby scan. */
    public static void launched(Projectile projectile, LivingEntity shooter, int index, @Nullable ItemStack weapon) {
        if (!(projectile instanceof AbstractArrow arrow) || !(shooter instanceof ServerPlayer owner) || index < 0
            || !ready(owner) || weapon == null || (owner.getMainHandItem() != weapon && owner.getOffhandItem() != weapon)
            || arrow.getOwner() != owner || PhantomVolley.isGhost(arrow)) return;
        GrapnelVolley.Batch volley = GrapnelVolley.launched(arrow, shooter, weapon);
        if (FLIGHTS.size() >= MAX_FLIGHTS) return;
        List<Gear> gears = rangedGears(weapon).stream()
            .filter(gear -> index == 0 || MULTISHOT.equals(gear.pair().primaryId()))
            .filter(gear -> !QUICK.equals(gear.pair().primaryId()) || GrapnelQuickCharge.proven(owner, weapon, gear.pair())).toList();
        if (!gears.isEmpty()) FLIGHTS.put(arrow, new Flight(owner.getUUID(), owner.level().dimension(), gears,
            arrow.isCritArrow(), volley, owner.level().getGameTime() + 1200L));
    }
    static boolean eligibleTarget(ServerPlayer owner, LivingEntity target, FusedPair pair) {
        if (attackFirst(pair)) return MeleeAttackThemes.eligible(owner, target, pair.primaryId());
        return (rangedFirst(pair) || GrapnelDefense.isPair(pair) || GrapnelDurability.isPair(pair) || GrapnelImpact.isPair(pair)
            || GrapnelWind.isPair(pair) || GrapnelThorns.isPair(pair) || GrapnelAttribute.isPair(pair) || GrapnelLunge.isPair(pair) || GrapnelChanneling.isPair(pair) || GrapnelLooting.isPair(pair)
            || GrapnelRiptide.isPair(pair))
            && target.level() == owner.level() && LoyaltyGuard.eligibleTarget(owner, target)
            && owner.level().mayInteract(owner, target.blockPosition()) && (!(target instanceof Player other) || other.canHarmPlayer(owner));
    }
    /** Capture before the actual arrow damage call, rather than trusting collision alone. */
    public static @Nullable ArrowHit beforeArrowHit(AbstractArrow arrow, Entity victim, DamageSource source, Vec3 location) {
        PENDING_PUSHES.remove(arrow);
        Flight flight = FLIGHTS.get(arrow);
        if (flight == null || !(victim instanceof LivingEntity target) || !(arrow.getOwner() instanceof ServerPlayer owner)
            || source.getDirectEntity() != arrow || source.getEntity() != owner || !flight.owner().equals(owner.getUUID())
            || !ready(owner) || owner.level() != arrow.level() || owner.level().getEntity(arrow.getUUID()) != arrow
            || target.level() != owner.level() || !target.isAlive()
            || !owner.level().dimension().equals(flight.dimension()) || owner.level().getGameTime() >= flight.expires()
            || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypeTags.BYPASSES_EFFECTS)
            || source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS) || PhantomVolley.isGhost(arrow) || MatrixEffectExecutor.isExecuting()) return null;
        boolean eligible = flight.gears().stream().anyMatch(gear -> eligibleTarget(owner, target, gear.pair()));
        return new ArrowHit(arrow, owner, target, flight, arrow.isOnFire(), eligible, location);
    }
    public static void afterArrowHit(@Nullable ArrowHit hit, boolean accepted) {
        if (hit == null || !accepted || !hit.eligibleBefore || !ready(hit.owner) || !hit.target.isAlive()
            || hit.owner.level() != hit.target.level() || hit.owner.level() != hit.arrow.level()
            || hit.arrow.getOwner() != hit.owner || FLIGHTS.get(hit.arrow) != hit.flight || !canMark(hit.owner)
            || ConfirmedMeleeMath.actualLoss(hit.health, hit.absorption, hit.target.getHealth(), hit.target.getAbsorptionAmount()) <= 0) return;
        if (hit.flight.gears().stream().anyMatch(gear -> PUNCH.equals(gear.pair().primaryId()) && owns(hit.owner, gear)))
            PENDING_PUSHES.put(hit.arrow, new PendingPush(hit.target.getUUID(), hit.flight, hit.owner.level().getGameTime()));
        if (hit.flight.gears().stream().anyMatch(gear -> PIERCING.equals(gear.pair().primaryId())
            && owns(hit.owner, gear) && eligibleTarget(hit.owner, hit.target, gear.pair())))
            GrapnelPiercing.prepare(hit.arrow, hit.owner, hit.target, hit.location, hit.impactBounds);
        for (Gear gear : hit.flight.gears()) {
            FusedPair pair = gear.pair();
            if (PUNCH.equals(pair.primaryId()) || PIERCING.equals(pair.primaryId())
                || !owns(hit.owner, gear) || !eligibleTarget(hit.owner, hit.target, pair)
                || (POWER.equals(pair.primaryId()) && !hit.flight.fullDraw())
                || (FLAME.equals(pair.primaryId()) && !hit.burning)
                || (MULTISHOT.equals(pair.primaryId()) && !GrapnelVolley.proven(hit.flight.volley()))) continue;
            markTarget(hit.owner, hit.target, gear); return;
        }
    }
    public static void markImpact(ServerPlayer owner, LivingEntity target, ItemStack weapon, FusedPair pair) {
        markImpact(owner, target, weapon, pair, null);
    }
    public static void markImpact(ServerPlayer owner, LivingEntity target, ItemStack weapon, FusedPair pair, @Nullable ThrownTrident projectile) {
        Gear gear = new Gear(weapon, pair, projectile);
        if (!ready(owner) || !canMark(owner) || (projectile == null && owner.getMainHandItem() != weapon) || !owns(owner, gear)
            || owner.level().getEntity(target.getUUID()) != target || !eligibleTarget(owner, target, pair) || !target.isAlive()) return;
        markTarget(owner, target, gear);
    }
    /** Thorns marks the actual attacker and stays bound to the original worn/held slot. */
    public static void markEquippedTarget(ServerPlayer owner, LivingEntity target, EquipmentSlot slot, ItemStack stack, FusedPair pair) {
        if (!GrapnelThorns.isPair(pair) || !ready(owner) || !canMark(owner) || !stillEquipped(owner, slot, stack, pair)
            || owner.level().getEntity(target.getUUID()) != target || !eligibleTarget(owner, target, pair)) return;
        markTarget(owner, target, new Gear(stack, pair), slot);
    }
    private static void markTarget(ServerPlayer owner, LivingEntity target, Gear gear) { markTarget(owner, target, gear, null); }
    private static void markTarget(ServerPlayer owner, LivingEntity target, Gear gear, @Nullable EquipmentSlot slot) {
        MARKS.put(owner.getUUID(), new TargetMark(gear, owner.level().dimension(),
            owner.level().getGameTime() + MARK_TICKS, slot, target.getUUID()));
        owner.level().sendParticles(ParticleTypes.FISHING, target.getX(), target.getY(0.6D), target.getZ(),
            8, 0.2D, 0.2D, 0.2D, 0.02D);
        owner.sendSystemMessage(Component.translatable(slot == null ? "message.enchantment_fusion.grapnel_marked"
            : "message.enchantment_fusion.grapnel_thorns_marked"), true);
    }
    /** A Punch mark is committed only after this confirmed hit really pushed the same target. */
    public static void afterArrowPush(AbstractArrow arrow, LivingEntity target, Vec3 before) {
        PendingPush pending = PENDING_PUSHES.remove(arrow);
        if (pending == null || FLIGHTS.get(arrow) != pending.flight() || !(arrow.getOwner() instanceof ServerPlayer owner)
            || !pending.flight().owner().equals(owner.getUUID()) || !pending.target().equals(target.getUUID())
            || owner.level() != arrow.level() || owner.level() != target.level() || owner.level().getGameTime() != pending.tick()
            || !ready(owner) || !canMark(owner)) return;
        Vec3 delta = target.getDeltaMovement().subtract(before);
        if (!Double.isFinite(delta.x) || !Double.isFinite(delta.z) || delta.horizontalDistanceSqr() < 0.0001D) return;
        for (Gear gear : pending.flight().gears()) {
            if (PUNCH.equals(gear.pair().primaryId()) && owns(owner, gear) && eligibleTarget(owner, target, gear.pair())) {
                markTarget(owner, target, gear); return;
            }
        }
    }
    /** Invoked only after the real arrow's continuous movement has passed the hit-time target bounds. */
    public static void confirmArrowPiercing(AbstractArrow arrow, LivingEntity target, UUID expectedOwner) {
        Flight flight = FLIGHTS.get(arrow);
        if (flight == null || !(arrow.getOwner() instanceof ServerPlayer owner) || !expectedOwner.equals(owner.getUUID())
            || !flight.owner().equals(owner.getUUID()) || !ready(owner) || !canMark(owner) || owner.level() != arrow.level()
            || target.level() != owner.level() || owner.level().getEntity(arrow.getUUID()) != arrow
            || owner.level().getEntity(target.getUUID()) != target || !owner.level().dimension().equals(flight.dimension())
            || owner.level().getGameTime() >= flight.expires() || PhantomVolley.isGhost(arrow)) return;
        for (Gear gear : flight.gears()) {
            if (PIERCING.equals(gear.pair().primaryId()) && owns(owner, gear) && eligibleTarget(owner, target, gear.pair())) {
                markTarget(owner, target, gear); return;
            }
        }
    }
    /** Project a missing Silk/Fortune primary only onto the copy used by the real block loot call. */
    public static ItemStack lootTool(ServerLevel level, ItemStack tool) {
        Gear gear = selected(tool, true);
        if (gear == null || EFFICIENCY.equals(gear.pair().primaryId())) return tool;
        Holder<Enchantment> primary = FusionStationOperations.lookupEnchantment(level.registryAccess(), gear.pair().primaryId()).orElse(null);
        if (primary == null || tool.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(primary) > 0) return tool;
        ItemStack copy = tool.copy();
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(copy.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY));
        enchantments.set(primary, gear.pair().primaryLevel()); EnchantmentHelper.setEnchantments(copy, enchantments.toImmutable());
        return copy;
    }
    /** Publish exact proven block or death-loot entities through the same safe recall path. */
    static void markDrops(ServerPlayer player, ItemStack item, FusedPair pair, @Nullable EquipmentSlot slot,
                          BlockPos origin, List<ItemEntity> verified) {
        ServerLevel level = player.level(); Gear gear = new Gear(item, pair);
        if (!ready(player) || !canMark(player) || !owns(player, gear) || (slot != null && !stillEquipped(player, slot, item, pair))
            || verified.isEmpty() || verified.size() > MAX_DROPS || !level.hasChunkAt(origin) || !level.isInWorldBounds(origin)
            || !level.mayInteract(player, origin) || level.getServer().isUnderSpawnProtection(level, origin, player)) return;
        List<Drop> drops = new ArrayList<>(verified.size());
        for (ItemEntity entity : verified) {
            ItemStack stack = entity.getItem();
            if (!stack.isEmpty() && GrapnelDropTargets.unchanged(player, entity, stack, stack.copy()))
                drops.add(new Drop(entity, stack, stack.copy()));
        }
        if (drops.isEmpty()) return;
        MARKS.put(player.getUUID(), new DropsMark(gear, level.dimension(), level.getGameTime() + MARK_TICKS,
            slot, origin.immutable(), List.copyOf(drops)));
        level.sendParticles(ParticleTypes.FISHING, origin.getX() + 0.5D, origin.getY() + 0.5D, origin.getZ() + 0.5D, 8, 0.2D, 0.2D, 0.2D, 0.02D);
        player.sendSystemMessage(Component.translatable(GrapnelLooting.isPair(pair)
            ? "message.enchantment_fusion.grapnel_looting_marked" : "message.enchantment_fusion.grapnel_drops_marked"), true);
    }
    static @Nullable FusedPair miningPair(@Nullable ItemStack stack) {
        Gear gear = selected(stack, true); return gear == null ? null : gear.pair();
    }
    /** Position marks from a real defensive contribution or shield block. */
    public static void markDefense(ServerPlayer owner, GrapnelDefense.Gear defensive, Vec3 origin) {
        markPosition(owner, defensive.slot(), defensive.stack(), defensive.pair(), origin);
    }
    /** Shared position marks bind the actual slot, item identity, pair and unmodified location. */
    public static void markPosition(ServerPlayer owner, EquipmentSlot slot, ItemStack stack, FusedPair pair, Vec3 origin) {
        tryMarkPosition(owner, slot, stack, pair, origin);
    }
    public static boolean tryMarkPosition(ServerPlayer owner, EquipmentSlot slot, ItemStack stack, FusedPair pair, Vec3 origin) {
        return tryMarkPosition(owner, slot, stack, pair, origin, null);
    }
    public static boolean markProjectilePosition(ServerPlayer owner, ThrownTrident projectile, ItemStack stack, FusedPair pair, Vec3 origin) {
        return GrapnelChanneling.isPair(pair) && tryMarkPosition(owner, null, stack, pair, origin, projectile);
    }
    private static boolean tryMarkPosition(ServerPlayer owner, @Nullable EquipmentSlot slot, ItemStack stack, FusedPair pair,
                                           Vec3 origin, @Nullable ThrownTrident projectile) {
        Gear gear = new Gear(stack, pair, projectile);
        if (!ready(owner) || !canMark(owner) || !GrapnelPositionTargets.allowedOrigin(owner, origin, projectile == null)
            || (projectile == null ? slot == null || !stillEquipped(owner, slot, stack, pair) : !owns(owner, gear))) return false;
        MARKS.put(owner.getUUID(), new PositionMark(gear, owner.level().dimension(), owner.level().getGameTime() + MARK_TICKS, slot, origin));
        GrapnelPositionTargets.announce(owner, pair, origin, projectile != null);
        return true;
    }
    private static boolean stillEquipped(ServerPlayer owner, EquipmentSlot slot, ItemStack stack, FusedPair pair) {
        if (GrapnelDefense.isPair(pair)) return GrapnelDefense.stillEquipped(owner, new GrapnelDefense.Gear(slot, stack, pair));
        if (GrapnelThorns.isPair(pair)) return GrapnelThorns.stillEquipped(owner, slot, stack, pair);
        return owner.getItemBySlot(slot) == stack && !stack.isEmpty() && applies(pair, stack)
            && stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().contains(pair);
    }
    private static boolean valid(ServerPlayer owner, Mark mark) {
        if (!ready(owner) || !owner.level().dimension().equals(mark.dimension()) || owner.level().getGameTime() >= mark.until()) return false;
        if (mark instanceof PositionMark position && position.slot() != null)
            return stillEquipped(owner, position.slot(), position.gear().stack(), position.gear().pair());
        if (mark instanceof DropsMark drops && drops.slot() != null)
            return stillEquipped(owner, drops.slot(), drops.gear().stack(), drops.gear().pair());
        if (mark instanceof TargetMark target && target.slot() != null)
            return stillEquipped(owner, target.slot(), target.gear().stack(), target.gear().pair());
        return owns(owner, mark.gear());
    }
    private static boolean reachableDrop(ServerPlayer owner, DropsMark mark, Drop drop) {
        return GrapnelDropTargets.reachable(owner, mark.origin(), drop.entity(), drop.stack(), drop.snapshot());
    }
    public static boolean hasEquipped(ServerPlayer player) {
        return selected(player.getMainHandItem(), false) != null || selected(player.getMainHandItem(), true) != null
            || !rangedGears(player.getMainHandItem()).isEmpty() || GrapnelImpact.hasEquipped(player.getMainHandItem())
            || GrapnelDefense.hasEquipped(player) || GrapnelDurability.hasEquipped(player)
            || GrapnelWind.hasEquipped(player) || GrapnelThorns.hasEquipped(player) || GrapnelAttribute.hasEquipped(player)
            || GrapnelLunge.selected(player.getMainHandItem()) != null || GrapnelChanneling.selected(player.getMainHandItem()) != null
            || GrapnelLooting.selected(player.getMainHandItem()) != null || GrapnelRiptide.hasEquipped(player) || hasProjectileMark(player);
    }
    private static boolean hasProjectileMark(ServerPlayer player) {
        Mark mark = MARKS.get(player.getUUID()); return mark != null && mark.gear().projectile() != null && valid(player, mark);
    }
    public static boolean activate(ServerPlayer owner) {
        if (!ready(owner) || owner.containerMenu != owner.inventoryMenu) return false;
        Mark mark = MARKS.get(owner.getUUID());
        if (mark == null || !valid(owner, mark)) {
            MARKS.remove(owner.getUUID()); owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.grapnel_need"), true); return false;
        }
        if (mark instanceof PositionMark position) {
            LivingEntity target = GrapnelPositionTargets.aimed(owner, position.origin());
            return target != null && eligibleTarget(owner, target, position.gear().pair()) && pull(owner, position, target);
        }
        if (mark instanceof TargetMark target && (target.slot() != null || target.gear().projectile() != null)) return recallTarget(owner, target);
        if (mark instanceof DropsMark drops && drops.slot() != null) return recallDrops(owner, drops);
        if (owner.getMainHandItem() != mark.gear().stack()) return false;
        return mark instanceof TargetMark target ? recallTarget(owner, target) : recallDrops(owner, (DropsMark) mark);
    }
    private static boolean recallTarget(ServerPlayer owner, TargetMark mark) {
        if (!(owner.level().getEntity(mark.target()) instanceof LivingEntity target) || !eligibleTarget(owner, target, mark.gear().pair())) return false;
        return pull(owner, mark, target);
    }
    private static boolean pull(ServerPlayer owner, Mark mark, LivingEntity target) {
        if (!MARKS.remove(owner.getUUID(), mark)) return false; // do not reenter during an observed impulse
        if (GrapnelPull.attempt(owner, target, mark.gear().pair())) return true;
        MARKS.putIfAbsent(owner.getUUID(), mark); return false;
    }
    private static boolean recallDrops(ServerPlayer owner, DropsMark mark) {
        if (mark.drops().stream().noneMatch(drop -> reachableDrop(owner, mark, drop)) || !MARKS.remove(owner.getUUID(), mark)) return false;
        int recovered = 0;
        for (Drop drop : mark.drops()) {
            boolean sameSource = mark.slot() == null ? owner.getMainHandItem() == mark.gear().stack()
                : stillEquipped(owner, mark.slot(), mark.gear().stack(), mark.gear().pair());
            if (!ready(owner) || !owner.level().dimension().equals(mark.dimension()) || !sameSource
                || !reachableDrop(owner, mark, drop)) continue;
            int before = drop.stack().getCount();
            drop.entity().playerTouch(owner); // vanilla pickup delay, restrictions, inventory insertion and pickup hooks
            int after = drop.entity().isAlive() ? drop.entity().getItem().getCount() : 0;
            recovered += Math.max(0, before - after);
        }
        if (recovered <= 0) {
            owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.grapnel_no_room"), true); return false;
        }
        owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.grapnel_recovered", recovered), true);
        CompendiumDiscoveryTracker.recordDiscovery(owner, mark.gear().pair().primaryId(), mark.gear().pair().secondaryId());
        return true;
    }
    public static void tickServer(MinecraftServer server) {
        GrapnelPiercing.tickServer(server); GrapnelQuickCharge.tickServer(server);
        GrapnelWind.tickServer(server); GrapnelThorns.tickServer(server); GrapnelMotion.tickServer(server); GrapnelSweep.tickServer(server); GrapnelBreath.tickServer(server);
        GrapnelTridentFlight.tickServer(server); GrapnelRiptide.tickServer(server);
        FLIGHTS.entrySet().removeIf(entry -> {
            Flight flight = entry.getValue(); ServerPlayer owner = server.getPlayerList().getPlayer(flight.owner());
            return owner == null || !ready(owner) || !entry.getKey().isAlive() || owner.level() != entry.getKey().level()
                || !owner.level().dimension().equals(flight.dimension()) || owner.level().getGameTime() >= flight.expires()
                || flight.gears().stream().noneMatch(gear -> owns(owner, gear));
        });
        PENDING_PUSHES.entrySet().removeIf(entry -> FLIGHTS.get(entry.getKey()) != entry.getValue().flight()
            || entry.getKey().level().getGameTime() != entry.getValue().tick());
        for (var entry : List.copyOf(MARKS.entrySet())) {
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey()); Mark mark = entry.getValue();
            boolean noTarget = owner != null && (mark instanceof TargetMark target ? owner.level().getEntity(target.target()) == null
                : mark instanceof DropsMark drops && drops.drops().stream().noneMatch(drop -> GrapnelDropTargets.unchanged(owner, drop.entity(), drop.stack(), drop.snapshot())));
            if (owner == null || !valid(owner, mark) || noTarget) MARKS.remove(entry.getKey(), mark);
        }
    }
    public static void clearPlayer(UUID id) {
        MARKS.remove(id);
        GrapnelPiercing.clearPlayer(id); GrapnelQuickCharge.clearPlayer(id);
        GrapnelWind.clearPlayer(id); GrapnelThorns.clearPlayer(id); GrapnelMotion.clearPlayer(id); GrapnelSweep.clearPlayer(id); GrapnelBreath.clearPlayer(id);
        GrapnelTridentFlight.clearPlayer(id); GrapnelRiptide.clearPlayer(id);
        MARKS.entrySet().removeIf(entry -> entry.getValue() instanceof TargetMark target && target.target().equals(id));
        FLIGHTS.entrySet().removeIf(entry -> entry.getValue().owner().equals(id));
        PENDING_PUSHES.entrySet().removeIf(entry -> entry.getValue().flight().owner().equals(id));
    }
    public static void clearServer() {
        MARKS.clear(); FLIGHTS.clear(); PENDING_PUSHES.clear();
        GrapnelPiercing.clearServer(); GrapnelDefense.clearServer(); GrapnelQuickCharge.clearServer(); GrapnelDurability.clearServer();
        GrapnelImpact.clearServer(); GrapnelWind.clearServer(); GrapnelThorns.clearServer(); GrapnelMotion.clearServer();
        GrapnelSweep.clearServer(); GrapnelBreath.clearServer(); GrapnelLunge.clearServer(); GrapnelChanneling.clearServer(); GrapnelLooting.clearServer(); GrapnelTridentFlight.clearServer(); GrapnelRiptide.clearServer(); ProtectionProjection.clear();
    }
}

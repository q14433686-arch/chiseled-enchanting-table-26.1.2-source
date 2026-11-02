package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.MatrixEffectExecutor;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import enchantment_fusion.model.RetainedEnchant;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.jetbrains.annotations.Nullable;

/**
 * Loyalty -> Lure: preserve the actual data-pack return effect and issue a location mark only
 * for a consumed, spawned trident that really flew back and whose original pickup entered its
 * owner's inventory. A matching item, a mere no-physics flag, or a manual ground pickup is not
 * a return receipt. This adapter is independent of the universal Loyalty -> Protection skill.
 */
public final class GrapnelLoyalty {
    public static final String LOYALTY = "minecraft:loyalty";
    private static final int MAX_FLIGHTS = 4096;
    private static final long FLIGHT_TICKS = 1200L;
    private static final ThreadLocal<Release> RELEASE = new ThreadLocal<>();
    private static final ThreadLocal<Touch> TOUCH = new ThreadLocal<>();
    private static final Map<ThrownTrident, Flight> FLIGHTS = new WeakHashMap<>();

    public static final class Release {
        final @Nullable Release previous; final ServerPlayer owner; final ServerLevel level;
        final ItemStack hand, snapshot; final FusedPair pair; final EquipmentSlot slot; final long tick;
        @Nullable ThrownTrident queried, spawned; boolean querySeen, queryProved, ambiguous;
        Release(@Nullable Release previous, ServerPlayer owner, ItemStack hand, FusedPair pair, EquipmentSlot slot) {
            this.previous = previous; this.owner = owner; this.level = owner.level(); this.hand = hand;
            this.snapshot = hand.copy(); this.pair = pair; this.slot = slot; this.tick = level.getGameTime();
        }
    }
    private static final class Flight {
        final UUID owner; final ResourceKey<Level> dimension; final ItemStack stack; final FusedPair pair; final long expires;
        boolean returned;
        Flight(Release release, ItemStack stack) {
            owner = release.owner.getUUID(); dimension = release.level.dimension(); this.stack = stack;
            pair = release.pair; expires = release.tick + FLIGHT_TICKS;
        }
    }
    public static final class ReturnTick {
        final Flight flight; final ServerPlayer owner; final ThrownTrident trident;
        final int count; final Vec3 before, eye;
        ReturnTick(Flight flight, ServerPlayer owner, ThrownTrident trident) {
            this.flight = flight; this.owner = owner; this.trident = trident;
            count = trident.clientSideReturnTridentTickCount; before = trident.position(); eye = owner.getEyePosition();
        }
    }
    public static final class Touch {
        final @Nullable Touch previous; final Flight flight; final ServerPlayer owner; final ThrownTrident trident;
        final ServerLevel level; final long tick; final ItemStack expected; boolean ambiguous, pickupSeen, pickupSucceeded;
        @Nullable ItemStack inserted;
        Touch(@Nullable Touch previous, Flight flight, ServerPlayer owner, ThrownTrident trident) {
            this.previous = previous; this.flight = flight; this.owner = owner; this.trident = trident;
            level = owner.level(); tick = level.getGameTime(); expected = flight.stack.copy();
        }
    }
    public static final class Pickup {
        final Touch touch; final RealInventoryInsertion receipt;
        Pickup(Touch touch) { this.touch = touch; this.receipt = RealInventoryInsertion.capture(touch.owner.getInventory()); }
    }

    private GrapnelLoyalty() {}
    public static boolean isPair(@Nullable FusedPair pair) {
        return pair != null && LOYALTY.equals(pair.primaryId()) && GrapnelSkills.LURE.equals(pair.secondaryId());
    }
    public static boolean supportsCarrier(@Nullable Set<FusionCarrier> roles) {
        return roles != null && roles.contains(FusionCarrier.TRIDENT);
    }
    public static @Nullable FusedPair selected(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        FusedPair best = null;
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if (isPair(pair) && GrapnelSkills.applies(pair, stack) && (best == null || pair.primaryLevel() > best.primaryLevel())) best = pair;
        return best;
    }
    private static boolean ready(ServerPlayer player) {
        return player.isAlive() && !player.isCreative() && !player.isSpectator() && !player.hasInfiniteMaterials();
    }
    private static boolean unambiguous(ItemStack stack) {
        return stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().stream()
            .filter(pair -> pair.involves(LOYALTY)).count() == 1
            && stack.getOrDefault(FusionDataComponents.RETAINED_ENCHANTS, List.<RetainedEnchant>of()).stream()
                .noneMatch(retained -> LOYALTY.equals(retained.id()));
    }
    private static boolean soleReturnEffect(ItemEnchantments nativeEnchantments, Holder<Enchantment> loyalty) {
        if (!loyalty.value().effects().has(EnchantmentEffectComponents.TRIDENT_RETURN_ACCELERATION)) return false;
        for (var entry : nativeEnchantments.entrySet())
            if (entry.getKey().value() != loyalty.value()
                && entry.getKey().value().effects().has(EnchantmentEffectComponents.TRIDENT_RETURN_ACCELERATION)) return false;
        return true;
    }
    private static boolean sameExceptWear(ItemStack before, ItemStack after) {
        if (before.isEmpty() || after.isEmpty() || before.getCount() != 1 || after.getCount() != 1
            || after.getDamageValue() < before.getDamageValue()) return false;
        ItemStack expected = before.copy(); expected.setDamageValue(after.getDamageValue());
        return ItemStack.matches(expected, after);
    }

    /** Observe a genuinely held and charged release; direct releaseUsing calls do not mint evidence. */
    public static @Nullable Release beginRelease(ItemStack stack, Level level, LivingEntity user, int remaining) {
        Release previous = RELEASE.get(); if (previous != null) previous.ambiguous = true;
        if (!(user instanceof ServerPlayer owner) || owner.level() != level || !ready(owner) || MatrixEffectExecutor.isExecuting()
            || !owner.isUsingItem() || owner.getUseItem() != stack || owner.getUseItemRemainingTicks() != remaining
            || stack.getUseDuration(user) - remaining < 10 || stack.getCount() != 1 || stack.nextDamageWillBreak()) return null;
        EquipmentSlot slot = owner.getUsedItemHand() == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        FusedPair pair = selected(stack);
        if (owner.getItemBySlot(slot) != stack || pair == null || !unambiguous(stack)) return null;
        Release frame = new Release(previous, owner, stack, pair, slot); RELEASE.set(frame); return frame;
    }
    public static void endRelease(@Nullable Release frame, boolean result, boolean completed) {
        if (frame == null) return;
        try {
            if (RELEASE.get() != frame || !completed || !result || frame.ambiguous || !frame.queryProved
                || frame.queried == null || frame.spawned != frame.queried || !frame.hand.isEmpty()
                || frame.owner.level() != frame.level || frame.level.getGameTime() != frame.tick || !ready(frame.owner)
                || FLIGHTS.size() >= MAX_FLIGHTS) return;
            ItemStack source = frame.spawned.getWeaponItem();
            if (source == null || !sameExceptWear(frame.snapshot, source)
                || !GrapnelTridentFlight.valid(frame.owner, frame.spawned, source, frame.pair)) return;
            FLIGHTS.put(frame.spawned, new Flight(frame, source));
        } finally { if (frame.previous == null) RELEASE.remove(); else RELEASE.set(frame.previous); }
    }

    /**
     * Called at the constructor's own return-acceleration query. Do not reroll an existing
     * native Loyalty; when missing, invoke that enchantment's real data-pack effect exactly once.
     * Only a sole return-effect source can prove which enchantment caused a positive result.
     */
    public static int project(ServerLevel level, ItemStack stack, Entity entity, int nativeResult) {
        FusedPair pair = selected(stack);
        if (pair == null || !(entity instanceof ThrownTrident trident) || trident.level() != level) return nativeResult;
        Holder<Enchantment> loyalty = FusionStationOperations.lookupEnchantment(level.registryAccess(), LOYALTY).orElse(null);
        if (loyalty == null) return nativeResult;
        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        int nativeLevel = enchantments.getLevel(loyalty), projectedLevel = pair.primaryLevel(), result = nativeResult;
        if (nativeLevel == 0) {
            for (FusedPair other : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
                if (LOYALTY.equals(other.primaryId())) projectedLevel = Math.max(projectedLevel, other.primaryLevel());
            for (RetainedEnchant retained : stack.getOrDefault(FusionDataComponents.RETAINED_ENCHANTS, List.<RetainedEnchant>of()))
                if (LOYALTY.equals(retained.id())) projectedLevel = Math.max(projectedLevel, retained.level());
            MutableFloat value = new MutableFloat(nativeResult);
            loyalty.value().modifyTridentReturnToOwnerAcceleration(level, projectedLevel, stack, trident, value);
            if (Float.isFinite(value.floatValue())) result = Math.max(0, value.intValue());
        }
        Release frame = RELEASE.get();
        if (frame != null) {
            if (frame.querySeen) frame.ambiguous = true;
            frame.querySeen = true; frame.queried = trident;
            frame.queryProved = frame.owner == trident.getOwner() && frame.level == level && frame.pair.equals(pair)
                && sameExceptWear(frame.snapshot, stack) && unambiguous(stack) && soleReturnEffect(enchantments, loyalty)
                && result > 0 && (nativeLevel > 0 ? nativeLevel == pair.primaryLevel()
                    : nativeResult == 0 && projectedLevel == pair.primaryLevel());
        }
        return result;
    }
    /** Called after the original spawn: a returned Java object alone does not prove world insertion. */
    public static void spawned(Projectile projectile, LivingEntity user, ItemStack consumed) {
        Release frame = RELEASE.get();
        if (frame == null) return;
        if (frame.spawned != null) { frame.ambiguous = true; return; }
        if (!(projectile instanceof ThrownTrident trident) || user != frame.owner || !frame.queryProved
            || frame.queried != trident || !frame.hand.isEmpty() || !sameExceptWear(frame.snapshot, consumed)
            || !ItemStack.matches(trident.getWeaponItem(), consumed)
            || !GrapnelTridentFlight.valid(frame.owner, trident, trident.getWeaponItem(), frame.pair)) return;
        frame.spawned = trident;
    }
    private static boolean valid(ServerPlayer owner, ThrownTrident trident, Flight flight) {
        return ready(owner) && owner.getUUID().equals(flight.owner) && owner.level().dimension().equals(flight.dimension)
            && owner.level().getGameTime() < flight.expires && GrapnelTridentFlight.valid(owner, trident, flight.stack, flight.pair);
    }
    public static @Nullable ReturnTick beginTick(ThrownTrident trident) {
        Flight flight = FLIGHTS.get(trident);
        return flight != null && !flight.returned && trident.getOwner() instanceof ServerPlayer owner && valid(owner, trident, flight)
            ? new ReturnTick(flight, owner, trident) : null;
    }
    public static void endTick(@Nullable ReturnTick tick, boolean completed) {
        if (tick == null || !completed || FLIGHTS.get(tick.trident) != tick.flight || !valid(tick.owner, tick.trident, tick.flight)
            || !tick.trident.isNoPhysics() || tick.count < 0 || tick.count == Integer.MAX_VALUE
            || tick.trident.clientSideReturnTridentTickCount != tick.count + 1) return;
        if (directedReturn(tick.before, tick.trident.position(), tick.eye)) tick.flight.returned = true;
    }
    /** The exact same vanilla return tick must move toward the owner's captured eye, within a bounded step. */
    public static boolean directedReturn(@Nullable Vec3 before, @Nullable Vec3 after, @Nullable Vec3 eye) {
        if (!finite(before) || !finite(after) || !finite(eye)) return false;
        double step = before.distanceToSqr(after), oldDistance = before.distanceToSqr(eye), newDistance = after.distanceToSqr(eye);
        return Double.isFinite(step) && Double.isFinite(oldDistance) && Double.isFinite(newDistance)
            && step > 1.0E-8D && step <= 144.0D && newDistance + 1.0E-8D < oldDistance;
    }
    private static boolean finite(@Nullable Vec3 v) {
        return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }

    /** Scope the owner's real nearby pickup, then wait for its original tryPickup and discard. */
    public static @Nullable Touch beginTouch(ThrownTrident trident, Player player) {
        Touch previous = TOUCH.get(); if (previous != null) previous.ambiguous = true;
        Flight flight = FLIGHTS.get(trident);
        if (!(player instanceof ServerPlayer owner) || flight == null || !flight.returned || !valid(owner, trident, flight)
            || owner.isPassenger() || trident.pickup != AbstractArrow.Pickup.ALLOWED || !trident.isNoPhysics()
            || !owner.getBoundingBox().inflate(1.0D, 0.5D, 1.0D).intersects(trident.getBoundingBox())) return null;
        Touch frame = new Touch(previous, flight, owner, trident); TOUCH.set(frame); return frame;
    }
    public static @Nullable Pickup beginPickup(ThrownTrident trident, Player player) {
        Touch touch = TOUCH.get();
        if (touch == null || touch.trident != trident || touch.owner != player) return null;
        if (touch.pickupSeen) { touch.ambiguous = true; return null; }
        touch.pickupSeen = true;
        if (!valid(touch.owner, trident, touch.flight) || !ItemStack.matches(touch.flight.stack, touch.expected)) return null;
        return new Pickup(touch);
    }
    public static void endPickup(@Nullable Pickup pickup, boolean result, boolean completed) {
        if (pickup == null) return;
        Touch touch = pickup.touch;
        if (TOUCH.get() != touch || !completed || !result || touch.ambiguous || !valid(touch.owner, touch.trident, touch.flight)
            || !ItemStack.matches(touch.flight.stack, touch.expected)) return;
        touch.inserted = pickup.receipt.inserted(touch.owner.getInventory(), touch.expected);
        touch.pickupSucceeded = touch.inserted != null;
    }
    public static void endTouch(@Nullable Touch touch, boolean completed) {
        if (touch == null) return;
        try {
            if (TOUCH.get() != touch || !completed || touch.ambiguous || !touch.pickupSucceeded || touch.inserted == null
                || !touch.trident.isRemoved() || touch.owner.level() != touch.level || touch.level.getGameTime() != touch.tick
                || !ready(touch.owner) || !ItemStack.matches(touch.inserted, touch.expected)
                || !RealInventoryInsertion.unique(touch.owner.getInventory(), touch.inserted)) return;
            GrapnelSkills.markReturned(touch.owner, touch.inserted, touch.flight.pair, touch.owner.position());
        } finally {
            // A full inventory leaves the original projectile alive; a later genuine pickup may succeed.
            if (touch.trident.isRemoved()) FLIGHTS.remove(touch.trident);
            if (touch.previous == null) TOUCH.remove(); else TOUCH.set(touch.previous);
        }
    }
    public static void tickServer(MinecraftServer server) {
        FLIGHTS.entrySet().removeIf(entry -> {
            Flight flight = entry.getValue(); ServerPlayer owner = server.getPlayerList().getPlayer(flight.owner);
            return owner == null || !valid(owner, entry.getKey(), flight);
        });
    }
    public static void clearPlayer(UUID owner) { FLIGHTS.values().removeIf(flight -> flight.owner.equals(owner)); }
    public static void clearServer() { FLIGHTS.clear(); RELEASE.remove(); TOUCH.remove(); }
}

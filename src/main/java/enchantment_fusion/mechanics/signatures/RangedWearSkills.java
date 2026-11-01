package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import enchantment_fusion.model.RetainedEnchant;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.jetbrains.annotations.Nullable;

/** The six selected 45.1 shooting/unbreaking directions. No ammunition is duplicated. */
public final class RangedWearSkills {
    public static final String POWER = "minecraft:power", PUNCH = "minecraft:punch", FLAME = "minecraft:flame";
    public static final String UNBREAKING = "minecraft:unbreaking";
    private static final int MAX_OWNERS = 256, MAX_STATES = 8;
    private record Gear(ItemStack stack, FusedPair pair) {}
    private static final class State {
        final Gear gear; final ResourceKey<Level> dimension;
        int streak, guard, charges; long lastHit, guardUntil, chargeUntil;
        State(Gear gear, ResourceKey<Level> dimension) { this.gear = gear; this.dimension = dimension; }
    }
    private static final Map<UUID, List<State>> STATES = new HashMap<>();
    private static final ThreadLocal<WearFrame> WEAR = new ThreadLocal<>();
    private static final ConfirmedArrow.Listener ARROW_LISTENER = new ConfirmedArrow.Listener() {
        @Override public boolean applies(FusedPair pair, ItemStack launcher) { return RangedWearSkills.applies(pair, launcher); }
        @Override public void hit(ConfirmedArrow.Hit hit) { onHit(hit); }
        @Override public void missed(ServerPlayer owner, ItemStack launcher, FusedPair pair) {
            if (!shootingFirst(pair)) return;
            State state = state(owner, new Gear(launcher, pair), false);
            if (state != null) state.streak = 0;
        }
    };
    private RangedWearSkills() {}
    public static void init() { ConfirmedArrow.register(ARROW_LISTENER); }
    public static boolean shooting(String id) { return POWER.equals(id) || PUNCH.equals(id) || FLAME.equals(id); }
    public static boolean shootingFirst(@Nullable FusedPair pair) {
        return pair != null && shooting(pair.primaryId()) && UNBREAKING.equals(pair.secondaryId());
    }
    public static boolean unbreakingFirst(@Nullable FusedPair pair) {
        return pair != null && UNBREAKING.equals(pair.primaryId()) && shooting(pair.secondaryId());
    }
    /** Explicit data-pack signatures and secondary wildcards retain priority. */
    public static boolean matches(@Nullable FusedPair pair) {
        return (shootingFirst(pair) || unbreakingFirst(pair)) && SignatureCatalog.resolve(pair).isEmpty();
    }
    public static boolean supportsCarrier(@Nullable Set<FusionCarrier> roles) {
        return roles != null && (roles.contains(FusionCarrier.BOW) || roles.contains(FusionCarrier.CROSSBOW));
    }
    public static boolean supportsHost(@Nullable ItemStack stack) {
        return DurabilityEconomy.supportsCarrier(stack) && supportsCarrier(FusionCarrier.classify(stack));
    }
    public static boolean applies(@Nullable FusedPair pair, @Nullable ItemStack stack) { return matches(pair) && supportsHost(stack); }
    public static int primaryLevel(@Nullable ItemStack stack, String id) {
        if (!supportsHost(stack)) return 0;
        int level = 0;
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if (id.equals(pair.primaryId()) && shootingFirst(pair) && applies(pair, stack)) level = Math.max(level, pair.primaryLevel());
        return level;
    }
    private static @Nullable Gear selected(@Nullable ItemStack stack, boolean forward) {
        if (!supportsHost(stack)) return null;
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if ((forward ? shootingFirst(pair) : unbreakingFirst(pair)) && applies(pair, stack)) return new Gear(stack, pair);
        return null;
    }
    private static @Nullable Gear held(ServerPlayer owner, boolean forward) {
        Gear gear = selected(owner.getMainHandItem(), forward);
        return gear != null ? gear : selected(owner.getOffhandItem(), forward);
    }
    private static boolean ready(ServerPlayer owner) { return owner.isAlive() && !owner.isSpectator() && !owner.isCreative(); }
    private static boolean owns(ServerPlayer owner, Gear gear) {
        if (!applies(gear.pair(), gear.stack()) || !gear.stack().getOrDefault(FusionDataComponents.FUSED_PAIRS,
            FusedEnchantmentList.EMPTY).pairs().contains(gear.pair())) return false;
        for (int i = 0; i < owner.getInventory().getContainerSize(); i++) if (owner.getInventory().getItem(i) == gear.stack()) return true;
        return false;
    }
    private static boolean equipped(ServerPlayer owner, ItemStack stack) {
        return owner.getMainHandItem() == stack || owner.getOffhandItem() == stack;
    }
    private static void expire(State state, long now) {
        if (RangedWearMath.expiredStreak(state.lastHit, now)) state.streak = 0;
        if (now >= state.guardUntil) state.guard = 0;
        if (now >= state.chargeUntil) state.charges = 0;
    }
    private static boolean empty(State state) { return state.streak == 0 && state.guard == 0 && state.charges == 0; }
    private static @Nullable State state(ServerPlayer owner, Gear gear, boolean create) {
        List<State> list = STATES.get(owner.getUUID()); long now = owner.level().getGameTime();
        if (list != null) {
            list.removeIf(entry -> {
                if (!ready(owner) || !entry.dimension.equals(owner.level().dimension()) || !owns(owner, entry.gear)) return true;
                expire(entry, now); return empty(entry);
            });
            for (State entry : list) if (entry.gear.stack() == gear.stack() && entry.gear.pair().equals(gear.pair())) return entry;
            if (list.isEmpty()) STATES.remove(owner.getUUID());
        }
        if (!create || !ready(owner) || !owns(owner, gear)) return null;
        if (list == null || list.isEmpty()) {
            if (STATES.size() >= MAX_OWNERS) return null;
            list = new ArrayList<>(); STATES.put(owner.getUUID(), list);
        }
        if (list.size() >= MAX_STATES) list.removeFirst();
        State result = new State(gear, owner.level().dimension()); list.add(result); return result;
    }
    private static void onHit(ConfirmedArrow.Hit hit) {
        FusedPair pair = hit.pair();
        if (!shootingFirst(pair) || !ready(hit.owner()) || !owns(hit.owner(), new Gear(hit.launcher(), pair))) return;
        State state = state(hit.owner(), new Gear(hit.launcher(), pair), true); if (state == null) return;
        int before = state.streak; long now = hit.owner().level().getGameTime();
        state.streak = RangedWearMath.nextStreak(state.streak, state.lastHit, now); state.lastHit = now;
        if (before < RangedWearMath.MIN_HITS && state.streak >= RangedWearMath.MIN_HITS)
            hit.owner().sendSystemMessage(Component.translatable("message.enchantment_fusion.ranged_wear_guard_ready"), true);
    }
    public static boolean hasEquipped(ServerPlayer owner, boolean forward) { return held(owner, forward) != null; }
    public static boolean activate(ServerPlayer owner, boolean forward) {
        Gear gear = held(owner, forward);
        if (!ready(owner) || owner.containerMenu != owner.inventoryMenu || gear == null || !owns(owner, gear)) return false;
        State state = state(owner, gear, false); long now = owner.level().getGameTime();
        if (forward) {
            if (state != null && state.guard > 0) {
                owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.ranged_wear_guard_active"), true); return false;
            }
            if (state == null || state.streak < RangedWearMath.MIN_HITS) {
                owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.ranged_wear_guard_need"), true); return false;
            }
            state.guard = RangedWearMath.guard(state.streak, gear.pair().secondaryLevel());
            state.streak = 0; state.guardUntil = now + RangedWearMath.GUARD_TICKS;
            owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.ranged_wear_guard_set", state.guard), true);
        } else {
            if (state == null || state.charges < 1) {
                owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.ranged_wear_echo_need"), true); return false;
            }
            List<net.minecraft.world.phys.Vec3> path = ConfirmedArrow.latestPath(owner, gear.stack(), gear.pair());
            if (path.isEmpty()) {
                owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.ranged_wear_path_need"), true); return false;
            }
            if (!VirtualArrowEcho.canLaunch(owner)) {
                owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.echo_busy"), true); return false;
            }
            int rank = Math.clamp(gear.pair().secondaryLevel(), 1, 10);
            VirtualArrowEcho.Theme theme = POWER.equals(gear.pair().secondaryId()) ? VirtualArrowEcho.Theme.POWER
                : PUNCH.equals(gear.pair().secondaryId()) ? VirtualArrowEcho.Theme.PUNCH : VirtualArrowEcho.Theme.FLAME;
            float damage = RangedWearMath.echoDamage(theme == VirtualArrowEcho.Theme.POWER, rank);
            // The shared executor cannot create or drop a projectile; consume one observed credit once.
            if (!VirtualArrowEcho.launch(owner, path, damage, theme, rank)) return false;
            state.charges--;
            owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.ranged_wear_echo_set"), true);
        }
        CompendiumDiscoveryTracker.recordDiscovery(owner, gear.pair().primaryId(), gear.pair().secondaryId());
        return true;
    }
    private static boolean unambiguous(ItemStack stack) {
        return stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().stream()
            .filter(pair -> pair.involves(UNBREAKING)).count() == 1
            && stack.getOrDefault(FusionDataComponents.RETAINED_ENCHANTS, List.<RetainedEnchant>of()).stream()
                .noneMatch(retained -> UNBREAKING.equals(retained.id()));
    }
    public static final class WearFrame {
        final @Nullable WearFrame previous; final @Nullable ServerPlayer owner; final ItemStack stack, snapshot;
        final @Nullable Gear forward, reverse; final RealWearObservation observation = new RealWearObservation();
        boolean seenUnbreaking, wrongLevel;
        WearFrame(@Nullable WearFrame previous, ItemStack stack, @Nullable ServerPlayer owner) {
            this.previous = previous; this.owner = owner; this.stack = stack; this.snapshot = stack.copy();
            boolean equipped = owner != null && ready(owner) && !owner.hasInfiniteMaterials() && equipped(owner, stack);
            this.forward = equipped ? selected(stack, true) : null;
            this.reverse = equipped ? selected(stack, false) : null;
        }
    }
    public static WearFrame beginWear(ItemStack stack, @Nullable ServerPlayer owner) {
        WearFrame previous = WEAR.get(); if (previous != null) previous.observation.nestedWear();
        WearFrame frame = new WearFrame(previous, stack, owner); WEAR.set(frame); return frame;
    }
    public static @Nullable WearFrame beginEffect(ItemStack stack) {
        WearFrame frame = WEAR.get();
        if (frame == null || frame.stack != stack || frame.reverse == null) return null;
        frame.observation.enterEffect(); return frame;
    }
    public static void observeEffect(@Nullable WearFrame frame, ServerLevel level, Enchantment enchantment, int rank,
                                     ItemStack stack, float before, float after) {
        if (frame == null || WEAR.get() != frame || frame.stack != stack || frame.owner == null || frame.reverse == null) return;
        if (frame.owner.level() != level) { frame.wrongLevel = true; return; }
        Holder<Enchantment> unbreaking = FusionStationOperations.lookupEnchantment(level.registryAccess(), UNBREAKING).orElse(null);
        boolean ours = unbreaking != null && unbreaking.value() == enchantment;
        if (ours) { frame.seenUnbreaking = true; if (rank != frame.reverse.pair().primaryLevel()) frame.wrongLevel = true; }
        frame.observation.record(ours, before, after);
    }
    public static void endEffect(@Nullable WearFrame frame) { if (frame != null) frame.observation.exitEffect(); }
    /** Apply a missing primary only if no other handler or native effect ran the real roll. */
    public static int applyNonNative(ServerLevel level, ItemStack stack, int amount, @Nullable ServerPlayer owner) {
        WearFrame frame = WEAR.get();
        if (amount <= 0 || frame == null || frame.stack != stack || frame.owner != owner || owner == null || frame.reverse == null
            || frame.seenUnbreaking || !ready(owner) || owner.level() != level || !equipped(owner, stack)) return amount;
        Holder<Enchantment> unbreaking = FusionStationOperations.lookupEnchantment(level.registryAccess(), UNBREAKING).orElse(null);
        if (unbreaking == null || stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(unbreaking) > 0) return amount;
        MutableFloat modified = new MutableFloat(amount);
        unbreaking.value().modifyDurabilityChange(level, frame.reverse.pair().primaryLevel(), stack, modified);
        return Float.isFinite(modified.floatValue()) ? modified.intValue() : amount;
    }
    public static int finishWear(WearFrame frame, int requested, int result) {
        ServerPlayer owner = frame.owner;
        if (WEAR.get() != frame || owner == null || !ready(owner) || owner.hasInfiniteMaterials() || requested <= 0 || result < 0
            || !equipped(owner, frame.stack) || !ItemStack.matches(frame.snapshot, frame.stack)) return result;
        if (frame.reverse != null && !frame.wrongLevel && unambiguous(frame.stack) && owns(owner, frame.reverse)
            // At the natural no-break cap, other reductions can be indistinguishable from Unbreaking.
            && frame.snapshot.getDamageValue() < frame.snapshot.getMaxDamage() - 1) {
            int saved = frame.observation.acceptedSaved(requested, result);
            if (saved > 0) {
                State entry = state(owner, frame.reverse, true);
                if (entry != null) {
                    int before = entry.charges; entry.charges = RangedWearMath.addCharges(entry.charges, saved);
                    entry.chargeUntil = owner.level().getGameTime() + RangedWearMath.CHARGE_TICKS;
                    if (before == 0) owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.ranged_wear_echo_ready"), true);
                }
            }
        }
        if (frame.forward != null && result > 0 && owns(owner, frame.forward)) {
            State entry = state(owner, frame.forward, false);
            if (entry != null) {
                int prevented = Math.min(result, entry.guard); entry.guard -= prevented; result -= prevented;
            }
        }
        return result;
    }
    public static void endWear(WearFrame frame) {
        if (WEAR.get() != frame) { WEAR.remove(); return; }
        if (frame.previous == null) WEAR.remove(); else WEAR.set(frame.previous);
    }
    public static void tickServer(MinecraftServer server) {
        for (var entry : List.copyOf(STATES.entrySet())) {
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
            if (owner == null || !ready(owner)) { STATES.remove(entry.getKey()); continue; }
            long now = owner.level().getGameTime();
            entry.getValue().removeIf(state -> {
                if (!state.dimension.equals(owner.level().dimension()) || !owns(owner, state.gear)) return true;
                expire(state, now); return empty(state);
            });
            if (entry.getValue().isEmpty()) STATES.remove(entry.getKey());
        }
    }
    public static void clearPlayer(UUID id) { STATES.remove(id); }
    public static void clearServer() { STATES.clear(); WEAR.remove(); }
}

package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.MatrixEffectExecutor;
import enchantment_fusion.mechanics.transmute.TransmuteRuntimeSupport;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.jetbrains.annotations.Nullable;

/** The six selected 42.1 melee/unbreaking directions; state is per real player and stack. */
public final class MeleeWearSkills {
    public static final String UNBREAKING = "minecraft:unbreaking";
    private static final int MAX_STATES_PER_PLAYER = 8;
    private record Gear(ItemStack stack, FusedPair pair) {}
    private static final class State {
        final Gear gear;
        final ResourceKey<Level> dimension;
        int streak, guard, shards, armedShards;
        long lastHit, guardUntil, shardUntil, armedUntil;
        State(Gear gear, ResourceKey<Level> dimension) { this.gear = gear; this.dimension = dimension; }
    }
    private static final Map<UUID, List<State>> STATES = new HashMap<>();
    private static final ThreadLocal<Attack> ATTACK = new ThreadLocal<>();
    private static final ThreadLocal<WearFrame> WEAR = new ThreadLocal<>();
    private MeleeWearSkills() {}

    public static void init() {
        ConfirmedMelee.register(MeleeWearSkills::onHit);
        MeleeAttackThemes.registerPrimaryTemplate("melee_wear", (pair, stack) -> isAttackFirst(pair) && applies(pair, stack));
    }
    public static boolean isAttackFirst(@Nullable FusedPair pair) {
        return pair != null && MeleeAttackThemes.isAttack(pair.primaryId()) && UNBREAKING.equals(pair.secondaryId());
    }
    public static boolean isUnbreakingFirst(@Nullable FusedPair pair) {
        return pair != null && UNBREAKING.equals(pair.primaryId()) && MeleeAttackThemes.isAttack(pair.secondaryId());
    }
    /** A data-pack exact signature or secondary wildcard always takes precedence over this template. */
    public static boolean matches(@Nullable FusedPair pair) {
        return (isAttackFirst(pair) || isUnbreakingFirst(pair)) && SignatureCatalog.resolve(pair).isEmpty();
    }
    public static boolean supportsHost(@Nullable ItemStack stack) {
        return DurabilityEconomy.supportsCarrier(stack) && MeleeAttackThemes.supportsWeapon(FusionCarrier.classify(stack));
    }
    public static boolean applies(@Nullable FusedPair pair, @Nullable ItemStack stack) {
        return matches(pair) && supportsHost(stack);
    }
    private static @Nullable Gear selected(@Nullable ItemStack stack, boolean attackFirst) {
        if (!supportsHost(stack)) return null;
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if ((attackFirst ? isAttackFirst(pair) : isUnbreakingFirst(pair)) && applies(pair, stack)) return new Gear(stack, pair);
        return null;
    }
    private static boolean ready(ServerPlayer owner) { return owner.isAlive() && !owner.isSpectator(); }
    private static boolean hasPair(Gear gear) {
        return !gear.stack().isEmpty() && gear.stack().getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY)
            .pairs().contains(gear.pair()) && applies(gear.pair(), gear.stack());
    }
    private static boolean owns(ServerPlayer owner, Gear gear) {
        if (!hasPair(gear)) return false;
        for (int i = 0; i < owner.getInventory().getContainerSize(); i++)
            if (owner.getInventory().getItem(i) == gear.stack()) return true;
        return false;
    }
    private static boolean live(ServerPlayer owner, State state) {
        return ready(owner) && owner.level().dimension().equals(state.dimension) && owns(owner, state.gear);
    }
    private static void expire(State state, long now) {
        long elapsed = now - state.lastHit;
        if (state.streak > 0 && (now < state.lastHit || elapsed < 0 || elapsed > MeleeWearMath.STREAK_TICKS)) state.streak = 0;
        if (now >= state.guardUntil) state.guard = 0;
        if (now >= state.shardUntil) state.shards = 0;
        if (now >= state.armedUntil) state.armedShards = 0;
    }
    private static boolean empty(State state) {
        return state.streak == 0 && state.guard == 0 && state.shards == 0 && state.armedShards == 0;
    }
    private static @Nullable State state(ServerPlayer owner, Gear gear, boolean create) {
        List<State> list = STATES.get(owner.getUUID());
        if (list != null) {
            long now = owner.level().getGameTime();
            list.removeIf(entry -> {
                if (!live(owner, entry)) return true;
                expire(entry, now);
                return empty(entry);
            });
            for (State entry : list) if (entry.gear.stack() == gear.stack() && entry.gear.pair().equals(gear.pair())) return entry;
            if (list.isEmpty()) STATES.remove(owner.getUUID());
        }
        if (!create || !ready(owner) || !owns(owner, gear)) return null;
        list = STATES.computeIfAbsent(owner.getUUID(), ignored -> new ArrayList<>());
        if (list.size() >= MAX_STATES_PER_PLAYER) list.removeFirst();
        State entry = new State(gear, owner.level().dimension()); list.add(entry); return entry;
    }
    public static boolean hasEquipped(ServerPlayer owner, boolean attackFirst) {
        return selected(owner.getMainHandItem(), attackFirst) != null;
    }
    /** Called only after normal server-side skill selection and rate limiting. */
    public static boolean activate(ServerPlayer owner, boolean attackFirst) {
        Gear gear = selected(owner.getMainHandItem(), attackFirst);
        if (!ready(owner) || owner.containerMenu != owner.inventoryMenu || gear == null || !owns(owner, gear)) return false;
        State state = state(owner, gear, false);
        long now = owner.level().getGameTime();
        if (attackFirst) {
            if (state != null && state.guard > 0) {
                owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.melee_wear_guard_active"), true); return false;
            }
            int guard = state == null ? 0 : MeleeWearMath.guardFor(state.streak, gear.pair().secondaryLevel());
            if (guard == 0) {
                owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.melee_wear_guard_need", state == null ? 0 : state.streak), true);
                return false;
            }
            state.streak = 0; state.guard = guard; state.guardUntil = now + MeleeWearMath.GUARD_TICKS;
            owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.melee_wear_guard_set", guard), true);
        } else {
            if (state != null && state.armedShards > 0) {
                owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.melee_wear_edge_active"), true); return false;
            }
            int spent = state == null ? 0 : MeleeWearMath.spendShards(state.shards);
            if (spent == 0) {
                owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.melee_wear_edge_need", state == null ? 0 : state.shards), true);
                return false;
            }
            state.shards -= spent; state.armedShards = spent; state.armedUntil = now + MeleeWearMath.ARMED_TICKS;
            owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.melee_wear_edge_set", spent), true);
        }
        CompendiumDiscoveryTracker.recordDiscovery(owner, gear.pair().primaryId(), gear.pair().secondaryId());
        return true;
    }

    /** One frame around the real attack/stab: a prepared damage bonus must be settled by that same hit. */
    public static final class Attack {
        final @Nullable Attack previous;
        final Player player;
        final ItemStack weapon;
        final Entity target;
        final EquipmentSlot slot;
        final @Nullable Gear forward, reverse;
        final boolean ordinary;
        boolean confirmedForward;
        @Nullable State prepared;
        @Nullable DamageSource preparedSource;
        Attack(@Nullable Attack previous, Player player, ItemStack weapon, Entity target, EquipmentSlot slot) {
            this.previous = previous; this.player = player; this.weapon = weapon; this.target = target; this.slot = slot;
            this.forward = slot == EquipmentSlot.MAINHAND ? selected(weapon, true) : null;
            this.reverse = slot == EquipmentSlot.MAINHAND ? selected(weapon, false) : null;
            this.ordinary = !MatrixEffectExecutor.isExecuting() && !GlazedStrikeEffect.isApplyingBonus()
                && !AmberSealSkill.isApplyingBurst() && !LoyaltyGuard.isExecuting()
                && !Boolean.TRUE.equals(TransmuteRuntimeSupport.IN_CUSTOM_COMBAT.get());
        }
    }
    public static Attack beginAttack(Player player, ItemStack weapon, Entity target, EquipmentSlot slot) {
        Attack frame = new Attack(ATTACK.get(), player, weapon, target, slot); ATTACK.set(frame); return frame;
    }
    public static void endAttack(Attack frame) {
        if (ATTACK.get() != frame) return;
        try {
            if (frame.ordinary && !frame.confirmedForward && frame.forward != null && frame.player instanceof ServerPlayer owner
                && owner.getMainHandItem() == frame.weapon) {
                State state = state(owner, frame.forward, false);
                if (state != null) state.streak = 0;
            }
        } finally {
            if (frame.previous == null) ATTACK.remove(); else ATTACK.set(frame.previous);
        }
    }
    private static boolean direct(ServerPlayer owner, DamageSource source) {
        return source.getEntity() == owner && source.getDirectEntity() == owner
            && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !source.is(DamageTypeTags.BYPASSES_EFFECTS)
            && !source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS) && !MatrixEffectExecutor.isExecuting()
            && !GlazedStrikeEffect.isApplyingBonus() && !AmberSealSkill.isApplyingBurst() && !LoyaltyGuard.isExecuting()
            && !Boolean.TRUE.equals(TransmuteRuntimeSupport.IN_CUSTOM_COMBAT.get());
    }
    /** Add to the real damage calculation; a blocked/cancelled hit leaves the prepared strike armed. */
    public static float modifyDamage(ServerLevel level, ItemStack weapon, Entity victim, DamageSource source, float value) {
        Attack frame = ATTACK.get();
        if (frame == null || !frame.ordinary || frame.reverse == null || frame.weapon != weapon || frame.target != victim
            || !(frame.player instanceof ServerPlayer owner) || !ready(owner) || owner.level() != level
            || owner.getMainHandItem() != weapon || !(victim instanceof LivingEntity target) || !target.isAlive()
            || !direct(owner, source) || !MeleeAttackThemes.eligible(owner, target, frame.reverse.pair().secondaryId())) return value;
        State state = state(owner, frame.reverse, false);
        if (state == null || state.armedShards <= 0) return value;
        float result = value + MeleeWearMath.strikeBonus(frame.reverse.pair().secondaryLevel(), frame.reverse.pair().primaryLevel(), state.armedShards);
        if (!Float.isFinite(result)) return value;
        frame.prepared = state; frame.preparedSource = source;
        return result;
    }
    private static void onHit(ConfirmedMelee.Hit hit) {
        Attack frame = ATTACK.get(); ServerPlayer owner = hit.player();
        if (frame == null || !frame.ordinary || frame.player != owner || frame.weapon != hit.weapon()
            || frame.target != hit.target() || frame.slot != EquipmentSlot.MAINHAND || hit.slot() != EquipmentSlot.MAINHAND
            || owner.getMainHandItem() != hit.weapon() || !hit.eligibleBefore()) return;
        if (frame.reverse != null && frame.prepared != null && frame.preparedSource == hit.source()
            && MeleeAttackThemes.matchesTarget(frame.reverse.pair().secondaryId(), hit.target())) {
            // Consume the specific prepared state whose bonus was included, not a fresh or copied item.
            frame.prepared.armedShards = 0; frame.prepared = null; frame.preparedSource = null;
        }
        Gear gear = frame.forward;
        if (gear == null || !MeleeAttackThemes.matchesTarget(gear.pair().primaryId(), hit.target())) return;
        State entry = state(owner, gear, true); if (entry == null) return;
        int before = entry.streak;
        entry.streak = MeleeWearMath.nextStreak(entry.streak, entry.lastHit, owner.level().getGameTime());
        entry.lastHit = owner.level().getGameTime(); frame.confirmedForward = true;
        if (before < MeleeWearMath.MIN_STREAK && entry.streak >= MeleeWearMath.MIN_STREAK)
            owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.melee_wear_guard_ready"), true);
    }

    /** Nested real wear requests have independent frames; child calls cannot credit their parent. */
    public static final class WearFrame {
        final @Nullable WearFrame previous;
        final ItemStack stack;
        final @Nullable ServerPlayer owner;
        final @Nullable Gear forward, reverse;
        final RealWearObservation observation = new RealWearObservation();
        WearFrame(@Nullable WearFrame previous, ItemStack stack, @Nullable ServerPlayer owner) {
            this.previous = previous; this.stack = stack; this.owner = owner;
            boolean equipped = owner != null && ready(owner) && !owner.hasInfiniteMaterials()
                && DurabilityEconomy.actuallyEquipped(owner, stack);
            this.forward = equipped ? selected(stack, true) : null;
            this.reverse = equipped ? selected(stack, false) : null;
        }
    }
    public static WearFrame beginWear(ItemStack stack, @Nullable ServerPlayer owner) {
        WearFrame previous = WEAR.get();
        if (previous != null) previous.observation.nestedWear();
        WearFrame frame = new WearFrame(previous, stack, owner); WEAR.set(frame); return frame;
    }
    /** A real effect may re-enter another effect; conservatively reject ambiguous attribution. */
    public static @Nullable WearFrame beginEffect(ItemStack stack) {
        WearFrame frame = WEAR.get();
        if (frame == null || frame.stack != stack || frame.reverse == null) return null;
        frame.observation.enterEffect();
        return frame;
    }
    public static void observedEffect(@Nullable WearFrame frame, ServerLevel level, Enchantment enchantment,
                                      ItemStack stack, float before, float after) {
        if (frame == null || WEAR.get() != frame || frame.stack != stack || frame.owner == null
            || frame.owner.level() != level || frame.reverse == null) return;
        Holder<Enchantment> unbreaking = FusionStationOperations.lookupEnchantment(level.registryAccess(), UNBREAKING).orElse(null);
        frame.observation.record(unbreaking != null && unbreaking.value() == enchantment, before, after);
    }
    public static void endEffect(@Nullable WearFrame frame) { if (frame != null) frame.observation.exitEffect(); }
    /** Replay missing Unbreaking once, never after another dedicated handler has already replayed it. */
    public static int applyNonNative(ServerLevel level, ItemStack stack, int amount, @Nullable ServerPlayer owner) {
        WearFrame frame = WEAR.get();
        if (amount <= 0 || frame == null || frame.stack != stack || frame.owner != owner || frame.reverse == null
            || owner == null || !ready(owner) || !DurabilityEconomy.actuallyEquipped(owner, stack)
            || DurabilityEconomy.selected(stack, false) != null || DurabilityReserveSkills.selected(stack) != null) return amount;
        Holder<Enchantment> unbreaking = FusionStationOperations.lookupEnchantment(level.registryAccess(), UNBREAKING).orElse(null);
        if (unbreaking == null || stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(unbreaking) > 0) return amount;
        MutableFloat modified = new MutableFloat(amount);
        unbreaking.value().modifyDurabilityChange(level, frame.reverse.pair().primaryLevel(), stack, modified);
        return Float.isFinite(modified.floatValue()) ? modified.intValue() : amount;
    }
    public static int finishWear(WearFrame frame, int requested, int result) {
        ServerPlayer owner = frame.owner;
        if (owner == null || !ready(owner) || owner.hasInfiniteMaterials() || requested <= 0 || result < 0
            || !DurabilityEconomy.actuallyEquipped(owner, frame.stack)) return result;
        if (frame.reverse != null && owns(owner, frame.reverse)) {
            int saved = frame.observation.acceptedSaved(requested, result);
            if (saved > 0) {
                State entry = state(owner, frame.reverse, true);
                if (entry != null) {
                    int before = entry.shards;
                    entry.shards = MeleeWearMath.addShards(entry.shards, saved);
                    entry.shardUntil = owner.level().getGameTime() + MeleeWearMath.SHARD_TICKS;
                    if (before < MeleeWearMath.MIN_SHARDS && entry.shards >= MeleeWearMath.MIN_SHARDS)
                        owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.melee_wear_edge_ready"), true);
                }
            }
        }
        if (frame.forward != null && result > 0 && owns(owner, frame.forward)) {
            State entry = state(owner, frame.forward, false);
            if (entry != null) {
                int blocked = MeleeWearMath.preventedWear(result, entry.guard);
                entry.guard -= blocked; result -= blocked;
            }
        }
        return result;
    }
    public static void endWear(WearFrame frame) {
        if (WEAR.get() != frame) return;
        if (frame.previous == null) WEAR.remove(); else WEAR.set(frame.previous);
    }
    public static void tickServer(MinecraftServer server) {
        for (ServerPlayer owner : server.getPlayerList().getPlayers()) {
            List<State> list = STATES.get(owner.getUUID()); if (list == null) continue;
            long now = owner.level().getGameTime();
            list.removeIf(entry -> {
                if (!live(owner, entry)) return true;
                expire(entry, now); return empty(entry);
            });
            if (list.isEmpty()) STATES.remove(owner.getUUID());
        }
    }
    public static void clearPlayer(UUID id) { if (id != null) STATES.remove(id); }
    public static void clearServer() { STATES.clear(); ATTACK.remove(); WEAR.remove(); }
}

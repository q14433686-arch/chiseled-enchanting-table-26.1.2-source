package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** The six ordered Power/Punch/Flame and Efficiency templates. */
public final class RangedWorkSkills {
    public static final String EFFICIENCY = "minecraft:efficiency";
    private static final int MAX_OWNERS = 256, MAX_STATES = 8;
    private static final Identifier SPEED = EnchantmentFusion.id("ranged_work_speed");
    record Gear(ItemStack stack, FusedPair pair) {}
    private static final class State {
        final Gear gear; final ResourceKey<Level> dimension;
        int streak, edges; long lastHit, edgeUntil;
        State(Gear gear, ResourceKey<Level> dimension) { this.gear = gear; this.dimension = dimension; }
    }
    private record Boost(Gear gear, ResourceKey<Level> dimension, long until, double multiplier) {}
    private record Armed(Gear gear, ResourceKey<Level> dimension, long until, int edges) {}
    private static final Map<UUID, List<State>> STATES = new HashMap<>();
    private static final Map<UUID, Boost> BOOSTS = new HashMap<>();
    private static final Map<UUID, Armed> ARMED = new HashMap<>();
    private static final ConfirmedArrow.Listener HIT_LISTENER = new ConfirmedArrow.Listener() {
        @Override public boolean applies(FusedPair pair, ItemStack launcher) { return RangedWorkSkills.applies(pair, launcher) && shootingFirst(pair); }
        @Override public void hit(ConfirmedArrow.Hit hit) { onHit(hit); }
        @Override public void missed(ServerPlayer owner, ItemStack launcher, FusedPair pair) {
            State state = state(owner, new Gear(launcher, pair), false);
            if (state != null) state.streak = 0;
        }
    };
    private static final ConfirmedArrow.InsertionListener INSERTION_LISTENER = new ConfirmedArrow.InsertionListener() {
        @Override public @Nullable Object capture(ServerPlayer owner, ItemStack launcher) {
            Armed armed = ARMED.get(owner.getUUID());
            return armed != null && validArmed(owner, armed) && RangedWearSkills.supportsCarrier(FusionCarrier.classify(launcher)) ? armed : null;
        }
        @Override public void inserted(ServerPlayer owner, ItemStack launcher, AbstractArrow arrow, Object captured) {
            if (!(captured instanceof Armed armed) || ARMED.get(owner.getUUID()) != armed || !validArmed(owner, armed)
                || arrow.getOwner() != owner || owner.level().getEntity(arrow.getUUID()) != arrow
                || !RangedWorkBlade.canArm(owner)) return;
            ARMED.remove(owner.getUUID()); // A genuine inserted central arrow spends the preparation once.
            RangedWorkBlade.arm(owner, arrow, armed.gear(), armed.edges());
            CompendiumDiscoveryTracker.recordDiscovery(owner, armed.gear().pair().primaryId(), armed.gear().pair().secondaryId());
        }
    };
    private static final ConfirmedMining.Listener MINING_LISTENER = RangedWorkSkills::onMined;
    private RangedWorkSkills() {}
    public static void init() {
        ConfirmedArrow.register(HIT_LISTENER);
        ConfirmedArrow.registerInsertion(INSERTION_LISTENER);
        ConfirmedMining.register(MINING_LISTENER);
    }
    public static boolean shootingFirst(@Nullable FusedPair pair) {
        return pair != null && RangedWearSkills.shooting(pair.primaryId()) && EFFICIENCY.equals(pair.secondaryId());
    }
    public static boolean miningFirst(@Nullable FusedPair pair) {
        return pair != null && EFFICIENCY.equals(pair.primaryId()) && RangedWearSkills.shooting(pair.secondaryId());
    }
    /** Exact and wildcard data-pack signatures, including retained Multishot, always win. */
    public static boolean matches(@Nullable FusedPair pair) {
        return (shootingFirst(pair) || miningFirst(pair)) && SignatureCatalog.resolve(pair).isEmpty();
    }
    public static boolean supportsCarrier(@Nullable FusedPair pair, @Nullable Set<FusionCarrier> roles) {
        return roles != null && (shootingFirst(pair) ? RangedWearSkills.supportsCarrier(roles)
            : miningFirst(pair) && MeleeWorkSkills.supportsTool(roles));
    }
    public static boolean applies(@Nullable FusedPair pair, @Nullable ItemStack stack) {
        return matches(pair) && stack != null && !stack.isEmpty() && supportsCarrier(pair, FusionCarrier.classify(stack));
    }
    public static int primaryLevel(@Nullable ItemStack stack, String id) {
        if (stack == null || stack.isEmpty()) return 0;
        int result = 0;
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if (id.equals(pair.primaryId()) && shootingFirst(pair) && applies(pair, stack)) result = Math.max(result, pair.primaryLevel());
        return result;
    }
    private static @Nullable Gear selected(@Nullable ItemStack stack, boolean forward) {
        if (stack == null || stack.isEmpty()) return null;
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if ((forward ? shootingFirst(pair) : miningFirst(pair)) && applies(pair, stack)) return new Gear(stack, pair);
        return null;
    }
    private static @Nullable Gear held(ServerPlayer player, boolean forward) {
        Gear gear = selected(player.getMainHandItem(), forward);
        return gear != null || !forward ? gear : selected(player.getOffhandItem(), true);
    }
    static boolean ready(ServerPlayer player) { return player.isAlive() && !player.isSpectator() && !player.isCreative(); }
    static boolean owns(ServerPlayer player, Gear gear) {
        if (!applies(gear.pair(), gear.stack()) || !gear.stack().getOrDefault(FusionDataComponents.FUSED_PAIRS,
            FusedEnchantmentList.EMPTY).pairs().contains(gear.pair())) return false;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) if (player.getInventory().getItem(i) == gear.stack()) return true;
        return false;
    }
    private static void expire(State state, long now) {
        if (RangedWearMath.expiredStreak(state.lastHit, now)) state.streak = 0;
        if (now >= state.edgeUntil) state.edges = 0;
    }
    private static @Nullable State state(ServerPlayer owner, Gear gear, boolean create) {
        List<State> list = STATES.get(owner.getUUID()); long now = owner.level().getGameTime();
        if (list != null) {
            list.removeIf(entry -> {
                if (!ready(owner) || !entry.dimension.equals(owner.level().dimension()) || !owns(owner, entry.gear)) return true;
                expire(entry, now); return entry.streak == 0 && entry.edges == 0;
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
        Gear gear = new Gear(hit.launcher(), hit.pair());
        if (!ready(hit.owner()) || !owns(hit.owner(), gear)) return;
        State state = state(hit.owner(), gear, true); if (state == null) return;
        int before = state.streak; long now = hit.owner().level().getGameTime();
        state.streak = RangedWearMath.nextStreak(state.streak, state.lastHit, now); state.lastHit = now;
        if (before < RangedWearMath.MIN_HITS && state.streak >= RangedWearMath.MIN_HITS)
            hit.owner().sendSystemMessage(Component.translatable("message.enchantment_fusion.ranged_work_ready"), true);
    }
    private static boolean validBoost(ServerPlayer player, Boost boost) {
        return ready(player) && player.level().dimension().equals(boost.dimension())
            && player.level().getGameTime() < boost.until() && owns(player, boost.gear());
    }
    private static boolean validArmed(ServerPlayer player, Armed armed) {
        return ready(player) && player.level().dimension().equals(armed.dimension())
            && player.level().getGameTime() < armed.until() && owns(player, armed.gear());
    }
    private static void onMined(ServerLevel level, ServerPlayer player, ItemStack tool, BlockPos pos, BlockState block) {
        Boost boost = BOOSTS.get(player.getUUID());
        if (boost != null && validBoost(player, boost)) {
            AttributeInstance speed = player.getAttribute(Attributes.BLOCK_BREAK_SPEED);
            if (speed != null && MiningWorkSupport.modifier(SPEED, boost.multiplier()).equals(speed.getModifier(SPEED))) {
                BOOSTS.remove(player.getUUID()); MiningWorkSupport.remove(player, SPEED, boost.multiplier());
            }
        }
        Gear gear = selected(tool, false); if (gear == null || !owns(player, gear)) return;
        State state = state(player, gear, true); if (state == null) return;
        state.edges = RangedWorkMath.addEdge(state.edges); state.edgeUntil = level.getGameTime() + RangedWorkMath.EDGE_TICKS;
    }
    public static boolean hasEquipped(ServerPlayer player, boolean forward) { return held(player, forward) != null; }
    public static boolean activate(ServerPlayer player, boolean forward) {
        if (!ready(player) || player.containerMenu != player.inventoryMenu) return false;
        Gear gear = held(player, forward); if (gear == null || !owns(player, gear)) return false;
        long now = player.level().getGameTime();
        if (forward) {
            Boost old = BOOSTS.get(player.getUUID());
            if (old != null && !validBoost(player, old)) { BOOSTS.remove(player.getUUID()); MiningWorkSupport.remove(player, SPEED, old.multiplier()); old = null; }
            if (old != null) { message(player, "message.enchantment_fusion.ranged_work_active"); return false; }
            State state = state(player, gear, false);
            if (state == null || state.streak < RangedWearMath.MIN_HITS) { message(player, "message.enchantment_fusion.ranged_work_need_hits"); return false; }
            if (BOOSTS.size() >= MAX_OWNERS) return false;
            Boost boost = new Boost(gear, player.level().dimension(), now + RangedWorkMath.PREPARED_TICKS,
                RangedWorkMath.speed(gear.pair().secondaryLevel(), state.streak));
            if (!MiningWorkSupport.refresh(player, SPEED, boost.multiplier(), false)) return false;
            BOOSTS.put(player.getUUID(), boost); state.streak = 0; message(player, "message.enchantment_fusion.ranged_work_set");
            CompendiumDiscoveryTracker.recordDiscovery(player, gear.pair().primaryId(), gear.pair().secondaryId());
        } else {
            Armed old = ARMED.get(player.getUUID());
            if (old != null && !validArmed(player, old)) { ARMED.remove(player.getUUID()); old = null; }
            if (old != null) { message(player, "message.enchantment_fusion.ranged_work_blade_active"); return false; }
            State state = state(player, gear, false);
            if (state == null || state.edges <= 0) { message(player, "message.enchantment_fusion.ranged_work_need_mining"); return false; }
            if (ARMED.size() >= MAX_OWNERS) return false;
            ARMED.put(player.getUUID(), new Armed(gear, player.level().dimension(), now + RangedWorkMath.PREPARED_TICKS, state.edges));
            state.edges = 0; message(player, "message.enchantment_fusion.ranged_work_blade_set");
        }
        return true;
    }
    private static void message(ServerPlayer player, String key) {
        player.sendSystemMessage(Component.translatable(key), true);
    }
    public static void tickServer(MinecraftServer server) {
        for (var entry : List.copyOf(BOOSTS.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey()); Boost boost = entry.getValue();
            if (player == null) { BOOSTS.remove(entry.getKey()); continue; }
            if (!validBoost(player, boost) || !MiningWorkSupport.refresh(player, SPEED, boost.multiplier(), false)) {
                BOOSTS.remove(entry.getKey()); MiningWorkSupport.remove(player, SPEED, boost.multiplier());
            }
        }
        for (var entry : List.copyOf(ARMED.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !validArmed(player, entry.getValue())) ARMED.remove(entry.getKey());
        }
        for (var entry : List.copyOf(STATES.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !ready(player)) { STATES.remove(entry.getKey()); continue; }
            long now = player.level().getGameTime();
            entry.getValue().removeIf(value -> {
                if (!value.dimension.equals(player.level().dimension()) || !owns(player, value.gear)) return true;
                expire(value, now); return value.streak == 0 && value.edges == 0;
            });
            if (entry.getValue().isEmpty()) STATES.remove(entry.getKey());
        }
        RangedWorkBlade.tickServer(server);
    }
    public static void clearPlayer(ServerPlayer player) {
        Boost boost = BOOSTS.remove(player.getUUID()); if (boost != null) MiningWorkSupport.remove(player, SPEED, boost.multiplier());
        ARMED.remove(player.getUUID()); STATES.remove(player.getUUID()); RangedWorkBlade.clearPlayer(player.getUUID());
    }
    public static void clearServer(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) clearPlayer(player);
        BOOSTS.clear(); ARMED.clear(); STATES.clear(); RangedWorkBlade.clear();
    }
}

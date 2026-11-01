package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** 44.2: one marked damage diversion, or real orb repair converted into a prepared blade. */
public final class MeleeMendingSkills {
    private static final String MENDING = DurabilityEconomy.MENDING;
    private static final int MAX_OWNERS = 256, MAX_STATES = 8;
    private record Gear(ItemStack stack, FusedPair pair) {}
    private static final class State {
        final Gear gear; final ResourceKey<Level> dimension;
        @Nullable UUID target; long markUntil, forgeUntil, armedUntil;
        int forge, armed;
        State(ServerPlayer player, Gear gear) { this.gear = gear; this.dimension = player.level().dimension(); }
    }
    private static final Map<UUID, List<State>> STATES = new HashMap<>();
    private static final ThreadLocal<Damage> DAMAGE = new ThreadLocal<>();
    private MeleeMendingSkills() {}
    public static void init() {
        ConfirmedMelee.register(MeleeMendingSkills::onHit);
        MeleeAttackThemes.registerPrimaryTemplate("melee_mending", (pair, stack) -> attackFirst(pair) && applies(pair, stack));
    }
    public static boolean attackFirst(@Nullable FusedPair pair) {
        return pair != null && MeleeAttackThemes.isAttack(pair.primaryId()) && MENDING.equals(pair.secondaryId());
    }
    public static boolean mendingFirst(@Nullable FusedPair pair) {
        return pair != null && MENDING.equals(pair.primaryId()) && MeleeAttackThemes.isAttack(pair.secondaryId());
    }
    public static boolean matches(@Nullable FusedPair pair) {
        if (!(attackFirst(pair) || mendingFirst(pair))) return false;
        // The selected 29.2 Sharpness/Mending design outranks this promoted template,
        // even though its forward direction has no exact signature in the default catalog.
        String attack = attackFirst(pair) ? pair.primaryId() : pair.secondaryId();
        return !MeleeAttackThemes.SHARP.equals(attack) && SignatureCatalog.resolve(pair).isEmpty();
    }
    public static boolean supportsCarrier(@Nullable ItemStack stack) {
        return DurabilityEconomy.supportsCarrier(stack) && MeleeAttackThemes.supportsWeapon(FusionCarrier.classify(stack));
    }
    public static boolean applies(@Nullable FusedPair pair, @Nullable ItemStack stack) {
        return matches(pair) && supportsCarrier(stack);
    }
    private static @Nullable Gear selected(@Nullable ItemStack stack, boolean reverse) {
        if (!supportsCarrier(stack)) return null;
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs()) {
            if ((reverse ? mendingFirst(pair) : attackFirst(pair)) && applies(pair, stack)) return new Gear(stack, pair);
        }
        return null;
    }
    /** Highest eligible primary for once-only non-native XP repair. */
    public static @Nullable FusedPair nonNativeMending(@Nullable ItemStack stack) {
        if (!supportsCarrier(stack)) return null;
        FusedPair best = null;
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs()) {
            if (mendingFirst(pair) && applies(pair, stack) && (best == null || pair.primaryLevel() > best.primaryLevel())) best = pair;
        }
        return best;
    }
    private static boolean ready(ServerPlayer player) { return player.isAlive() && !player.isSpectator() && !player.isCreative(); }
    private static boolean owns(ServerPlayer player, Gear gear) {
        if (!applies(gear.pair(), gear.stack()) || !gear.stack().getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY)
            .pairs().contains(gear.pair())) return false;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) if (player.getInventory().getItem(i) == gear.stack()) return true;
        return false;
    }
    private static void expire(State state, long now) {
        if (now >= state.markUntil) state.target = null;
        if (now >= state.forgeUntil) state.forge = 0;
        if (now >= state.armedUntil) state.armed = 0;
    }
    private static @Nullable State state(ServerPlayer player, Gear gear, boolean create) {
        List<State> entries = STATES.get(player.getUUID()); long now = player.level().getGameTime();
        if (entries != null) {
            entries.removeIf(entry -> {
                if (!ready(player) || !entry.dimension.equals(player.level().dimension()) || !owns(player, entry.gear)) return true;
                expire(entry, now); return entry.target == null && entry.forge == 0 && entry.armed == 0;
            });
            for (State entry : entries) if (entry.gear.stack() == gear.stack() && entry.gear.pair().equals(gear.pair())) return entry;
            if (entries.isEmpty()) STATES.remove(player.getUUID());
        }
        if (!create || !ready(player) || !owns(player, gear)) return null;
        if (entries == null || entries.isEmpty()) {
            if (STATES.size() >= MAX_OWNERS) return null;
            entries = new ArrayList<>(); STATES.put(player.getUUID(), entries);
        }
        if (entries.size() >= MAX_STATES) entries.removeFirst();
        State entry = new State(player, gear); entries.add(entry); return entry;
    }
    /** Nested frame carries proof that THIS direct attack actually had its raw damage reduced. */
    public static final class Damage {
        private final @Nullable Damage previous; private final @Nullable ConfirmedMelee.Observation observation;
        private final @Nullable State diverted; private final float damage;
        private Damage(@Nullable Damage previous, @Nullable ConfirmedMelee.Observation observation, @Nullable State diverted, float damage) {
            this.previous = previous; this.observation = observation; this.diverted = diverted; this.damage = damage;
        }
        public float damage() { return damage; }
    }
    public static Damage beginDamage(@Nullable ConfirmedMelee.Observation observation, float damage) {
        State diverted = null;
        if (observation != null && observation.slot() == EquipmentSlot.MAINHAND && observation.eligibleBefore()
            && ready(observation.player()) && observation.player().getMainHandItem() == observation.weapon()
            && Float.isFinite(damage) && damage > 0 && observation.weapon().isDamaged()) {
            Gear gear = selected(observation.weapon(), false);
            if (gear != null && MeleeAttackThemes.matchesTarget(gear.pair().primaryId(), observation.target())) {
                State current = state(observation.player(), gear, false);
                if (current != null && observation.target().getUUID().equals(current.target)) diverted = current;
            }
        }
        float adjusted = diverted == null ? damage : MeleeMendingMath.divertedDamage(damage, diverted.gear.pair().secondaryLevel());
        Damage frame = new Damage(DAMAGE.get(), observation, diverted, adjusted); DAMAGE.set(frame); return frame;
    }
    public static void endDamage(Damage frame) {
        if (DAMAGE.get() != frame) return;
        if (frame.previous == null) DAMAGE.remove(); else DAMAGE.set(frame.previous);
    }
    private static void onHit(ConfirmedMelee.Hit hit) {
        Damage frame = DAMAGE.get(); ServerPlayer player = hit.player();
        if (frame == null || frame.observation == null || frame.observation.player() != player
            || frame.observation.weapon() != hit.weapon() || frame.observation.target() != hit.target()
            || hit.slot() != EquipmentSlot.MAINHAND || !hit.eligibleBefore() || !ready(player)
            || player.getMainHandItem() != hit.weapon()) return;
        Gear forward = selected(hit.weapon(), false);
        if (forward != null && MeleeAttackThemes.matchesTarget(forward.pair().primaryId(), hit.target())) {
            if (frame.diverted != null && frame.diverted.gear.stack() == hit.weapon()
                && frame.diverted.gear.pair().equals(forward.pair()) && hit.target().getUUID().equals(frame.diverted.target)
                && owns(player, forward)) {
                frame.diverted.target = null;
                int before = hit.weapon().getDamageValue();
                int repair = MeleeMendingMath.repair(hit.actualLoss(), forward.pair().secondaryLevel(), before);
                if (repair > 0) hit.weapon().setDamageValue(before - repair);
                CompendiumDiscoveryTracker.recordDiscovery(player, forward.pair().primaryId(), forward.pair().secondaryId());
            } else if (hit.full() && hit.target().isAlive()) {
                State entry = state(player, forward, true);
                if (entry != null) { entry.target = hit.target().getUUID(); entry.markUntil = player.level().getGameTime() + MeleeMendingMath.MARK_TICKS; }
            }
        }
        Gear reverse = selected(hit.weapon(), true);
        if (reverse == null || !hit.full() || !MeleeAttackThemes.matchesTarget(reverse.pair().secondaryId(), hit.target())) return;
        State entry = state(player, reverse, false); if (entry == null || entry.armed <= 0) return;
        int spent = entry.armed; entry.armed = 0;
        VirtualBlade.cast(player, player.getLookAngle(), hit.target(), 5.0D,
            MeleeMendingMath.bladeDamage(reverse.pair().secondaryLevel(), reverse.pair().primaryLevel(), spent), 8,
            target -> MeleeAttackThemes.eligible(player, target, reverse.pair().secondaryId()), ParticleTypes.CRIT);
        CompendiumDiscoveryTracker.recordDiscovery(player, reverse.pair().primaryId(), reverse.pair().secondaryId());
    }
    /** Called after a real orb repair, not for direct setDamageValue or generated XP. */
    public static void afterOrbRepair(ServerPlayer player, ItemStack stack, int actualRepair) {
        if (!ready(player) || actualRepair <= 0 || (player.getMainHandItem() != stack && player.getOffhandItem() != stack)) return;
        Gear gear = selected(stack, true); if (gear == null || !owns(player, gear)) return;
        State entry = state(player, gear, true); if (entry == null) return;
        entry.forge = MeleeMendingMath.addForge(entry.forge, actualRepair);
        entry.forgeUntil = player.level().getGameTime() + MeleeMendingMath.FORGE_TICKS;
    }
    public static boolean hasEquipped(ServerPlayer player) { return selected(player.getMainHandItem(), true) != null; }
    public static boolean activate(ServerPlayer player) {
        if (!ready(player) || player.containerMenu != player.inventoryMenu) return false;
        Gear gear = selected(player.getMainHandItem(), true); if (gear == null || !owns(player, gear)) return false;
        State entry = state(player, gear, false);
        if (entry == null || entry.forge <= 0 || entry.armed > 0) {
            player.sendSystemMessage(Component.translatable("message.enchantment_fusion.melee_mending_need"), true); return false;
        }
        int spent = MeleeMendingMath.spend(entry.forge); entry.forge -= spent; entry.armed = spent;
        entry.armedUntil = player.level().getGameTime() + MeleeMendingMath.ARMED_TICKS;
        player.sendSystemMessage(Component.translatable("message.enchantment_fusion.melee_mending_set"), true); return true;
    }
    public static void tickServer(MinecraftServer server) {
        for (var entry : List.copyOf(STATES.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !ready(player)) { STATES.remove(entry.getKey()); continue; }
            long now = player.level().getGameTime();
            entry.getValue().removeIf(value -> {
                if (!value.dimension.equals(player.level().dimension()) || !owns(player, value.gear)) return true;
                expire(value, now); return value.target == null && value.forge == 0 && value.armed == 0;
            });
            if (entry.getValue().isEmpty()) STATES.remove(entry.getKey());
        }
    }
    public static void clearPlayer(UUID player) { STATES.remove(player); }
    public static void clearServer() { STATES.clear(); DAMAGE.remove(); }
}

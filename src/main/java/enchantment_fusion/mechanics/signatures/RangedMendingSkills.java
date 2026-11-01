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
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Real-arrow kill XP route, or a real Mending repair spent on one later real arrow. */
public final class RangedMendingSkills {
    public static final String MENDING = DurabilityEconomy.MENDING;
    private static final int MAX_OWNERS = 256, MAX_STATES = 8, MAX_FLIGHTS = 1024;
    private record Gear(ItemStack stack, FusedPair pair) {}
    private static final class State {
        final Gear gear; final ResourceKey<Level> dimension;
        int forge, armed; long forgeUntil, armedUntil;
        State(ServerPlayer owner, Gear gear) { this.gear = gear; this.dimension = owner.level().dimension(); }
    }
    private static final class Shot {
        final UUID owner; final ResourceKey<Level> dimension; final Gear gear; final int spent; final long born;
        @Nullable UUID hitTarget;
        Shot(ServerPlayer owner, Gear gear, int spent) {
            this.owner = owner.getUUID(); this.dimension = owner.level().dimension(); this.gear = gear;
            this.spent = spent; this.born = owner.level().getGameTime();
        }
    }
    private static final Map<UUID, List<State>> STATES = new HashMap<>();
    private static final Map<AbstractArrow, Shot> SHOTS = new WeakHashMap<>();
    private static final ConfirmedArrow.Listener HITS = new ConfirmedArrow.Listener() {
        @Override public boolean applies(FusedPair pair, ItemStack launcher) { return RangedMendingSkills.applies(pair, launcher); }
        @Override public void hit(ConfirmedArrow.Hit hit) { afterHit(hit); }
        @Override public void missed(ServerPlayer owner, ItemStack launcher, FusedPair pair, AbstractArrow arrow) { SHOTS.remove(arrow); }
    };
    private static final ConfirmedArrow.InsertionListener INSERTIONS = new ConfirmedArrow.InsertionListener() {
        @Override public @Nullable Object capture(ServerPlayer owner, ItemStack launcher) {
            Gear gear = selected(launcher, true); if (gear == null) return null;
            State state = state(owner, gear, false);
            return state != null && state.armed > 0 ? state : null;
        }
        @Override public void inserted(ServerPlayer owner, ItemStack launcher, AbstractArrow arrow, Object raw) {
            if (!(raw instanceof State state) || state.gear.stack() != launcher || state.armed <= 0
                || state(owner, state.gear, false) != state || arrow.getOwner() != owner
                || owner.level().getEntity(arrow.getUUID()) != arrow) return;
            if (SHOTS.size() >= MAX_FLIGHTS) {
                owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.ranged_mending_full"), true); return;
            }
            int spent = state.armed; state.armed = 0;
            SHOTS.put(arrow, new Shot(owner, state.gear, spent));
            CompendiumDiscoveryTracker.recordDiscovery(owner, state.gear.pair().primaryId(), state.gear.pair().secondaryId());
        }
    };
    private RangedMendingSkills() {}
    public static void init() { ConfirmedArrow.register(HITS); ConfirmedArrow.registerInsertion(INSERTIONS); }
    public static boolean shootingFirst(@Nullable FusedPair pair) {
        return pair != null && RangedWearSkills.shooting(pair.primaryId()) && MENDING.equals(pair.secondaryId());
    }
    public static boolean mendingFirst(@Nullable FusedPair pair) {
        return pair != null && MENDING.equals(pair.primaryId()) && RangedWearSkills.shooting(pair.secondaryId());
    }
    public static boolean matches(@Nullable FusedPair pair) {
        return (shootingFirst(pair) || mendingFirst(pair)) && SignatureCatalog.resolve(pair).isEmpty();
    }
    public static boolean supportsCarrier(@Nullable Set<FusionCarrier> roles) {
        return RangedWearSkills.supportsCarrier(roles);
    }
    public static boolean supportsCarrier(@Nullable ItemStack stack) {
        return DurabilityEconomy.supportsCarrier(stack) && supportsCarrier(FusionCarrier.classify(stack));
    }
    public static boolean applies(@Nullable FusedPair pair, @Nullable ItemStack stack) {
        return matches(pair) && supportsCarrier(stack);
    }
    public static int primaryLevel(@Nullable ItemStack stack, String id) {
        if (stack == null || stack.isEmpty()) return 0; int result = 0;
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if (shootingFirst(pair) && id.equals(pair.primaryId()) && applies(pair, stack)) result = Math.max(result, pair.primaryLevel());
        return result;
    }
    private static @Nullable Gear selected(@Nullable ItemStack stack, boolean reverse) {
        if (!supportsCarrier(stack) || stack.isBroken()) return null; FusedPair best = null;
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if ((reverse ? mendingFirst(pair) : shootingFirst(pair)) && applies(pair, stack)
                && (best == null || pair.primaryLevel() > best.primaryLevel())) best = pair;
        return best == null ? null : new Gear(stack, best);
    }
    public static @Nullable FusedPair nonNativeMending(@Nullable ItemStack stack) {
        Gear gear = selected(stack, true); return gear == null ? null : gear.pair();
    }
    static boolean ready(ServerPlayer owner) {
        return owner.isAlive() && !owner.isSpectator() && !owner.isCreative() && !owner.hasInfiniteMaterials();
    }
    static boolean owns(ServerPlayer owner, ItemStack stack, FusedPair pair) {
        if (!ready(owner) || !applies(pair, stack) || stack.isBroken()
            || !stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().contains(pair)) return false;
        int matches = 0;
        for (int i = 0; i < owner.getInventory().getContainerSize(); i++) if (owner.getInventory().getItem(i) == stack) matches++;
        return matches == 1;
    }
    private static boolean held(ServerPlayer owner, ItemStack stack) {
        return owner.getMainHandItem() == stack || owner.getOffhandItem() == stack;
    }
    private static void expire(State state, long now) {
        if (now >= state.forgeUntil) state.forge = 0;
        if (now >= state.armedUntil) state.armed = 0;
    }
    private static @Nullable State state(ServerPlayer owner, Gear gear, boolean create) {
        List<State> entries = STATES.get(owner.getUUID()); long now = owner.level().getGameTime();
        if (entries != null) {
            entries.removeIf(entry -> {
                if (!entry.dimension.equals(owner.level().dimension()) || !owns(owner, entry.gear.stack(), entry.gear.pair())) return true;
                expire(entry, now); return entry.forge <= 0 && entry.armed <= 0;
            });
            for (State entry : entries) if (entry.gear.stack() == gear.stack() && entry.gear.pair().equals(gear.pair())) return entry;
            if (entries.isEmpty()) STATES.remove(owner.getUUID());
        }
        if (!create || !owns(owner, gear.stack(), gear.pair())) return null;
        if (entries == null || entries.isEmpty()) {
            if (STATES.size() >= MAX_OWNERS) return null;
            entries = new ArrayList<>(); STATES.put(owner.getUUID(), entries);
        }
        if (entries.size() >= MAX_STATES) return null;
        State result = new State(owner, gear); entries.add(result); return result;
    }
    /** Called only inside the nested, actually completed XP-orb repair calculation. */
    public static void afterOrbRepair(ServerPlayer owner, ItemStack stack, int before, int repaired, boolean projected) {
        Gear gear = selected(stack, true);
        if (gear == null || !held(owner, stack) || !owns(owner, stack, gear.pair())
            || !GrapnelDurability.provenMendingRepair(owner, stack, gear.pair(), before, repaired, projected)) return;
        State state = state(owner, gear, true); if (state == null) return;
        state.forge = RangedMendingMath.addForge(state.forge, repaired);
        state.forgeUntil = owner.level().getGameTime() + RangedMendingMath.FORGE_TICKS;
    }
    public static boolean hasEquipped(ServerPlayer owner) {
        return selected(owner.getMainHandItem(), true) != null || selected(owner.getOffhandItem(), true) != null;
    }
    public static boolean activate(ServerPlayer owner) {
        if (!ready(owner) || owner.containerMenu != owner.inventoryMenu) return false;
        Gear main = selected(owner.getMainHandItem(), true), off = selected(owner.getOffhandItem(), true);
        State chosen = main == null ? null : state(owner, main, false);
        if ((chosen == null || chosen.forge <= 0 || chosen.armed > 0) && off != null) chosen = state(owner, off, false);
        if (chosen == null || chosen.forge <= 0 || chosen.armed > 0) {
            owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.ranged_mending_need"), true); return false;
        }
        int spent = RangedMendingMath.spend(chosen.forge); chosen.forge -= spent; chosen.armed = spent;
        chosen.armedUntil = owner.level().getGameTime() + RangedMendingMath.ARMED_TICKS;
        owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.ranged_mending_set"), true); return true;
    }
    private static boolean validShot(ServerPlayer owner, AbstractArrow arrow, Shot shot) {
        long now = owner.level().getGameTime();
        return owner.getUUID().equals(shot.owner) && owner.level().dimension().equals(shot.dimension)
            && now >= shot.born && now - shot.born < RangedMendingMath.FLIGHT_TICKS && arrow.getOwner() == owner
            && arrow.level() == owner.level() && owner.level().getEntity(arrow.getUUID()) == arrow
            && !PhantomVolley.isGhost(arrow) && owns(owner, shot.gear.stack(), shot.gear.pair());
    }
    /** The Power-themed extra is part of the same real damage call; no second hit or loot. */
    public static float bonusDamage(@Nullable ConfirmedArrow.Damage evidence, float amount) {
        if (!Float.isFinite(amount) || amount <= 0) return amount;
        ConfirmedArrow.DamageEvidence hit = ConfirmedArrow.evidence(evidence); if (hit == null) return amount;
        Shot shot = SHOTS.get(hit.arrow());
        if (shot == null || shot.hitTarget != null || !RangedWearSkills.POWER.equals(shot.gear.pair().secondaryId())
            || !hit.pairs().contains(shot.gear.pair()) || hit.launcher() != shot.gear.stack()
            || !validShot(hit.owner(), hit.arrow(), shot)) return amount;
        float enhanced = amount + RangedMendingMath.damageBonus(shot.gear.pair().secondaryLevel(), shot.spent);
        return Float.isFinite(enhanced) ? enhanced : amount;
    }
    private static void afterHit(ConfirmedArrow.Hit hit) {
        Shot shot = SHOTS.get(hit.arrow());
        if (shot == null || shot.hitTarget != null || hit.launcher() != shot.gear.stack()
            || !hit.pair().equals(shot.gear.pair()) || !validShot(hit.owner(), hit.arrow(), shot)) return;
        shot.hitTarget = hit.target().getUUID();
        if (RangedWearSkills.FLAME.equals(shot.gear.pair().secondaryId()) && hit.target().isAlive())
            hit.target().igniteForSeconds(RangedMendingMath.fireSeconds(shot.gear.pair().secondaryLevel(), shot.spent));
        hit.owner().level().sendParticles(ParticleTypes.ENCHANT, hit.location().x, hit.location().y, hit.location().z,
            8, .2D, .2D, .2D, .02D);
    }
    /** Punch is added to vanilla's one knockback calculation after actual accepted net damage. */
    public static float bonusKnockback(AbstractArrow arrow, Entity target, DamageSource source, float value) {
        Shot shot = SHOTS.get(arrow);
        if (shot == null || !(arrow.getOwner() instanceof ServerPlayer owner) || shot.hitTarget == null
            || !shot.hitTarget.equals(target.getUUID()) || source.getDirectEntity() != arrow || source.getEntity() != owner
            || !RangedWearSkills.PUNCH.equals(shot.gear.pair().secondaryId()) || !validShot(owner, arrow, shot)
            || !Float.isFinite(value)) return value;
        SHOTS.remove(arrow, shot);
        return Math.max(value, Math.min(16.0F, value + RangedMendingMath.knockbackBonus(shot.gear.pair().secondaryLevel(), shot.spent)));
    }
    public static void tickServer(MinecraftServer server) {
        for (var entry : List.copyOf(STATES.entrySet())) {
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
            if (owner == null || !ready(owner)) { STATES.remove(entry.getKey()); continue; }
            long now = owner.level().getGameTime();
            entry.getValue().removeIf(state -> {
                if (!state.dimension.equals(owner.level().dimension()) || !owns(owner, state.gear.stack(), state.gear.pair())) return true;
                expire(state, now); return state.forge <= 0 && state.armed <= 0;
            });
            if (entry.getValue().isEmpty()) STATES.remove(entry.getKey());
        }
        SHOTS.entrySet().removeIf(entry -> {
            Shot shot = entry.getValue(); ServerPlayer owner = server.getPlayerList().getPlayer(shot.owner);
            return owner == null || entry.getKey().isRemoved() || !validShot(owner, entry.getKey(), shot);
        });
        RangedMendingCapture.tickServer(server);
    }
    public static void clearPlayer(UUID owner) {
        STATES.remove(owner); SHOTS.entrySet().removeIf(entry -> entry.getValue().owner.equals(owner)); RangedMendingCapture.clearPlayer(owner);
    }
    public static void clearServer() { STATES.clear(); SHOTS.clear(); RangedMendingCapture.clearServer(); }
}

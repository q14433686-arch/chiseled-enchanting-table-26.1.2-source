package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mechanics.SignaturePairs.SignatureEntry;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** 33.4/44.1 reverse: one explicitly prepared, permission-checked hit can be deferred. */
public final class MeleeSilkSealSkills {
    public static final String SILK = "minecraft:silk_touch";
    private static final Set<FusionCarrier> WEAPONS = Set.of(FusionCarrier.SWORD, FusionCarrier.AXE,
        FusionCarrier.SPEAR, FusionCarrier.MACE);
    private static final Identifier SLOW_ID = EnchantmentFusion.id("melee_silk_seal_slow");
    private static final AttributeModifier SLOW = new AttributeModifier(SLOW_ID, -0.30D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    private static final int MAX_OWNERS = 256;
    private static final long PREPARE_TICKS = 160, SEAL_TICKS = 120;
    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final Map<UUID, State> TARGETS = new HashMap<>();
    private static final ThreadLocal<Frame> CURRENT = new ThreadLocal<>();

    private static final class State {
        final ServerPlayer owner; final ServerLevel level; final ItemStack weapon; final FusedPair pair;
        long expires;
        @Nullable LivingEntity target;
        float stored;
        State(ServerPlayer owner, ItemStack weapon, FusedPair pair, long expires) {
            this.owner = owner; this.level = owner.level(); this.weapon = weapon; this.pair = pair; this.expires = expires;
        }
    }
    public static final class Frame {
        final @Nullable Frame previous; final @Nullable ConfirmedMelee.Observation observation; final @Nullable State state;
        final float output; final boolean releasing;
        boolean ambiguous, reached, shieldInteracted; int captures; float captured, loss;
        Frame(@Nullable Frame previous, @Nullable ConfirmedMelee.Observation observation, @Nullable State state,
              float output, boolean releasing) {
            this.previous = previous; this.observation = observation; this.state = state; this.output = output; this.releasing = releasing;
            this.ambiguous = previous != null;
        }
        public float damage() { return output; }
    }
    private MeleeSilkSealSkills() {}

    public static void init() { ConfirmedLivingDamage.register(MeleeSilkSealSkills::observeLoss); }
    public static boolean silkFirst(@Nullable FusedPair pair) {
        return pair != null && SILK.equals(pair.primaryId()) && MeleeAttackThemes.isAttack(pair.secondaryId());
    }
    /** Keep unrelated exact datapack signatures; replace the old Sharpness trophy with its selected exception. */
    public static boolean matches(@Nullable FusedPair pair) {
        return silkFirst(pair) && SignatureCatalog.resolve(pair).map(MeleeSilkSealSkills::selectedSignature).orElse(true);
    }
    public static boolean selectedSignature(@Nullable SignatureEntry entry) {
        return entry != null && "pristine_trophy".equals(entry.id()) && SILK.equals(entry.primaryId())
            && MeleeAttackThemes.SHARP.equals(entry.secondaryId());
    }
    public static boolean supportsCarrier(@Nullable Set<FusionCarrier> roles) {
        return roles != null && roles.stream().anyMatch(WEAPONS::contains);
    }
    public static boolean applies(@Nullable FusedPair pair, @Nullable ItemStack weapon) {
        return matches(pair) && weapon != null && !weapon.isEmpty() && supportsCarrier(FusionCarrier.classify(weapon));
    }
    private static @Nullable FusedPair selected(ItemStack weapon) {
        if (weapon.isEmpty()) return null;
        for (FusedPair pair : weapon.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if (applies(pair, weapon)) return pair;
        return null;
    }
    private static boolean ready(ServerPlayer owner) { return owner.isAlive() && !owner.isSpectator(); }
    private static boolean valid(State state) {
        ServerPlayer owner = state.owner;
        if (!ready(owner) || owner.level() != state.level || state.level.getGameTime() >= state.expires
            || owner.getMainHandItem() != state.weapon || !applies(state.pair, state.weapon)
            || !state.weapon.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().contains(state.pair)) return false;
        LivingEntity target = state.target;
        if (target == null) return true;
        AttributeInstance speed = target.getAttribute(Attributes.MOVEMENT_SPEED);
        return target.isAlive() && target.level() == state.level && state.level.getEntity(target.getUUID()) == target
            && TARGETS.get(target.getUUID()) == state && speed != null && SLOW.equals(speed.getModifier(SLOW_ID))
            && MeleeAttackThemes.eligible(owner, target, state.pair.secondaryId());
    }
    public static boolean hasEquipped(ServerPlayer owner) { return selected(owner.getMainHandItem()) != null; }
    /** Activating with a stored hit dissipates it instead of damaging the target. */
    public static boolean activate(ServerPlayer owner) {
        if (!ready(owner) || owner.containerMenu != owner.inventoryMenu) return false;
        ItemStack weapon = owner.getMainHandItem(); FusedPair pair = selected(weapon);
        if (pair == null) return false;
        State old = STATES.get(owner.getUUID());
        if (old != null && !valid(old)) { clear(old); old = null; }
        if (old != null) {
            if (old.target != null) { clear(old); owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.melee_silk_dissipated"), true); return true; }
            owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.melee_silk_ready"), true); return false;
        }
        if (STATES.size() >= MAX_OWNERS) return false;
        STATES.put(owner.getUUID(), new State(owner, weapon, pair, owner.level().getGameTime() + PREPARE_TICKS));
        owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.melee_silk_ready"), true);
        return true;
    }
    private static boolean targetReady(State state, LivingEntity target) {
        AttributeInstance speed = target.getAttribute(Attributes.MOVEMENT_SPEED);
        return target.isAlive() && target.level() == state.level && state.level.getEntity(target.getUUID()) == target
            && !target.isBlocking() && !TARGETS.containsKey(target.getUUID()) && speed != null && !speed.hasModifier(SLOW_ID)
            && MeleeAttackThemes.eligible(state.owner, target, state.pair.secondaryId());
    }
    private static boolean eligible(ConfirmedMelee.Observation observation, State state) {
        return observation.player() == state.owner && observation.slot() == EquipmentSlot.MAINHAND
            && observation.weapon() == state.weapon && observation.eligibleBefore() && valid(state)
            && MeleeAttackThemes.eligible(state.owner, observation.target(), state.pair.secondaryId());
    }
    public static float combinedDamage(float current, float stored) {
        if (!Float.isFinite(current) || current <= 0.0F || !Float.isFinite(stored) || stored <= 0.0F) return current;
        return (float) Math.min(Float.MAX_VALUE, (double) current + stored);
    }
    /** The ordinary attack amount has already incorporated its own data-pack calculations. */
    public static @Nullable Frame begin(@Nullable ConfirmedMelee.Observation observation, float damage) {
        Frame previous = CURRENT.get();
        if (previous != null) {
            // A nested attack is ordinary: it must neither capture nor release the outer claim again.
            // After a successful interception, a different target need not invalidate the seal;
            // same-target or unidentifiable nesting still has ambiguous attribution.
            if (previous.captured <= 0.0F || previous.observation == null || observation == null
                || observation.target() == previous.observation.target()) previous.ambiguous = true;
            Frame nested = new Frame(previous, observation, null, damage, false);
            CURRENT.set(nested);
            return nested;
        }
        State candidate = observation == null ? null : STATES.get(observation.player().getUUID());
        if (candidate != null && !valid(candidate)) { clear(candidate); candidate = null; }
        boolean release = candidate != null && candidate.target == observation.target() && eligible(observation, candidate);
        boolean capture = candidate != null && candidate.target == null && observation.full() && eligible(observation, candidate)
            && targetReady(candidate, observation.target());
        if ((!release && !capture) || !Float.isFinite(damage) || damage <= 0.0F) candidate = null;
        if (candidate == null && previous == null) return null;
        float output = candidate != null && release ? combinedDamage(damage, candidate.stored) : damage;
        Frame frame = new Frame(previous, observation, candidate, output, candidate != null && release);
        CURRENT.set(frame); return frame;
    }
    /**
     * Called inside the real hurtServer method, after damage permission and blocking, before armor/health.
     * No synthetic hit is sent to Fabric listeners. A blocked target is deliberately not captured.
     */
    public static float beforeActuallyHurt(LivingEntity victim, ServerLevel level, DamageSource source, float amount) {
        Frame frame = CURRENT.get();
        if (frame == null || frame.observation == null || frame.state == null || frame.observation.target() != victim
            || frame.observation.source() != source || frame.state.level != level) return amount;
        frame.reached = true;
        if (frame.releasing) return amount;
        if (frame.captures++ != 0 || frame.ambiguous || !Float.isFinite(amount) || amount <= 0.0F
            || !eligible(frame.observation, frame.state) || !targetReady(frame.state, victim)) {
            frame.ambiguous = true; return amount;
        }
        frame.captured = amount;
        return 0.0F;
    }
    /** A blocked release has already spent real shield durability even if hurtServer returns false. */
    public static void blocking(LivingEntity victim, DamageSource source, float blocked) {
        Frame frame = CURRENT.get();
        if (frame != null && frame.releasing && frame.observation != null && frame.observation.target() == victim
            && frame.observation.source() == source && Float.isFinite(blocked) && blocked > 0.0F) frame.shieldInteracted = true;
    }
    /** Fabric still reports the accepted outer hit; our health-triggered effects must not count a deferred loss. */
    public static boolean isDeferredHit(LivingEntity victim, DamageSource source) {
        Frame frame = CURRENT.get();
        return frame != null && !frame.releasing && frame.state != null && frame.observation != null
            && frame.observation.target() == victim && frame.observation.source() == source
            && frame.captures == 1 && frame.captured > 0.0F && frame.loss <= 0.0F;
    }
    private static void observeLoss(ConfirmedLivingDamage.Hit hit) {
        Frame frame = CURRENT.get();
        if (frame != null && frame.observation != null && frame.observation.target() == hit.victim()
            && frame.observation.source() == hit.source() && Float.isFinite(hit.loss())) frame.loss += hit.loss();
    }
    public static void end(@Nullable Frame frame, boolean completed, boolean accepted) {
        if (frame == null) return;
        if (CURRENT.get() != frame) { CURRENT.remove(); if (frame.previous != null) frame.previous.ambiguous = true; return; }
        if (frame.previous == null) CURRENT.remove(); else CURRENT.set(frame.previous);
        State state = frame.state; ConfirmedMelee.Observation observation = frame.observation;
        if (state == null || observation == null || STATES.get(state.owner.getUUID()) != state) return;
        if (frame.releasing) {
            // Consume on a real settlement or shield interaction, even if an outer callback later rejects it.
            if (frame.reached || frame.shieldInteracted || frame.loss > 0.0F) clear(state);
            return;
        }
        if (!completed || !accepted || frame.ambiguous || frame.captures != 1 || frame.captured <= 0.0F || frame.loss > 0.0F
            || ConfirmedMeleeMath.actualLoss(observation.health(), observation.absorption(),
                observation.target().getHealth(), observation.target().getAbsorptionAmount()) > 0.0F
            || !eligible(observation, state) || !targetReady(state, observation.target())) return;
        LivingEntity target = observation.target(); AttributeInstance speed = target.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null || speed.hasModifier(SLOW_ID)) return;
        state.target = target; state.stored = frame.captured; state.expires = state.level.getGameTime() + SEAL_TICKS;
        try {
            speed.addTransientModifier(SLOW);
            if (!SLOW.equals(speed.getModifier(SLOW_ID))) { clear(state); return; }
            TARGETS.put(target.getUUID(), state);
        } catch (RuntimeException error) {
            clear(state);
            EnchantmentFusion.LOGGER.warn("Could not safely apply the melee damage seal to {}", target.getUUID(), error);
            return;
        }
        state.level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY() + target.getBbHeight() * .5D,
            target.getZ(), 12, .3D, .3D, .3D, .01D);
        state.owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.melee_silk_stored"), true);
        CompendiumDiscoveryTracker.recordDiscovery(state.owner, state.pair.primaryId(), state.pair.secondaryId());
    }
    private static void clear(State state) {
        STATES.remove(state.owner.getUUID(), state);
        if (state.target != null) {
            TARGETS.remove(state.target.getUUID(), state);
            AttributeInstance speed = state.target.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null && SLOW.equals(speed.getModifier(SLOW_ID))) speed.removeModifier(SLOW_ID);
        }
    }
    public static void tickServer(MinecraftServer server) {
        for (State state : List.copyOf(STATES.values())) {
            if (server.getPlayerList().getPlayer(state.owner.getUUID()) != state.owner || !valid(state)) clear(state);
        }
    }
    public static void clearPlayer(UUID owner) { State state = STATES.get(owner); if (state != null) clear(state); }
    public static void clearServer() { for (State state : List.copyOf(STATES.values())) clear(state); CURRENT.remove(); }
}

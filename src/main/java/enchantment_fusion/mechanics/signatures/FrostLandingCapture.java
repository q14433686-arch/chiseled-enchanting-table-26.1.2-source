package enchantment_fusion.mechanics.signatures;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.effects.AddValue;
import org.jetbrains.annotations.Nullable;

/**
 * A balanced, per-call observation of a real fall. No damage or random enchantment effect
 * is replayed. For additive protection effects, the marginal Feather Falling contribution
 * can be attributed after the actual vanilla 20-point protection cap. Non-additive data-pack
 * effects are deliberately left unattributed rather than guessing a contribution.
 */
public final class FrostLandingCapture {
    private static final int MAX_NESTING = 8;
    private static final ThreadLocal<Deque<Frame>> ACTIVE = ThreadLocal.withInitial(ArrayDeque::new);

    public static final class Frame {
        private final ServerPlayer player;
        private final DamageSource source;
        private final FrostFallSkills.Gear gear;
        private boolean additive = true;
        private boolean observedMagic;
        private boolean confirmed;
        private boolean lethal;
        private float featherPoints;
        private float observedPoints;
        private float totalProtection;
        private float damageBeforeProtection;
        private float damageAfterProtection;
        private Frame(ServerPlayer player, DamageSource source, FrostFallSkills.Gear gear) {
            this.player = player;
            this.source = source;
            this.gear = gear;
        }
    }

    private FrostLandingCapture() {}

    public static @Nullable Frame begin(ServerPlayer player, double distance, DamageSource source) {
        Deque<Frame> stack = ACTIVE.get();
        // A nested fall on the same player may reuse a DamageSource instance. Never let
        // its callbacks be mistaken for part of the outer observation, even at the cap.
        Frame parent = stack.peek();
        if (parent != null && parent.player == player) parent.additive = false;
        if (source == null || !source.is(DamageTypes.FALL) || distance < 4.0D || !Double.isFinite(distance)
            || !player.isAlive() || player.isSpectator()) {
            if (stack.isEmpty()) ACTIVE.remove();
            return null;
        }
        FrostFallSkills.Gear gear = FrostFallSkills.find(player, true);
        if (gear == null || stack.size() >= MAX_NESTING) {
            if (stack.isEmpty()) ACTIVE.remove();
            return null;
        }
        Frame frame = new Frame(player, source, gear);
        stack.push(frame);
        return frame;
    }

    private static @Nullable Frame current(Entity victim, DamageSource source) {
        Deque<Frame> stack = ACTIVE.get();
        Frame frame = stack.peek();
        return frame != null && frame.player == victim && frame.source == source ? frame : null;
    }

    /** Observe the exact accumulator delta produced by this enchantment's actual call. */
    public static void observeEnchantment(Enchantment enchantment, ServerLevel level, ItemStack item,
                                          Entity victim, DamageSource source, float before, float after) {
        Frame frame = current(victim, source);
        if (frame == null) return;
        if (!Float.isFinite(before) || !Float.isFinite(after)) {
            frame.additive = false;
            return;
        }
        // An arbitrary multiply/set/nonlinear effect could transform a previous entry; without
        // replaying its random or side-effectful conditions, exact attribution is impossible.
        if (enchantment.getEffects(EnchantmentEffectComponents.DAMAGE_PROTECTION).stream()
            .anyMatch(effect -> !(effect.effect() instanceof AddValue))) frame.additive = false;
        frame.observedPoints += after - before;
        if (item == frame.gear.stack() && FrostFallSkills.isFeatherEnchantment(level, enchantment)) {
            frame.featherPoints += Math.max(0.0F, after - before);
        }
    }

    /** Observe the final protection application after resistance; do not alter the result. */
    public static void observeMagic(Entity victim, DamageSource source, float damage, float total, float result) {
        Frame frame = current(victim, source);
        if (frame == null) return;
        if (!Float.isFinite(damage) || !Float.isFinite(total) || !Float.isFinite(result)
            || damage <= 0.0F || result < 0.0F) {
            frame.additive = false;
            return;
        }
        if (frame.observedMagic || !Float.isFinite(frame.observedPoints)
            || Math.abs(frame.observedPoints - total) > 0.001F) frame.additive = false;
        frame.observedMagic = true;
        frame.totalProtection = total;
        frame.damageBeforeProtection = damage;
        frame.damageAfterProtection = result;
    }

    /** Runs immediately after the actual health subtraction, before death/totem callbacks. */
    public static void afterHealthDamage(ServerPlayer player, DamageSource source) {
        Frame frame = current(player, source);
        if (frame != null && player.getHealth() <= 0.0F) frame.lethal = true;
    }

    /** Fabric only reaches AFTER_DAMAGE for an accepted damage path and a surviving entity. */
    public static void confirm(ServerPlayer player, DamageSource source, boolean blocked) {
        Frame frame = current(player, source);
        if (frame != null && !blocked) frame.confirmed = true;
    }

    /** Pure marginal reduction, bounded by the total reduction observed in the real hit. */
    public static float marginalAbsorption(float damage, float actualResult, float totalProtection, float featherPoints) {
        if (!Float.isFinite(damage) || !Float.isFinite(actualResult) || !Float.isFinite(totalProtection)
            || !Float.isFinite(featherPoints) || damage <= 0 || actualResult < 0 || featherPoints <= 0) return 0.0F;
        float with = Math.clamp(totalProtection, 0.0F, 20.0F);
        float without = Math.clamp(totalProtection - featherPoints, 0.0F, 20.0F);
        float marginal = damage * Math.max(0.0F, with - without) / 25.0F;
        return Math.max(0.0F, Math.min(marginal, damage - actualResult));
    }

    public static void end(@Nullable Frame frame, boolean completed) {
        if (frame == null) return;
        Deque<Frame> stack = ACTIVE.get();
        boolean balanced = stack.peek() == frame;
        if (balanced) stack.pop();
        else stack.remove(frame);
        if (stack.isEmpty()) ACTIVE.remove();
        if (!balanced || !completed || !frame.confirmed || frame.lethal || !frame.observedMagic
            || !frame.player.isAlive() || !FrostFallSkills.stillEquipped(frame.player, frame.gear)) return;
        if (!frame.additive) {
            frame.player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                "message.enchantment_fusion.frost_attribution_unsupported"), true);
            return;
        }
        float absorbed = marginalAbsorption(frame.damageBeforeProtection, frame.damageAfterProtection,
            frame.totalProtection, frame.featherPoints);
        if (absorbed > 0.0F) FrostFallSkills.onAbsorbed(frame.player, frame.gear, absorbed);
    }
}

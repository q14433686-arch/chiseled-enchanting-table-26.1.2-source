package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.RetainedEnchant;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.effects.AddValue;
import org.jetbrains.annotations.Nullable;

/** Nested evidence of one real, item-attributable protection reduction. */
public final class MeleeGuardCapture {
    private static final int MAX_DEPTH = 12;
    private static final ThreadLocal<Deque<Frame>> FRAMES = ThreadLocal.withInitial(ArrayDeque::new);
    private MeleeGuardCapture() {}

    private static final class Observation {
        float contribution; int calls; boolean ambiguous;
    }
    /** Both direction families consume the same one-pass, per-item attribution. */
    private interface Source {
        ItemStack stack(); FusedPair pair(); boolean equipped(ServerPlayer owner); void credit(ServerPlayer owner, float amount);
    }
    private record MeleeSource(MeleeGuardSkills.Gear gear) implements Source {
        public ItemStack stack() { return gear.stack(); } public FusedPair pair() { return gear.pair(); }
        public boolean equipped(ServerPlayer owner) { return MeleeGuardSkills.stillEquipped(owner, gear); }
        public void credit(ServerPlayer owner, float amount) { MeleeGuardSkills.addCharge(owner, gear, amount); }
    }
    private record RangedSource(RangedGuardSkills.Gear gear) implements Source {
        public ItemStack stack() { return gear.stack(); } public FusedPair pair() { return gear.pair(); }
        public boolean equipped(ServerPlayer owner) { return RangedGuardSkills.stillEquipped(owner, gear); }
        public void credit(ServerPlayer owner, float amount) { RangedGuardSkills.addCharge(owner, gear, amount); }
    }
    public static final class Frame {
        final ServerPlayer player; final DamageSource source; final List<Source> gear;
        final Map<ItemStack, Observation> observations = new IdentityHashMap<>();
        final boolean cooldownClear;
        @Nullable ItemStack blockingItem;
        boolean ambiguous, blockAmbiguous, formulaSeen, finalSeen, shieldSeen, fullyBlocked;
        float input, total, result, beforeMagic, afterMagic, actualLoss, blocked;
        Frame(ServerPlayer player, DamageSource source, List<Source> gear) {
            this.player = player; this.source = source; this.gear = gear;
            this.cooldownClear = player.invulnerableTime <= 10 || source.is(DamageTypeTags.BYPASSES_COOLDOWN);
        }
    }

    public static @Nullable Frame begin(ServerPlayer player, DamageSource source) {
        Deque<Frame> stack = FRAMES.get();
        boolean skip = stack.size() >= MAX_DEPTH || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
        List<Source> gear = new ArrayList<>();
        if (!skip) {
            for (MeleeGuardSkills.Gear item : MeleeGuardSkills.equipped(player)) gear.add(new MeleeSource(item));
            for (RangedGuardSkills.Gear item : RangedGuardSkills.equipped(player)) gear.add(new RangedSource(item));
        }
        if (gear.isEmpty() && stack.isEmpty()) { FRAMES.remove(); return null; }
        Frame frame = new Frame(player, source, List.copyOf(gear)); stack.push(frame); return frame;
    }
    private static @Nullable Frame current(ServerPlayer player, DamageSource source) {
        Deque<Frame> stack = FRAMES.get(); Frame frame = stack.peek();
        if (frame == null) { FRAMES.remove(); return null; }
        return frame.player == player && frame.source == source ? frame : null;
    }
    /** Observe the real datapack effects once; no re-rolling or hypothetical enchantment call. */
    public static void observe(Enchantment enchantment, ServerLevel level, int enchantmentLevel, ItemStack item,
                               Entity victim, DamageSource source, float before, float after) {
        if (!(victim instanceof ServerPlayer player)) return;
        Frame frame = current(player, source); if (frame == null || frame.gear.isEmpty()) return;
        if (!Float.isFinite(before) || !Float.isFinite(after)) { frame.ambiguous = true; return; }
        // Even a non-additive effect that happened not to change the observed accumulator could
        // change the counterfactual without this item's contribution; do not infer its marginal value.
        if (enchantment.getEffects(EnchantmentEffectComponents.DAMAGE_PROTECTION).stream()
            .anyMatch(effect -> !(effect.effect() instanceof AddValue))) frame.ambiguous = true;
        Holder<Enchantment> protection = FusionStationOperations.lookupEnchantment(level.registryAccess(), MeleeGuardSkills.PROTECTION).orElse(null);
        if (protection == null || protection.value() != enchantment) return;
        for (Source gear : frame.gear) {
            if (gear.stack() != item) continue;
            Observation found = frame.observations.computeIfAbsent(item, ignored -> new Observation());
            found.calls++;
            long sameName = item.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().stream()
                .filter(pair -> pair.involves(MeleeGuardSkills.PROTECTION)).count();
            boolean retained = item.getOrDefault(FusionDataComponents.RETAINED_ENCHANTS, List.<RetainedEnchant>of()).stream()
                .anyMatch(entry -> MeleeGuardSkills.PROTECTION.equals(entry.id()));
            if (found.calls != 1 || sameName != 1 || retained || enchantmentLevel != gear.pair().primaryLevel()) found.ambiguous = true;
            found.contribution += Math.max(0.0F, after - before);
            return;
        }
    }
    /** Observe vanilla's real blocking result on the exact shield, including a fully blocked hit. */
    public static void shieldBlocked(ServerPlayer player, DamageSource source, @Nullable ItemStack item, float incoming, float blocked) {
        Frame frame = current(player, source); if (frame == null || frame.gear.isEmpty() || item == null) return;
        if (frame.shieldSeen || !Float.isFinite(incoming) || !Float.isFinite(blocked) || incoming < 0 || blocked < 0 || blocked > incoming + 1.0E-5F) {
            frame.blockAmbiguous = true; return;
        }
        frame.shieldSeen = true;
        if (blocked <= 0) return;
        frame.blockingItem = item; frame.blocked = Math.min(blocked, incoming);
        frame.fullyBlocked = frame.blocked >= incoming - 1.0E-5F;
    }
    public static void formula(ServerPlayer player, DamageSource source, float input, float total, float result) {
        Frame frame = current(player, source); if (frame == null || frame.gear.isEmpty()) return;
        if (frame.formulaSeen || !Float.isFinite(input) || !Float.isFinite(total) || !Float.isFinite(result)
            || input < 0 || result < 0 || result > input) { frame.ambiguous = true; return; }
        frame.formulaSeen = true; frame.input = input; frame.total = total; frame.result = result;
    }
    public static void afterMagic(ServerPlayer player, DamageSource source, float before, float after) {
        Frame frame = current(player, source); if (frame == null || frame.gear.isEmpty()) return;
        if (frame.finalSeen || !Float.isFinite(before) || !Float.isFinite(after) || before < 0 || after < 0) {
            frame.ambiguous = true; return;
        }
        frame.finalSeen = true; frame.beforeMagic = before; frame.afterMagic = after;
    }
    public static void afterHealth(ServerPlayer player, DamageSource source, float beforeHealth, float beforeAbsorption) {
        Frame frame = current(player, source); if (frame == null || frame.gear.isEmpty()) return;
        float loss = ConfirmedMeleeMath.actualLoss(beforeHealth, beforeAbsorption, player.getHealth(), player.getAbsorptionAmount());
        frame.actualLoss += loss;
        if (!Float.isFinite(frame.actualLoss)) frame.ambiguous = true;
    }
    public static void end(@Nullable Frame frame, boolean completed, boolean success) {
        if (frame == null) return;
        Deque<Frame> stack = FRAMES.get(); boolean balanced = stack.peek() == frame;
        if (balanced) stack.pop(); else stack.remove(frame);
        if (stack.isEmpty()) FRAMES.remove();
        if (!balanced || !completed || !frame.player.isAlive() || frame.player.isSpectator()) return;
        // A completely blocked hit returns false in vanilla; the shield still performed real blocking.
        if (!frame.blockAmbiguous && frame.cooldownClear && frame.blockingItem != null && frame.blocked > 0 && (success || frame.fullyBlocked)) {
            for (Source gear : frame.gear) {
                if (gear.stack() == frame.blockingItem && gear.equipped(frame.player)) {
                    gear.credit(frame.player, Math.min(4.0F, frame.blocked)); break;
                }
            }
        }
        if (frame.ambiguous || !success || !frame.formulaSeen || !frame.finalSeen || frame.actualLoss <= 0) return;
        float budget = Math.max(0, Math.min(frame.input - frame.result, frame.beforeMagic - frame.afterMagic));
        if (!Float.isFinite(budget) || budget <= 0) return;
        record Credit(Source gear, float amount) {}
        List<Credit> credits = new ArrayList<>(); float total = 0;
        for (Source gear : frame.gear) {
            Observation observed = frame.observations.get(gear.stack());
            if (observed == null || observed.ambiguous || !gear.equipped(frame.player)) continue;
            float amount = MeleeGuardMath.contribution(frame.input, frame.total, observed.contribution);
            if (amount > 0) { credits.add(new Credit(gear, amount)); total += amount; }
        }
        if (!Float.isFinite(total) || total <= 0) return;
        float scale = Math.min(1.0F, budget / total);
        for (Credit credit : credits) credit.gear().credit(frame.player, credit.amount() * scale);
    }
    public static void clear() { FRAMES.remove(); }
}

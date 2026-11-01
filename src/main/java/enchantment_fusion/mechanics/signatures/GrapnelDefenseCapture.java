package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.MatrixEffectExecutor;
import enchantment_fusion.mechanics.transmute.TransmuteRuntimeSupport;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.RetainedEnchant;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
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
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Nested real item-specific defense evidence; never rerolls a hypothetical enchantment. */
public final class GrapnelDefenseCapture {
    private static final int MAX_DEPTH = 12;
    private static final ThreadLocal<Deque<Frame>> FRAMES = ThreadLocal.withInitial(ArrayDeque::new);
    private GrapnelDefenseCapture() {}
    private static final class Observation { float contribution; int calls; boolean ambiguous; }
    public static final class Frame {
        final ServerPlayer player; final ServerLevel level; final DamageSource source; final List<GrapnelDefense.Gear> gears; final Vec3 origin;
        final Map<ItemStack, Map<String, Observation>> observed = new IdentityHashMap<>();
        final boolean cooldownClear;
        @Nullable ItemStack blockingItem;
        boolean ambiguous, blockAmbiguous, formulaSeen, magicSeen, shieldSeen, fullyBlocked;
        float input, total, result, beforeMagic, afterMagic, actualLoss, blocked;
        Frame(ServerPlayer player, DamageSource source, List<GrapnelDefense.Gear> gears) {
            this.player = player; this.level = player.level(); this.source = source; this.gears = gears; this.origin = player.position();
            this.cooldownClear = player.invulnerableTime <= 10 || source.is(DamageTypeTags.BYPASSES_COOLDOWN);
        }
    }
    public static @Nullable Frame begin(ServerPlayer player, DamageSource source) {
        Deque<Frame> frames = FRAMES.get();
        boolean skip = frames.size() >= MAX_DEPTH || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
            || MatrixEffectExecutor.isExecuting() || LoyaltyGuard.isExecuting() || Boolean.TRUE.equals(TransmuteRuntimeSupport.IN_CUSTOM_COMBAT.get());
        List<GrapnelDefense.Gear> gears = skip ? List.of() : GrapnelDefense.equipped(player);
        if (gears.isEmpty() && frames.isEmpty()) { FRAMES.remove(); return null; }
        Frame frame = new Frame(player, source, gears); frames.push(frame); return frame;
    }
    private static @Nullable Frame current(ServerPlayer player, DamageSource source) {
        Deque<Frame> frames = FRAMES.get(); Frame frame = frames.peek();
        if (frame == null) { FRAMES.remove(); return null; }
        return frame.player == player && frame.source == source ? frame : null;
    }
    public static void observe(Enchantment enchantment, ServerLevel level, int enchantmentLevel, ItemStack item,
                               Entity victim, DamageSource source, float before, float after) {
        if (!(victim instanceof ServerPlayer player)) return;
        Frame frame = current(player, source); if (frame == null || frame.gears.isEmpty()) return;
        if (!Float.isFinite(before) || !Float.isFinite(after)) { frame.ambiguous = true; return; }
        if (enchantment.getEffects(EnchantmentEffectComponents.DAMAGE_PROTECTION).stream()
            .anyMatch(effect -> !(effect.effect() instanceof AddValue))) frame.ambiguous = true;
        for (GrapnelDefense.Gear gear : frame.gears) {
            if (gear.stack() != item) continue;
            String primaryId = gear.pair().primaryId();
            Holder<Enchantment> primary = FusionStationOperations.lookupEnchantment(level.registryAccess(), primaryId).orElse(null);
            if (primary == null || primary.value() != enchantment) continue;
            Observation observation = frame.observed.computeIfAbsent(item, ignored -> new HashMap<>())
                .computeIfAbsent(primaryId, ignored -> new Observation());
            observation.calls++;
            long sameName = item.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().stream()
                .filter(pair -> pair.involves(primaryId)).count();
            boolean retained = item.getOrDefault(FusionDataComponents.RETAINED_ENCHANTS, List.<RetainedEnchant>of()).stream()
                .anyMatch(entry -> primaryId.equals(entry.id()));
            if (observation.calls != 1 || sameName != 1 || retained || enchantmentLevel != gear.pair().primaryLevel()) observation.ambiguous = true;
            observation.contribution += Math.max(0.0F, after - before);
        }
    }
    public static void shieldBlocked(ServerPlayer player, DamageSource source, @Nullable ItemStack item, float incoming, float blocked) {
        Frame frame = current(player, source); if (frame == null || frame.gears.isEmpty() || item == null) return;
        if (frame.shieldSeen || !Float.isFinite(incoming) || !Float.isFinite(blocked) || incoming < 0 || blocked < 0 || blocked > incoming + 1.0E-5F) {
            frame.blockAmbiguous = true; return;
        }
        frame.shieldSeen = true;
        if (blocked > 0) {
            frame.blockingItem = item; frame.blocked = Math.min(blocked, incoming);
            frame.fullyBlocked = frame.blocked >= incoming - 1.0E-5F;
        }
    }
    public static void formula(ServerPlayer player, DamageSource source, float input, float total, float result) {
        Frame frame = current(player, source); if (frame == null || frame.gears.isEmpty()) return;
        if (frame.formulaSeen || !Float.isFinite(input) || !Float.isFinite(total) || !Float.isFinite(result)
            || input < 0 || result < 0 || result > input) { frame.ambiguous = true; return; }
        frame.formulaSeen = true; frame.input = input; frame.total = total; frame.result = result;
    }
    public static void afterMagic(ServerPlayer player, DamageSource source, float before, float after) {
        Frame frame = current(player, source); if (frame == null || frame.gears.isEmpty()) return;
        if (frame.magicSeen || !Float.isFinite(before) || !Float.isFinite(after) || before < 0 || after < 0) {
            frame.ambiguous = true; return;
        }
        frame.magicSeen = true; frame.beforeMagic = before; frame.afterMagic = after;
    }
    public static void afterHealth(ServerPlayer player, DamageSource source, float health, float absorption) {
        Frame frame = current(player, source); if (frame == null || frame.gears.isEmpty()) return;
        frame.actualLoss += ConfirmedMeleeMath.actualLoss(health, absorption, player.getHealth(), player.getAbsorptionAmount());
        if (!Float.isFinite(frame.actualLoss)) frame.ambiguous = true;
    }
    public static void end(@Nullable Frame frame, boolean completed, boolean success) {
        if (frame == null) return;
        Deque<Frame> frames = FRAMES.get(); boolean balanced = frames.peek() == frame;
        if (balanced) frames.pop(); else frames.remove(frame);
        if (frames.isEmpty()) FRAMES.remove();
        if (!balanced || !completed || frame.player.level() != frame.level || !frame.player.isAlive() || frame.player.isSpectator()) return;
        if (!frame.blockAmbiguous && frame.cooldownClear && frame.blockingItem != null && frame.blocked > 0 && (success || frame.fullyBlocked)) {
            for (GrapnelDefense.Gear gear : frame.gears) {
                if (gear.stack() == frame.blockingItem && GrapnelDefense.stillEquipped(frame.player, gear)
                    && GrapnelDefense.matchesDanger(gear.pair(), frame.source)) {
                    GrapnelSkills.markDefense(frame.player, gear, frame.origin); return;
                }
            }
        }
        if (frame.ambiguous || !success || !frame.formulaSeen || !frame.magicSeen || frame.actualLoss <= 0) return;
        float budget = Math.max(0, Math.min(frame.input - frame.result, frame.beforeMagic - frame.afterMagic));
        if (!Float.isFinite(budget) || budget <= 0) return;
        for (GrapnelDefense.Gear gear : frame.gears) {
            Map<String, Observation> byId = frame.observed.get(gear.stack());
            Observation observation = byId == null ? null : byId.get(gear.pair().primaryId());
            if (observation == null || observation.ambiguous || !GrapnelDefense.stillEquipped(frame.player, gear)
                || !GrapnelDefense.matchesDanger(gear.pair(), frame.source)) continue;
            if (MeleeGuardMath.contribution(frame.input, frame.total, observation.contribution) > 0) {
                GrapnelSkills.markDefense(frame.player, gear, frame.origin); return;
            }
        }
    }
    public static void clear() { FRAMES.remove(); }
}

package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.RetainedEnchant;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.jetbrains.annotations.Nullable;

/** Mending and Unbreaking share item-bound location marks, but not their resource events. */
public final class GrapnelDurability {
    public static final String MENDING = "minecraft:mending", UNBREAKING = "minecraft:unbreaking";
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final ThreadLocal<WearFrame> WEAR = new ThreadLocal<>();
    private static final ThreadLocal<RepairFrame> REPAIR = new ThreadLocal<>();
    private static final ThreadLocal<CapFrame> CAPPED_WEAR = new ThreadLocal<>();
    public static final class CapFrame {
        final @Nullable CapFrame previous; final ItemStack stack;
        CapFrame(@Nullable CapFrame previous, ItemStack stack) { this.previous = previous; this.stack = stack; }
    }
    /** Scope is one vanilla recursive repairPlayerItems invocation, not the whole orb. */
    public static final class RepairFrame {
        final @Nullable RepairFrame previous; final ServerPlayer player; final ServerLevel level; final Vec3 origin;
        @Nullable ItemStack stack, snapshot; int input, output; boolean seen, active, ambiguous;
        final RealRepairObservation observation = new RealRepairObservation();
        RepairFrame(@Nullable RepairFrame previous, ServerPlayer player) {
            this.previous = previous; this.player = player; this.level = player.level(); this.origin = player.position();
        }
    }
    public static final class WearFrame {
        final @Nullable WearFrame previous; final ItemStack stack; final @Nullable ServerPlayer player;
        final @Nullable ServerLevel level; final @Nullable EquipmentSlot slot; final @Nullable FusedPair pair;
        final @Nullable Vec3 origin; final ItemStack snapshot;
        final RealWearObservation observation = new RealWearObservation();
        boolean seenUnbreaking, wrongLevel;
        WearFrame(@Nullable WearFrame previous, ItemStack stack, @Nullable ServerPlayer player) {
            this.previous = previous; this.stack = stack; this.player = player;
            this.level = player == null ? null : player.level(); this.origin = player == null ? null : player.position();
            this.slot = player == null || !ready(player) ? null : equippedSlot(player, stack);
            this.pair = slot == null ? null : selected(stack, UNBREAKING);
            this.snapshot = stack.copy();
        }
    }
    private GrapnelDurability() {}
    public static boolean isPair(@Nullable FusedPair pair) {
        return pair != null && GrapnelSkills.LURE.equals(pair.secondaryId())
            && (MENDING.equals(pair.primaryId()) || UNBREAKING.equals(pair.primaryId()));
    }
    private static boolean ready(@Nullable ServerPlayer player) {
        return player != null && player.isAlive() && !player.isSpectator() && !player.isCreative() && !player.hasInfiniteMaterials();
    }
    /** One physical stack must not simultaneously appear in multiple slots. */
    private static @Nullable EquipmentSlot equippedSlot(ServerPlayer player, ItemStack stack) {
        EquipmentSlot result = null;
        for (EquipmentSlot slot : SLOTS) if (player.getItemBySlot(slot) == stack) {
            if (result != null) return null;
            result = slot;
        }
        return result;
    }
    public static @Nullable FusedPair selected(@Nullable ItemStack stack, String primary) {
        if (!DurabilityEconomy.supportsCarrier(stack)) return null;
        FusedPair best = null;
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs()) {
            if (primary.equals(pair.primaryId()) && isPair(pair) && GrapnelSkills.applies(pair, stack)
                && (best == null || pair.primaryLevel() > best.primaryLevel())) best = pair;
        }
        return best;
    }
    public static @Nullable FusedPair nonNativeMending(@Nullable ItemStack stack) { return selected(stack, MENDING); }
    public static boolean hasEquipped(ServerPlayer player) {
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (selected(stack, MENDING) != null || selected(stack, UNBREAKING) != null) return true;
        }
        return false;
    }
    private static boolean unambiguous(ItemStack stack, String id) {
        long involving = stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().stream()
            .filter(pair -> pair.involves(id)).count();
        boolean retained = stack.getOrDefault(FusionDataComponents.RETAINED_ENCHANTS, List.<RetainedEnchant>of()).stream()
            .anyMatch(entry -> id.equals(entry.id()));
        return involving == 1 && !retained;
    }
    private static boolean stillSame(ServerPlayer player, EquipmentSlot slot, ItemStack stack, FusedPair pair) {
        return ready(player) && player.getItemBySlot(slot) == stack && equippedSlot(player, stack) == slot
            && pair.equals(selected(stack, pair.primaryId()));
    }
    /** hurtWithoutBreaking naturally clamps a stack at max - 1, independently of Unbreaking. */
    public static CapFrame beginCappedWear(ItemStack stack) {
        CapFrame frame = new CapFrame(CAPPED_WEAR.get(), stack); CAPPED_WEAR.set(frame); return frame;
    }
    public static void endCappedWear(CapFrame frame) {
        if (CAPPED_WEAR.get() != frame) { CAPPED_WEAR.remove(); return; }
        if (frame.previous == null) CAPPED_WEAR.remove(); else CAPPED_WEAR.set(frame.previous);
    }
    private static boolean naturallyCapped(WearFrame frame) {
        CapFrame capped = CAPPED_WEAR.get();
        return capped != null && capped.stack == frame.stack
            && frame.snapshot.getDamageValue() >= frame.snapshot.getMaxDamage() - 1;
    }
    /** Returns an empty nested frame when necessary so child wear cannot be credited to its parent. */
    public static WearFrame beginWear(ItemStack stack, @Nullable ServerPlayer player) {
        WearFrame previous = WEAR.get(); if (previous != null) previous.observation.nestedWear();
        WearFrame frame = new WearFrame(previous, stack, player); WEAR.set(frame); return frame;
    }
    public static @Nullable WearFrame beginEffect(ItemStack stack) {
        WearFrame frame = WEAR.get();
        if (frame == null || frame.stack != stack || frame.pair == null) return null;
        frame.observation.enterEffect(); return frame;
    }
    public static void observeEffect(@Nullable WearFrame frame, ServerLevel level, Enchantment enchantment,
                                     int enchantmentLevel, ItemStack stack, float before, float after) {
        if (frame == null || WEAR.get() != frame || frame.stack != stack || frame.pair == null) return;
        if (frame.level != level) { frame.wrongLevel = true; return; }
        Holder<Enchantment> unbreaking = FusionStationOperations.lookupEnchantment(level.registryAccess(), UNBREAKING).orElse(null);
        boolean ours = unbreaking != null && unbreaking.value() == enchantment;
        if (ours) { frame.seenUnbreaking = true; if (enchantmentLevel != frame.pair.primaryLevel()) frame.wrongLevel = true; }
        frame.observation.record(ours, before, after);
    }
    public static void endEffect(@Nullable WearFrame frame) { if (frame != null) frame.observation.exitEffect(); }
    /** Apply the real datapack roll only if no native or other fusion handler has applied it already. */
    public static int applyNonNative(ServerLevel level, ItemStack stack, int amount, @Nullable ServerPlayer player) {
        WearFrame frame = WEAR.get();
        if (amount <= 0 || frame == null || frame.stack != stack || frame.player != player || frame.level != level
            || frame.pair == null || frame.slot == null || frame.seenUnbreaking || !stillSame(player, frame.slot, stack, frame.pair)) return amount;
        Holder<Enchantment> unbreaking = FusionStationOperations.lookupEnchantment(level.registryAccess(), UNBREAKING).orElse(null);
        if (unbreaking == null || stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(unbreaking) > 0) return amount;
        MutableFloat modified = new MutableFloat(amount);
        unbreaking.value().modifyDurabilityChange(level, frame.pair.primaryLevel(), stack, modified);
        return Float.isFinite(modified.floatValue()) ? modified.intValue() : amount;
    }
    /** A fully avoided actual request needs no subsequent item mutation; ambiguous partial requests are skipped. */
    public static void finishWear(WearFrame frame, int requested, int result) {
        ServerPlayer player = frame.player;
        if (WEAR.get() != frame || player == null || frame.pair == null || frame.slot == null || frame.origin == null
            || frame.level != player.level() || frame.wrongLevel
            || !GrapnelDurabilityMath.fullySaved(requested, result, frame.observation.acceptedSaved(requested, result))
            || stackChanged(frame) || naturallyCapped(frame)
            || !stillSame(player, frame.slot, frame.stack, frame.pair) || !unambiguous(frame.stack, UNBREAKING)) return;
        GrapnelSkills.markPosition(player, frame.slot, frame.stack, frame.pair, frame.origin);
    }
    private static boolean stackChanged(WearFrame frame) {
        return frame.stack.isEmpty() || !ItemStack.matches(frame.stack, frame.snapshot);
    }
    public static void endWear(WearFrame frame) {
        if (WEAR.get() != frame) { WEAR.remove(); return; }
        if (frame.previous == null) WEAR.remove(); else WEAR.set(frame.previous);
    }
    /** Nested vanilla recursion gets a separate frame; reentry during an effect invalidates the parent. */
    public static RepairFrame beginRepair(ServerPlayer player) {
        RepairFrame previous = REPAIR.get();
        if (previous != null && previous.active) { previous.ambiguous = true; previous.observation.nested(); }
        RepairFrame frame = new RepairFrame(previous, player); REPAIR.set(frame); return frame;
    }
    public static @Nullable RepairFrame beginRepairCalculation(ServerPlayer player, ServerLevel level, ItemStack stack) {
        RepairFrame frame = REPAIR.get();
        if (frame == null) return null;
        if (frame.player != player || frame.level != level) { frame.ambiguous = true; return null; }
        if (frame.seen || frame.active) { frame.ambiguous = true; return null; }
        frame.seen = true; frame.active = true; frame.stack = stack; frame.snapshot = stack.copy();
        return frame;
    }
    /** Observe the actual value effect once. Never re-run a datapack effect just to prove it. */
    public static @Nullable RepairFrame beginRepairEffect(ServerLevel level, ItemStack stack) {
        RepairFrame frame = REPAIR.get();
        if (frame == null || !frame.active) return null;
        if (frame.level != level || frame.stack != stack) { frame.ambiguous = true; return null; }
        frame.observation.enter(); return frame;
    }
    public static void endRepairEffect(@Nullable RepairFrame frame, ServerLevel level, Enchantment enchantment,
                                       int rank, ItemStack stack, float before, float after, boolean completed) {
        if (frame == null) return;
        if (REPAIR.get() != frame || frame.level != level || frame.stack != stack) frame.ambiguous = true;
        Holder<Enchantment> mending = FusionStationOperations.lookupEnchantment(level.registryAccess(), MENDING).orElse(null);
        frame.observation.record(mending != null && mending.value() == enchantment, rank, before, after, completed);
        frame.observation.exit();
    }
    public static void finishRepairCalculation(@Nullable RepairFrame frame, int input, int output) {
        if (frame == null) return;
        if (REPAIR.get() != frame || !frame.active || frame.stack == null || frame.snapshot == null
            || !ItemStack.matches(frame.stack, frame.snapshot)) frame.ambiguous = true;
        frame.input = input; frame.output = output; frame.active = false;
    }
    /** One real orb calculation with uniquely attributable, positive Mending contribution. */
    public static boolean provenMendingRepair(ServerPlayer player, ItemStack stack, FusedPair pair,
                                               int before, int repaired, boolean projected) {
        RepairFrame frame = REPAIR.get();
        if (frame == null || frame.player != player || frame.level != player.level() || frame.stack != stack
            || frame.snapshot == null || frame.active || frame.ambiguous || !frame.seen
            || !GrapnelDurabilityMath.improvedRepair(frame.input, frame.output)
            || !frame.observation.proved(pair == null ? 0 : pair.primaryLevel(), frame.input, frame.output)
            || frame.snapshot.getDamageValue() != before || !ready(player) || repaired <= 0
            || pair == null || !MENDING.equals(pair.primaryId()) || !unambiguous(stack, MENDING)
            || !stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().contains(pair)) return false;
        ItemStack expected = frame.snapshot.copy(); expected.setDamageValue(stack.getDamageValue());
        if (!ItemStack.matches(expected, stack)) return false;
        EquipmentSlot slot = equippedSlot(player, stack);
        if (slot == null) return false;
        Holder<Enchantment> mending = FusionStationOperations.lookupEnchantment(player.registryAccess(), MENDING).orElse(null);
        if (mending == null || !mending.value().effects().has(EnchantmentEffectComponents.REPAIR_WITH_XP)) return false;
        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        int nativeLevel = enchantments.getLevel(mending);
        if (nativeLevel > 0 && nativeLevel != pair.primaryLevel()) return false;
        if (!projected && (nativeLevel == 0 || !mending.value().matchingSlot(slot))) return false;
        for (var entry : enchantments.entrySet()) {
            Holder<Enchantment> other = entry.getKey();
            if (other.value() != mending.value() && other.value().effects().has(EnchantmentEffectComponents.REPAIR_WITH_XP)) return false;
        }
        return true;
    }
    /** A real repair counts only for this calculation and a sole, positively contributing Mending effect. */
    public static void afterOrbRepair(ServerPlayer player, ItemStack stack, int before, int repaired, boolean projected) {
        RepairFrame frame = REPAIR.get(); FusedPair pair = selected(stack, MENDING);
        EquipmentSlot slot = equippedSlot(player, stack);
        if (frame == null || slot == null || pair == null || !stillSame(player, slot, stack, pair)
            || !provenMendingRepair(player, stack, pair, before, repaired, projected)) return;
        GrapnelSkills.markPosition(player, slot, stack, pair, frame.origin);
    }
    public static void endRepair(RepairFrame frame) {
        if (REPAIR.get() != frame) { REPAIR.remove(); return; }
        if (frame.previous == null) REPAIR.remove(); else REPAIR.set(frame.previous);
    }
    public static void clearServer() { WEAR.remove(); REPAIR.remove(); CAPPED_WEAR.remove(); }
}

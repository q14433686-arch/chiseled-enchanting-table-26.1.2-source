package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.MatrixEffectExecutor;
import enchantment_fusion.mechanics.TemporaryFrostEffects;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import enchantment_fusion.model.RetainedEnchant;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Frost Walker -> Lure: observe this actual conditional Frost Walker water write, not nearby old ice. */
public final class GrapnelFrost {
    public static final String FROST = "minecraft:frost_walker";
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();
    public static final class Scope {
        final @Nullable Scope previous; final ServerPlayer owner; final ServerLevel level;
        final ItemStack boots; final FusedPair pair; final int rank; final Vec3 origin; final long tick;
        boolean ambiguous, placed;
        Scope(@Nullable Scope previous, ServerPlayer owner, ItemStack boots, FusedPair pair, int rank) {
            this.previous = previous; this.owner = owner; this.level = owner.level(); this.boots = boots;
            this.pair = pair; this.rank = rank; this.origin = owner.position(); this.tick = level.getGameTime();
        }
    }
    private GrapnelFrost() {}
    public static boolean isPair(@Nullable FusedPair pair) {
        return pair != null && FROST.equals(pair.primaryId()) && GrapnelSkills.LURE.equals(pair.secondaryId());
    }
    public static boolean supportsCarrier(@Nullable Set<FusionCarrier> roles) {
        return roles != null && roles.contains(FusionCarrier.FOOT_ARMOR);
    }
    public static @Nullable FusedPair selected(@Nullable ItemStack boots) {
        if (boots == null || boots.isEmpty() || boots.isBroken()) return null;
        FusedPair best = null;
        for (FusedPair pair : boots.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if (isPair(pair) && GrapnelSkills.applies(pair, boots) && (best == null || pair.primaryLevel() > best.primaryLevel())) best = pair;
        return best;
    }
    public static boolean hasEquipped(ServerPlayer player) { return selected(player.getItemBySlot(EquipmentSlot.FEET)) != null; }
    private static boolean unambiguous(ItemStack boots) {
        return boots.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().stream()
            .filter(pair -> pair.involves(FROST)).count() == 1
            && boots.getOrDefault(FusionDataComponents.RETAINED_ENCHANTS, List.<RetainedEnchant>of()).stream()
                .noneMatch(retained -> FROST.equals(retained.id()));
    }
    private static boolean equipped(ServerPlayer player, ItemStack boots, FusedPair pair) {
        return player.isAlive() && !player.isSpectator() && player.getItemBySlot(EquipmentSlot.FEET) == boots
            && !boots.isEmpty() && !boots.isBroken() && pair.equals(selected(boots));
    }
    /** When this adapter is present, merge other valid Frost primaries by highest level rather than replaying them twice. */
    static @Nullable FusedPair projectionPair(ItemStack boots) {
        FusedPair best = selected(boots); if (best == null) return null;
        for (FusedPair pair : boots.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs()) {
            if (!FROST.equals(pair.primaryId())) continue;
            String signature = SignatureCatalog.resolve(pair).map(entry -> entry.id()).orElse("");
            boolean applicable = isPair(pair) && GrapnelSkills.applies(pair, boots)
                || FrostFallSkills.isStepPair(pair) && FrostFallSkills.applies(pair, boots, signature)
                || FrostWakeDiveSkills.isWakePair(pair) && FrostWakeDiveSkills.applies(pair, boots, signature)
                || FrostFireSkills.isBasaltPair(pair) && FrostFireSkills.applies(pair, boots, signature)
                || MeleeFrostSkills.frostFirst(pair) && MeleeFrostSkills.applies(pair, boots);
            if (applicable && pair.primaryLevel() > best.primaryLevel()) best = pair;
        }
        return best;
    }
    static int projectionRank(ItemStack boots, FusedPair best) {
        int rank = best.primaryLevel();
        for (RetainedEnchant retained : boots.getOrDefault(FusionDataComponents.RETAINED_ENCHANTS, List.<RetainedEnchant>of()))
            if (FROST.equals(retained.id())) rank = Math.max(rank, retained.level());
        return rank;
    }
    /** Preserve missing non-location immunity using the same current data-pack damage predicate once. */
    public static boolean projectImmunity(ServerLevel level, LivingEntity victim, DamageSource source) {
        if (!(victim instanceof ServerPlayer player) || player.level() != level || !player.isAlive() || player.isSpectator()) return false;
        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET); FusedPair pair = projectionPair(boots);
        if (pair == null) return false;
        Holder<Enchantment> frost = FusionStationOperations.lookupEnchantment(level.registryAccess(), FROST).orElse(null);
        if (frost == null || !frost.value().matchingSlot(EquipmentSlot.FEET)
            || boots.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(frost) > 0) return false;
        return frost.value().isImmuneToDamage(level, projectionRank(boots, pair), player, source);
    }
    /** Scope only the exact equipped Frost enchantment; nested location effects cannot borrow its ownership. */
    public static @Nullable Scope begin(Enchantment enchantment, ServerLevel level, int rank,
                                         EnchantedItemInUse item, LivingEntity wearer) {
        Scope previous = CURRENT.get(); if (previous != null) previous.ambiguous = true;
        if (!(wearer instanceof ServerPlayer owner) || owner.level() != level || item.inSlot() != EquipmentSlot.FEET
            || owner.getItemBySlot(EquipmentSlot.FEET) != item.itemStack()
            || !FusionStationOperations.lookupEnchantment(level.registryAccess(), FROST)
                .map(holder -> holder.value() == enchantment).orElse(false)) return null;
        FusedPair pair = selected(item.itemStack());
        if (pair == null || !equipped(owner, item.itemStack(), pair)) return null;
        Scope scope = new Scope(previous, owner, item.itemStack(), pair, rank);
        // Both sides of a nested/reentrant location invocation are ambiguous; neither
        // may turn the other's water attempt into a new path or an attributable mark.
        scope.ambiguous = previous != null;
        CURRENT.set(scope); return scope;
    }
    /** Called only after the native data-pack predicate actually attempted to replace this water cell. */
    public static void waterWrite(ServerLevel level, BlockPos water, BlockState replacement) {
        Scope scope = CURRENT.get();
        if (scope == null || scope.ambiguous || scope.level != level || level.getGameTime() != scope.tick
            || MatrixEffectExecutor.isExecuting() || !scope.origin.equals(scope.owner.position())
            || !equipped(scope.owner, scope.boots, scope.pair) || !scope.owner.onGround() || scope.owner.isPassenger()
            || scope.owner.isShiftKeyDown() || scope.owner.isInWater() || !replacement.is(Blocks.FROSTED_ICE)) return;
        if (TemporaryFrostEffects.placeObservedWakeCell(level, scope.owner, water)) scope.placed = true;
    }
    public static void end(@Nullable Scope scope, boolean completed) {
        if (scope == null) return;
        try {
            if (CURRENT.get() != scope || !completed || scope.ambiguous || !scope.placed
                || scope.level != scope.owner.level() || scope.level.getGameTime() != scope.tick
                || !scope.origin.equals(scope.owner.position()) || !equipped(scope.owner, scope.boots, scope.pair)
                || scope.rank != scope.pair.primaryLevel() || !unambiguous(scope.boots)) return;
            GrapnelSkills.tryMarkPosition(scope.owner, EquipmentSlot.FEET, scope.boots, scope.pair, scope.origin);
        } finally { if (scope.previous == null) CURRENT.remove(); else CURRENT.set(scope.previous); }
    }
    public static void clearServer() { CURRENT.remove(); }
}

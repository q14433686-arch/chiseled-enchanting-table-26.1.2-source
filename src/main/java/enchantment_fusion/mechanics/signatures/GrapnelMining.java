package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.ExtraBlockHarvest;
import enchantment_fusion.mechanics.HarvestDropCapture;
import enchantment_fusion.mechanics.MatrixEffectExecutor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Bounded per-break evidence for a worn, effective Aqua Affinity primary; never scans nearby drops. */
public final class GrapnelMining {
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();
    public static final class Scope {
        final @Nullable Scope previous;
        final ServerLevel level;
        final ServerPlayer player;
        final BlockPos pos;
        final BlockState block;
        final ItemStack tool;
        final @Nullable GrapnelAttribute.Gear helmet;
        final @Nullable PrimaryAttributeProjection.Evidence attribute;
        boolean ambiguous;
        Scope(@Nullable Scope previous, ServerLevel level, ServerPlayer player, BlockPos pos, BlockState block,
              ItemStack tool, @Nullable GrapnelAttribute.Gear helmet, @Nullable PrimaryAttributeProjection.Evidence attribute) {
            this.previous = previous; this.level = level; this.player = player; this.pos = pos.immutable(); this.block = block;
            this.tool = tool; this.helmet = helmet; this.attribute = attribute;
        }
    }
    private GrapnelMining() {}
    private static @Nullable GrapnelAttribute.Gear helmet(ServerPlayer player) {
        for (GrapnelAttribute.Gear gear : GrapnelAttribute.selectedAll(player, EquipmentSlot.HEAD))
            if (GrapnelAttribute.AQUA.equals(gear.pair().primaryId())) return gear;
        return null;
    }
    public static Scope begin(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState block) {
        Scope previous = CURRENT.get();
        if (previous != null) previous.ambiguous = true;
        GrapnelAttribute.Gear helmet = previous == null ? helmet(player) : null;
        PrimaryAttributeProjection.Evidence attribute = null;
        ItemStack tool = player.getMainHandItem();
        if (helmet != null && previous == null && player.level() == level && player.isAlive() && !player.isCreative() && !player.isSpectator()
            && !ExtraBlockHarvest.isActive() && !MatrixEffectExecutor.isExecuting() && !tool.isEmpty()
            && !block.isAir() && level.hasChunkAt(pos) && level.isInWorldBounds(pos)
            && level.mayInteract(player, pos) && !level.getServer().isUnderSpawnProtection(level, pos, player)
            && !player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer())
            && block.getDestroySpeed(level, pos) >= 0 && player.isEyeInFluid(FluidTags.WATER)) {
            attribute = GrapnelAttribute.evidence(player, helmet);
            // This proves an effective underwater attribute and a subsequent real harvest, not
            // an exact counterfactual duration after other mods alter the final break speed.
        }
        Scope scope = new Scope(previous, level, player, pos, block, tool, helmet, attribute);
        CURRENT.set(scope);
        return scope;
    }
    public static void end(@Nullable Scope scope) {
        if (scope == null) return;
        if (CURRENT.get() != scope) { CURRENT.remove(); if (scope.previous != null) scope.previous.ambiguous = true; return; }
        if (scope.previous == null) CURRENT.remove(); else CURRENT.set(scope.previous);
    }
    /** Called only inside that same real playerDestroy after successful vanilla block loot spawns. */
    static @Nullable GrapnelAttribute.Gear confirmed(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState block) {
        Scope scope = CURRENT.get();
        if (scope == null || scope.ambiguous || scope.previous != null || scope.level != level || scope.player != player
            || !scope.pos.equals(pos) || !scope.block.equals(block) || scope.helmet == null || scope.attribute == null
            || player.level() != level || !player.isAlive() || player.isCreative() || player.isSpectator()
            || ExtraBlockHarvest.isActive() || MatrixEffectExecutor.isExecuting() || !player.isEyeInFluid(FluidTags.WATER)
            || !HarvestDropCapture.isOriginalMainHand(level, player, pos, scope.tool)
            || !scope.attribute.equals(GrapnelAttribute.evidence(player, scope.helmet))) return null;
        return scope.helmet;
    }
}

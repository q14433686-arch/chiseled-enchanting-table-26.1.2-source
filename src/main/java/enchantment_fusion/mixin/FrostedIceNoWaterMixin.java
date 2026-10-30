package enchantment_fusion.mixin;

import enchantment_fusion.config.ModRuntimeConfig;
import enchantment_fusion.mechanics.AberrantRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents {@link Blocks#FROSTED_ICE} from generating water blocks when broken by players or when
 * melting outside an existing body of water (controlled via {@code frosted_ice_no_water}).
 */
@Mixin(IceBlock.class)
public abstract class FrostedIceNoWaterMixin {

    @Inject(method = "playerDestroy", at = @At("HEAD"), cancellable = true)
    private void ef$preventWaterOnFrostedIceBreak(
        Level level,
        Player player,
        BlockPos pos,
        BlockState state,
        @Nullable BlockEntity blockEntity,
        ItemStack tool,
        CallbackInfo ci
    ) {
        if (!state.is(Blocks.FROSTED_ICE) || !ModRuntimeConfig.active().frostedIceNoWater()) {
            return;
        }
        if (player != null) {
            player.awardStat(Stats.BLOCK_MINED.get(state.getBlock()));
            player.causeFoodExhaustion(0.005F);
        }
        Block.dropResources(state, level, pos, blockEntity, player, tool);
        AberrantRegistry.clearTrackedFrostedIce(level, pos);
        if (level.getBlockState(pos).is(Blocks.FROSTED_ICE)) {
            level.removeBlock(pos, false);
        }
        ci.cancel();
    }

    @Inject(method = "melt", at = @At("HEAD"), cancellable = true)
    private void ef$preventDryFrostedIceMeltWater(
        BlockState state,
        Level level,
        BlockPos pos,
        CallbackInfo ci
    ) {
        if (!state.is(Blocks.FROSTED_ICE) || !ModRuntimeConfig.active().frostedIceNoWater()) {
            return;
        }
        AberrantRegistry.clearTrackedFrostedIce(level, pos);
        if (!ef$isInsideWaterBody(level, pos)) {
            level.removeBlock(pos, false);
            ci.cancel();
        }
    }

    private static boolean ef$isInsideWaterBody(Level level, BlockPos pos) {
        if (level.getBlockState(pos.below()).is(Blocks.WATER)) {
            return true;
        }
        int horizontalSources = 0;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockState adj = level.getBlockState(pos.relative(dir));
            if (adj.is(Blocks.WATER) && adj.getFluidState().isSource()) {
                horizontalSources++;
                if (horizontalSources >= 2) {
                    return true;
                }
            }
        }
        return false;
    }
}

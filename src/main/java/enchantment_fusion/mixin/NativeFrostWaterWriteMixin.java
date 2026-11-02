package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import enchantment_fusion.mechanics.signatures.NativeFrostWaterGuard;
import enchantment_fusion.mechanics.signatures.GrapnelFrost;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.enchantment.effects.ReplaceBlock;
import net.minecraft.world.item.enchantment.effects.ReplaceDisk;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Preserve original water without cancelling unrelated Frost Walker location effects. */
@Mixin({ReplaceBlock.class, ReplaceDisk.class})
public abstract class NativeFrostWaterWriteMixin {
    @WrapOperation(method = "apply", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerLevel;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean ef$preserveWaterCell(ServerLevel level, BlockPos pos, BlockState state, Operation<Boolean> original) {
        if (NativeFrostWaterGuard.blocksWaterWrite(level, pos)) {
            GrapnelFrost.waterWrite(level, pos, state);
            return false; // the original source water is never replaced or reported as changed
        }
        return original.call(level, pos, state);
    }
}

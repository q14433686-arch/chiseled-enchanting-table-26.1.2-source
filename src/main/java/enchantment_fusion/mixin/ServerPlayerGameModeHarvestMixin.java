package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import enchantment_fusion.mechanics.CrossDomainTransmutations;
import enchantment_fusion.mechanics.ExtraBlockHarvest;
import enchantment_fusion.mechanics.HarvestDropCapture;
import enchantment_fusion.mechanics.SignaturePairs;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Post-harvest smelting sees the actual drops only after playerDestroy. Extra mining is also
 * deferred to this point so the original block consumes its own tool durability and drops first.
 * A method wrapper, rather than separate HEAD/RETURN injections, balances the nested frame even
 * when Fabric or another mod cancels a break or the original call exits exceptionally.
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeHarvestMixin {
    @Shadow
    @Final
    protected ServerPlayer player;

    @WrapMethod(method = "destroyBlock")
    private boolean ef$processHarvest(BlockPos pos, Operation<Boolean> original) {
        ExtraBlockHarvest.begin();
        boolean completed = false;
        HarvestDropCapture.Scope capture = null;
        try {
            ServerLevel level = this.player != null && this.player.level() instanceof ServerLevel serverLevel ? serverLevel : null;
            BlockState state = level == null ? null : level.getBlockState(pos);
            ItemStack tool = this.player == null ? ItemStack.EMPTY : this.player.getMainHandItem().copy();
            if (level != null && this.player != null) capture = HarvestDropCapture.begin(level, this.player, pos);
            boolean destroyed = original.call(pos);
            if (destroyed && level != null && this.player != null && this.player.level() == level && !this.player.isCreative()) {
                SignaturePairs.onPostDestroyHarvestDrops(level, this.player, tool, pos, state);
                CrossDomainTransmutations.onPostDestroyBlockDrops(level, this.player, pos, state, tool);
            }
            completed = true;
            return destroyed;
        } finally {
            try {
                if (capture != null) capture.close();
            } finally {
                ExtraBlockHarvest.finish(completed);
            }
        }
    }

    @WrapOperation(method = "destroyBlock", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/block/Block;playerDestroy(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/item/ItemStack;)V"))
    private void ef$capturePlayerDestroy(Block block, Level level, Player breaker, BlockPos pos, BlockState state,
                                         @Nullable BlockEntity blockEntity, ItemStack tool, Operation<Void> original) {
        boolean tracked = level instanceof ServerLevel && breaker instanceof ServerPlayer;
        if (tracked) HarvestDropCapture.enterPlayerDestroy((ServerLevel) level, (ServerPlayer) breaker, pos);
        try {
            original.call(block, level, breaker, pos, state, blockEntity, tool);
        } finally {
            if (tracked) HarvestDropCapture.leavePlayerDestroy((ServerLevel) level, (ServerPlayer) breaker, pos);
        }
    }
}

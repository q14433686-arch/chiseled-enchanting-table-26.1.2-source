package enchantment_fusion.mixin;

import enchantment_fusion.mechanics.CrossDomainTransmutations;
import enchantment_fusion.mechanics.SignaturePairs;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Runs post-harvest Auto-Smelt ({@code 自动熔炼 / 炭化伐木 / 熔金时运 / 熔心神镐}) and drop-magnet
 * effects at the exact tail of {@link ServerPlayerGameMode#destroyBlock(BlockPos)} — strictly AFTER
 * {@code block.playerDestroy(...)} has spawned Fortune-multiplied {@code ItemEntity} drops into the world.
 *
 * <p>Fixes the Fabric {@code PlayerBlockBreakEvents.AFTER} timing gap where {@code AFTER} fires immediately
 * after {@code removeBlock(pos)} before {@code playerDestroy} creates any drop entities.
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeHarvestMixin {
    @Shadow
    @Final
    protected ServerPlayer player;

    @Unique
    private static final ThreadLocal<Deque<HarvestSnapshot>> EF_HARVEST_STACK =
        ThreadLocal.withInitial(ArrayDeque::new);

    private record HarvestSnapshot(BlockPos pos, BlockState state, ItemStack toolSnapshot) {}

    @Inject(method = "destroyBlock", at = @At("HEAD"))
    private void ef$capturePreDestroyBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (this.player != null && this.player.level() instanceof ServerLevel serverLevel) {
            EF_HARVEST_STACK.get().push(new HarvestSnapshot(
                pos.immutable(),
                serverLevel.getBlockState(pos),
                this.player.getMainHandItem().copy()
            ));
        }
    }

    @Inject(method = "destroyBlock", at = @At("RETURN"))
    private void ef$processPostDestroyDrops(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        Deque<HarvestSnapshot> stack = EF_HARVEST_STACK.get();
        HarvestSnapshot snap = stack.pollFirst();
        if (stack.isEmpty()) {
            EF_HARVEST_STACK.remove();
        }
        if (snap == null || !cir.getReturnValueZ() || this.player == null || this.player.isCreative()) {
            return;
        }
        if (this.player.level() instanceof ServerLevel serverLevel) {
            SignaturePairs.onPostDestroyHarvestDrops(
                serverLevel, this.player, snap.toolSnapshot(), snap.pos(), snap.state()
            );
            CrossDomainTransmutations.onPostDestroyBlockDrops(
                serverLevel, this.player, snap.pos(), snap.state(), snap.toolSnapshot()
            );
        }
    }
}

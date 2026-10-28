package enchantment_fusion.mixin;

import enchantment_fusion.mechanics.SignaturePairs;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies Boundless Mining ([Efficiency ▷ Aqua Affinity] ignores 5x airborne/water slowdown),
 * Liquid Cutter ([Aqua Affinity ▷ Efficiency] +50% speed in water/rain),
 * and Soulburn Elegy (Vanishing Curse +25% mining speed).
 */
@Mixin(Player.class)
public abstract class PlayerMiningFusionMixin {
    @Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
    private void ef$modifyDestroySpeed(BlockState state, CallbackInfoReturnable<Float> cir) {
        float speed = cir.getReturnValueF();
        if (speed <= 1.0F) {
            return;
        }
        Player self = (Player) (Object) this;
        ItemStack tool = self.getMainHandItem();
        if (tool.isEmpty()) {
            return;
        }

        float mult = 1.0F;
        if (SignaturePairs.stackHasSignature(tool, "boundless_mining") && !self.onGround()) {
            mult *= 5.0F;
        }
        if (SignaturePairs.stackHasSignature(tool, "liquid_cutter") && self.isInWaterOrRain()) {
            mult *= 1.5F;
        }
        if (SignaturePairs.stackHasSignature(tool, "soulburn_elegy_curse")) {
            mult *= 1.25F;
        }

        if (mult != 1.0F) {
            cir.setReturnValue(speed * mult);
        }
    }
}

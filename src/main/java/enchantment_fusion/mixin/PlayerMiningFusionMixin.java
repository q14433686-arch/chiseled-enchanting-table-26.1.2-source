package enchantment_fusion.mixin;

import enchantment_fusion.mechanics.CrossDomainTransmutations;
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
 * Soulburn Elegy (Vanishing Curse +25% mining speed), and Phase 3 Cross-Domain Single
 * Transmutation mining speed, entity reach ({@code 沂水}), and swing cadence modifiers.
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

        float current = speed * mult;
        current = CrossDomainTransmutations.modifyDestroySpeed(self, tool, state, current);
        if (Math.abs(current - speed) > 0.001F) {
            cir.setReturnValue(Math.max(0.25F, current));
        }
    }

    @Inject(method = "entityInteractionRange", at = @At("RETURN"), cancellable = true)
    private void ef$adjustEntityReach(CallbackInfoReturnable<Double> cir) {
        Player self = (Player) (Object) this;
        double base = cir.getReturnValueD();
        double adjusted = CrossDomainTransmutations.modifyEntityReach(self, base);
        if (Math.abs(adjusted - base) > 0.001D) {
            cir.setReturnValue(adjusted);
        }
    }

    @Inject(method = "getCurrentItemAttackStrengthDelay", at = @At("RETURN"), cancellable = true)
    private void ef$adjustAttackStrengthDelay(CallbackInfoReturnable<Float> cir) {
        Player self = (Player) (Object) this;
        float base = cir.getReturnValueF();
        float adjusted = CrossDomainTransmutations.modifyAttackDelay(self, base);
        if (Math.abs(adjusted - base) > 0.001F) {
            cir.setReturnValue(Math.max(1.0F, adjusted));
        }
    }
}

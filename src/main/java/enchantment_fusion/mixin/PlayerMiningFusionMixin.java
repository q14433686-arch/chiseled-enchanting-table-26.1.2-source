package enchantment_fusion.mixin;

import enchantment_fusion.mechanics.CrossDomainTransmutations;
import enchantment_fusion.mechanics.SignaturePairs;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies Boundless Mining ([Aqua Affinity ▷ Respiration] ignores 5x airborne & underwater slowdown
 * across any equipped slot — API-05 / MIX-05), Ocean Breath Mine ([Aqua Affinity ▷ Efficiency] +35% speed in water/rain),
 * Soulburn Elegy (+25% mining speed), and Cross-Domain Single Transmutation mining speed, entity reach, and swing cadence.
 */
@Mixin(Player.class)
public abstract class PlayerMiningFusionMixin {
    @Unique
    private static final EquipmentSlot[] EF_EQUIP_SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
        EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
    };

    @Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
    private void ef$modifyDestroySpeed(BlockState state, CallbackInfoReturnable<Float> cir) {
        float speed = cir.getReturnValueF();
        // MIX-05: Only skip unbreakable/zero speeds — underwater airborne base speed can be 0.04F (< 1.0F)!
        if (speed <= 0.0F) {
            return;
        }
        Player self = (Player) (Object) this;
        ItemStack tool = self.getMainHandItem();

        float mult = 1.0F;
        if (ef$hasEquippedSignature(self, "ocean_breath_mine")) {
            if (!self.onGround()) {
                mult *= 5.0F;
            }
            if (self.isEyeInFluid(FluidTags.WATER)) {
                mult *= 5.0F;
            }
            if (self.isInWaterOrRain()) {
                mult *= 1.35F;
            }
        }
        if (!tool.isEmpty() && SignaturePairs.stackHasSignature(tool, "geyser_bore")
            && state.getDestroySpeed(self.level(), BlockPos.ZERO) >= 3.0F) {
            mult *= 1.65F;
        }
        if (!tool.isEmpty() && SignaturePairs.stackHasSignature(tool, "soulburn_elegy_curse")) {
            mult *= 1.25F;
        }

        float current = speed * mult;
        if (!tool.isEmpty()) {
            current = CrossDomainTransmutations.modifyDestroySpeed(self, tool, state, current);
        }
        if (Math.abs(current - speed) > 0.001F) {
            cir.setReturnValue(Math.max(0.05F, current));
        }
    }

    @Unique
    private static boolean ef$hasEquippedSignature(Player player, String sigId) {
        if (player instanceof ServerPlayer sp) {
            return SignaturePairs.playerHasEquippedSignature(sp, sigId);
        }
        for (EquipmentSlot slot : EF_EQUIP_SLOTS) {
            if (SignaturePairs.stackHasSignature(player.getItemBySlot(slot), sigId)) {
                return true;
            }
        }
        return false;
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

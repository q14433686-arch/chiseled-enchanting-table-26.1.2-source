package enchantment_fusion.mixin;

import enchantment_fusion.data.FusionMatrixRegistry;
import enchantment_fusion.mechanics.CrossDomainTransmutations;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.HostForm;
import enchantment_fusion.registry.FusionDataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Syncs client + server mining speed, entity attack reach ({@code 沂水}), and swing cadence
 * for both binary Fused Pairs and Phase 3 Cross-Domain Single Transmutations.
 */
@Mixin(Player.class)
public abstract class PlayerMiningFusionMixin {
    @Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
    private void ef$boostFusedMiningSpeed(BlockState state, CallbackInfoReturnable<Float> cir) {
        float base = cir.getReturnValueF();
        if (base <= 1.0F) {
            return;
        }
        Player self = (Player) (Object) this;
        ItemStack tool = self.getMainHandItem();
        if (tool.isEmpty()) {
            return;
        }

        float current = base;
        FusedEnchantmentList list = tool.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        if (!list.isEmpty()) {
            HostForm form = HostForm.resolve(tool);
            FusionMatrixRegistry.Snapshot snap = FusionMatrixRegistry.current();
            float bonusMultiplier = 1.0F;
            for (FusedPair pair : list.pairs()) {
                if (pair.isAberrant()) {
                    continue;
                }
                if (pair.primaryId().equals("minecraft:efficiency") || pair.secondaryId().equals("minecraft:efficiency")) {
                    bonusMultiplier += 0.08F * Math.max(pair.primaryLevel(), pair.secondaryLevel());
                }
                if (pair.secondaryId().equals("minecraft:aqua_affinity") && self.isInWater()) {
                    bonusMultiplier += 0.35F;
                }
                var modOpt = snap.modifierOf(pair.secondaryId());
                if (modOpt.isPresent() && form == HostForm.TOOL) {
                    bonusMultiplier += 0.04F * pair.secondaryLevel();
                }
            }
            if (bonusMultiplier > 1.0F) {
                current = current * bonusMultiplier;
            }
        }

        current = CrossDomainTransmutations.modifyDestroySpeed(self, tool, state, current);
        if (Math.abs(current - base) > 0.001F) {
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

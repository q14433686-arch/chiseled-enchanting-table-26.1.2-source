package enchantment_fusion.mixin;

import enchantment_fusion.menu.FusionSkillNetworking;
import enchantment_fusion.mechanics.signatures.SoulBladeSkills;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.registry.FusionDataComponents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Report only vanilla's actual attack-miss branch; the server never trusts the client for resources or charge. */
@Mixin(Minecraft.class)
public abstract class ClientSoulBladeInputMixin {
    @Inject(method = "startAttack", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;resetAttackStrengthTicker()V"))
    private void ef$airAttack(CallbackInfoReturnable<Boolean> cir) {
        Minecraft client = (Minecraft) (Object) this;
        if (client.player == null || !ClientPlayNetworking.canSend(FusionSkillNetworking.AirAttackPayload.TYPE)) return;
        var stack = client.player.getMainHandItem();
        boolean equipped = stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().stream()
            .anyMatch(pair -> SoulBladeSkills.applies(pair, stack, SoulBladeSkills.BLADE));
        if (equipped) ClientPlayNetworking.send(FusionSkillNetworking.AirAttackPayload.INSTANCE);
    }
}

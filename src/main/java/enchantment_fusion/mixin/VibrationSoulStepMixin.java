package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import enchantment_fusion.mechanics.signatures.SoulStepSkills;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Muffle only this player's eligible movement vibrations, not actions or other game-event listeners. */
@Mixin(VibrationSystem.Listener.class)
public abstract class VibrationSoulStepMixin {
    @WrapOperation(method = "handleGameEvent", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/gameevent/vibrations/VibrationSystem$User;isValidVibration(Lnet/minecraft/core/Holder;Lnet/minecraft/world/level/gameevent/GameEvent$Context;)Z"))
    private boolean ef$muffleSoulMovement(VibrationSystem.User user, Holder<GameEvent> event, GameEvent.Context context,
                                           Operation<Boolean> original) {
        if (context.sourceEntity() instanceof ServerPlayer player && SoulStepSkills.muffle(player, event)) return false;
        return original.call(user, event, context);
    }
}

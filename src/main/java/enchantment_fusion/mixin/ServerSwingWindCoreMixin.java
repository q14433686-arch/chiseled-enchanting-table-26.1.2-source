package enchantment_fusion.mixin;

import enchantment_fusion.mechanics.signatures.WindSweepSkills;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe the client's actual swing packet only after the vanilla server-thread handler. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerSwingWindCoreMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "handleAnimate", at = @At("TAIL"))
    private void ef$releaseWindCore(ServerboundSwingPacket packet, CallbackInfo ci) {
        WindSweepSkills.onSwing(player, packet.getHand());
    }
}

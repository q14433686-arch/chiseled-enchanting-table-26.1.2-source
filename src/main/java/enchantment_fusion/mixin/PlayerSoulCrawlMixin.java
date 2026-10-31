package enchantment_fusion.mixin;

import enchantment_fusion.mechanics.signatures.SoulStepSkills;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Ask vanilla's pose selection for crawl; its usual fit tests still prevent clipping. */
@Mixin(Player.class)
public abstract class PlayerSoulCrawlMixin {
    @Inject(method = "getDesiredPose", at = @At("RETURN"), cancellable = true)
    private void ef$preferSoulCrawl(CallbackInfoReturnable<Pose> cir) {
        Player player = (Player) (Object) this;
        Pose original = cir.getReturnValue();
        if ((original == Pose.STANDING || original == Pose.CROUCHING) && SoulStepSkills.forceCrawling(player)) {
            cir.setReturnValue(Pose.SWIMMING);
        }
    }
}

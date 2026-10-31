package enchantment_fusion.mixin;

import enchantment_fusion.mechanics.transmute.ReplenishTracker;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe the actual player's credited XP, rather than a later tick's unrelated total delta. */
@Mixin(Player.class)
public abstract class PlayerExperienceFusionMixin {
    @Unique private int ef$xpDepth;
    @Unique private int ef$xpBefore;

    @Inject(method = "giveExperiencePoints", at = @At("HEAD"))
    private void ef$beforeExperience(int amount, CallbackInfo ci) {
        if (ef$xpDepth++ == 0) ef$xpBefore = ((Player) (Object) this).totalExperience;
    }

    @Inject(method = "giveExperiencePoints", at = @At("RETURN"))
    private void ef$afterExperience(int amount, CallbackInfo ci) {
        if (--ef$xpDepth != 0 || amount <= 0) return;
        Player player = (Player) (Object) this;
        long actual = Math.min((long) amount, Math.max(0L, (long) player.totalExperience - ef$xpBefore));
        if (actual > 0 && player instanceof ServerPlayer serverPlayer) {
            ReplenishTracker.onExperienceCredited(serverPlayer, (int) actual);
        }
    }
}

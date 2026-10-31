package enchantment_fusion.mixin;

import enchantment_fusion.mechanics.HarvestDropCapture;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Only ore_drops and uniform_bonus_count have no non-Fortune baseline addition. */
@Mixin(targets = {
    "net.minecraft.world.level.storage.loot.functions.ApplyBonusCount$OreDrops",
    "net.minecraft.world.level.storage.loot.functions.ApplyBonusCount$UniformBonusCount"
})
public abstract class ApplyBonusSafeFormulaMixin {
    @Inject(method = "calculateNewCount", at = @At("RETURN"))
    private void ef$markSafeFortune(RandomSource random, int before, int level, CallbackInfoReturnable<Integer> result) {
        HarvestDropCapture.markSafeFortuneFormula(before, level, result.getReturnValue());
    }
}

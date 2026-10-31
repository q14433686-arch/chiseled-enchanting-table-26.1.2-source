package enchantment_fusion.mixin;

import enchantment_fusion.mechanics.signatures.PhantomVolley;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.CrossbowItem;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The vanilla index distinguishes the real center arrow from the two flanking arrows. */
@Mixin(CrossbowItem.class)
public abstract class PhantomCrossbowMixin {
    @Inject(method = "shootProjectile", at = @At("HEAD"))
    private void ef$configureDirectionalArrows(LivingEntity shooter, Projectile projectile, int index, float power,
                                               float inaccuracy, float angle, @Nullable LivingEntity targetOverride,
                                               CallbackInfo ci) {
        PhantomVolley.configureCrossbow(projectile, shooter, index);
    }
}

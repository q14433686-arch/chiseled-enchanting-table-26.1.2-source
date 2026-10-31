package enchantment_fusion.mixin;

import enchantment_fusion.mechanics.signatures.PhantomVolley;
import enchantment_fusion.mechanics.signatures.HeavyCrushProjectiles;
import enchantment_fusion.mechanics.signatures.RangedCadenceSkills;
import enchantment_fusion.mechanics.signatures.EchoPathSkills;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BowItem;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Recognizes vanilla/datapack-generated bow side arrows without cloning them again. */
@Mixin(BowItem.class)
public abstract class PhantomBowMixin {
    @Inject(method = "shootProjectile", at = @At("HEAD"))
    private void ef$configureDirectionalArrows(LivingEntity shooter, Projectile projectile, int index, float power,
                                               float inaccuracy, float angle, @Nullable LivingEntity targetOverride,
                                               CallbackInfo ci) {
        PhantomVolley.configureBow(projectile, shooter, index);
        RangedCadenceSkills.trackBow(projectile, shooter, index, HeavyCrushProjectiles.exactLauncher(shooter));
        EchoPathSkills.launched(projectile, shooter, index, HeavyCrushProjectiles.exactLauncher(shooter));
    }
}

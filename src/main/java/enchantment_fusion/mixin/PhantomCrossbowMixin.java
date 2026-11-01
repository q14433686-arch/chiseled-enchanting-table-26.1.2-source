package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import enchantment_fusion.mechanics.signatures.PhantomVolley;
import enchantment_fusion.mechanics.signatures.RangedCadenceSkills;
import enchantment_fusion.mechanics.signatures.RangedSneakSkills;
import enchantment_fusion.mechanics.signatures.EchoPathSkills;
import enchantment_fusion.mechanics.signatures.ConfirmedArrow;
import enchantment_fusion.mechanics.signatures.PiercingChainSkills;
import enchantment_fusion.mechanics.signatures.ThermalProjectileSkills;
import enchantment_fusion.mechanics.signatures.HeavyCrushProjectiles;
import enchantment_fusion.mechanics.signatures.GrapnelSkills;
import enchantment_fusion.mechanics.signatures.GrapnelQuickCharge;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
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
        RangedCadenceSkills.configureCrossbowShot(projectile, shooter, index);
        EchoPathSkills.launched(projectile, shooter, index, HeavyCrushProjectiles.exactLauncher(shooter));
        ConfirmedArrow.launched(projectile, shooter, index, HeavyCrushProjectiles.exactLauncher(shooter));
        PiercingChainSkills.launched(projectile, shooter, index, HeavyCrushProjectiles.exactLauncher(shooter));
        ThermalProjectileSkills.launched(projectile, shooter, index, HeavyCrushProjectiles.exactLauncher(shooter));
        GrapnelSkills.launched(projectile, shooter, index, HeavyCrushProjectiles.exactLauncher(shooter));
    }

    @WrapMethod(method = "onUseTick")
    private void ef$scopeQuickLoad(Level level, LivingEntity user, ItemStack weapon, int remaining, Operation<Void> original) {
        GrapnelQuickCharge.Loading frame = GrapnelQuickCharge.beginLoading(user, weapon);
        try { original.call(level, user, weapon, remaining); }
        finally { GrapnelQuickCharge.endLoading(frame); }
    }

    @WrapOperation(method = "onUseTick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/item/CrossbowItem;getChargingSounds(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/CrossbowItem$ChargingSounds;"))
    private CrossbowItem.ChargingSounds ef$quickChargingSounds(CrossbowItem self, ItemStack weapon,
                                                               Operation<CrossbowItem.ChargingSounds> original,
                                                               Level level, LivingEntity user, ItemStack actual, int remaining) {
        CrossbowItem.ChargingSounds vanilla = original.call(self, weapon);
        return weapon == actual ? GrapnelQuickCharge.chargingSounds(weapon, user, vanilla) : vanilla;
    }

    @WrapOperation(method = "onUseTick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/item/CrossbowItem;tryLoadProjectiles(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean ef$realArrowLoad(LivingEntity shooter, ItemStack weapon, Operation<Boolean> original) {
        boolean loaded = original.call(shooter, weapon);
        if (loaded) {
            RangedCadenceSkills.loadedCrossbow(shooter, weapon);
            RangedSneakSkills.loadedCrossbow(shooter, weapon);
            PiercingChainSkills.loaded(shooter, weapon);
            GrapnelQuickCharge.loaded(shooter, weapon);
        }
        return loaded;
    }

    @WrapMethod(method = "performShooting")
    private void ef$scopeChargedArrow(Level level, LivingEntity shooter, InteractionHand hand, ItemStack weapon,
                                      float power, float inaccuracy, @Nullable LivingEntity targetOverride,
                                      Operation<Void> original) {
        GrapnelQuickCharge.Shot quick = GrapnelQuickCharge.beginShot(shooter, weapon);
        try {
            RangedCadenceSkills.beginCrossbowShot(shooter, weapon);
            try { original.call(level, shooter, hand, weapon, power, inaccuracy, targetOverride); }
            finally { RangedCadenceSkills.endCrossbowShot(); }
        } finally { GrapnelQuickCharge.endShot(quick); }
    }
}

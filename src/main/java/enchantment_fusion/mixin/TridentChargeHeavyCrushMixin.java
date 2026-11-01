package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import enchantment_fusion.mechanics.signatures.HeavyCrushSkill;
import enchantment_fusion.mechanics.signatures.MeleeLightningSkills;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The ordinary ten-tick trident threshold becomes fifteen ticks for this fusion. */
@Mixin(TridentItem.class)
public abstract class TridentChargeHeavyCrushMixin {
    @Inject(method = "releaseUsing", at = @At("HEAD"), cancellable = true)
    private void ef$heavyThrow(ItemStack weapon, Level level, LivingEntity user, int remaining,
                               CallbackInfoReturnable<Boolean> cir) {
        if (user instanceof Player && HeavyCrushSkill.selected(weapon) != null
            && weapon.getUseDuration(user) - remaining < 15) {
            cir.setReturnValue(false);
        }
    }

    @WrapOperation(method = "releaseUsing", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/projectile/Projectile;spawnProjectileFromRotation(Lnet/minecraft/world/entity/projectile/Projectile$ProjectileFactory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;FFF)Lnet/minecraft/world/entity/projectile/Projectile;"))
    private Projectile ef$recordActualThrow(Projectile.ProjectileFactory<?> factory, ServerLevel level, ItemStack consumed,
                                             LivingEntity owner, float angle, float power, float uncertainty,
                                             Operation<Projectile> original) {
        Projectile projectile = original.call(factory, level, consumed, owner, angle, power, uncertainty);
        MeleeLightningSkills.launched(projectile, owner, consumed);
        return projectile;
    }
}

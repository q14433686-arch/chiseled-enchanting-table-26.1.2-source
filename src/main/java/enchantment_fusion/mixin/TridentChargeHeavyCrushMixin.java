package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import enchantment_fusion.mechanics.signatures.HeavyCrushSkill;
import enchantment_fusion.mechanics.signatures.MeleeLightningSkills;
import enchantment_fusion.mechanics.signatures.GrapnelTridentFlight;
import enchantment_fusion.mechanics.signatures.GrapnelRiptide;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.sounds.SoundEvent;
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

    @WrapMethod(method = "releaseUsing")
    private boolean ef$observeRiptideRelease(ItemStack stack, Level level, LivingEntity user, int remaining, Operation<Boolean> original) {
        GrapnelRiptide.Release release = GrapnelRiptide.begin(stack, level, user, remaining);
        boolean result = false, completed = false;
        try { result = original.call(stack, level, user, remaining); completed = true; return result; }
        finally { GrapnelRiptide.end(release, result, completed); }
    }

    @WrapOperation(method = {"use", "releaseUsing"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;getTridentSpinAttackStrength(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)F"))
    private float ef$projectRiptideStrength(ItemStack stack, LivingEntity user, Operation<Float> original) {
        GrapnelRiptide.Release query = GrapnelRiptide.beginQuery(stack, user);
        float result = 0; boolean completed = false;
        try {
            result = GrapnelRiptide.project(stack, user, original.call(stack, user)); completed = true; return result;
        } finally { GrapnelRiptide.endQuery(query, result, completed); }
    }

    @WrapOperation(method = "releaseUsing", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;pickHighestLevel(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/component/DataComponentType;)Ljava/util/Optional;"))
    private Optional<Holder<SoundEvent>> ef$projectRiptideSound(ItemStack stack, DataComponentType<List<Holder<SoundEvent>>> effect,
        Operation<Optional<Holder<SoundEvent>>> original, ItemStack weapon, Level world, LivingEntity user, int remaining) {
        return GrapnelRiptide.projectSound(stack, world, effect, original.call(stack, effect));
    }

    @WrapOperation(method = "releaseUsing", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/player/Player;push(DDD)V"))
    private void ef$observeRiptideImpulse(Player player, double x, double y, double z, Operation<Void> original) {
        GrapnelRiptide.Push push = GrapnelRiptide.beginPush(player, x, y, z); boolean completed = false;
        try { original.call(player, x, y, z); completed = true; }
        finally { GrapnelRiptide.endPush(push, completed); }
    }

    @WrapOperation(method = "releaseUsing", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/projectile/Projectile;spawnProjectileFromRotation(Lnet/minecraft/world/entity/projectile/Projectile$ProjectileFactory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;FFF)Lnet/minecraft/world/entity/projectile/Projectile;"))
    private Projectile ef$recordActualThrow(Projectile.ProjectileFactory<?> factory, ServerLevel level, ItemStack consumed,
                                             LivingEntity owner, float angle, float power, float uncertainty,
                                             Operation<Projectile> original) {
        Projectile projectile = original.call(factory, level, consumed, owner, angle, power, uncertainty);
        MeleeLightningSkills.launched(projectile, owner, consumed);
        GrapnelTridentFlight.launched(projectile, owner, consumed);
        return projectile;
    }
}

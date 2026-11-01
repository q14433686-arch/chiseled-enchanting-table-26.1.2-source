package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import enchantment_fusion.mechanics.signatures.PhantomVolley;
import enchantment_fusion.mechanics.signatures.HeavyCrushProjectiles;
import enchantment_fusion.mechanics.signatures.GrapnelVolley;
import enchantment_fusion.mechanics.signatures.GrapnelProjectilePrimary;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Scopes the actual bow projectile list so native multishot and generated ghosts merge once. */
@Mixin(ProjectileWeaponItem.class)
public abstract class PhantomBowBatchMixin {
    @WrapOperation(method = "useAmmo", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;processAmmoUse(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;I)I"))
    private static int ef$infinityBeforeAmmoSplit(ServerLevel level, ItemStack weapon, ItemStack ammo, int amount,
        Operation<Integer> original, ItemStack actualWeapon, ItemStack actualAmmo, LivingEntity shooter, boolean forceInfinite) {
        int vanilla = original.call(level, weapon, ammo, amount);
        return weapon == actualWeapon && ammo == actualAmmo && !forceInfinite
            ? GrapnelProjectilePrimary.ammo(level, weapon, ammo, shooter, vanilla) : vanilla;
    }

    @WrapMethod(method = "shoot")
    private void ef$scopeBowBatch(ServerLevel level, LivingEntity shooter, InteractionHand hand, ItemStack weapon,
                                   List<ItemStack> projectiles, float power, float inaccuracy, boolean crit,
                                   @Nullable LivingEntity targetOverride, Operation<Void> original) {
        boolean entered = (Object) this instanceof BowItem
            && PhantomVolley.enterBowBatch(weapon, (int) projectiles.stream().filter(stack -> !stack.isEmpty()).count());
        HeavyCrushProjectiles.enterShot(shooter, weapon);
        GrapnelVolley.Batch grapnel = GrapnelVolley.begin(shooter, weapon);
        boolean completed = false;
        try {
            original.call(level, shooter, hand, weapon, projectiles, power, inaccuracy, crit, targetOverride);
            completed = true;
        } finally {
            try { GrapnelVolley.end(grapnel, completed); }
            finally {
                HeavyCrushProjectiles.exitShot();
                PhantomVolley.exitBowBatch(entered);
            }
        }
    }
}

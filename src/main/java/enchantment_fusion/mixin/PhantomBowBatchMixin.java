package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import enchantment_fusion.mechanics.signatures.PhantomVolley;
import enchantment_fusion.mechanics.signatures.HeavyCrushProjectiles;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

/** Scopes the actual bow projectile list so native multishot and generated ghosts merge once. */
@Mixin(ProjectileWeaponItem.class)
public abstract class PhantomBowBatchMixin {
    @WrapMethod(method = "shoot")
    private void ef$scopeBowBatch(ServerLevel level, LivingEntity shooter, InteractionHand hand, ItemStack weapon,
                                   List<ItemStack> projectiles, float power, float inaccuracy, boolean crit,
                                   @Nullable LivingEntity targetOverride, Operation<Void> original) {
        boolean entered = (Object) this instanceof BowItem
            && PhantomVolley.enterBowBatch(weapon, (int) projectiles.stream().filter(stack -> !stack.isEmpty()).count());
        HeavyCrushProjectiles.enterShot(shooter, weapon);
        try {
            original.call(level, shooter, hand, weapon, projectiles, power, inaccuracy, crit, targetOverride);
        } finally {
            HeavyCrushProjectiles.exitShot();
            PhantomVolley.exitBowBatch(entered);
        }
    }
}

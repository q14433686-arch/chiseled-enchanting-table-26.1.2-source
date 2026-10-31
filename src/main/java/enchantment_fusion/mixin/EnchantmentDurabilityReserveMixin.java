package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import enchantment_fusion.mechanics.signatures.DurabilityReserveSkills;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.spongepowered.asm.mixin.Mixin;

/** Observe the real, normally rolled Unbreaking effect; never repeat the random roll. */
@Mixin(Enchantment.class)
public abstract class EnchantmentDurabilityReserveMixin {
    @WrapMethod(method = "modifyDurabilityChange")
    private void ef$observeActualUnbreaking(ServerLevel level, int enchantmentLevel, ItemStack stack,
                                            MutableFloat change, Operation<Void> original) {
        float before = change.floatValue();
        original.call(level, enchantmentLevel, stack, change);
        DurabilityReserveSkills.observed(level, (Enchantment) (Object) this, stack, before, change.floatValue());
    }
}

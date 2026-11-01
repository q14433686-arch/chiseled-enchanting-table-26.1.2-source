package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import enchantment_fusion.mechanics.signatures.GrapnelLooting;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.EnchantedCountIncreaseFunction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Observe the one real Looting count roll; never call the function or its random provider again. */
@Mixin(EnchantedCountIncreaseFunction.class)
public abstract class EnchantedCountLootProvenanceMixin {
    @Shadow @Final private Holder<Enchantment> enchantment;
    @WrapMethod(method = "run")
    private ItemStack ef$observeCount(ItemStack stack, LootContext context, Operation<ItemStack> original) {
        GrapnelLooting.Count count = GrapnelLooting.beginCount(this.enchantment, stack, context);
        ItemStack result = null; boolean completed = false;
        try { result = original.call(stack, context); completed = true; return result; }
        finally { GrapnelLooting.endCount(count, result, completed); }
    }
}

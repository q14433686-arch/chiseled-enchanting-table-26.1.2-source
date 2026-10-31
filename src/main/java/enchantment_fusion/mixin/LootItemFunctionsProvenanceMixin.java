package enchantment_fusion.mixin;

import enchantment_fusion.mechanics.HarvestDropCapture;
import java.util.List;
import java.util.function.BiFunction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.ApplyExplosionDecay;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctions;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Detect later loot functions, even when their final count happens to equal the Fortune result. */
@Mixin(LootItemFunctions.class)
public abstract class LootItemFunctionsProvenanceMixin {
    @Inject(method = "compose", at = @At("RETURN"), cancellable = true)
    private static void ef$guardSubsequentFunctions(
        List<? extends BiFunction<ItemStack, LootContext, ItemStack>> functions,
        CallbackInfoReturnable<BiFunction<ItemStack, LootContext, ItemStack>> cir) {
        if (functions.isEmpty()) return;
        List<? extends BiFunction<ItemStack, LootContext, ItemStack>> terms = List.copyOf(functions);
        BiFunction<ItemStack, LootContext, ItemStack> original = cir.getReturnValue();
        cir.setReturnValue((stack, context) -> {
            if (!HarvestDropCapture.canCaptureFortune(context)) return original.apply(stack, context);
            ItemStack current = stack;
            for (BiFunction<ItemStack, LootContext, ItemStack> function : terms) {
                // Vanilla explosion decay is exactly a no-op without an explosion parameter.
                if (!(function.getClass() == ApplyExplosionDecay.class
                    && context.getOptionalParameter(LootContextParams.EXPLOSION_RADIUS) == null))
                    HarvestDropCapture.invalidateFortuneOutput(context, current);
                current = function.apply(current, context);
            }
            return current;
        });
    }
}

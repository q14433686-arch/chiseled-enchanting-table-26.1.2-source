package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import enchantment_fusion.mechanics.HarvestDropCapture;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Observe the single real Fortune calculation; never reroll or mutate it here. */
@Mixin(ApplyBonusCount.class)
public abstract class ApplyBonusFortuneCaptureMixin {
    @Shadow @Final private Holder<Enchantment> enchantment;

    @WrapMethod(method = "run")
    private ItemStack ef$captureActualFortune(ItemStack stack, LootContext context, Operation<ItemStack> original) {
        boolean capture = this.enchantment.is(Enchantments.FORTUNE) && HarvestDropCapture.canCaptureFortune(context);
        if (!capture && !HarvestDropCapture.fortuneFormulaActive()) return original.call(stack, context);
        int before = capture ? stack.getCount() : -1;
        ItemStack snapshot = capture && before > 0 ? stack.copyWithCount(1) : ItemStack.EMPTY;
        HarvestDropCapture.beginFortuneFormula(before);
        ItemStack result; int safeResult;
        try {
            result = original.call(stack, context);
        } finally {
            safeResult = HarvestDropCapture.endFortuneFormula();
        }
        if (capture && before > 0 && result == stack && safeResult == result.getCount())
            HarvestDropCapture.recordFortune(context, result, snapshot, before);
        return result;
    }
}

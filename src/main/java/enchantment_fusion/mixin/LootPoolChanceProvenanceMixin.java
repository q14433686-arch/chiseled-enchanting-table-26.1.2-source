package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import enchantment_fusion.mechanics.signatures.GrapnelLootChance;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** A direct pool condition is necessary for each output; entry-local conditions are not generalized. */
@Mixin(LootPool.class)
public abstract class LootPoolChanceProvenanceMixin {
    @Shadow @Final private List<LootItemCondition> conditions;
    @WrapMethod(method = "addRandomItems")
    private void ef$observePool(Consumer<ItemStack> output, LootContext context, Operation<Void> original) {
        GrapnelLootChance.Pool pool = GrapnelLootChance.beginPool(context, conditions);
        if (pool == null) { original.call(output, context); return; }
        boolean completed = false;
        try {
            original.call((Consumer<ItemStack>) stack -> { GrapnelLootChance.output(pool, stack); output.accept(stack); }, context);
            completed = true;
        } finally { GrapnelLootChance.endPool(pool, completed); }
    }
    @WrapOperation(method = "addRandomItems", at = @At(value = "INVOKE",
        target = "Ljava/util/function/Predicate;test(Ljava/lang/Object;)Z"))
    private boolean ef$poolConditions(Predicate<LootContext> condition, Object value, Operation<Boolean> original) {
        if (!(value instanceof LootContext context)) return original.call(condition, value);
        GrapnelLootChance.beginConditions(context);
        boolean passed = false, completed = false;
        try { passed = original.call(condition, value); completed = true; return passed; }
        finally { GrapnelLootChance.endConditions(context, passed, completed); }
    }
}

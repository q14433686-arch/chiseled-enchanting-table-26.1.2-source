package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import enchantment_fusion.mechanics.signatures.GrapnelLootChance;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceWithEnchantedBonusCondition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Compare the same actual roll against the real Looting and level-zero thresholds. */
@Mixin(LootItemRandomChanceWithEnchantedBonusCondition.class)
public abstract class LootChanceProvenanceMixin {
    @WrapMethod(method = "test(Lnet/minecraft/world/level/storage/loot/LootContext;)Z")
    private boolean ef$observeChance(LootContext context, Operation<Boolean> original) {
        var self = (LootItemRandomChanceWithEnchantedBonusCondition) (Object) this;
        GrapnelLootChance.Chance chance = GrapnelLootChance.beginChance(self, context);
        boolean result = false, completed = false;
        try { result = original.call(context); completed = true; return result; }
        finally { GrapnelLootChance.endChance(chance, result, completed); }
    }
    @WrapOperation(method = "test(Lnet/minecraft/world/level/storage/loot/LootContext;)Z", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/item/enchantment/LevelBasedValue;calculate(I)F"))
    private float ef$observeActualThreshold(LevelBasedValue value, int level, Operation<Float> original) {
        float threshold = original.call(value, level);
        GrapnelLootChance.observedThreshold((LootItemRandomChanceWithEnchantedBonusCondition) (Object) this, level, threshold);
        return threshold;
    }
    @WrapOperation(method = "test(Lnet/minecraft/world/level/storage/loot/LootContext;)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextFloat()F"))
    private float ef$observeActualRoll(RandomSource random, Operation<Float> original) {
        float roll = original.call(random);
        GrapnelLootChance.observedRoll((LootItemRandomChanceWithEnchantedBonusCondition) (Object) this, roll);
        return roll;
    }
}

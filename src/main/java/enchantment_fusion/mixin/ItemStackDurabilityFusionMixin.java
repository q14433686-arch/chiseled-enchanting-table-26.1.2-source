package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mechanics.signatures.DurabilityEconomy;
import enchantment_fusion.mechanics.signatures.DurabilityEconomyMath;
import enchantment_fusion.model.FusedPair;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Intercepts real durability changes after vanilla enchantment reduction, before breakage. */
@Mixin(ItemStack.class)
public abstract class ItemStackDurabilityFusionMixin {
    @WrapOperation(method = "processDurabilityChange", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;processDurabilityChange(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;I)I"))
    private int ef$nonNativeUnbreaking(ServerLevel level, ItemStack stack, int amount, Operation<Integer> original,
                                       int requested, ServerLevel contextLevel, @Nullable ServerPlayer player) {
        int vanilla = original.call(level, stack, amount);
        return player != null && DurabilityEconomy.actuallyEquipped(player, stack)
            ? DurabilityEconomy.nonNativeUnbreaking(level, stack, vanilla) : vanilla;
    }

    @WrapMethod(method = "applyDamage")
    private void ef$settleWearAndRescue(int newDamage, @Nullable ServerPlayer player, Consumer<Item> onBreak,
                                       Operation<Void> original) {
        ItemStack stack = (ItemStack) (Object) this;
        FusedPair pair = DurabilityEconomy.selected(stack, false);
        if (player == null || pair == null || !DurabilityEconomy.actuallyEquipped(player, stack)
            || !DurabilityEconomy.enter()) {
            original.call(newDamage, player, onBreak);
            return;
        }
        try {
            int maximum = stack.getMaxDamage(), before = stack.getDamageValue();
            boolean crossing = DurabilityEconomyMath.crossedOnePercent(maximum, before, newDamage);
            int restored = newDamage >= maximum && newDamage > before ? DurabilityEconomy.payRescue(player, maximum) : 0;
            int actualRequested = restored > 0 ? maximum - restored : newDamage;
            original.call(actualRequested, player, onBreak);
            // If another mod cancels or replaces damage, avoid charging armor for a nonexistent
            // transition. A complete break counts as crossing; normal onBreak already ran once.
            boolean applied = stack.isEmpty() || (stack.getMaxDamage() == maximum
                && (stack.getDamageValue() != before || (restored > 0 && stack.getDamageValue() == actualRequested)));
            if (restored > 0 && applied && !stack.isEmpty()) {
                CompendiumDiscoveryTracker.recordDiscovery(player, pair.primaryId(), pair.secondaryId());
            }
            if (crossing && applied) DurabilityEconomy.convertArmorWear(player, stack, pair, maximum);
        } finally {
            DurabilityEconomy.exit();
        }
    }
}

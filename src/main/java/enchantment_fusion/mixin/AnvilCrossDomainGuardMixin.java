package enchantment_fusion.mixin;

import enchantment_fusion.mechanics.CrossDomainTransmutations;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.Map;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Enforces {@code fusion_table_only_transmuted_name}: the Vanilla Anvil strictly obeys native
 * compatibility and can NEVER perform Cross-Domain Single Inscription ({@code 化形铭刻 / 异位化形}),
 * even when {@code player.getAbilities().instabuild} (Creative Mode) would otherwise let vanilla
 * force non-native books onto incompatible equipment.
 */
@Mixin(value = AnvilMenu.class, priority = 1200)
public abstract class AnvilCrossDomainGuardMixin extends ItemCombinerMenu {
    @Shadow
    @Final
    private DataSlot cost;

    public AnvilCrossDomainGuardMixin(
        @Nullable MenuType<?> type,
        int syncId,
        Inventory playerInventory,
        ContainerLevelAccess context,
        ItemCombinerMenuSlotDefinition forgingSlotsManager
    ) {
        super(type, syncId, playerInventory, context, forgingSlotsManager);
    }

    @Inject(method = "createResult", at = @At("TAIL"))
    private void ef$blockVanillaAnvilCrossDomainInscription(CallbackInfo ci) {
        ItemStack left = this.inputSlots.getItem(0);
        ItemStack right = this.inputSlots.getItem(1);
        ItemStack result = this.resultSlots.getItem(0);
        if (left.isEmpty() || right.isEmpty() || result.isEmpty()) {
            return;
        }
        // Book + Book merges stay vanilla (subject to sister-mod rules if installed)
        if (left.is(Items.ENCHANTED_BOOK)) {
            return;
        }

        // Prevent sacrificing a fused/transmuted item in the right anvil slot where its custom components would be lost
        if (!right.is(Items.ENCHANTED_BOOK)) {
            boolean rightHasFused = !right.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).isEmpty();
            boolean rightHasTransmuted = !right.getOrDefault(FusionDataComponents.TRANSMUTED_ENCHANTS, Map.of()).isEmpty();
            if (rightHasFused || rightHasTransmuted) {
                this.resultSlots.setItem(0, ItemStack.EMPTY);
                this.cost.set(0);
                return;
            }
        }

        ItemEnchantments leftEnchants = EnchantmentHelper.getEnchantmentsForCrafting(left);
        ItemEnchantments resultEnchants = EnchantmentHelper.getEnchantmentsForCrafting(result);
        for (var entry : resultEnchants.entrySet()) {
            var holder = entry.getKey();
            int newLevel = entry.getIntValue();
            int prevLevel = leftEnchants.getLevel(holder);
            if (newLevel > prevLevel) {
                String enchId = holder.unwrapKey().map(k -> k.identifier().toString()).orElse("");
                if (!holder.value().canEnchant(left) || CrossDomainTransmutations.resolve(left, enchId).isPresent()) {
                    this.resultSlots.setItem(0, ItemStack.EMPTY);
                    this.cost.set(0);
                    return;
                }
            }
        }
    }
}

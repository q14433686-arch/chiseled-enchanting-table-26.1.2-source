package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Non-native, missing Respiration shared by the selected helmet families; never writes an enchantment. */
final class RespirationPrimary {
    private RespirationPrimary() {}
    static List<PrimaryAttributeProjection.Source> sources(ServerPlayer owner) {
        ItemStack stack = owner.getItemBySlot(EquipmentSlot.HEAD);
        if (stack.isEmpty() || stack.isBroken() || !FusionCarrier.classify(stack).contains(FusionCarrier.HEAD_ARMOR)
            || !FusionStationOperations.lookupEnchantment(owner.registryAccess(), GrapnelAttribute.RESPIRATION)
                .map(holder -> holder.value().matchingSlot(EquipmentSlot.HEAD)).orElse(false)) return List.of();
        List<PrimaryAttributeProjection.Source> result = new ArrayList<>();
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs()) {
            boolean pressure = WaterPressureSkills.isBreathFirst(pair) && WaterPressureSkills.applies(pair, stack);
            boolean mining = UnderwaterMiningSkills.isSpendPair(pair) && SignatureCatalog.resolve(pair)
                .map(entry -> UnderwaterMiningSkills.applies(pair, stack, entry.id())).orElse(false);
            if (pressure || mining) result.add(new PrimaryAttributeProjection.Source(EquipmentSlot.HEAD, stack, pair));
            if (result.size() >= 64) break;
        }
        return List.copyOf(result);
    }
}

package enchantment_fusion.mechanics;

import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.registry.FusionDataComponents;
import enchantment_fusion.registry.FusionItems;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Restrained Vessel Capacity ("器魂槽") rules per GDD §3:
 * <ul>
 *   <li>Standard equipment starts with {@code 1} vessel slot ({@code ◇}).</li>
 *   <li>Gold equipment naturally starts with {@code 2} vessel slots ({@code ◇◇}).</li>
 *   <li>Non-gold equipment can inlay at most {@code +1} extra slot ({@code vessel_bonus = 1}),
 *       strictly capped at {@code 2} total across the entire game.</li>
 * </ul>
 */
public final class VesselCapacityRules {
    public static final int DEFAULT_SLOTS = 1;
    public static final int GOLD_INNATE_SLOTS = 2;
    public static final int HARD_CAP_SLOTS = FusedEnchantmentList.HARD_MAX_VESSEL_SLOTS;

    public static final TagKey<Item> INNATE_TWO_VESSEL_ITEMS = TagKey.create(
        Registries.ITEM,
        EnchantmentFusion.id("innate_two_vessel_items")
    );

    private static final Set<String> BUILTIN_GOLD_ITEM_IDS = Set.of(
        "minecraft:golden_sword",
        "minecraft:golden_axe",
        "minecraft:golden_pickaxe",
        "minecraft:golden_shovel",
        "minecraft:golden_hoe",
        "minecraft:golden_helmet",
        "minecraft:golden_chestplate",
        "minecraft:golden_leggings",
        "minecraft:golden_boots"
    );

    private VesselCapacityRules() {}

    public static int computeMaxSlots(boolean innateTwoSlots, int vesselBonus) {
        int base = innateTwoSlots ? GOLD_INNATE_SLOTS : DEFAULT_SLOTS;
        int clampedBonus = Math.max(0, Math.min(1, vesselBonus));
        return Math.min(HARD_CAP_SLOTS, base + clampedBonus);
    }

    public static boolean canExpandSlots(boolean innateTwoSlots, int vesselBonus) {
        return computeMaxSlots(innateTwoSlots, vesselBonus) < HARD_CAP_SLOTS;
    }

    public static boolean isInnateTwoSlotItemId(String itemId) {
        return itemId != null && BUILTIN_GOLD_ITEM_IDS.contains(itemId);
    }

    public static boolean isInnateTwoSlotStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (stack.is(INNATE_TWO_VESSEL_ITEMS)) {
            return true;
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && isInnateTwoSlotItemId(id.toString());
    }

    public static int getMaxVesselSlots(ItemStack stack) {
        if (!isPotentialEquipmentHost(stack)) {
            return 0;
        }
        int bonus = stack.getOrDefault(FusionDataComponents.VESSEL_BONUS, 0);
        return computeMaxSlots(isInnateTwoSlotStack(stack), bonus);
    }

    public static int maxVesselSlots(ItemStack stack) {
        return getMaxVesselSlots(stack);
    }

    public static int vesselBonus(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        return stack.getOrDefault(FusionDataComponents.VESSEL_BONUS, 0);
    }

    public static int getUsedVesselSlots(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        return stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).size();
    }

    public static boolean canExpandStack(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !isPotentialEquipmentHost(stack)) {
            return false;
        }
        int bonus = stack.getOrDefault(FusionDataComponents.VESSEL_BONUS, 0);
        return canExpandSlots(isInnateTwoSlotStack(stack), bonus);
    }

    public static boolean isPotentialEquipmentHost(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (stack.is(Items.BOOK) || stack.is(Items.ENCHANTED_BOOK) || stack.is(FusionItems.FUSED_ENCHANTED_BOOK)
            || stack.is(Items.TOTEM_OF_UNDYING) || stack.is(Items.GLASS_BOTTLE) || stack.is(Items.EXPERIENCE_BOTTLE)) {
            return false;
        }
        return stack.getMaxStackSize() == 1 && (stack.isDamageableItem() || stack.isEnchantable() || stack.isEnchanted());
    }
}

package chiseled_enchanting_table.mechanics.transformation;

import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.ApplyEnchantmentPayload;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.OptionType;
import chiseled_enchanting_table.menu.ChiseledEnchantingTableScreenHandler;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Transformation gameplay mechanic: turn an input item into its data-defined output, consuming the
 * resolved costs. Lives in {@code mechanics.transformation} so the transformation rule is decoupled
 * from the menu container and from the enchantment mechanic.
 */
public final class ItemTransformationApplication {
    private ItemTransformationApplication() {}

    /**
     * Apply a transformation option to the menu's enchantable slot. Returns true if applied.
     *
     * @param handler the open menu (provides slots, cost resolution and consumption)
     * @param payload the apply request (id identifies the transformation rule)
     */
    public static boolean apply(ChiseledEnchantingTableScreenHandler handler, ApplyEnchantmentPayload payload) {
        var transformation = ChiseledEnchantingTableData.itemTransformation(payload.enchantment_id());
        if (transformation.isEmpty()) {
            return false;
        }
        var rule = transformation.get();
        ItemStack input = handler.get_enchantable_item();
        if (!rule.matchesInput(input)) {
            return false;
        }
        // Charge costs at the transformation's defined level.
        if (!handler.applyEnchantementCondition(new ApplyEnchantmentPayload(OptionType.TRANSFORMATION, payload.enchantment_id(), rule.level()))) {
            return false;
        }

        Player player = handler.player;
        Level world = handler.world();
        ItemStack outStack = rule.outputStack(input.getCount());
        handler.setEnchantableSlot(outStack);
        handler.markInventoryChanged();
        player.awardStat(Stats.ENCHANT_ITEM);
        world.playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 2.0F, world.getRandom().nextFloat() * 0.1F + 0.9F);
        chiseled_enchanting_table.utils.Advancement.give(player, "transmute_item");
        if (outStack.is(net.minecraft.world.item.Items.ENCHANTED_GOLDEN_APPLE)) {
            chiseled_enchanting_table.utils.Advancement.give(player, "enchanted_golden_apple");
        }
        return true;
    }
}

package chiseled_enchanting_table.mechanics.transformation;

import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData.ItemTransformation;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.ApplyEnchantmentPayload;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.OptionType;
import chiseled_enchanting_table.menu.ChiseledEnchantingTableScreenHandler;
import chiseled_enchanting_table.utils.Advancement;
import chiseled_enchanting_table.utils.InventoryUtils;
import java.util.Optional;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Item transformation gameplay mechanic with strict linear unit/batch consumption:
 * <ul>
 *   <li>Standard transmutations strictly consume {@code 1} input item per ritual, closing the
 *       multi-stack duplication loophole.</li>
 *   <li>{@link ChiseledEnchantingTableData#BLANK_OVERWRITE_ID} supports stack batching up to
 *       {@link ChiseledEnchantingTableScreenHandler#resolveDepletedTomeBatchCount()} and scales both
 *       material bundles and baseline XP strictly linearly by {@code batchCount}.</li>
 * </ul>
 */
public final class ItemTransformationApplication {
    private ItemTransformationApplication() {}

    public static boolean apply(ChiseledEnchantingTableScreenHandler handler, ApplyEnchantmentPayload payload) {
        Optional<ItemTransformation> match = ChiseledEnchantingTableData.itemTransformation(payload.enchantment_id());
        if (match.isEmpty()) {
            return false;
        }
        ItemTransformation rule = match.get();
        ItemStack input = handler.get_enchantable_item();
        if (!rule.matchesInput(input)) {
            return false;
        }

        boolean isBlankOverwrite = rule.id().equals(ChiseledEnchantingTableData.BLANK_OVERWRITE_ID);
        int batchCount = isBlankOverwrite ? handler.resolveDepletedTomeBatchCount() : 1;
        batchCount = Math.max(1, Math.min(input.getCount(), batchCount));

        ApplyEnchantmentPayload canonical = new ApplyEnchantmentPayload(
            OptionType.TRANSFORMATION,
            rule.id(),
            rule.level()
        );
        if (!handler.applyEnchantementCondition(canonical)) {
            return false;
        }

        ItemStack produced = rule.outputStack(batchCount * Math.max(1, rule.level()));
        if (input.getCount() <= batchCount) {
            handler.setEnchantableSlot(produced);
        } else {
            input.shrink(batchCount);
            InventoryUtils.insertItem(handler.player, produced, Optional.empty());
        }
        handler.advanceTableEpiphanySeed(false);

        Player player = handler.player;
        handler.markInventoryChanged();
        player.getInventory().setChanged();
        handler.world().playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
        Advancement.give(player, "transmute_item");
        if (rule.id().equals(ChiseledEnchantingTableData.BLANK_OVERWRITE_ID)) {
            Advancement.give(player, "blank_overwrite");
        }
        if (produced.is(Items.ENCHANTED_GOLDEN_APPLE)) {
            Advancement.give(player, "enchanted_golden_apple");
        }
        return true;
    }
}

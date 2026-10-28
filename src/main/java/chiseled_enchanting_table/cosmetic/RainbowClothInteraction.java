package chiseled_enchanting_table.cosmetic;

import chiseled_enchanting_table.block.ChiseledEnchantingTableBlock;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/** The early callback is required: vanilla skips block-use handlers while sneaking with an item. */
public final class RainbowClothInteraction {
    private RainbowClothInteraction() {}
    public static void register() {
        UseBlockCallback.EVENT.register((player,level,hand,hit)->{
            var stack=player.getItemInHand(hand);
            var name=stack.get(DataComponents.CUSTOM_NAME);
            if (!player.isShiftKeyDown() || !stack.is(Items.NAME_TAG) || name==null || !RainbowCloth.magicName(name.getString()))
                return InteractionResult.PASS;
            var pos=hit.getBlockPos(); var state=level.getBlockState(pos);
            if (!(state.getBlock() instanceof ChiseledEnchantingTableBlock)) return InteractionResult.PASS;
            if (player.isSpectator() || !player.getAbilities().mayBuild || !level.mayInteract(player,pos)
                || !player.mayUseItemAt(pos,hit.getDirection(),stack)) return InteractionResult.FAIL;
            if (!level.isClientSide() && !state.getValue(ChiseledEnchantingTableBlock.RAINBOW_CLOTH)) {
                level.setBlock(pos,state.setValue(ChiseledEnchantingTableBlock.RAINBOW_CLOTH,true),Block.UPDATE_ALL);
                chiseled_enchanting_table.menu.TableVisualEvents.onClothVisual(level, pos, 0, false, true);
            }
            // A reusable cosmetic key, not a rename: no name-tag consumption or table-name mutation.
            return InteractionResult.SUCCESS;
        });
    }
}

package chiseled_enchanting_table.smoke;

import chiseled_enchanting_table.menu.ChiseledEnchantingTableScreenHandler;
import chiseled_enchanting_table.utils.Advancement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;

/** Real world fixtures: normal advancement grant, actual bookshelf contents and normal live synchronization. */
final class TomeWorldProbe {
    static void prepare(ChiseledEnchantingTableScreenHandler menu, ServerPlayer player, BlockPos table) {
        Advancement.give(player, Advancement.HOW_MUCH_LONGER);
        var level=player.level();
        var pos=table.offset(2,0,0);
        level.setBlockAndUpdate(table.offset(1,0,0),Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(table.above(),Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pos,Blocks.CHISELED_BOOKSHELF.defaultBlockState());
        var shelf=(ChiseledBookShelfBlockEntity)level.getBlockEntity(pos);
        var registry=level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        for(int slot=0;slot<2;slot++) {
            var book=new ItemStack(Items.ENCHANTED_BOOK);
            book.enchant(registry.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT,Identifier.withDefaultNamespace(slot==0?"sharpness":"mending"))),1);
            if(slot==0)book.enchant(registry.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT,Identifier.withDefaultNamespace("knockback"))),1);
            shelf.setItem(slot,book);
        }
        shelf.setChanged();
        menu.sendInitialLiveSync();
    }
}

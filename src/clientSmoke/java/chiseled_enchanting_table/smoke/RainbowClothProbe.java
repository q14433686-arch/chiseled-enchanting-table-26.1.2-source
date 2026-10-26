package chiseled_enchanting_table.smoke;

import chiseled_enchanting_table.block.ChiseledEnchantingTableBlock;
import chiseled_enchanting_table.block.ChiseledEnchantingTableBlockEntity;
import chiseled_enchanting_table.cosmetic.RainbowCloth;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

final class RainbowClothProbe {
    private static void check(boolean ok,String why) { if(!ok)throw new AssertionError("rainbow cloth: "+why); }
    static void verify(ServerPlayer player,BlockPos pos) {
        var level=player.level();var saved=player.getMainHandItem().copy();boolean sneaking=player.isShiftKeyDown();
        var tag=new ItemStack(Items.NAME_TAG);tag.set(DataComponents.CUSTOM_NAME,Component.literal("jeb_"));
        var hit=new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false);
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND,tag);
            player.setShiftKeyDown(false);
            UseBlockCallback.EVENT.invoker().interact(player,level,InteractionHand.MAIN_HAND,hit);
            check(!level.getBlockState(pos).getValue(ChiseledEnchantingTableBlock.RAINBOW_CLOTH),"non-sneak enabled egg");
            player.setShiftKeyDown(true);tag.set(DataComponents.CUSTOM_NAME,Component.literal("_JEB"));
            UseBlockCallback.EVENT.invoker().interact(player,level,InteractionHand.MAIN_HAND,hit);
            check(!level.getBlockState(pos).getValue(ChiseledEnchantingTableBlock.RAINBOW_CLOTH),"wrong name enabled egg");
            tag.set(DataComponents.CUSTOM_NAME,Component.literal("jeb_"));
            UseBlockCallback.EVENT.invoker().interact(player,level,InteractionHand.MAIN_HAND,hit);
            var state=level.getBlockState(pos);
            check(state.getValue(ChiseledEnchantingTableBlock.RAINBOW_CLOTH),"sneaking callback did not enable");
            check(tag.getCount()==1,"cosmetic key consumed");
            var be=(ChiseledEnchantingTableBlockEntity)level.getBlockEntity(pos);
            var dropped=Block.getDrops(state,level,pos,be).stream().filter(s->s.is(state.getBlock().asItem())).findFirst().orElseThrow();
            check(dropped.get(DataComponents.CUSTOM_DATA).copyTag().getBooleanOr(RainbowCloth.ITEM_FLAG,false),"actual loot lost rainbow flag");
            // Dyed underlying colour and frozen metadata must coexist with the flag.
            be.color=0x123456;
            check(be.collectComponents().get(DataComponents.DYED_COLOR).rgb()==0x123456,"base dye lost");
            System.out.println("RAINBOW CLOTH PASS: real sneaking callback + exact key/name guards + reusable tag + actual loot custom-data flag + underlying dye preserved");
        } finally { player.setItemInHand(InteractionHand.MAIN_HAND,saved);player.setShiftKeyDown(sneaking); }
    }
}

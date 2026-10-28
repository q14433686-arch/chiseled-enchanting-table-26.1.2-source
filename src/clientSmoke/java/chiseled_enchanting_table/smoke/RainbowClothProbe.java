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
        var level=player.level();var saved=player.getMainHandItem().copy();boolean sneaking=player.isShiftKeyDown(); boolean mayBuild=player.getAbilities().mayBuild;
        var tag=new ItemStack(Items.NAME_TAG);tag.set(DataComponents.CUSTOM_NAME,Component.literal("jeb_"));
        var hit=new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false);
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND,tag);
            player.setShiftKeyDown(false);
            UseBlockCallback.EVENT.invoker().interact(player,level,InteractionHand.MAIN_HAND,hit);
            check(!level.getBlockState(pos).getValue(ChiseledEnchantingTableBlock.RAINBOW_CLOTH),"non-sneak enabled egg");
            player.setShiftKeyDown(true);
            player.getAbilities().mayBuild=false;
            UseBlockCallback.EVENT.invoker().interact(player,level,InteractionHand.MAIN_HAND,hit);
            check(!level.getBlockState(pos).getValue(ChiseledEnchantingTableBlock.RAINBOW_CLOTH),"build permission bypassed");
            player.getAbilities().mayBuild=mayBuild;
            tag.set(DataComponents.CUSTOM_NAME,Component.literal("_JEB"));
            UseBlockCallback.EVENT.invoker().interact(player,level,InteractionHand.MAIN_HAND,hit);
            check(!level.getBlockState(pos).getValue(ChiseledEnchantingTableBlock.RAINBOW_CLOTH),"wrong name enabled egg");
            tag.set(DataComponents.CUSTOM_NAME,Component.literal("jeb_"));
            UseBlockCallback.EVENT.invoker().interact(player,level,InteractionHand.MAIN_HAND,hit);
            var state=level.getBlockState(pos);
            check(state.getValue(ChiseledEnchantingTableBlock.RAINBOW_CLOTH),"sneaking callback did not enable");
            check(tag.getCount()==1,"cosmetic key consumed");
            var be=(ChiseledEnchantingTableBlockEntity)level.getBlockEntity(pos);
            be.color=0x123456; be.pendingFreezeTicks=1200;
            var dropped=Block.getDrops(state,level,pos,be).stream().filter(s->s.is(state.getBlock().asItem())).findFirst().orElseThrow();
            check(dropped.get(DataComponents.CUSTOM_DATA).copyTag().getBooleanOr(RainbowCloth.ITEM_FLAG,false),"actual loot lost rainbow flag");
            check(dropped.get(DataComponents.DYED_COLOR).rgb()==0x123456,"base dye lost");
            check(dropped.get(DataComponents.CUSTOM_DATA).copyTag().getLongOr("pendingFreezeTicks",0)==1200,"freeze metadata lost");
            var placed=state.getBlock().getStateForPlacement(new net.minecraft.world.item.context.BlockPlaceContext(player,InteractionHand.MAIN_HAND,dropped,hit));
            check(placed.getValue(ChiseledEnchantingTableBlock.RAINBOW_CLOTH),"placement lost rainbow flag");
            var clone=new ChiseledEnchantingTableBlockEntity(pos,placed);
            clone.applyComponentsFromItemStack(dropped);
            check(clone.color==0x123456 && clone.pendingFreezeTicks==1200,"replacement item component restore failed");
            player.setShiftKeyDown(false);
            var dye=new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse("minecraft:red_dye")),2);
            state.useItemOn(dye,level,player,InteractionHand.MAIN_HAND,hit);
            check(!level.getBlockState(pos).getValue(ChiseledEnchantingTableBlock.RAINBOW_CLOTH),"dye did not exit rainbow mode");
            check(dye.getCount()==1,"normal dye consumption changed");
            player.setShiftKeyDown(true);
            UseBlockCallback.EVENT.invoker().interact(player,level,InteractionHand.MAIN_HAND,hit);
            check(level.getBlockState(pos).getValue(ChiseledEnchantingTableBlock.RAINBOW_CLOTH),"reactivation failed");
            player.getInventory().setItem(8,dropped.copy());
            var normal=dropped.copy();
            var normalData=normal.get(DataComponents.CUSTOM_DATA).copyTag();
            normalData.remove(RainbowCloth.ITEM_FLAG);
            normal.set(DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(normalData));
            player.getInventory().setItem(7,normal);
            be.pendingFreezeTicks=0;
            System.out.println("RAINBOW CLOTH PASS: real sneaking callback + exact key/name guards + reusable tag + actual loot/placement flag + dye/freeze components + dye exit/consumption + reactivation");
        } finally { player.setItemInHand(InteractionHand.MAIN_HAND,saved);player.setShiftKeyDown(sneaking);player.getAbilities().mayBuild=mayBuild; }
    }
}

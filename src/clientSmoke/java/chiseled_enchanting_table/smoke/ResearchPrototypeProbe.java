package chiseled_enchanting_table.smoke;

import chiseled_enchanting_table.research.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;

/** Uses the actual player command dispatcher, not direct grants or fake evidence callbacks. */
final class ResearchPrototypeProbe {
    private static void check(boolean ok,String why) { if (!ok) throw new AssertionError("research prototype: "+why); }
    private static void command(ServerPlayer player,String command) {
        player.level().getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(),"cet_research "+command);
    }
    static void verify(ServerPlayer player,BlockPos table) {
        check(ResearchPrototype.enabled(),"opt-in smoke flag absent");
        var shelf=(ChiseledBookShelfBlockEntity)player.level().getBlockEntity(table.offset(2,0,0));
        var saved=shelf.getItem(4).copy(); var menu=player.containerMenu;
        double x=player.getX(),y=player.getY(),z=player.getZ();
        try {
            var book=new ItemStack(Items.ENCHANTED_BOOK);
            var efficiency=player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(
                ResourceKey.create(Registries.ENCHANTMENT,Identifier.withDefaultNamespace("efficiency")));
            book.enchant(efficiency,1); shelf.setItem(4,book);
            player.containerMenu=player.inventoryMenu; // chat commands must work after closing the table screen
            command(player,"bind "+table.getX()+" "+table.getY()+" "+table.getZ());
            check(ResearchWorkspace.resolve(player).isPresent(),"bound chat workspace unavailable");
            command(player,"start");
            check(ResearchRuntime.journal(player).attempts().containsKey("prototype:efficiency_2"),"real start command failed");
            command(player,"analyze");
            check(ResearchRuntime.journal(player).attempts().get("prototype:efficiency_2").observations().isEmpty(),"level I falsely supplied level II evidence");
            book.enchant(efficiency,2); shelf.setItem(4,book);
            player.setPos(x+32,y,z); command(player,"analyze");
            check(ResearchRuntime.journal(player).attempts().get("prototype:efficiency_2").observations().isEmpty(),"remote command bypassed range");
            player.setPos(x,y,z); command(player,"analyze"); command(player,"conclude");
            check(ResearchRuntime.manufacturingLimit(player,"minecraft:efficiency","prototype:general")==2,"analysis/conclusion command loop failed");
            check(shelf.getItem(4).getCount()==1,"analysis consumed sample");
            command(player,"cancel"); command(player,"unbind");
            check(ResearchWorkspace.resolve(player).isEmpty(),"unbind retained workspace");
            check(ResearchRuntime.journal(player).conclusions().contains("prototype:efficiency_2_learned"),"unbind/cancel erased knowledge");
            System.out.println("RESEARCH PROTOTYPE PASS: real chat commands + closed-menu binding + level I start + sample quality denial + range denial + level II analysis + conclusion + learned limit + nonconsuming sample + unbind");
        } finally {
            player.setPos(x,y,z); player.containerMenu=menu; shelf.setItem(4,saved); shelf.setChanged();
        }
    }
}

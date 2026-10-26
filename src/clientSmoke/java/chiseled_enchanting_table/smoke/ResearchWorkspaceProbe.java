package chiseled_enchanting_table.smoke;

import chiseled_enchanting_table.block.ChiseledEnchantingTableBlock;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.research.ResearchWorkspace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;

/** Uses the real menu and world shelf created by TomeWorldProbe; restores every changed fixture. */
final class ResearchWorkspaceProbe {
    private static void check(boolean ok,String why) { if (!ok) throw new AssertionError("research workspace: " + why); }
    static void verify(ServerPlayer player,BlockPos table) {
        var level = player.level();
        var cfg = ChiseledEnchantingTableData.shelfGatesConfig();
        var shelf = (ChiseledBookShelfBlockEntity)level.getBlockEntity(table.offset(2,0,0));
        var slot2 = shelf.getItem(2).copy(); var slot3 = shelf.getItem(3).copy();
        var oldMenu = player.containerMenu;
        boolean oldBuild = player.getAbilities().mayBuild;
        double x = player.getX(), y = player.getY(), z = player.getZ();
        BlockPos wood = ChiseledEnchantingTableBlock.getScanOffsets(cfg).stream()
            .filter(p -> Math.abs(p.getX())+Math.abs(p.getY())+Math.abs(p.getZ()) >= 2)
            .map(table::offset).filter(p -> level.hasChunkAt(p) && level.getBlockState(p).isAir())
            .findFirst().orElseThrow();
        var woodState = level.getBlockState(wood);
        try {
            var baseline = ResearchWorkspace.resolve(player).orElseThrow();
            check(baseline.knowledge().containsKey("minecraft:sharpness") && baseline.knowledge().containsKey("minecraft:mending"),"actual stored enchantments absent");
            shelf.setItem(2,shelf.getItem(0).copy());
            check(ResearchWorkspace.resolve(player).orElseThrow().equals(baseline),"duplicate book increased breadth/depth");
            var higher = new ItemStack(Items.ENCHANTED_BOOK);
            higher.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(
                ResourceKey.create(Registries.ENCHANTMENT,Identifier.withDefaultNamespace("sharpness"))),5);
            shelf.setItem(3,higher);
            var upgraded = ResearchWorkspace.resolve(player).orElseThrow();
            check(upgraded.knowledge().get("minecraft:sharpness") == 5,"highest quality not selected");
            check(upgraded.knowledge().size() == baseline.knowledge().size(),"quality upgrade inflated breadth");
            level.setBlockAndUpdate(wood,Blocks.BOOKSHELF.defaultBlockState());
            check(ResearchWorkspace.resolve(player).orElseThrow().shelves() == upgraded.shelves()+1,"wood shelf not counted");
            player.getAbilities().mayBuild = false;
            check(ResearchWorkspace.resolve(player).isEmpty(),"build permission bypass");
            player.getAbilities().mayBuild = oldBuild;
            player.containerMenu = player.inventoryMenu;
            check(ResearchWorkspace.resolve(player).isEmpty(),"no table menu authorized");
            player.containerMenu = oldMenu;
            player.setPos(x+32,y,z);
            check(ResearchWorkspace.resolve(player).isEmpty(),"out-of-range menu authorized");
            player.setPos(x,y,z);
            check(ResearchWorkspace.resolve(player).isPresent(),"authorization failed after restoring position");
            System.out.println("RESEARCH WORKSPACE PASS: real table menu + stored books + duplicate dedup + best quality + wooden shelf + build/menu/range denial + restored access");
        } finally {
            player.containerMenu = oldMenu; player.getAbilities().mayBuild = oldBuild; player.setPos(x,y,z);
            shelf.setItem(2,slot2); shelf.setItem(3,slot3); shelf.setChanged();
            level.setBlockAndUpdate(wood,woodState);
        }
    }
}

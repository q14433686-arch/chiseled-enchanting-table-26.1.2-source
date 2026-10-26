package chiseled_enchanting_table.gui;

import chiseled_enchanting_table.catalog.TomeCatalog;
import java.util.List;
import net.minecraft.client.Minecraft;

/** Runs catalogue hit targets and localized choices inside the registry-loaded real client. */
final class TomeDirectoryProbe {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError("catalogue: "+message); }
    static void verify(Minecraft client) {
        var m=new TomeCatalog();
        m.update(List.of(
            new TomeCatalog.Row(new TomeCatalog.Key(1,0),List.of("minecraft:sharpness","minecraft:knockback"),"A",3,4,false,false),
            new TomeCatalog.Row(new TomeCatalog.Key(1,1),List.of("minecraft:mending"),"B",0,8,true,true)));
        var controls=new TomeDirectoryControls();
        Runnable refresh=()->{};
        check(controls.click(1,12,0,0,m,client.player,refresh),"remaining filter hit target");
        controls.click(1,56,0,0,m,client.player,refresh);
        check(m.remaining==0 && m.visible().size()==1,"remaining filter choices");
        controls.click(130,23,0,0,m,client.player,refresh);
        check(m.visible().size()==2,"reset did not restore books");
        controls.click(1,34,0,0,m,client.player,refresh);
        check(m.selected().size()==2,"visible selection count control");
        controls.click(102,34,0,0,m,client.player,refresh);
        check(m.selected().isEmpty(),"clear selection control");
        controls.click(1,23,0,0,m,client.player,refresh);
        controls.click(1,56,0,0,m,client.player,refresh);
        check(m.sort==TomeCatalog.Sort.REMAINING,"sort menu choices");
        controls.click(102,23,0,0,m,client.player,refresh);
        check(m.descending && m.visible().getFirst().remaining()==3,"descending control");
        controls.click(1,1,0,0,m,client.player,refresh);
        check(controls.scroll(-1),"open enchantment dropdown did not own wheel input");
        controls.close(); check(!controls.scroll(-1),"closed dropdown swallowed list scroll");
        var id=chiseled_enchanting_table.ChiseledEnchantingTable.identifier("gui/tome_directory.png");
        try(var stream=client.getResourceManager().getResource(id).orElseThrow().open();
            var image=com.mojang.blaze3d.platform.NativeImage.read(stream)) {
            check(image.getWidth()==306 && image.getHeight()==90,"selected AI toolbar resource missing");
        } catch(java.io.IOException e) { throw new AssertionError(e); }
        System.out.println("CATALOGUE BOUNDARY PASS: real-client localized filter/sort dropdown hit targets + descending + visible selection/clear + popup wheel ownership + selected toolbar resource");
    }
}

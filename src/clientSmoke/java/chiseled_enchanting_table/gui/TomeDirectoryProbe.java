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
        press(controls,2,m,client,refresh);
        choose(controls,1,m,client,refresh);
        check(m.remaining==0 && m.visible().size()==1,"remaining filter choices");
        press(controls,6,m,client,refresh);
        check(m.visible().size()==2,"reset did not restore books");
        press(controls,7,m,client,refresh);
        check(m.selected().size()==2,"select-visible icon");
        press(controls,8,m,client,refresh);
        check(m.selected().isEmpty(),"clear icon");
        press(controls,4,m,client,refresh); choose(controls,1,m,client,refresh);
        check(m.sort==TomeCatalog.Sort.REMAINING,"sort menu choices");
        press(controls,5,m,client,refresh);
        check(m.descending && m.visible().getFirst().remaining()==3,"descending icon");
        press(controls,0,m,client,refresh);
        check(controls.scroll(-1),"open enchantment dropdown did not own wheel input");
        controls.close(); check(!controls.scroll(-1),"closed dropdown swallowed list scroll");
        check(!controls.click(1,1,0,0,m,client.player,refresh),"outer ornament was an active button");
        for(String name:List.of("frame","frame_hover","frame_disabled","icon_ascending","icon_descending","icon_reset","icon_select_visible","icon_clear",
            "icon_lock_next","icon_lock_full","icon_lock_permanent","icon_unlock","icon_batch_full")) {
            var id=chiseled_enchanting_table.ChiseledEnchantingTable.identifier("gui/catalogue_"+name+".png");
            try(var stream=client.getResourceManager().getResource(id).orElseThrow().open();
                var image=com.mojang.blaze3d.platform.NativeImage.read(stream)) {
                int size=name.startsWith("frame") ? 32 : 7;
                check(image.getWidth()==size && image.getHeight()==size,"shared skin resource geometry: "+name);
            } catch(java.io.IOException e) { throw new AssertionError(e); }
        }
        System.out.println("CATALOGUE BOUNDARY PASS: real-client localized filter/sort dropdown hit targets + descending + visible selection/clear + popup wheel ownership + selected toolbar resource");
    }
    private static void press(TomeDirectoryControls controls,int id,TomeCatalog model,Minecraft client,Runnable refresh) {
        var b=TomeDirectoryLayout.button(id);
        check(controls.click(b.x()+b.width()/2.0,b.y()+b.height()/2.0,0,0,model,client.player,refresh),"button hit region "+id);
    }
    private static void choose(TomeDirectoryControls controls,int index,TomeCatalog model,Minecraft client,Runnable refresh) {
        var b=TomeDirectoryLayout.popupRow(index);
        check(controls.click(b.x()+b.width()/2.0,b.y()+b.height()/2.0,0,0,model,client.player,refresh),"popup hit region");
    }

}

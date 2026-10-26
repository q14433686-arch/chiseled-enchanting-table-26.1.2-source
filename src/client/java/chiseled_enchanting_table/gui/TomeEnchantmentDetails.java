package chiseled_enchanting_table.gui;

import java.util.*;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.TomeBookEntry;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/** Bounded pages ensure Shift can inspect every enchantment without a screen-height tooltip wall. */
final class TomeEnchantmentDetails {
    private static String identity = "";
    private static int page;
    private static void identify(TomeBookEntry book) {
        String next = book.shelf_pos()+":"+book.slot()+":"+book.enchantments().hashCode();
        if (!next.equals(identity)) { identity=next; page=0; }
    }
    static void scroll(TomeBookEntry book, double delta) {
        identify(book); page=Math.max(0,page-(int)Math.signum(delta));
    }
    static void render(GuiGraphicsExtractor ctx, Font font, TomeBookEntry book, Player player, int x, int y) {
        identify(book);
        int size=Math.max(1,Math.min(8,(ctx.guiHeight()-80)/18));
        int pages=Math.max(1,(book.enchantments().size()+size-1)/size); page=Math.min(page,pages-1);
        var lines=new ArrayList<Component>();
        lines.add(Component.literal(TomeDisplayNames.tomeTitle(book,player)));
        lines.add(Component.translatable("gui.chiseled_enchanting_table.catalog.enchantments"));
        for (var e:book.enchantments().subList(page*size,Math.min(book.enchantments().size(),(page+1)*size))) {
            lines.add(EnchantementListWidget.findEnchantment(e.enchantment_id(),player)
                .map(h -> net.minecraft.world.item.enchantment.Enchantment.getFullname(h,e.enchantment_level()))
                .orElse(Component.literal(e.enchantment_id()+" "+e.enchantment_level())));
        }
        lines.add(Component.translatable("gui.chiseled_enchanting_table.catalog.location",book.shelf_pos().getX(),book.shelf_pos().getY(),book.shelf_pos().getZ(),book.slot()+1));
        if (pages>1) lines.add(Component.translatable("gui.chiseled_enchanting_table.catalog.page",page+1,pages));
        TooltipPresentation.show(ctx,font,lines,Optional.empty(),x,y,null);
    }
}

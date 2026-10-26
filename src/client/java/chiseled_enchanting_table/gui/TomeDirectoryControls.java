package chiseled_enchanting_table.gui;


import chiseled_enchanting_table.catalog.TomeCatalog;
import java.util.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Compact catalogue controls and scrollable choice popup, independent of screen/list owners. */
final class TomeDirectoryControls {
    private static final String PREFIX = "gui.chiseled_enchanting_table.catalog.";
    private int open = -1, offset;
    private record Choice(String label, Runnable apply) {}
    private List<Choice> choices = List.of();
    static String text(String key, Object... args) { return Component.translatable(PREFIX + key, args).getString(); }
    boolean scroll(double amount) {
        if (open < 0) return false;
        offset = Math.max(0, Math.min(Math.max(0, choices.size()-TomeDirectoryLayout.POPUP_LIMIT), offset - (int)Math.signum(amount)));
        return true;
    }
    void close() { open = -1; }
    private static String enchantName(String id, net.minecraft.world.entity.player.Player player) {
        var identifier = net.minecraft.resources.Identifier.tryParse(id);
        return identifier == null ? id : EnchantementListWidget.findEnchantment(identifier, player)
            .map(h -> h.value().description().getString()).orElse(id);
    }
    private List<Choice> choices(int button, TomeCatalog model, net.minecraft.world.entity.player.Player player) {
        var result = new ArrayList<Choice>();
        if (button == 0) {
            result.add(new Choice(text("all_enchantments"), () -> model.enchantment = null));
            model.source().stream().flatMap(r -> r.enchantments().stream()).distinct()
                .sorted(Comparator.comparing(id -> enchantName(id, player))).forEach(id ->
                    result.add(new Choice(enchantName(id, player), () -> model.enchantment = id)));
        } else if (button == 1) {
            for (var value : TomeCatalog.Lock.values()) result.add(new Choice(text("lock."+value.name().toLowerCase(Locale.ROOT)), () -> model.lock = value));
        } else if (button == 2 || button == 3) {
            result.add(new Choice(text("any"), () -> { if (button == 2) model.remaining = null; else model.total = null; }));
            model.source().stream().map(r -> button == 2 ? r.remaining() : r.total()).distinct().sorted().forEach(n ->
                result.add(new Choice(Integer.toString(n), () -> { if (button == 2) model.remaining = n; else model.total = n; })));
        } else {
            for (var value : TomeCatalog.Sort.values()) result.add(new Choice(text("sort."+value.name().toLowerCase(Locale.ROOT)), () -> model.sort = value));
        }
        return List.copyOf(result);
    }
    boolean click(double mx, double my, int x, int y, TomeCatalog model,
                  net.minecraft.world.entity.player.Player player, Runnable refresh) {
        if (open >= 0) {
            int count = Math.min(TomeDirectoryLayout.POPUP_LIMIT, choices.size()-offset);
            for(int row=0;row<count;row++) {
                if(!TomeDirectoryLayout.popupRow(row).contains(mx-x,my-y)) continue;
                choices.get(offset+row).apply().run(); model.refresh(); refresh.run();
                break;
            }
            close(); return true;
        }
        for (int i=0;i<9;i++) {
            if (!TomeDirectoryLayout.button(i).contains(mx-x,my-y)) continue;
            switch (i) {
                case 5 -> { model.descending = !model.descending; model.refresh(); refresh.run(); }
                case 6 -> { model.reset(); refresh.run(); }
                case 7 -> model.selectVisible();
                case 8 -> model.clearSelection();
                default -> { choices = choices(i, model, player); open = i; offset = 0; }
            }
            return true;
        }
        return false;
    }
    boolean render(GuiGraphicsExtractor ctx, Font font, int x, int y, int mx, int my,
                   TomeCatalog model, net.minecraft.world.entity.player.Player player) {
        CatalogueSkin.frame(ctx,x,y,TomeDirectoryLayout.WIDTH,TomeDirectoryLayout.HEIGHT,false,true);
        String[] labels = {
            model.enchantment == null ? text("all_enchantments") : enchantName(model.enchantment, player),
            text("lock."+model.lock.name().toLowerCase(Locale.ROOT)),
            text("remaining", model.remaining == null ? text("any") : model.remaining),
            text("total", model.total == null ? text("any") : model.total),
            text("sort_by", text("sort."+model.sort.name().toLowerCase(Locale.ROOT))),
            text(model.descending ? "descending" : "ascending"),text("reset"),text("select_visible"),text("clear")
        };
        boolean hovered = false;
        String[] icons={model.descending ? "descending" : "ascending","reset","select_visible","clear"};
        ctx.enableScissor(x+TomeDirectoryLayout.FRAME,y+TomeDirectoryLayout.FRAME,
            x+TomeDirectoryLayout.WIDTH-TomeDirectoryLayout.FRAME,y+TomeDirectoryLayout.HEIGHT-TomeDirectoryLayout.FRAME);
        try {
            for (int i=0;i<labels.length;i++) {
                var b=TomeDirectoryLayout.button(i); int bx=x+b.x(), by=y+b.y();
                boolean over=b.contains(mx-x,my-y); hovered |= over;
                boolean enabled=i!=7 || !model.visible().isEmpty();
                if(i<5) {
                    CatalogueSkin.frame(ctx,bx,by,b.width(),b.height(),over,true);
                    CatalogueSkin.text(ctx,font,labels[i],bx,by,b.width(),b.height(),TomeDirectoryLayout.BUTTON_INSET,0xFFE0EBDD);
                } else CatalogueSkin.icon(ctx,icons[i-5],bx,by,b.width(),b.height(),over,enabled);
                if (over && open < 0) TooltipPresentation.show(ctx,font,i<5
                    ? List.of(Component.literal(labels[i]),Component.translatable(PREFIX+"help"))
                    : List.of(Component.literal(labels[i])),Optional.empty(),mx,my,null);
            }
            var b=TomeDirectoryLayout.selection();
            CatalogueSkin.text(ctx,font,text("selection",model.selected().size(),model.visible().size()),
                x+b.x(),y+b.y(),b.width(),b.height(),1,0xFFD0E8E1);
        } finally { ctx.disableScissor(); }
        if (open >= 0) {
            int count=Math.min(TomeDirectoryLayout.POPUP_LIMIT,choices.size()-offset);
            var outer=TomeDirectoryLayout.popup(count);
            CatalogueSkin.frame(ctx,x+outer.x(),y+outer.y(),outer.width(),outer.height(),false,true);
            for (int j=0;j<count;j++) {
                var b=TomeDirectoryLayout.popupRow(j); int bx=x+b.x(),by=y+b.y();
                if(b.contains(mx-x,my-y)) ctx.fill(bx+1,by+1,bx+b.width()-1,by+b.height()-1,0xFF284B43);
                CatalogueSkin.text(ctx,font,choices.get(offset+j).label(),bx,by,b.width(),b.height(),2,0xFFE0EBDD);
            }
            return true;
        }
        return hovered;
    }
}

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
    private static int[] box(int i) {
        return switch (i) {
            case 0 -> new int[]{0,0,99,10}; case 1 -> new int[]{100,0,51,10};
            case 2 -> new int[]{0,11,75,10}; case 3 -> new int[]{76,11,75,10};
            case 4 -> new int[]{0,22,99,10}; case 5 -> new int[]{100,22,24,10};
            case 6 -> new int[]{125,22,26,10}; case 7 -> new int[]{0,33,99,10};
            default -> new int[]{100,33,51,10};
        };
    }
    boolean scroll(double amount) {
        if (open < 0) return false;
        offset = Math.max(0, Math.min(Math.max(0, choices.size()-7), offset - (int)Math.signum(amount)));
        return true;
    }
    void close() { open = -1; }
    private static String enchantName(String id, net.minecraft.world.entity.player.Player player) {
        var identifier = net.minecraft.resources.Identifier.tryParse(id);
        return identifier == null ? id : EnchantmentDisplayNames.findEnchantment(identifier, player)
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
            int row = (int)((my-y-44)/11);
            if (mx >= x && mx < x+151 && my >= y+44 && row >= 0 && row < Math.min(7, choices.size()-offset)) {
                choices.get(offset+row).apply().run(); model.refresh(); refresh.run();
            }
            close(); return true;
        }
        for (int i=0;i<9;i++) {
            int[] b = box(i);
            if (mx < x+b[0] || mx >= x+b[0]+b[2] || my < y+b[1] || my >= y+b[1]+b[3]) continue;
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
        var texture = chiseled_enchanting_table.ChiseledEnchantingTable.identifier("gui/tome_directory.png");
        ctx.blit(texture, x-1,y-1,x+152,y+44,0,1,0,1);
        String[] labels = {
            model.enchantment == null ? text("all_enchantments") : enchantName(model.enchantment, player),
            text("lock."+model.lock.name().toLowerCase(Locale.ROOT)),
            text("remaining", model.remaining == null ? text("any") : model.remaining),
            text("total", model.total == null ? text("any") : model.total),
            text("sort_by", text("sort."+model.sort.name().toLowerCase(Locale.ROOT))),
            model.descending ? "↓" : "↑", text("reset"),
            text("selection",model.selected().size(),model.visible().size()), text("clear")
        };
        boolean hovered = false;
        for (int i=0;i<labels.length;i++) {
            int[] b=box(i); int bx=x+b[0], by=y+b[1];
            boolean over=mx>=bx && mx<bx+b[2] && my>=by && my<by+b[3]; hovered |= over;
            ctx.fill(bx,by,bx+b[2],by+b[3],over ? 0xFF345E59 : 0xDC142A2A);
            RowRenderSupport.renderCenteredBoxText(ctx,font,labels[i],bx+1,b[2]-2,by+5,0.60f,0xFFE0EBDD);
            if (over && open < 0) TooltipPresentation.show(ctx,font,List.of(Component.literal(labels[i]),
                Component.translatable(PREFIX+"help")),Optional.empty(),mx,my,null);
        }
        if (open >= 0) {
            int count=Math.min(7,choices.size()-offset);
            ctx.fill(x-1,y+43,x+152,y+45+count*11,0xFFF1CD7A);
            for (int j=0;j<count;j++) {
                int by=y+44+j*11;
                boolean over=mx>=x && mx<x+151 && my>=by && my<by+11;
                ctx.fill(x,by,x+151,by+11,over?0xFF32675F:0xFF142222);
                RowRenderSupport.renderCenteredBoxText(ctx,font,choices.get(offset+j).label(),x+2,147,by+5.5f,0.70f,0xFFE0EBDD);
            }
            return true;
        }
        return hovered;
    }
}

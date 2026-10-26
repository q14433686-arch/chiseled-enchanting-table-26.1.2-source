package chiseled_enchanting_table.gui;

import chiseled_enchanting_table.menu.ChiseledEnchantingTableScreenHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Chinese UI contracts checked in the real SDL client, not just translation-file searches. */
public final class UiPresentationProbe {
    private static void check(boolean pass, String message) {
        if (!pass) throw new AssertionError("UI presentation: " + message);
    }
    public static void verify(Minecraft client, ChiseledEnchantingTableScreenHandler menu) {
        check(TableSkin.TAB_Y_OFFSET + TableSkin.TAB_H <= -2, "tabs overlap the header");
        check(TableSkin.SHELF_HUD_X >= 120 && TableSkin.SHELF_HUD_X + TableSkin.SHELF_HUD_W <= 326,
            "bookshelf text crosses title/gold corner safe areas");
        check(TableSkin.SHELF_HUD_Y > 0 && TableSkin.SHELF_HUD_Y + TableSkin.SHELF_HUD_H < 15,
            "bookshelf status crosses the header edge");
        String header = Component.translatable("gui.chiseled_enchanting_table.header.shelf_hud_max", 0, 30).getString();
        check(header.contains("普通书架") && header.contains("书架总数"), "Chinese full labels were not loaded");
        check(!header.contains("木 0"), "ambiguous one-character status remains");
        check(!TooltipPresentation.detailsRequested(), "default hover unexpectedly expands details");
        var hover = ResonanceTooltip.build(menu, null);
        check(hover.size() <= 6, "default bookshelf hover became a mechanics wall");
        check(hover.stream().noneMatch(line -> line.getString().contains("ΣL")), "technical appendix leaked into default hover");
        for (String state : new String[] { "active", "inactive" }) {
            for (String tab : new String[] { "inscribe", "cleanse", "tome" }) {
                var id = chiseled_enchanting_table.ChiseledEnchantingTable.identifier("gui/tab_" + tab + "_" + state + ".png");
                try (var stream = client.getResourceManager().getResource(id).orElseThrow().open();
                     var image = com.mojang.blaze3d.platform.NativeImage.read(stream)) {
                    check(image.getWidth() == 150 && image.getHeight() == 36, "tab textures do not share geometry");
                } catch (java.io.IOException error) { throw new AssertionError(error); }
            }
        }
        System.out.println("UI BOUNDARY PASS: real Chinese full labels + disjoint tabs/header/gold-corner geometry + compact hover + matching tab texture geometry");
    }
}

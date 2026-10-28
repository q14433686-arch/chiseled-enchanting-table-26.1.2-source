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
        TomeDirectoryProbe.verify(client);
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
        check(hover.size() <= 7, "default bookshelf hover became a mechanics wall");
        check(hover.stream().noneMatch(line -> line.getString().contains("ΣL")), "technical appendix leaked into default hover");
        TooltipPresentation.setForceDetailsForTest(true);
        try {
            check(TooltipPresentation.detailsRequested(), "Shift-detail override failed to engage");
            var expandedHover = ResonanceTooltip.build(menu, null);
            check(expandedHover.size() >= hover.size(), "Shift-detail hover did not expand");
        } finally {
            TooltipPresentation.setForceDetailsForTest(false);
        }
        check(!TooltipPresentation.detailsRequested(), "Shift-detail override leaked after reset");
        for (String state : new String[] { "active", "inactive" }) {
            for (String tab : new String[] { "inscribe", "cleanse", "tome" }) {
                var id = chiseled_enchanting_table.ChiseledEnchantingTable.identifier("gui/tab_" + tab + "_" + state + ".png");
                try (var stream = client.getResourceManager().getResource(id).orElseThrow().open();
                     var image = com.mojang.blaze3d.platform.NativeImage.read(stream)) {
                    check(image.getWidth() == 150 && image.getHeight() == 36, "tab textures do not share geometry");
                } catch (java.io.IOException error) { throw new AssertionError(error); }
            }
        }
        checkTextureSize(client, TableSkin.RITUAL_PANEL, 238, 122);
        checkTextureSize(client, TableSkin.BTN_SACRIFICE_PLAQUE, 160, 64);
        checkTextureSize(client, TableSkin.BADGE_COST_CYCLE, 32, 32);
        for (var boxId : new net.minecraft.resources.Identifier[] {
            TableSkin.SEARCH_BOX, TableSkin.SEARCH_BOX_FOCUSED,
            TableSkin.CLEANSE_SEARCH_BOX, TableSkin.CLEANSE_SEARCH_BOX_FOCUSED
        }) {
            checkTextureSize(client, boxId, 160, 36);
        }
        for (var iconId : new net.minecraft.resources.Identifier[] {
            TableSkin.BTN_RITUAL_SACRIFICE, TableSkin.BTN_RITUAL_FUEL,
            TableSkin.BTN_RITUAL_RUSH, TableSkin.BTN_RITUAL_ABORT,
            TableSkin.BTN_TOME_MEND, TableSkin.BTN_TOME_SANCTIFY, TableSkin.BTN_TOME_LOCATE,
            TableSkin.BTN_FILTER_AFFORDABLE, TableSkin.BTN_FILTER_EXPAND,
            TableSkin.BTN_PURGE_ALL_CURSES, TableSkin.BTN_CLEANSE_MODE,
            TableSkin.BTN_CLEANSE_AFFORDABLE, TableSkin.BTN_CLEANSE_EXPAND
        }) {
            checkTextureSize(client, iconId, 64, 64);
        }
        check(Component.translatable("gui.chiseled_enchanting_table.inscribe.search_placeholder").getString().contains("附魔"),
            "inscribe search placeholder localization missing");
        check(Component.translatable("gui.chiseled_enchanting_table.cleanse_filter.placeholder").getString().contains("洗练")
            || Component.translatable("gui.chiseled_enchanting_table.cleanse_filter.placeholder").getString().contains("附魔"),
            "cleanse search placeholder localization missing");
        System.out.println("UI BOUNDARY PASS: real Chinese full labels + disjoint tabs/header/gold-corner geometry + compact hover + matching tab & custom ritual/tome/filter texture geometry");
    }

    private static void checkTextureSize(Minecraft client, net.minecraft.resources.Identifier id, int w, int h) {
        try (var stream = client.getResourceManager().getResource(id).orElseThrow().open();
             var image = com.mojang.blaze3d.platform.NativeImage.read(stream)) {
            check(image.getWidth() == w && image.getHeight() == h,
                id + " expected " + w + "x" + h + " but got " + image.getWidth() + "x" + image.getHeight());
        } catch (java.io.IOException error) { throw new AssertionError(error); }
    }
}

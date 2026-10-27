package chiseled_enchanting_table.gui;
import chiseled_enchanting_table.ChiseledEnchantingTable;
import net.minecraft.resources.Identifier;
/** Shared immutable panel geometry and visual resources; the screen owns input routing. */
final class TableSkin {
    private TableSkin() {}
    static final Identifier BACKGROUND = ChiseledEnchantingTable.identifier("gui/chiseled_enchanting_table.png");
    static final Identifier EPIPHANY_PANEL = ChiseledEnchantingTable.identifier("gui/epiphany_panel.png");
    static final Identifier EPIPHANY_PANEL_FROZEN = ChiseledEnchantingTable.identifier("gui/epiphany_panel_frozen.png");
    static final Identifier CLEANSE_PANEL = ChiseledEnchantingTable.identifier("gui/cleanse_panel.png");
    static final Identifier RITUAL_PANEL = ChiseledEnchantingTable.identifier("gui/ritual_panel.png");
    static final Identifier BTN_FREEZE = ChiseledEnchantingTable.identifier("gui/btn_freeze.png");
    static final Identifier BTN_REROLL = ChiseledEnchantingTable.identifier("gui/btn_reroll.png");
    static final Identifier BTN_CLEANSE_ALL = ChiseledEnchantingTable.identifier("gui/btn_cleanse_all.png");
    static final Identifier BTN_GRIMOIRE = ChiseledEnchantingTable.identifier("gui/btn_grimoire.png");
    static final Identifier BTN_CONFIG = ChiseledEnchantingTable.identifier("gui/btn_config.png");
    static final Identifier BTN_SACRIFICE_PLAQUE = ChiseledEnchantingTable.identifier("gui/btn_sacrifice_plaque.png");
    static final Identifier BTN_RITUAL_SACRIFICE = ChiseledEnchantingTable.identifier("gui/btn_ritual_sacrifice.png");
    static final Identifier BTN_RITUAL_FUEL = ChiseledEnchantingTable.identifier("gui/btn_ritual_fuel.png");
    static final Identifier BTN_RITUAL_RUSH = ChiseledEnchantingTable.identifier("gui/btn_ritual_rush.png");
    static final Identifier BTN_RITUAL_ABORT = ChiseledEnchantingTable.identifier("gui/btn_ritual_abort.png");
    static final Identifier BTN_TOME_MEND = ChiseledEnchantingTable.identifier("gui/btn_tome_mend.png");
    static final Identifier BTN_TOME_SANCTIFY = ChiseledEnchantingTable.identifier("gui/btn_tome_sanctify.png");
    static final Identifier BTN_TOME_LOCATE = ChiseledEnchantingTable.identifier("gui/btn_tome_locate.png");
    static final Identifier BTN_FILTER_AFFORDABLE = ChiseledEnchantingTable.identifier("gui/btn_filter_affordable.png");
    static final Identifier BTN_FILTER_EXPAND = ChiseledEnchantingTable.identifier("gui/btn_filter_expand.png");
    static final Identifier BADGE_COST_CYCLE = ChiseledEnchantingTable.identifier("gui/badge_cost_cycle.png");
    static final Identifier SEARCH_BOX = ChiseledEnchantingTable.identifier("gui/config/search_box.png");
    static final Identifier SEARCH_BOX_FOCUSED = ChiseledEnchantingTable.identifier("gui/config/search_box_focused.png");

    static final int CONFIG_BTN_X = 293;
    static final int CONFIG_BTN_Y_OFFSET = -20;
    static final int CONFIG_BTN_SIZE = 15;
    static final int GRIMOIRE_BTN_X = 311;
    static final int GRIMOIRE_BTN_Y_OFFSET = -20;
    static final int GRIMOIRE_BTN_SIZE = 15;

    static final Identifier TAB_INSCRIBE_ACTIVE = ChiseledEnchantingTable.identifier("gui/tab_inscribe_active.png");
    static final Identifier TAB_INSCRIBE_INACTIVE = ChiseledEnchantingTable.identifier("gui/tab_inscribe_inactive.png");
    static final Identifier TAB_CLEANSE_ACTIVE = ChiseledEnchantingTable.identifier("gui/tab_cleanse_active.png");
    static final Identifier TAB_CLEANSE_INACTIVE = ChiseledEnchantingTable.identifier("gui/tab_cleanse_inactive.png");
    static final Identifier TAB_TOME_ACTIVE = ChiseledEnchantingTable.identifier("gui/tab_tome_active.png");
    static final Identifier TAB_TOME_INACTIVE = ChiseledEnchantingTable.identifier("gui/tab_tome_inactive.png");

    // Top mode switcher tabs aligned pixel-for-pixel with the purple header cloth [x = 14 .. 167].
    static final int TAB_W = 75;
    static final int TAB_H = 18;
    static final int TAB_Y_OFFSET = -21;
    static final int TAB_INSCRIBE_X = 14;
    static final int TAB_CLEANSE_X = 92;
    static final int TAB_TOMES_X = 170;
    static final int SACRIFICE_BTN_X = 248;
    static final int SACRIFICE_BTN_W = 40;

    // Integrated Sanctum / Cleanse Console coordinates inside the cohesive 340x166 window chassis.
    static final int PANEL_X = TomeDirectoryLayout.PANEL_X;
    static final int PANEL_Y = TomeDirectoryLayout.PANEL_Y;
    static final int PANEL_W = TomeDirectoryLayout.PANEL_W;
    static final int PANEL_H = TomeDirectoryLayout.PANEL_H;

    // Text-only bookshelf status inside the header safe area; no frame over the gold corner or tabs.
    static final int SHELF_HUD_X = 124;
    static final int SHELF_HUD_Y = 3;
    static final int SHELF_HUD_W = 200;
    static final int SHELF_HUD_H = 9;
    static final long DISENCHANT_CONFIRM_WINDOW_MS = 5_000L;

    static final int BTN_SIZE = 14;
    static final int FREEZE_BTN_X = PANEL_X + 79;
    static final int FREEZE_BTN_Y = PANEL_Y + 35;
    static final int REROLL_BTN_X = PANEL_X + 95;
    static final int REROLL_BTN_Y = PANEL_Y + 35;
    static final int CLEANSE_ALL_BTN_X = PANEL_X + 95;
    static final int CLEANSE_ALL_BTN_Y = PANEL_Y + 35;

    static final int SKIP_SACRIFICE_BTN_X = PANEL_X + 10;
    static final int SKIP_SACRIFICE_BTN_Y = PANEL_Y + 36;
    static final int SKIP_BTN_W = 48;
    static final int SKIP_BTN_H = 14;
    static final int SKIP_RUSH_BTN_X = PANEL_X + 61;
    static final int SKIP_RUSH_BTN_Y = PANEL_Y + 36;

}

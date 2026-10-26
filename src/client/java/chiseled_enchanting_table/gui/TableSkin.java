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
    static final Identifier BTN_FREEZE = ChiseledEnchantingTable.identifier("gui/btn_freeze.png");
    static final Identifier BTN_REROLL = ChiseledEnchantingTable.identifier("gui/btn_reroll.png");
    static final Identifier BTN_CLEANSE_ALL = ChiseledEnchantingTable.identifier("gui/btn_cleanse_all.png");
    static final Identifier BTN_GRIMOIRE = ChiseledEnchantingTable.identifier("gui/btn_grimoire.png");
    static final Identifier BTN_CONFIG = ChiseledEnchantingTable.identifier("gui/btn_config.png");

    static final int CONFIG_BTN_X = 293;
    static final int CONFIG_BTN_Y_OFFSET = -15;
    static final int CONFIG_BTN_SIZE = 15;
    static final int GRIMOIRE_BTN_X = 311;
    static final int GRIMOIRE_BTN_Y_OFFSET = -15;
    static final int GRIMOIRE_BTN_SIZE = 15;

    static final Identifier TAB_INSCRIBE_ACTIVE = ChiseledEnchantingTable.identifier("gui/tab_inscribe_active.png");
    static final Identifier TAB_INSCRIBE_INACTIVE = ChiseledEnchantingTable.identifier("gui/tab_inscribe_inactive.png");
    static final Identifier TAB_CLEANSE_ACTIVE = ChiseledEnchantingTable.identifier("gui/tab_cleanse_active.png");
    static final Identifier TAB_CLEANSE_INACTIVE = ChiseledEnchantingTable.identifier("gui/tab_cleanse_inactive.png");
    static final Identifier[] TOME_BUTTONS = {
        ChiseledEnchantingTable.identifier("gui/tome_button_0.png"),
        ChiseledEnchantingTable.identifier("gui/tome_button_1.png"),
        ChiseledEnchantingTable.identifier("gui/tome_button_2.png"),
        ChiseledEnchantingTable.identifier("gui/tome_button_3.png"),
        ChiseledEnchantingTable.identifier("gui/tome_button_4.png")
    };
    static final Identifier TAB_TOME_ACTIVE = ChiseledEnchantingTable.identifier("gui/tab_tome_active.png");
    static final Identifier TAB_TOME_INACTIVE = ChiseledEnchantingTable.identifier("gui/tab_tome_inactive.png");

    // Top mode switcher tabs aligned pixel-for-pixel with the purple header cloth [x = 14 .. 167].
    static final int TAB_W = 75;
    static final int TAB_H = 18;
    static final int TAB_Y_OFFSET = -15;
    static final int TAB_INSCRIBE_X = 14;
    static final int TAB_CLEANSE_X = 92;
    static final int TAB_TOMES_X = 170;
    static final int SACRIFICE_BTN_X = 248;
    static final int SACRIFICE_BTN_W = 40;

    // Integrated Sanctum / Cleanse Console coordinates inside the cohesive 340x166 window chassis.
    static final int PANEL_X = 214;
    static final int PANEL_Y = 18;
    static final int PANEL_W = 119;
    static final int PANEL_H = 61;

    // Compact persistent W / S bookshelf HUD badge in the stitched header bar directly above PANEL_X.
    static final int SHELF_HUD_X = PANEL_X;
    static final int SHELF_HUD_Y = 2;
    static final int SHELF_HUD_W = 118;
    static final int SHELF_HUD_H = 12;
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

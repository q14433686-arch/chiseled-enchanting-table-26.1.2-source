package chiseled_enchanting_table.gui.config;
import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.config.ConfigCatalog.CategoryId;
import net.minecraft.resources.Identifier;
/** Shared immutable configuration visual tokens, not editor state. */
final class ConfigSkin {
    private ConfigSkin() {}
    // ---- Color Palette (Obsidian / Crying Amethyst / Gilded Brass) ----
    static final int BG_SCRIM = 0xD206040D;
    static final int GOLD_TEXT = 0xFFFFE599;
    static final int CREAM_TEXT = 0xFFF5EEFF;
    static final int MUTED_TEXT = 0xFF9F91B8;
    static final int DIM_TEXT = 0xFF75688C;
    static final int EMERALD_TEXT = 0xFF7CFFA8;
    static final int CYAN_TEXT = 0xFF82EEFF;
    static final int AMBER_TEXT = 0xFFFFC978;
    static final int CORAL_TEXT = 0xFFFF8585;


    // ---- Modular 2x HD Pixel-Art Layers (assets/chiseled_enchanting_table/gui/config/) ----
    static final Identifier MODAL_FRAME = ChiseledEnchantingTable.identifier("gui/config/modal_frame.png");
    static final Identifier FRAME_GEM_PIP = ChiseledEnchantingTable.identifier("gui/config/frame_gem_pip.png");
    static final Identifier EMBLEM_TRACE_LARGE = ChiseledEnchantingTable.identifier("gui/config/emblem_trace_large.png");

    static final Identifier RAIL_TAB = ChiseledEnchantingTable.identifier("gui/config/rail_tab.png");
    static final Identifier RAIL_TAB_ACTIVE = ChiseledEnchantingTable.identifier("gui/config/rail_tab_active.png");

    static final Identifier SECTION_PLAQUE = ChiseledEnchantingTable.identifier("gui/config/section_plaque.png");
    static final Identifier ROW_PLAQUE = ChiseledEnchantingTable.identifier("gui/config/row_plaque.png");
    static final Identifier ROW_PLAQUE_HOVER = ChiseledEnchantingTable.identifier("gui/config/row_plaque_hover.png");
    static final Identifier ROW_PLAQUE_ATOM = ChiseledEnchantingTable.identifier("gui/config/row_plaque_atom.png");
    static final Identifier ROW_PLAQUE_DISABLED = ChiseledEnchantingTable.identifier("gui/config/row_plaque_disabled.png");

    static final Identifier BTN_PLAQUE = ChiseledEnchantingTable.identifier("gui/config/btn_plaque.png");
    static final Identifier BTN_PLAQUE_HOVER = ChiseledEnchantingTable.identifier("gui/config/btn_plaque_hover.png");
    static final Identifier BTN_PLAQUE_GOLD = ChiseledEnchantingTable.identifier("gui/config/btn_plaque_gold.png");
    static final Identifier BTN_PLAQUE_DISABLED = ChiseledEnchantingTable.identifier("gui/config/btn_plaque_disabled.png");

    static final Identifier MICRO_BTN = ChiseledEnchantingTable.identifier("gui/config/micro_btn.png");
    static final Identifier MICRO_BTN_HOVER = ChiseledEnchantingTable.identifier("gui/config/micro_btn_hover.png");
    static final Identifier MICRO_BTN_DISABLED = ChiseledEnchantingTable.identifier("gui/config/micro_btn_disabled.png");
    static final Identifier ICON_RESET = ChiseledEnchantingTable.identifier("gui/config/icon_reset.png");
    static final Identifier ICON_RESET_DISABLED = ChiseledEnchantingTable.identifier("gui/config/icon_reset_disabled.png");

    static final Identifier SEARCH_BOX = ChiseledEnchantingTable.identifier("gui/config/search_box.png");
    static final Identifier SEARCH_BOX_FOCUS = ChiseledEnchantingTable.identifier("gui/config/search_box_focus.png");

    static final Identifier TOGGLE_ON = ChiseledEnchantingTable.identifier("gui/config/toggle_on.png");
    static final Identifier TOGGLE_OFF = ChiseledEnchantingTable.identifier("gui/config/toggle_off.png");
    static final Identifier TOGGLE_LOCKED = ChiseledEnchantingTable.identifier("gui/config/toggle_locked.png");

    static final Identifier SLIDER_TRACK = ChiseledEnchantingTable.identifier("gui/config/slider_track.png");
    static final Identifier SLIDER_FILL = ChiseledEnchantingTable.identifier("gui/config/slider_fill.png");
    static final Identifier SLIDER_KNOB = ChiseledEnchantingTable.identifier("gui/config/slider_knob.png");
    static final Identifier SLIDER_KNOB_HOVER = ChiseledEnchantingTable.identifier("gui/config/slider_knob_hover.png");

    static final Identifier ICON_TRACE = ChiseledEnchantingTable.identifier("gui/config/icon_domain_trace.png");

    static Identifier domainIcon(CategoryId category) {
        return ChiseledEnchantingTable.identifier("gui/config/" + category.iconFile());
    }

}

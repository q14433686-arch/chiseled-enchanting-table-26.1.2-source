package chiseled_enchanting_table.registry;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public final class SoundRegistry {
    public static final SoundEvent GRIMOIRE_OPEN = register("grimoire_open");
    public static final SoundEvent GRIMOIRE_CLOSE = register("grimoire_close");
    public static final SoundEvent GRIMOIRE_PAGE_TURN = register("grimoire_page_turn");
    public static final SoundEvent GRIMOIRE_CHAPTER_SELECT = register("grimoire_chapter_select");
    public static final SoundEvent GRIMOIRE_HOME = register("grimoire_home");
    public static final SoundEvent GRIMOIRE_TAB_SWITCH = register("grimoire_tab_switch");

    private SoundRegistry() {}

    private static SoundEvent register(String path) {
        Identifier id = ChiseledEnchantingTable.identifier(path);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }

    public static void init() {
        // Static fields perform registration when class-loaded during ModInitializer.
    }
}

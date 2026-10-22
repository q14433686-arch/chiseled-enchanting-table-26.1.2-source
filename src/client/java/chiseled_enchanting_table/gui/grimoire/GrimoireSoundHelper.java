package chiseled_enchanting_table.gui.grimoire;

import chiseled_enchanting_table.registry.SoundRegistry;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;

/**
 * Tactile UI foley helper for {@link ArcaneGrimoireScreen}.
 * Uses dedicated Vorbis sound assets registered in {@link SoundRegistry} with subtle random pitch
 * variation so consecutive interactions feel natural rather than mechanical.
 */
@Environment(EnvType.CLIENT)
public final class GrimoireSoundHelper {
    private GrimoireSoundHelper() {}

    private static float jitter(float min, float max) {
        return min + ThreadLocalRandom.current().nextFloat() * (max - min);
    }

    public static void playOpen() {
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forUI(SoundRegistry.GRIMOIRE_OPEN, jitter(0.98f, 1.03f), 0.90f)
        );
    }

    public static void playClose() {
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forUI(SoundRegistry.GRIMOIRE_CLOSE, jitter(0.97f, 1.02f), 0.85f)
        );
    }

    public static void playPageTurn() {
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forUI(SoundRegistry.GRIMOIRE_PAGE_TURN, jitter(0.95f, 1.06f), 0.88f)
        );
    }

    public static void playChapterSelect() {
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forUI(SoundRegistry.GRIMOIRE_CHAPTER_SELECT, jitter(0.98f, 1.03f), 0.86f)
        );
    }

    public static void playHome() {
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forUI(SoundRegistry.GRIMOIRE_HOME, jitter(0.99f, 1.02f), 0.86f)
        );
    }

    public static void playTabSwitch() {
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forUI(SoundRegistry.GRIMOIRE_TAB_SWITCH, jitter(0.96f, 1.05f), 0.78f)
        );
    }
}

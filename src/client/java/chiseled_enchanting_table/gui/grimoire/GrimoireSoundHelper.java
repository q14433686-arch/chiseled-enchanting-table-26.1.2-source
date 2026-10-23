package chiseled_enchanting_table.gui.grimoire;

import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/**
 * Native vanilla book &amp; UI audio helper for {@link ArcaneGrimoireScreen}, using built-in
 * Minecraft {@link SoundEvents#BOOK_PAGE_TURN}, {@link SoundEvents#BOOK_PUT}, and
 * {@link SoundEvents#UI_BUTTON_CLICK} sounds with subtle natural pitch variation.
 */
@Environment(EnvType.CLIENT)
public final class GrimoireSoundHelper {

    private GrimoireSoundHelper() {}

    public static void playOpen() {
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 0.94F, 1.0F)
        );
    }

    public static void playClose() {
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forUI(SoundEvents.BOOK_PUT, 0.96F, 0.85F)
        );
    }

    public static void playPageTurn() {
        float pitch = 0.94F + ThreadLocalRandom.current().nextFloat() * 0.14F;
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, pitch, 1.0F)
        );
    }

    public static void playChapterSelect() {
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.08F, 1.0F)
        );
    }

    public static void playHome() {
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 0.88F, 1.0F)
        );
    }

    public static void playTabSwitch() {
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.12F)
        );
    }
}
